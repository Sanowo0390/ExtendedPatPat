package jp.mintymc.patpat.extension;

import org.cloudburstmc.math.vector.*;
import org.cloudburstmc.protocol.bedrock.data.entity.*;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.session.GeyserSession;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** All methods except construction are confined to the Geyser session event loop. */
final class HandRenderer {
    private final GeyserSession session;
    private final Supplier<Settings> settings;
    private final Map<Integer,Hand> hands=new HashMap<>();
    private boolean ticking;
    HandRenderer(GeyserSession session,Supplier<Settings> settings) { this.session=session;this.settings=settings; }
    int size() { return hands.size(); }
    private Entity find(int id) {
        return session.getPlayerEntity().javaId()==id ? session.getPlayerEntity() : session.getEntityCache().getEntityByJavaId(id);
    }
    void show(int javaId,boolean text) {
        Entity entity=find(javaId);
        if(entity==null || !entity.isValid() || entity.getFlag(EntityFlag.INVISIBLE)) return;
        Hand old=hands.remove(javaId); if(old!=null) remove(old.id);
        Settings cfg=settings.get();
        if(hands.size()>=cfg.maxActive()) return;
        long id=session.getEntityCache().nextEntityId();
        String name=text ? "PAT" : cfg.frames().getFirst();
        AddEntityPacket packet=spawnPacket(id,anchor(entity),name);
        session.sendUpstreamPacket(packet);
        hands.put(javaId,new Hand(id,entity,System.nanoTime(),text,name));
        if(!ticking) { ticking=true; schedule(); }
    }
    static AddEntityPacket spawnPacket(long id,Vector3f position,String name) {
        AddEntityPacket packet=new AddEntityPacket();
        packet.setRuntimeEntityId(id); packet.setUniqueEntityId(id);
        packet.setIdentifier("minecraft:armor_stand");
        packet.setPosition(position); packet.setMotion(Vector3f.ZERO); packet.setRotation(Vector2f.ZERO);
        EnumMap<EntityFlag,Boolean> flags=new EnumMap<>(EntityFlag.class);
        for(EntityFlag flag:List.of(EntityFlag.CAN_SHOW_NAME,EntityFlag.ALWAYS_SHOW_NAME,EntityFlag.NO_AI,EntityFlag.SILENT))flags.put(flag,true);
        packet.getMetadata().putFlags(flags);
        // Invisible flag also hides Bedrock nametags. Zero scale hides only the model.
        packet.getMetadata().put(EntityDataTypes.SCALE,0f);
        packet.getMetadata().put(EntityDataTypes.WIDTH,0f);
        packet.getMetadata().put(EntityDataTypes.HEIGHT,0f);
        packet.getMetadata().put(EntityDataTypes.NAME,name);
        packet.getMetadata().put(EntityDataTypes.NAMETAG_ALWAYS_SHOW,(byte)1);
        return packet;
    }
    private Vector3f anchor(Entity entity) {
        return entity.position().add(0,entity.getBoundingBoxHeight()+(float)settings.get().height(),0);
    }
    private void schedule() { session.scheduleInEventLoop(this::tick,50,TimeUnit.MILLISECONDS); }
    private void tick() {
        if(session.isClosed()) { hands.clear();ticking=false;return; }
        Settings cfg=settings.get(); long now=System.nanoTime();
        for(Iterator<Map.Entry<Integer,Hand>> it=hands.entrySet().iterator();it.hasNext();) {
            var entry=it.next(); Hand hand=entry.getValue(); long elapsed=(now-hand.started)/1_000_000;
            if(elapsed>=cfg.duration() || find(entry.getKey())!=hand.entity || !hand.entity.isValid()
                    || hand.entity.getFlag(EntityFlag.INVISIBLE)) { remove(hand.id);it.remove();continue; }
            MoveEntityAbsolutePacket move=new MoveEntityAbsolutePacket();
            move.setRuntimeEntityId(hand.id); move.setPosition(anchor(hand.entity)); move.setRotation(Vector3f.ZERO);
            move.setTeleported(true); session.sendUpstreamPacket(move);
            String frame=hand.text ? "PAT" : cfg.frames().get((int)(elapsed/cfg.frameMs()%cfg.frames().size()));
            if(!frame.equals(hand.name)) {
                SetEntityDataPacket update=new SetEntityDataPacket(); update.setRuntimeEntityId(hand.id);
                update.getMetadata().put(EntityDataTypes.NAME,frame); session.sendUpstreamPacket(update);hand.name=frame;
            }
        }
        if(hands.isEmpty()) ticking=false;else schedule();
    }
    void clear() { if(!session.isClosed()) hands.values().forEach(h->remove(h.id)); hands.clear(); }
    private void remove(long id) { RemoveEntityPacket packet=new RemoveEntityPacket();packet.setUniqueEntityId(id);session.sendUpstreamPacket(packet); }
    private static final class Hand {
        final long id,started;final Entity entity;final boolean text;String name;
        Hand(long id,Entity entity,long started,boolean text,String name) {this.id=id;this.entity=entity;this.started=started;this.text=text;this.name=name;}
    }
}
