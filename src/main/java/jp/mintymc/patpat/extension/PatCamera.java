package jp.mintymc.patpat.extension;

import org.cloudburstmc.protocol.bedrock.data.CameraShakeAction;
import org.cloudburstmc.protocol.bedrock.data.CameraShakeType;
import org.cloudburstmc.protocol.bedrock.packet.CameraShakePacket;

/** Optional gentle 140ms pat tactile feedback, only for the initiating viewer. */
final class PatCamera {
    private PatCamera() {}
    static CameraShakePacket shake(float intensity) {
        CameraShakePacket p=new CameraShakePacket();
        p.setShakeType(CameraShakeType.POSITIONAL);
        p.setShakeAction(CameraShakeAction.ADD);
        p.setIntensity(intensity);
        p.setDuration(0.14f);
        return p;
    }
}
