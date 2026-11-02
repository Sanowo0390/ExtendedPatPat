package jp.mintymc.patpat.extension;

import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraEase;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraSetInstruction;
import org.cloudburstmc.protocol.bedrock.packet.CameraInstructionPacket;
import org.geysermc.geyser.impl.camera.CameraDefinitions;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;

import java.util.concurrent.TimeUnit;

/**
 * A short, Y-axis-only view bob for the player BEING patted.
 *
 * Bedrock's CameraShakePacket has only positional/rotational modes and no Y-axis
 * mask. A temporary free camera allows a deterministic vertical stroke without
 * a random earthquake-like shake or any server-side player teleportation.
 *
 * All methods run on the recipient's Geyser event loop. Do not run when Geyser
 * already owns a forced camera (cinematic plugins, spectator, etc.).
 */
final class PatCamera {
    static final long DURATION_MS = 240;
    private static final long INTERVAL_MS = 30;

    private final GeyserSession session;
    private int generation;
    private boolean active;

    PatCamera(GeyserSession session) {
        this.session = session;
    }

    static float offsetY(long elapsedMs, float depth) {
        if(elapsedMs <= 0 || elapsedMs >= DURATION_MS || depth <= 0f) return 0f;
        double t=(double)elapsedMs/DURATION_MS;
        // One gentle downward press followed by a return to the original eye height.
        double eased=1.0-(1.0-t)*(1.0-t);
        return (float)(-depth*Math.sin(Math.PI*eased));
    }

    boolean play(float depth) {
        if(depth <= 0f || session.isClosed() || session.getPlayerEntity()==null
                || session.getPlayerEntity().position()==null
                || session.getGameMode()==GameMode.SPECTATOR
                || session.getSpectatedEntity()!=null
                || session.camera().forcedCameraPerspective()!=null) return false;
        int current=++generation;
        active=true;
        long start=System.nanoTime();
        sendFrame(0,depth);
        schedule(current,start,depth);
        return true;
    }

    private void schedule(int current,long start,float depth) {
        session.scheduleInEventLoop(()->tick(current,start,depth),INTERVAL_MS,TimeUnit.MILLISECONDS);
    }

    private void tick(int current,long start,float depth) {
        if(!active || current!=generation)return;
        if(session.isClosed() || session.getPlayerEntity()==null
                || session.getPlayerEntity().position()==null
                || session.getSpectatedEntity()!=null
                || session.camera().forcedCameraPerspective()!=null) {
            // Another plugin took over the camera. Do not clear their view.
            active=false;
            generation++;
            return;
        }
        long elapsed=(System.nanoTime()-start)/1_000_000L;
        if(elapsed>=DURATION_MS) {
            stop();
            return;
        }
        sendFrame(elapsed,depth);
        schedule(current,start,depth);
    }

    private void sendFrame(long elapsed,float depth) {
        var player=session.getPlayerEntity();
        Vector3f pos=player.position();
        float eyeHeight=session.isSneaking()?1.27f:1.62f;
        CameraSetInstruction set=new CameraSetInstruction();
        // Use the free preset with player audio/effects, retaining ordinary input.
        set.setPreset(CameraDefinitions.getByFunctionality(true,true));
        set.setPos(Vector3f.from(pos.getX(),pos.getY()+eyeHeight+offsetY(elapsed,depth),pos.getZ()));
        set.setRot(Vector2f.from(player.getPitch(),player.getYaw()));
        if(elapsed>0)set.setEase(new CameraSetInstruction.EaseData(CameraEase.LINEAR,0.03f));
        CameraInstructionPacket packet=new CameraInstructionPacket();
        packet.setSetInstruction(set);
        session.sendUpstreamPacket(packet);
    }

    void stop() {
        generation++;
        if(!active)return;
        active=false;
        if(session.isClosed() || session.camera().forcedCameraPerspective()!=null
                || session.getSpectatedEntity()!=null)return;
        CameraInstructionPacket clear=new CameraInstructionPacket();
        clear.setClear(true);
        session.sendUpstreamPacket(clear);
    }
}
