package jp.mintymc.patpat.extension;

import net.kyori.adventure.key.Key;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.geysermc.geyser.entity.type.*;
import org.geysermc.geyser.entity.type.living.ArmorStandEntity;
import org.geysermc.geyser.entity.type.player.PlayerEntity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.*;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.*;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Incoming/outgoing callbacks only dispatch work; inventory/entity access is on Geyser's event loop. */
final class PatSession extends SessionAdapter {
    private final PatPatExtension owner;
    private final GeyserSession session;
    private final Session downstream;
    private final HandRenderer renderer;
    private final Set<Packet> bypass=Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private volatile boolean ready,closed;
    private int generation;
    private long nextPat;
    PatSession(PatPatExtension owner,GeyserSession session) {
        this.owner=owner;this.session=session;this.downstream=session.getDownstream().getSession();
        this.renderer=new HandRenderer(session,owner::settings,owner::animation);
    }
    GeyserSession session(){return session;}
    boolean ready(){return ready;}
    int active(){return renderer.size();}
    void beginHandshake() {
        ready=false;nextPat=0;generation++;renderer.clear();
        if(!closed && owner.settings().enabled()) handshake(generation,0);
    }
    private void handshake(int current,int attempts) {
        if(closed || session.isClosed() || generation!=current || ready || attempts>=5) return;
        if(session.getDownstream()!=null && session.getDownstream().isState(ProtocolState.GAME)) {
            // Re-register only our own channels so new backends also emit their hello.
            payload("minecraft:unregister",PatProtocol.CHANNELS);
            payload("minecraft:register",PatProtocol.CHANNELS);
        }
        session.scheduleInEventLoop(()->handshake(current,attempts+1),1000,TimeUnit.MILLISECONDS);
    }
    private void payload(String channel,byte[] data) {
        session.sendDownstreamGamePacket(new ServerboundCustomPayloadPacket(Key.key(channel),data));
    }
    @Override public void packetReceived(Session network,Packet packet) {
        if(closed) return;
        if(packet instanceof ClientboundLoginPacket || packet instanceof ClientboundRespawnPacket) {
            // Backend switches and dimension/respawn resets may discard the old channels/entities.
            ready=false;
            session.executeInEventLoop(this::beginHandshake);
        } else if(packet instanceof ClientboundCustomPayloadPacket custom) {
            String channel=custom.getChannel().asString();
            if(channel.equals(PatProtocol.HELLO_S2C)) {
                boolean supported=PatProtocol.supportedHello(custom.getData());
                session.executeInEventLoop(()->{
                    if(closed || !owner.settings().enabled()) return;
                    ready=supported;
                    if(ready) payload(PatProtocol.HELLO_C2S,PatProtocol.hello());
                });
            } else if(channel.equals(PatProtocol.PAT_S2C)) {
                try {
                    PatProtocol.Pat pat=PatProtocol.decode(custom.getData());
                    session.executeInEventLoop(()->{if(!closed && owner.settings().enabled())renderer.show(pat.target(),false);});
                } catch(IllegalArgumentException ignored) { /* Ignore malformed packets without log spam. */ }
            }
        }
    }
    @Override public void packetSending(PacketSendingEvent event) {
        Packet packet=event.getPacket();
        if(bypass.remove(packet) || closed || event.isCancelled() || !ready || !owner.settings().enabled()) return;
        if(packet instanceof ServerboundInteractPacket interact) {
            if(interact.getHand()!=Hand.MAIN_HAND
                    || (owner.settings().sneak() && !interact.isSneaking() && !session.isSneaking())) return;
        } else if(packet instanceof ServerboundUseItemPacket use) {
            // Bedrock can send "use item in air" instead of "interact entity" for a
            // crouched right-click on a player with no native Bedrock interaction.
            // Only reinterpret it while crouching AND a specific entity is hovered.
            if(use.getHand()!=Hand.MAIN_HAND || !session.isSneaking() || session.getMouseoverEntity()==null) return;
        } else return;
        event.setCancelled(true);
        session.executeInEventLoop(()->{
            if(closed || session.isClosed()) return;
            Entity target=packet instanceof ServerboundInteractPacket interact
                    ? session.getEntityCache().getEntityByJavaId(interact.getEntityId())
                    : session.getMouseoverEntity();
            if(!attempt(target)) {
                // Preserve vanilla behavior when the aim, permissions, or range are invalid.
                // Bypass only this packet instance, not unrelated plugins or later clicks.
                bypass.add(packet);session.sendDownstreamGamePacket(packet);
            }
        });
    }
    private boolean eligible(Entity entity) {
        Settings cfg=owner.settings();
        if(entity==null || !(entity instanceof LivingEntity) || entity instanceof ArmorStandEntity
                || !entity.isValid() || entity.javaId()==session.getPlayerEntity().javaId()
                || entity.getFlag(EntityFlag.INVISIBLE) || session.getGameMode()==GameMode.SPECTATOR
                || session.getPlayerEntity().getHealth()<=0 || !cfg.allows(session)) return false;
        if(!cfg.players() && entity instanceof PlayerEntity) return false;
        if(cfg.emptyHand() && !session.getPlayerInventory().getItemInHand().isEmpty()) return false;
        Vector3f eye=session.getPlayerEntity().position().add(0,session.isSneaking()?1.27f:1.62f,0);
        Vector3f pos=entity.position();double half=entity.getBoundingBoxWidth()/2d;
        double dx=axis(eye.getX(),pos.getX()-half,pos.getX()+half);
        double dy=axis(eye.getY(),pos.getY(),pos.getY()+entity.getBoundingBoxHeight());
        double dz=axis(eye.getZ(),pos.getZ()-half,pos.getZ()+half);
        if (dx*dx+dy*dy+dz*dz>cfg.distance()*cfg.distance()) return false;
        // An entity use packet can reach a nearby hitbox while the crosshair is elsewhere.
        // Validate current look direction, not just the interaction entity ID and range.
        return AimCheck.hits(eye,session.getPlayerEntity().getYaw(),session.getPlayerEntity().getPitch(),
                pos,entity.getBoundingBoxWidth(),entity.getBoundingBoxHeight(),cfg.distance()+0.1);
    }
    static double axis(double n,double min,double max){return Math.max(0,Math.max(min-n,n-max));}
    /** true also covers cooldown suppression, to consume duplicate Interact/InteractAt packets. */
    private boolean attempt(Entity target) {
        if(!ready || !owner.settings().enabled() || !eligible(target)) return false;
        long now=System.nanoTime();if(now<nextPat)return true;
        nextPat=now+owner.settings().cooldown()*1_000_000L;
        payload(PatProtocol.PAT_C2S,PatProtocol.pat(target.javaId()));
        if(owner.settings().localPreview()) renderer.show(target.javaId(),false);
        return true;
    }
    void commandPat() {
        if(!ready){session.sendMessage("PatPat本体の応答がありません。バックエンドにPatPat Plugin 1.2.5+があるか確認してください。");return;}
        Entity target=session.getMouseoverEntity();
        if(!attempt(target))session.sendMessage("撫でられる対象がいません。対象に照準を合わせ、空の手と距離を確認してください。");
    }
    void test(boolean text) {
        Entity target=session.getMouseoverEntity();
        if(target==null || !target.isValid())target=session.getPlayerEntity();
        renderer.show(target.javaId(),text);
        session.sendMessage("表示テスト（自分のみ）。対象がなければ自分の頭上です。backendHello="+ready);
    }
    void clear(){renderer.clear();}
    void close() {
        closed=true;ready=false;downstream.removeListener(this);bypass.clear();
        if(!session.isClosed())session.executeInEventLoop(renderer::clear);
    }
}
