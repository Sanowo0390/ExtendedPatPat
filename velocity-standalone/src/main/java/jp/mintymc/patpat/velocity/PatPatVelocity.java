package jp.mintymc.patpat.velocity;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPosition;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPositionAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerJoinGame;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPluginMessage;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.scheduler.ScheduledTask;
import jakarta.inject.Inject;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Plugin(
    id="patpatvelocity",
    name="PatPatVelocity",
    version="0.1.1",
    authors={"Sanowo0390"},
    dependencies={@Dependency(id="packetevents")}
)
public final class PatPatVelocity {
    private static final String JAVA_TO_BEDROCK="jp.mintymc.patpat.bridge.v1.java-to-bedrock";
    private static final String BEDROCK_TO_JAVA="jp.mintymc.patpat.bridge.v1.bedrock-to-java";
    private static final String BEDROCK_USERS="jp.mintymc.patpat.bridge.v1.bedrock-users";
    // These names and packet formats are defined by the upstream LopyMine/PatPat-Plugin.
    private static final String REGISTER_CHANNEL="minecraft:register";
    private final ProxyServer proxy;
    private final Logger log;
    private final Path folder;
    private volatile Properties config=new Properties();
    private final Map<UUID,View> views=new ConcurrentHashMap<>();
    private final Set<UUID> disabled=ConcurrentHashMap.newKeySet();
    private final AtomicInteger entitySequence=new AtomicInteger(1_900_000_000);
    private final AtomicInteger active=new AtomicInteger();
    private final Consumer<Object[]> bedrockConsumer=this::receiveBedrockPat;

    @Inject
    public PatPatVelocity(ProxyServer proxy,Logger log,@DataDirectory Path folder) {
        this.proxy=proxy;this.log=log;this.folder=folder;
    }

    @Subscribe
    public void init(ProxyInitializeEvent event) {
        reload();
        PacketEvents.getAPI().getEventManager().registerListener(new Listener());
        var meta=proxy.getCommandManager().metaBuilder("patpatvelocity").aliases("ppv").plugin(this).build();
        proxy.getCommandManager().register(meta,new Commands());
        System.getProperties().put(BEDROCK_TO_JAVA,bedrockConsumer);
        log.info("PatPatVelocity active; Java: PacketEvents virtual BlockDisplays, Bedrock: optional Geyser bridge");
    }

    @Subscribe
    public void shutdown(ProxyShutdownEvent event) {
        if(System.getProperties().get(BEDROCK_TO_JAVA)==bedrockConsumer)
            System.getProperties().remove(BEDROCK_TO_JAVA);
        views.clear();
    }

    @Subscribe
    public void switchServer(ServerConnectedEvent event) {
        // A server switch invalidates entity IDs, hello status and player preferences
        // negotiated with the previous backend (but not the persistent opt-out).
        Player player=event.getPlayer();
        UUID uuid=player.getUniqueId();
        View replacement=new View(uuid,null);
        replacement.backend=serverOf(player);
        views.put(uuid,replacement);
        if(!enabled("official-integration-enabled",true) || isBedrock(uuid))return;
        // The official Paper plugin sends HELLO only after the client has registered
        // its incoming channels (PlayerRegisterChannelEvent). The proxy does this for vanilla.
        for(int attempt=0;attempt<3;attempt++) {
            final int retry=attempt;
            proxy.getScheduler().buildTask(this,()->registerPatPatChannels(player,replacement))
                .delay(350L+retry*1000L,TimeUnit.MILLISECONDS).schedule();
        }
    }

    private void registerPatPatChannels(Player actor,View state) {
        if(!actor.isActive() || views.get(actor.getUniqueId())!=state
            || state.officialHello || state.realMod || isBedrock(actor.getUniqueId()))return;
        actor.getCurrentServer().ifPresent(server->{
            if(!java.util.Objects.equals(state.backend,server.getServerInfo().getName()))return;
            server.sendPluginMessage(MinecraftChannelIdentifier.from(REGISTER_CHANNEL),PatPatProtocol.REGISTERED_CHANNELS);
        });
    }

    private boolean sendBackend(Player player,String channel,byte[] payload) {
        return player.getCurrentServer().map(server->server.sendPluginMessage(
            MinecraftChannelIdentifier.from(channel),payload)).orElse(false);
    }

