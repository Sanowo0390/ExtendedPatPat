package jp.mintymc.patpat.extension;

import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.CameraInstructionPacket;
import org.geysermc.geyser.api.bedrock.camera.CameraData;
import org.geysermc.geyser.api.bedrock.camera.CameraPerspective;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatCameraTest {
    @Test void verticalFeedbackUsesFreeCameraOnlyFor240msAndRestoresPerspective() {
        GeyserSession recipient=mock(GeyserSession.class);
        SessionPlayerEntity player=mock(SessionPlayerEntity.class);
        when(recipient.getPlayerEntity()).thenReturn(player);
        when(player.position()).thenReturn(Vector3f.from(3,64,8));
        when(recipient.camera()).thenReturn(mock(CameraData.class));
        when(recipient.getGameMode()).thenReturn(GameMode.SURVIVAL);
        PatCamera animation=new PatCamera(recipient);
        assertTrue(animation.play(.1f));
        verify(recipient).sendUpstreamPacket(argThat(packet -> {
            if(!(packet instanceof CameraInstructionPacket c) || c.getSetInstruction()==null)return false;
            var pos=c.getSetInstruction().getPos();
            return pos.getX()==3f && pos.getY()==65.62f && pos.getZ()==8f;
        }));
        animation.stop();
        verify(recipient).sendUpstreamPacket(argThat(packet ->
                packet instanceof CameraInstructionPacket c && c.getSetInstruction()==null && c.getClear().isPresent()));
    }

    @Test void doesNotHijackExistingCinematicOrSpectatorCamera() {
        GeyserSession recipient=mock(GeyserSession.class);
        SessionPlayerEntity player=mock(SessionPlayerEntity.class);
        when(recipient.getPlayerEntity()).thenReturn(player);
        when(player.position()).thenReturn(Vector3f.ZERO);
        CameraData camera=mock(CameraData.class);
        when(recipient.camera()).thenReturn(camera);
        when(camera.forcedCameraPerspective()).thenReturn(CameraPerspective.FREE);
        assertFalse(new PatCamera(recipient).play(.1f));
        verify(recipient,never()).sendUpstreamPacket(any());

        when(camera.forcedCameraPerspective()).thenReturn(null);
        when(recipient.getGameMode()).thenReturn(GameMode.SPECTATOR);
        assertFalse(new PatCamera(recipient).play(.1f));
        verify(recipient,never()).sendUpstreamPacket(any());
    }
}
