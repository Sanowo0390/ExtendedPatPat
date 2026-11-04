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

/** PNG/GIF-only hand renderer, confined to the Geyser session event loop. */
final class HandRenderer {
    private final GeyserSession session;
    private final Supplier<Settings> settings;
    private final Supplier<ImageAnimation> animation;
    private final Map<Integer,Hand> hands=new HashMap<>();
    private boolean ticking;
    HandRenderer(GeyserSession session,Supplier<Settings> settings,Supplier<ImageAnimation> animation) {
        this.session=session;this.settings=settings;this.animation=animation;
    }
    int size(){return hands.size();}
    private Entity find(int id) {
        return session.getPlayerEntity().javaId()==id
            ? session.getPlayerEntity() : session.getEntityCache().getEntityByJavaId(id);
    }
    void show(int javaId) {
        ImageAnimation sprite=animation.get();
        if(sprite==null)return; // Fail closed when a custom image cannot be loaded.
        Entity entity=find(javaId);
        if(entity==null || !entity.isValid() || entity.getFlag(EntityFlag.INVISIBLE))return;
        Hand old=hands.remove(javaId);
        if(old!=null)remove(old.id);
        Settings cfg=settings.get();
        if(hands.size()>=cfg.maxActive())return;
        long id=session.getEntityCache().nextEntityId();
        Vector3f position=anchor(entity);
        session.sendUpstreamPacket(spawnImagePacket(id,position,(float)cfg.scale(),0));
        if(cfg.squishEnabled())session.sendUpstreamPacket(SquishAnimations.packet(entity));
        if(cfg.soundEnabled()){
            PlaySoundPacket sound=new PlaySoundPacket();sound.setSound(cfg.soundEvent());
            // Each recipient hears the sound at full intended level.
            sound.setPosition(session.getPlayerEntity().position());
            sound.setVolume((float)cfg.soundVolume());sound.setPitch((float)cfg.soundPitch());
            session.sendUpstreamPacket(sound);
        }
        hands.put(javaId,new Hand(id,entity,System.nanoTime(),position,cfg.scale()));
        if(!ticking){ticking=true;schedule();}
    }
    static AddEntityPacket spawnImagePacket(long id,Vector3f position,float scale,int frame){
        AddEntityPacket packet=new AddEntityPacket();
        packet.setRuntimeEntityId(id);packet.setUniqueEntityId(id);
        packet.setIdentifier(ImagePack.ENTITY);
        packet.setPosition(position);packet.setMotion(Vector3f.ZERO);packet.setRotation(Vector2f.ZERO);
        packet.getMetadata().setFlag(EntityFlag.NO_AI,true);
        packet.getMetadata().setFlag(EntityFlag.SILENT,true);
        packet.getMetadata().put(EntityDataTypes.SCALE,scale);
        packet.getMetadata().put(EntityDataTypes.VARIANT,frame);
        packet.getMetadata().put(EntityDataTypes.WIDTH,0f);
        packet.getMetadata().put(EntityDataTypes.HEIGHT,0f);
        return packet;
    }
    private Vector3f anchor(Entity entity){
        Settings cfg=settings.get();
        return anchor(entity.position(),displayHeight(entity,cfg),
            session.getPlayerEntity().position(),session.getPlayerEntity().getYaw(),cfg);
    }
    static float displayHeight(Entity entity,Settings cfg){
        float height=entity.getBoundingBoxHeight();
        if(entity instanceof PlayerEntity &&
                (entity.getFlag(EntityFlag.RIDING) || entity.getFlag(EntityFlag.SITTING)))
            height+=(float)cfg.sittingOffsetY();
        return height;
    }
    static Vector3f anchor(Vector3f feet,float height,Vector3f viewer,float viewerYaw,Settings cfg){
        double x=cfg.offsetX(),z=cfg.offsetZ();
        if(!cfg.offsetSpace().equals("world")){
            double tx=viewer.getX()-feet.getX(),tz=viewer.getZ()-feet.getZ();
            double length=Math.hypot(tx,tz);
            if(length>1e-6){tx/=length;tz/=length;}
            else {
                double yaw=Math.toRadians(viewerYaw);
                tx=Math.sin(yaw);tz=-Math.cos(yaw);
            }
            double dx=tx*x+tz*z,dz=tz*x-tx*z;
            x=dx;z=dz;
        }
        return feet.add((float)x,height+(float)cfg.height(),(float)z);
    }
    private void schedule(){
        // The sprite's hand stroke runs locally on the Bedrock client.
        // A 50ms update is sufficient for entity tracking and frame changes.
        session.scheduleInEventLoop(this::tick,50,TimeUnit.MILLISECONDS);
    }
    private void tick(){
        if(session.isClosed()){hands.clear();ticking=false;return;}
        Settings cfg=settings.get();
        ImageAnimation sprite=animation.get();
        if(sprite==null){clear();ticking=false;return;}
        long now=System.nanoTime();
        for(Iterator<Map.Entry<Integer,Hand>> it=hands.entrySet().iterator();it.hasNext();){
            var entry=it.next();
            Hand hand=entry.getValue();
            long elapsed=(now-hand.started)/1_000_000;
            long duration=cfg.duration()>0?cfg.duration():sprite.duration();
            if(elapsed>=duration || find(entry.getKey())!=hand.entity
                    || !hand.entity.isValid() || hand.entity.getFlag(EntityFlag.INVISIBLE)){
                remove(hand.id);it.remove();continue;
            }
            Vector3f position=anchor(hand.entity);
            // Stationary mobs viewed from a stationary player need no movement packet.
            if(!position.equals(hand.position)){
                MoveEntityAbsolutePacket move=new MoveEntityAbsolutePacket();
                move.setRuntimeEntityId(hand.id);
                move.setPosition(position);move.setRotation(Vector3f.ZERO);
                move.setTeleported(true);
                session.sendUpstreamPacket(move);
                hand.position=position;
            }
            int frame=sprite.frameAt(elapsed,cfg.imageLoop());
            if(frame!=hand.frame || cfg.scale()!=hand.scale){
                SetEntityDataPacket update=new SetEntityDataPacket();
                update.setRuntimeEntityId(hand.id);
                if(frame!=hand.frame)update.getMetadata().put(EntityDataTypes.VARIANT,frame);
                if(cfg.scale()!=hand.scale)update.getMetadata().put(EntityDataTypes.SCALE,(float)cfg.scale());
                session.sendUpstreamPacket(update);
                hand.frame=frame;hand.scale=cfg.scale();
            }
        }
        if(hands.isEmpty())ticking=false;
        else schedule();
    }
    void clear(){
        if(!session.isClosed())hands.values().forEach(h->remove(h.id));
        hands.clear();
    }
    private void remove(long id){
        RemoveEntityPacket packet=new RemoveEntityPacket();
        packet.setUniqueEntityId(id);
        session.sendUpstreamPacket(packet);
    }
    private static final class Hand{
        final long id,started;final Entity entity;
        Vector3f position;int frame;double scale;
        Hand(long id,Entity entity,long started,Vector3f position,double scale){
            this.id=id;this.entity=entity;this.started=started;
            this.position=position;this.scale=scale;
        }
    }
}