    @Subscribe
    public void disconnect(DisconnectEvent event) {
        views.remove(event.getPlayer().getUniqueId());
    }

    private void reload() {
        try {
            Files.createDirectories(folder);
            Path file=folder.resolve("config.properties");
            if(!Files.exists(file))try(InputStream in=getClass().getResourceAsStream("/config.properties")) {
                if(in!=null)Files.copy(in,file);
            }
            Properties loaded=new Properties();
            try(var in=Files.newInputStream(file)){loaded.load(in);}
            config=loaded;
            disabled.clear();
            Path disabledFile=folder.resolve("disabled-players.txt");
            if(Files.exists(disabledFile))for(String line:Files.readAllLines(disabledFile)) {
                try { disabled.add(UUID.fromString(line.trim())); }catch(IllegalArgumentException ignored) {}
            }
        } catch(IOException exception) { log.error("Unable to load PatPatVelocity configuration",exception); }
    }
    private void persistDisabled() {
        try { Files.write(folder.resolve("disabled-players.txt"),
            disabled.stream().map(UUID::toString).sorted().toList()); }
        catch(IOException exception) { log.error("Unable to save personal preference",exception); }
    }
    private boolean enabled(String key,boolean fallback) {
        return Boolean.parseBoolean(config.getProperty(key,Boolean.toString(fallback)));
    }
    private String text(String key,String fallback) {return config.getProperty(key,fallback).trim();}
    private double number(String key,double fallback,double lo,double hi) {
        try {double n=Double.parseDouble(text(key,Double.toString(fallback)));
            return Double.isFinite(n)?Math.max(lo,Math.min(hi,n)):fallback;}
        catch(NumberFormatException ignored) {return fallback;}
    }
    private boolean isBedrock(UUID uuid) {
        Object value=System.getProperties().get(BEDROCK_USERS);
        return value instanceof Set<?> set && set.contains(uuid);
    }

    private static final class Tracked {
        final int id;
        final UUID uuid;
        final String type;
        volatile Vector3d pos;
        Tracked(int id,UUID uuid,String type,Vector3d pos) {
            this.id=id;this.uuid=uuid;this.type=type;this.pos=pos;
        }
    }
    private static final class View {
        final UUID player;
        volatile User user;
        volatile Vector3d self;
        final Map<Integer,Tracked> byId=new ConcurrentHashMap<>();
        final Map<UUID,Tracked> byUuid=new ConcurrentHashMap<>();
        volatile long lastPat;
        final long connectedAt=System.nanoTime();
        volatile String backend;
        volatile boolean officialHello;
        volatile boolean realMod;
        volatile int selfEntityId=Integer.MIN_VALUE;
        View(UUID player,User user){this.player=player;this.user=user;}
        void track(Tracked target) {
            Tracked old=byId.put(target.id,target);
            if(old!=null)byUuid.remove(old.uuid,old);
            byUuid.put(target.uuid,target);
        }
        void remove(int id) {
            Tracked old=byId.remove(id);
            if(old!=null)byUuid.remove(old.uuid,old);
        }
        Tracked find(UUID target) {return byUuid.get(target);}
    }
    private View view(UUID uuid,User user) {
        View view=views.computeIfAbsent(uuid,u->new View(uuid,user));
        view.user=user;
        return view;
    }

