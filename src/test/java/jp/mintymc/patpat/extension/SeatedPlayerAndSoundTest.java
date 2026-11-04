package jp.mintymc.patpat.extension;

import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.PlaySoundPacket;
import org.geysermc.geyser.entity.type.LivingEntity;
import org.geysermc.geyser.entity.type.player.PlayerEntity;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.EntityCache;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SeatedPlayerAndSoundTest {
    @Test void lowersHandOnlyForSeatedPlayers() {
        Properties props=new Properties();props.setProperty("sitting-offset-y","-0.7");
        Settings cfg=Settings.from(props);
        PlayerEntity player=mock(PlayerEntity.class);
        when(player.getBoundingBoxHeight()).thenReturn(1.8f);
        assertEquals(1.8f,HandRenderer.displayHeight(player,cfg),1e-5);
        when(player.getFlag(EntityFlag.RIDING)).thenReturn(true);
        assertEquals(1.1f,HandRenderer.displayHeight(player,cfg),1e-5);
        LivingEntity mob=mock(LivingEntity.class);
        when(mob.getBoundingBoxHeight()).thenReturn(1.8f);
        assertEquals(1.8f,HandRenderer.displayHeight(mob,cfg),1e-5);
    }

    @Test void usesListenerPositionSoDistanceCannotAttenuateThePatSound() {
        GeyserSession session=mock(GeyserSession.class);
        EntityCache cache=mock(EntityCache.class);
        SessionPlayerEntity viewer=mock(SessionPlayerEntity.class);
        LivingEntity target=mock(LivingEntity.class);
        when(session.getEntityCache()).thenReturn(cache);
        when(session.getPlayerEntity()).thenReturn(viewer);
        when(viewer.position()).thenReturn(Vector3f.from(50,70,60));
        when(cache.getEntityByJavaId(300)).thenReturn(target);
        when(target.isValid()).thenReturn(true);
        when(target.position()).thenReturn(Vector3f.from(10,60,20));
        when(target.getBoundingBoxHeight()).thenReturn(1.5f);
        var sprite=new ImageAnimation(
            java.util.List.of(new java.awt.image.BufferedImage(8,8,java.awt.image.BufferedImage.TYPE_INT_ARGB)),
            java.util.List.of(250));
        when(cache.nextEntityId()).thenReturn(101L);
        HandRenderer renderer=new HandRenderer(session,()->Settings.from(new Properties()),()->sprite);
        renderer.show(300);
        ArgumentCaptor<org.cloudburstmc.protocol.bedrock.packet.BedrockPacket> packets=
                ArgumentCaptor.forClass(org.cloudburstmc.protocol.bedrock.packet.BedrockPacket.class);
        verify(session,atLeastOnce()).sendUpstreamPacket(packets.capture());
        PlaySoundPacket sound=packets.getAllValues().stream().filter(p->p instanceof PlaySoundPacket)
                .map(p->(PlaySoundPacket)p).findFirst().orElseThrow();
        assertEquals(Vector3f.from(50,70,60),sound.getPosition());
        assertEquals(1f,sound.getVolume());
    }
}
