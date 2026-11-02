package jp.mintymc.patpat.extension;

import org.cloudburstmc.math.vector.*;
import org.cloudburstmc.protocol.bedrock.data.entity.*;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.player.PlayerEntity;
import org.geysermc.geyser.session.GeyserSession;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** All methods except construction are confined to the Geyser session event loop. */
final class HandRenderer {
    private final GeyserSession session;
    private final Supplier<Settings> settings;
    private final Supplier<ImageAnimation> animation;
    private final Map<Integer,Hand> hands=new HashMap<>();
    private boolean ticking;
    HandRenderer(GeyserSession session,Supplier<Settings> settings,Supplier<ImageAnimation> animation) { this.session=session;this.settings=settings;this.animation=animation; }
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
        boolean image=!text && cfg.renderer().equals("image") && animation.get()!=null;
        AddEntityPacket packet=image?spawnImagePacket(id,anchor(entity),(float)cfg.scale(),0):spawnPacket(id,anchor(entity),name);
        session.sendUpstreamPacket(packet);
        // Only real Pat effects animate the target; text-only diagnostics must be
        // non-invasive. AnimateEntityPacket is clientbound, so it alters no server pose.
        if(image && cfg.squishEnabled()) session.sendUpstreamPacket(SquishAnimations.packet(entity));
        if(!text && cfg.soundEnabled()) {
            PlaySoundPacket sound=new PlaySoundPacket();sound.setSound(cfg.soundEvent());
            // Sound is sent separately to each viewer: use the listener position to prevent distance attenuation.
            sound.setPosition(session.getPlayerEntity().position());sound.setVolume((float)cfg.soundVolume());sound.setPitch((float)cfg.soundPitch());session.sendUpstreamPacket(sound);
        }
        hands.put(javaId,new Hand(id,entity,System.nanoTime(),text,name,image,cfg.scale()));
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
    static AddEntityPacket spawnImagePacket(long id,Vector3f position,float scale,int frame) {
        AddEntityPacket packet=new AddEntityPacket();packet.setRuntimeEntityId(id);packet.setUniqueEntityId(id);
        packet.setIdentifier(ImagePack.ENTITY);packet.setPosition(position);packet.setMotion(Vector3f.ZERO);packet.setRotation(Vector2f.ZERO);
        packet.getMetadata().setFlag(EntityFlag.NO_AI,true);packet.getMetadata().setFlag(EntityFlag.SILENT,true);
        packet.getMetadata().put(EntityDataTypes.SCALE,scale);packet.getMetadata().put(EntityDataTypes.VARIANT,frame);
        packet.getMetadata().put(EntityDataTypes.WIDTH,0f);packet.getMetadata().put(EntityDataTypes.HEIGHT,0f);
        return packet;
    }
    private Vector3f anchor(Entity entity) {
        Settings cfg=settings.get();
        return visibleAnchor(entity.position(),displayHeight(entity,cfg),entity.getBoundingBoxWidth(),
                session.getPlayerEntity().position(),session.getPlayerEntity().getYaw(),cfg);
    }
    /**
     * Keep the hand quad outside the target hitbox rather than allowing half the
     * sprite to clip into a wide mob. Positive view-space X stays the user's
     * preferred distance, but grows as necessary for large hitboxes.
     */
    static Vector3f visibleAnchor(Vector3f feet,float height,float hitboxWidth,
                                  Vector3f viewer,float viewerYaw,Settings cfg) {
        Vector3f base=anchor(feet,height,viewer,viewerYaw,cfg);
        if(!cfg.avoidHandOcclusion() || !cfg.offsetSpace().equals("view") || hitboxWidth<=0)return base;
        double tx=viewer.getX()-feet.getX(),tz=viewer.getZ()-feet.getZ();
        double length=Math.hypot(tx,tz);
        if(length<1e-5) {
            // Looking almost straight down: move the image above the target's
            // upper face instead of attempting an undefined horizontal offset.
            double above=viewer.getY()+1.62-(feet.getY()+height);
            if(above<=0)return base;
            double lift=cfg.scale()*0.5+cfg.handFrontClearance()
                    +(cfg.handMotionEnabled()?cfg.handMotionDepth():0);
            return base.add(0,(float)Math.min(lift,Math.max(0,above-0.1)),0);
        }
        tx/=length;tz/=length;
        // A square hitbox's front face is farther along a diagonal view ray.
        double faceDistance=(hitboxWidth*0.5)/Math.max(Math.abs(tx),Math.abs(tz));
        // The image is one block square before multiplying its entity scale.
        double quadHalfWidth=cfg.scale()*0.5;
        double required=faceDistance+quadHalfWidth+cfg.handFrontClearance();
        double additional=Math.max(0,required-cfg.offsetX());
        // Do not move the sprite past the camera on very close encounters.
        additional=Math.min(additional,Math.max(0,length-0.1-Math.max(0,cfg.offsetX())));
        return base.add((float)(tx*additional),0,(float)(tz*additional));
    }
    /** Keep user-defined offsets fixed; the Pat movement is an additional Y-only displacement. */
    static Vector3f motionPosition(Vector3f base, long elapsedMs, boolean text, Settings cfg) {
        if (text || !cfg.handMotionEnabled()) return base;
        return base.add(0f,HandMotion.displacement(elapsedMs,cfg.handMotionDuration(),cfg.handMotionDepth()),0f);
    }
    static float displayHeight(Entity entity,Settings cfg) {
        float height=entity.getBoundingBoxHeight();
        // Geyser keeps the standing collision height while a player is mounted/sitting.
        if(entity instanceof PlayerEntity && (entity.getFlag(EntityFlag.RIDING) || entity.getFlag(EntityFlag.SITTING)))
            height+=(float)cfg.sittingOffsetY();
        return height;
    }
    static Vector3f anchor(Vector3f feet,float height,Vector3f viewer,float viewerYaw,Settings settings) {
        double x=settings.offsetX(),z=settings.offsetZ();
        if(!settings.offsetSpace().equals("world")) {
            double tx=viewer.getX()-feet.getX(),tz=viewer.getZ()-feet.getZ();
            double length=Math.hypot(tx,tz);
            if(length>1e-6){tx/=length;tz/=length;}
            else {
                // Self tests / directly overhead: use the opposite of the camera's
                // horizontal look direction so offsets stay finite and predictable.
                double yaw=Math.toRadians(viewerYaw);tx=Math.sin(yaw);tz=-Math.cos(yaw);
            }
            // X = toward this viewer. Z = this viewer's right. Y stays world-up.
            double dx=tx*x+tz*z,dz=tz*x-tx*z;x=dx;z=dz;
        }
        return feet.add((float)x,height+(float)settings.height(),(float)z);
    }
    private void schedule() {
        // Use 20 ms updates during the short press so the bob is visibly smooth.
        // Return to 50 ms updates afterward to avoid continuous extra packets.
        long now=System.nanoTime();
        boolean animating=settings.get().handMotionEnabled() && hands.values().stream()
                .anyMatch(h->!h.text && (now-h.started)/1_000_000 < settings.get().handMotionDuration());
        session.scheduleInEventLoop(this::tick,animating?20:50,TimeUnit.MILLISECONDS);
    }
    private void tick() {
        if(session.isClosed()) { hands.clear();ticking=false;return; }
        Settings cfg=settings.get(); long now=System.nanoTime();
        for(Iterator<Map.Entry<Integer,Hand>> it=hands.entrySet().iterator();it.hasNext();) {
            var entry=it.next(); Hand hand=entry.getValue(); long elapsed=(now-hand.started)/1_000_000;
            long duration=cfg.duration()>0?cfg.duration():(hand.image?animation.get().duration():800);
            if(elapsed>=duration || find(entry.getKey())!=hand.entity || !hand.entity.isValid()
                    || hand.entity.getFlag(EntityFlag.INVISIBLE)) { remove(hand.id);it.remove();continue; }
            MoveEntityAbsolutePacket move=new MoveEntityAbsolutePacket();
            move.setRuntimeEntityId(hand.id); move.setPosition(motionPosition(anchor(hand.entity),elapsed,hand.text,cfg)); move.setRotation(Vector3f.ZERO);
            move.setTeleported(!cfg.handMotionEnabled() || hand.text || elapsed>=cfg.handMotionDuration()); session.sendUpstreamPacket(move);
            if(hand.image) {
                int index=animation.get().frameAt(elapsed,cfg.imageLoop());
                if(index!=hand.frame || cfg.scale()!=hand.scale) {
                    SetEntityDataPacket update=new SetEntityDataPacket();update.setRuntimeEntityId(hand.id);
                    update.getMetadata().put(EntityDataTypes.VARIANT,index);update.getMetadata().put(EntityDataTypes.SCALE,(float)cfg.scale());
                    session.sendUpstreamPacket(update);hand.frame=index;hand.scale=cfg.scale();
                }
                continue;
            }
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
        final long id,started;final Entity entity;final boolean text,image;String name;int frame;double scale;
        Hand(long id,Entity entity,long started,boolean text,String name,boolean image,double scale) {this.id=id;this.entity=entity;this.started=started;this.text=text;this.name=name;this.image=image;this.scale=scale;}
    }
}
