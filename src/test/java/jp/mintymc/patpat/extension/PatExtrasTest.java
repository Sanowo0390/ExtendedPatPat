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
        assertTrue(cfg.swingHandEnabled());
        p.setProperty("swing-hand-enabled","false");
        assertFalse(Settings.from(p).swingHandEnabled());
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
    @Test void optionalCameraFeedbackIsVerticalAndFinishesAtRest() {
        Properties props=new Properties();
        assertFalse(Settings.from(props).cameraShakeEnabled(),"Do not take over the camera by default");
        props.setProperty("camera-shake-enabled","true");
        props.setProperty("camera-shake-intensity","0.07");
        Settings cfg=Settings.from(props);
        assertTrue(cfg.cameraShakeEnabled());
        assertEquals(.07,cfg.cameraShakeIntensity(),1e-6);
        assertEquals(0f,PatCamera.offsetY(0,.07f));
        float pressed=PatCamera.offsetY(70,.07f);
        assertTrue(pressed<-.06f,"View should move down, not shake randomly in X/Z");
        assertTrue(PatCamera.offsetY(180,.07f)<0f);
        assertEquals(0f,PatCamera.offsetY(PatCamera.DURATION_MS,.07f));
        assertEquals(0f,PatCamera.offsetY(500,.07f));
        assertEquals(0f,PatCamera.offsetY(70,0f));
    }

}