    private final class Listener extends PacketListenerAbstract {
        @Override public void onPacketSend(PacketSendEvent event) {
            UUID userId=event.getUser().getUUID();
            if(userId==null) return;
            var type=event.getPacketType();
            View view=view(userId,event.getUser());
            try {
                if(type==com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.PLUGIN_MESSAGE) {
                    onBackendMessage(userId,view,new WrapperPlayServerPluginMessage(event));
                } else if(type==com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.SPAWN_ENTITY) {
                    var packet=new WrapperPlayServerSpawnEntity(event);
                    if(packet.getEntityId()>=1_900_000_000)return;
                    Optional<UUID> uuid=packet.getUUID();
                    if(uuid.isPresent())view.track(new Tracked(packet.getEntityId(),uuid.get(),
                        packet.getEntityType().getName().toString(),packet.getPosition()));
                } else if(type==com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.ENTITY_TELEPORT) {
                    var packet=new WrapperPlayServerEntityTeleport(event);
                    Tracked old=view.byId.get(packet.getEntityId());
                    if(old!=null)old.pos=packet.getPosition();
                } else if(type==com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.ENTITY_POSITION_SYNC) {
                    var packet=new WrapperPlayServerEntityPositionSync(event);
                    Tracked old=view.byId.get(packet.getId());
                    if(old!=null)old.pos=packet.getValues().getPosition();
                } else if(type==com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
                    var packet=new WrapperPlayServerEntityRelativeMove(event);
                    Tracked old=view.byId.get(packet.getEntityId());
                    if(old!=null)old.pos=packet.getDelta()!=null ? packet.getDelta().apply(old.pos)
                        : add(old.pos,packet.getDeltaX(),packet.getDeltaY(),packet.getDeltaZ());
                } else if(type==com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
                    var packet=new WrapperPlayServerEntityRelativeMoveAndRotation(event);
                    Tracked old=view.byId.get(packet.getEntityId());
                    if(old!=null)old.pos=packet.getDelta()!=null ? packet.getDelta().apply(old.pos)
                        : add(old.pos,packet.getDeltaX(),packet.getDeltaY(),packet.getDeltaZ());
                } else if(type==com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.DESTROY_ENTITIES) {
                    for(int id:new WrapperPlayServerDestroyEntities(event).getEntityIds())view.remove(id);
                } else if(type==com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.RESPAWN
                    || type==com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.JOIN_GAME) {
                    view.byId.clear();view.byUuid.clear();view.self=null;
                    if(type==com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.JOIN_GAME)
                        view.selfEntityId=new WrapperPlayServerJoinGame(event).getEntityId();
                }
            } catch(Exception ex) {log.debug("Ignored malformed entity tracking packet",ex);}
        }
        @Override public void onPacketReceive(PacketReceiveEvent event) {
            UUID uuid=event.getUser().getUUID();
            if(uuid==null)return;
            View view=view(uuid,event.getUser());
            var type=event.getPacketType();
            try {
                if(type==com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.PLUGIN_MESSAGE) {
                    // A real PatPat client already negotiates and renders the official
                    // protocol. Never register a second version or draw duplicate hands.
                    String channel=new WrapperPlayClientPluginMessage(event).getChannelName();
                    if(PatPatProtocol.HELLO_C2S.equals(channel) || PatPatProtocol.PAT_C2S.equals(channel))
                        view.realMod=true;
                    else if(REGISTER_CHANNEL.equals(channel)) {
                        byte[] data=new WrapperPlayClientPluginMessage(event).getData();
                        if(new String(data,StandardCharsets.UTF_8).contains(PatPatProtocol.HELLO_S2C))
                            view.realMod=true;
                    }
                } else if(type==com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.PLAYER_POSITION) {
                    view.self=new WrapperPlayClientPlayerPosition(event).getPosition();
                } else if(type==com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
                    view.self=new WrapperPlayClientPlayerPositionAndRotation(event).getPosition();
                } else if(type==com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.INTERACT_ENTITY) {
                    if(!enabled("enabled",true) || isBedrock(uuid) || disabled.contains(uuid) || view.realMod)return;
                    var p=new WrapperPlayClientInteractEntity(event);
                    if(p.getHand()!=InteractionHand.MAIN_HAND)return;
                    if(enabled("require-sneaking",true) && !p.isSneaking().orElse(false))return;
                    Tracked target=view.byId.get(p.getEntityId());
                    if(target==null || target.uuid.equals(uuid) || !looksLiving(target.type))return;
                    Player actor=proxy.getPlayer(uuid).orElse(null);
                    if(actor==null || actor.getCurrentServer().isEmpty())return;
                    String permission=text("required-permission","");
                    if(!permission.isBlank() && !actor.hasPermission(permission))return;
                    if(view.self!=null && squared(view.self,target.pos)
                            > Math.pow(number("max-distance",4,1,6),2))return;
                    long now=System.nanoTime();
                    boolean official=enabled("official-integration-enabled",true) && view.officialHello;
                    if(!official && enabled("official-integration-enabled",true)
                        && now-view.connectedAt < (long)number("official-detection-ms",3000,250,10000)*1_000_000L)
                        return; // Do not emit a standalone Pat before the backend handshake settles.
                    if(!official && !enabled("standalone-fallback",true))return;
                    long cooldown=(long)number("cooldown-ms",250,0,10000)*1_000_000L;
                    if(now-view.lastPat<cooldown)return;
                    view.lastPat=now;
                    if(enabled("cancel-interaction",false))event.setCancelled(true);
                    UUID targetUuid=target.uuid;
                    if(official) {
                        // Upstream validates: permission, rate limit, whitelist/blacklist,
                        // living target/range, and cancellable PatPacketReceiveEvent.
                        // Only its S2C event is allowed to confirm a Pat.
                        boolean sent=sendBackend(actor,PatPatProtocol.PAT_C2S,PatPatProtocol.request(target.id));
                        if(!sent)log.debug("Cannot send PatPat v2 request to backend {}",view.backend);
                        if(sent && enabled("official-local-preview",false))
                            proxy.getScheduler().buildTask(PatPatVelocity.this,
                                ()->renderToJavaViewer(actor,view,targetUuid)).schedule();
                        return;
                    }
                    proxy.getScheduler().buildTask(PatPatVelocity.this,()->{
                        renderToJava(uuid,targetUuid);
                        if(enabled("bridge-enabled",true))sendToBedrock(uuid,targetUuid);
                    }).schedule();
                }
            } catch(Exception ex) {log.debug("Ignored malformed interaction",ex);}
        }
    }

