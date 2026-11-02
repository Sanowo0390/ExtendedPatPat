package jp.mintymc.patpat.extension;

import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;

/** Only sends the vanilla Bedrock visual swing; never sends an attack/damage packet. */
final class PatSwing {
    private PatSwing() {}
    static AnimatePacket packet(long runtimeId) {
        AnimatePacket packet=new AnimatePacket();
        packet.setRuntimeEntityId(runtimeId);
        packet.setAction(AnimatePacket.Action.SWING_ARM);
        return packet;
    }
}
