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
    @Test void discoversRequiredGeyserCapabilitiesWithoutPinningBuildHash() {
        GeyserCompatibility.Report report=GeyserCompatibility.inspect();
        assertTrue(report.supported(),()->"Incompatible Geyser internals: "+report.missing());
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

    @Test void newDefaultPatVisualsAndCooldownMatchPublicConfig() {
        Settings cfg=Settings.from(new Properties());
        assertEquals(100,cfg.cooldown());
        assertEquals(0.5,cfg.scale(),1e-6);
        assertEquals(0.5,cfg.offsetX(),1e-6);
        assertEquals(0.2,cfg.sittingOffsetY(),1e-6);
        assertTrue(cfg.standaloneMode());
    }

}