    /** Only trust an official serverbound approval notification for Java viewers. */
    private void onBackendMessage(UUID viewerId,View view,WrapperPlayServerPluginMessage message) {
        if(!enabled("enabled",true) || !enabled("official-integration-enabled",true)
                || isBedrock(viewerId))return;
        String channel=message.getChannelName();
        if(PatPatProtocol.HELLO_S2C.equals(channel)) {
            if(!PatPatProtocol.supportedHello(message.getData()))return;
            view.officialHello=true;
            if(!view.realMod) {
                proxy.getPlayer(viewerId).ifPresent(player->{
                    if(views.get(viewerId)==view)
                        sendBackend(player,PatPatProtocol.HELLO_C2S,PatPatProtocol.hello());
                });
            }
            return;
        }
        if(!PatPatProtocol.PAT_S2C.equals(channel) || !view.officialHello
                || view.realMod || disabled.contains(viewerId))return;
        try {
            PatPatProtocol.Pat pat=PatPatProtocol.decode(message.getData());
            // Backend entity IDs are scoped to the recipient's current server.
            Tracked target=view.byId.get(pat.target());
            UUID targetUuid=target!=null?target.uuid:
                pat.target()==view.selfEntityId?viewerId:null;
            if(targetUuid==null)return;
            proxy.getPlayer(viewerId).ifPresent(player->
                proxy.getScheduler().buildTask(PatPatVelocity.this,()->
                    renderToJavaViewer(player,view,targetUuid)).schedule());
        }catch(IllegalArgumentException badPacket) {
            log.debug("Malformed PatPat v2 notification from backend {}",view.backend);
        }
    }

    private static boolean looksLiving(String type) {
        // A conservative non-living exclusion list; the official Paper plugin
        // always performs the authoritative LivingEntity check itself.
        String name=type.toLowerCase(java.util.Locale.ROOT);
        return !(name.contains("armor_stand") || name.contains("display") ||
            name.contains("minecart") || name.contains("boat") ||
            name.contains("item") || name.contains("painting") ||
            name.contains("projectile") || name.contains("arrow") ||
            name.contains("fireball") || name.contains("experience_orb") ||
            name.contains("fishing_bobber") || name.contains("leash_knot"));
    }

    private static Vector3d add(Vector3d p,double dx,double dy,double dz) {
        return new Vector3d(p.getX()+dx,p.getY()+dy,p.getZ()+dz);
    }
    private static double squared(Vector3d a,Vector3d b) {
        double x=a.getX()-b.getX(),y=a.getY()-b.getY(),z=a.getZ()-b.getZ();
        return x*x+y*y+z*z;
    }
    private static String serverOf(Player p) {
        return p.getCurrentServer().map(c->c.getServerInfo().getName()).orElse(null);
    }
    private void sendToBedrock(UUID actor,UUID target) {
        Object callback=System.getProperties().get(JAVA_TO_BEDROCK);
        if(callback instanceof Consumer<?> consumer) {
            @SuppressWarnings("unchecked") Consumer<Object[]> bridge=(Consumer<Object[]>)consumer;
            try {bridge.accept(new Object[]{actor,target});}
            catch(Exception ex){log.warn("Geyser bridge refused Pat event: {}",ex.getMessage());}
        }
    }
    private void receiveBedrockPat(Object[] payload) {
        if(payload==null || payload.length!=2 || !(payload[0] instanceof UUID actor)
                || !(payload[1] instanceof UUID target))return;
        if(!enabled("enabled",true) || !enabled("bridge-enabled",true))return;
        proxy.getScheduler().buildTask(this,()->renderToJava(actor,target)).schedule();
    }

