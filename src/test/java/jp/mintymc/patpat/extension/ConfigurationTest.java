package jp.mintymc.patpat.extension;
import org.junit.jupiter.api.Test;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;
class ConfigurationTest {
    @Test void clampsInvalidSettingsAndPreservesValidGlyphs() {
        Properties p=new Properties();p.setProperty("duration-ms","-1");p.setProperty("max-distance","NaN");p.setProperty("frames",",,");
        Settings cfg=Settings.from(p);
        assertEquals(0,cfg.duration());assertEquals(3,cfg.distance());assertEquals(4,cfg.frames().size());
        p.setProperty("frames","A,B");assertEquals(2,Settings.from(p).frames().size());
    }
    @Test void requiresExactRuntimeBuildAndCommit() {
        String sha="64bb3993b4beef6743af0ab76ee5dfc8e67a0d21";
        assertTrue(PatPatExtension.compatible("1280",sha));
        assertFalse(PatPatExtension.compatible("1281",sha));
        assertFalse(PatPatExtension.compatible("1280","different"));
    }
    @Test void standaloneAndSeatedOffsetDefaultsAreAppliedWithoutOverridingCustomValues() {
        Properties fresh=new Properties();
        assertTrue(Settings.from(fresh).standaloneMode());
        assertEquals(0.2,Settings.from(fresh).sittingOffsetY(),1e-6);
        fresh.setProperty("sitting-offset-y","-0.6");
        fresh.setProperty("standalone-mode","false");
        assertEquals(-0.6,Settings.from(fresh).sittingOffsetY(),1e-6);
        assertFalse(Settings.from(fresh).standaloneMode());
    }

}
