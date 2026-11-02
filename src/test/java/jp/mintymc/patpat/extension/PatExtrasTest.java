package jp.mintymc.patpat.extension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PatExtrasTest {
    @TempDir Path temp;

    @Test void swingingLooksLikeLeftClickWithoutSendingAnAttack() {
        AnimatePacket packet=PatSwing.packet(123L);
        assertEquals(123L,packet.getRuntimeEntityId());
        assertEquals(AnimatePacket.Action.SWING_ARM,packet.getAction());
    }

    @Test void mobAndPlayerIgnoreListsAreNormalizedAndConfigurable() {
        UUID id=UUID.randomUUID();
        Properties p=new Properties();
        p.setProperty("ignored-entity-types","cow,MINECRAFT:zombie,   ,example:custom_mob");
        p.setProperty("ignored-player-uuids",id+",not-a-valid-id");
        Settings cfg=Settings.from(p);
        assertTrue(cfg.ignoredEntityTypes().contains("minecraft:cow"));
        assertTrue(cfg.ignoredEntityTypes().contains("minecraft:zombie"));
        assertTrue(cfg.ignoredEntityTypes().contains("example:custom_mob"));
        assertFalse(cfg.ignoredEntityTypes().contains(""));
        assertEquals(Set.of(id),cfg.ignoredPlayerUuids());
        assertTrue(cfg.avoidHandOcclusion());
        assertTrue(cfg.swingHandEnabled());
        assertEquals(.15,cfg.handFrontClearance(),1e-6);
        p.setProperty("swing-hand-enabled","false");
        p.setProperty("hand-avoid-occlusion","false");
        assertFalse(Settings.from(p).swingHandEnabled());
        assertFalse(Settings.from(p).avoidHandOcclusion());
    }

    @Test void personalPatTotalsPersistAcrossReloadAndNeverMixPlayers() throws Exception {
        Path path=temp.resolve("stats.properties");
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        PatStatistics statistics=new PatStatistics(path);
        assertEquals(1,statistics.record(a));
        assertEquals(2,statistics.record(a));
        assertEquals(1,statistics.record(b));
        statistics.flush();
        PatStatistics reloaded=new PatStatistics(path);
        assertEquals(2,reloaded.total(a));
        assertEquals(1,reloaded.total(b));
        assertEquals(0,reloaded.total(UUID.randomUUID()));
        assertEquals(3,reloaded.record(a));
        reloaded.flush();
        assertEquals(3,new PatStatistics(path).total(a));
    }
    @Test void optionalCameraShakeUsesLowIntensityAndShortDuration() {
        Properties props=new Properties();
        assertFalse(Settings.from(props).cameraShakeEnabled(),"Avoid involuntary camera motion by default");
        props.setProperty("camera-shake-enabled","true");
        props.setProperty("camera-shake-intensity","0.07");
        Settings cfg=Settings.from(props);
        assertTrue(cfg.cameraShakeEnabled());
        var packet=PatCamera.shake((float)cfg.cameraShakeIntensity());
        assertEquals(.07f,packet.getIntensity(),1e-6f);
        assertEquals(.14f,packet.getDuration(),1e-6f);
        assertEquals(org.cloudburstmc.protocol.bedrock.data.CameraShakeAction.ADD,packet.getShakeAction());
        assertEquals(org.cloudburstmc.protocol.bedrock.data.CameraShakeType.POSITIONAL,packet.getShakeType());
    }

}