    private static final float[][] HAND={
        {-0.150f,-0.160f,0.300f,0.290f},
        {-0.145f,0.105f,0.065f,0.215f},
        {-0.068f,0.130f,0.068f,0.230f},
        {0.012f,0.123f,0.067f,0.220f},
        {0.091f,0.099f,0.062f,0.201f},
        {0.129f,-0.150f,0.105f,0.165f}
    };
    private void renderToJava(UUID actorId,UUID targetId) {
        Player actor=proxy.getPlayer(actorId).orElse(null);
        if(actor==null)return;
        String server=serverOf(actor);
        if(server==null)return;
        for(Player player:proxy.getAllPlayers()) {
            if(isBedrock(player.getUniqueId()) || disabled.contains(player.getUniqueId())
                    || !server.equals(serverOf(player)))continue;
            View view=views.get(player.getUniqueId());
            renderToJavaViewer(player,view,targetId);
        }
    }
    private void renderToJavaViewer(Player player,View view,UUID targetId) {
        if(view==null || views.get(player.getUniqueId())!=view || view.user==null ||
            !player.isActive() || isBedrock(player.getUniqueId()) ||
            disabled.contains(player.getUniqueId()) || view.realMod ||
            !enabled("hand-enabled",true))return;
        Tracked target=view.find(targetId);
        Vector3d pos=target==null && targetId.equals(player.getUniqueId()) ? view.self
            : target==null?null:target.pos;
        if(pos==null || (view.self!=null && squared(view.self,pos)>256))return;
        startHand(view,player,targetId);
        if(enabled("sound-enabled",true)) {
            try {
                String key=text("sound-event","minecraft:block.wool.place");
                float volume=(float)number("sound-volume",0.6,0,4);
                float pitch=(float)number("sound-pitch",1,0.1,4);
                player.playSound(Sound.sound(Key.key(key),Sound.Source.PLAYER,volume,pitch),Sound.Emitter.self());
            } catch(Exception ex) {log.debug("Sound is not available",ex);}
        }
    }
    private void startHand(View view,Player player,UUID targetId) {
        if(active.incrementAndGet()>(int)number("max-active",24,1,128)){active.decrementAndGet();return;}
        final int duration=(int)number("hand-duration-ticks",6,2,40);
        final double depth=number("hand-depth",0.425,0,2);
        final double offset=number("hand-offset-y",0.15,-2,2);
        final float size=(float)number("hand-size",1,0.1,4);
        final int[] fakeIds=new int[HAND.length];
        try {
            int block=WrappedBlockState.getByString(view.user.getClientVersion(),
                text("hand-material","minecraft:pink_terracotta")).getGlobalId();
            Vector3d pos=targetPosition(view,targetId,offset,0);
            if(pos==null){active.decrementAndGet();return;}
            for(int i=0;i<HAND.length;i++) {
                int id=entitySequence.getAndIncrement();
                fakeIds[i]=id;
                view.user.sendPacketSilently(new WrapperPlayServerSpawnEntity(id,UUID.randomUUID(),
                    EntityTypes.BLOCK_DISPLAY,new Location(pos.getX(),pos.getY(),pos.getZ(),0,0),0,0,null));
                float[] part=HAND[i];
                view.user.sendPacketSilently(new WrapperPlayServerEntityMetadata(id,List.of(
                    new EntityData<>(11,EntityDataTypes.VECTOR3F,new Vector3f(part[0]*size,part[1]*size,0)),
                    new EntityData<>(12,EntityDataTypes.VECTOR3F,new Vector3f(part[2]*size,part[3]*size,0.06f)),
                    new EntityData<>(15,EntityDataTypes.BYTE,(byte)3),
                    new EntityData<>(17,EntityDataTypes.FLOAT,16.0f),
                    new EntityData<>(23,EntityDataTypes.BLOCK_STATE,block)
                )));
            }
        } catch(Exception ex) {
            cleanup(view,fakeIds);
            active.decrementAndGet();
            log.warn("Unable to spawn vanilla hand: {}",ex.getMessage());
            return;
        }
        final AtomicReference<ScheduledTask> job=new AtomicReference<>();
        ScheduledTask task=proxy.getScheduler().buildTask(this,new Runnable() {
            int tick=0;
            @Override public void run() {
                try {
                    if(tick++>=duration || views.get(player.getUniqueId())!=view
                            || !player.isActive()) {finish();return;}
                    double t=(double)tick/duration;
                    double eased=1-(1-t)*(1-t);
                    double down=depth*Math.sin(Math.PI*eased);
                    Vector3d p=targetPosition(view,targetId,offset,down);
                    if(p==null){finish();return;}
                    for(int id:fakeIds)if(id>0)
                        view.user.sendPacketSilently(new WrapperPlayServerEntityTeleport(id,p,0,0,false));
                } catch(Exception ex) {finish();}
            }
            private boolean finished;
            private void finish() {
                if(finished)return;
                finished=true;
                cleanup(view,fakeIds);
                active.decrementAndGet();
                ScheduledTask scheduled=job.get();
                if(scheduled!=null)scheduled.cancel();
            }
        }).repeat(50,TimeUnit.MILLISECONDS).schedule();
        job.set(task);
    }
    private Vector3d targetPosition(View view,UUID targetId,double extraY,double down) {
        Tracked target=view.find(targetId);
        if(target!=null) {
            String type=target.type.toLowerCase(java.util.Locale.ROOT);
            double height=type.contains("player")?1.8:
                type.contains("chicken")||type.contains("rabbit")||type.contains("cat")?0.9:
                type.contains("cow")||type.contains("horse")?1.5:1.6;
            return add(target.pos,0,height+extraY-down,0);
        }
        if(view.player.equals(targetId) && view.self!=null)return add(view.self,0,1.8+extraY-down,0);
        return null;
    }
    private void cleanup(View view,int[] ids) {
        try {
            int count=0;for(int id:ids)if(id>0)count++;
            if(count==0)return;
            int[] valid=new int[count];int i=0;
            for(int id:ids)if(id>0)valid[i++]=id;
            view.user.sendPacketSilently(new WrapperPlayServerDestroyEntities(valid));
        }catch(Exception ignored) {}
    }

    private final class Commands implements SimpleCommand {
        @Override public void execute(Invocation ctx) {
            CommandSource sender=ctx.source();
            String[] args=ctx.arguments();
            if(args.length==0 || args[0].equalsIgnoreCase("status")) {
                View view=sender instanceof Player player?views.get(player.getUniqueId()):null;
                String state=view==null?"unknown":view.realMod?"native-client-mod":
                    view.officialHello?"official-backend":
                    (System.nanoTime()-view.connectedAt <
                        (long)number("official-detection-ms",3000,250,10000)*1_000_000L)
                        ?"detecting":"standalone";
                sender.sendMessage(Component.text("PatPatVelocity v0.1.1 | mode="+state
                    +" | active="+active.get()
                    +" | Geyser bridge="+(System.getProperties().get(JAVA_TO_BEDROCK)!=null)));
                return;
            }
            if(args[0].equalsIgnoreCase("reload")) {
                if(!sender.hasPermission("patpatvelocity.admin")) {
                    sender.sendMessage(Component.text("No permission: patpatvelocity.admin"));return;
                }
                reload();sender.sendMessage(Component.text("PatPatVelocity configuration reloaded"));return;
            }
            if(args[0].equalsIgnoreCase("toggle") && sender instanceof Player p) {
                boolean desired=args.length>1 ? args[1].equalsIgnoreCase("on")
                    : disabled.contains(p.getUniqueId());
                if(desired)disabled.remove(p.getUniqueId());else disabled.add(p.getUniqueId());
                persistDisabled();
                sender.sendMessage(Component.text("PatPatVelocity "+(desired?"ON":"OFF")));return;
            }
            sender.sendMessage(Component.text("/ppv <status|toggle [on|off]|reload>"));
        }
        @Override public List<String> suggest(Invocation invocation) {
            String[] args=invocation.arguments();
            if(args.length<=1)return List.of("status","toggle","reload");
            if(args.length==2 && args[0].equalsIgnoreCase("toggle"))return List.of("on","off");
            return List.of();
        }
    }
}
