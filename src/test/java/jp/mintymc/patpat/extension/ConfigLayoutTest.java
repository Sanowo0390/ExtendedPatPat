package jp.mintymc.patpat.extension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class ConfigLayoutTest {
    @TempDir Path folder;

    @Test void upgradesOldConfigOnceAndRetainsValuesCustomKeysAndBackup() throws Exception {
        String original="# My old configuration\n"
                +"hand-motion-depth=0.8\n"
                +"camera-shake-enabled=true\n"
                +"camera-shake-intensity=0.13\n"
                +"offset-x=0.2\n"
                +"frames=\\uE2A0,\\uE2A1\n"
                +"custom-key=a=b\n";
        Path file=folder.resolve("config.properties");
        Files.writeString(file,original,StandardCharsets.UTF_8);
        assertTrue(ConfigLayout.formatIfLegacy(file));
        String formatted=Files.readString(file,StandardCharsets.UTF_8);
        assertTrue(formatted.contains(ConfigLayout.MARKER));
        assertTrue(formatted.contains("# 07. 撫でられた側だけのカメラ演出"));
        assertTrue(formatted.contains("camera-shake-intensity=0.13"));
        assertTrue(formatted.contains("custom-key=a=b"));
        assertTrue(formatted.contains("frames=\\uE2A0,\\uE2A1"));
        assertEquals(original,Files.readString(folder.resolve("config.properties.backup-before-layout-v2")));
        Settings cfg=Settings.load(file);
        assertEquals(.8,cfg.handMotionDepth(),.00001);
        assertEquals(.2,cfg.offsetX(),.00001);
        assertTrue(cfg.cameraShakeEnabled());
        assertEquals(.13,cfg.cameraShakeIntensity(),.00001);
        assertEquals(2,cfg.frames().size());
        assertFalse(ConfigLayout.formatIfLegacy(file));
        assertEquals(formatted,Files.readString(file));
    }

    @Test void recognizesOldV2LayoutAfterBrandRenameWithoutTouchingUsersConfig() throws Exception {
        Path file=folder.resolve("config.properties");
        String old="# PatPatGeyserExtension | Config Layout v2\n"
            +"hand-motion-depth=0.93\ncustom-setting=keep\n";
        Files.writeString(file,old,StandardCharsets.UTF_8);
        assertFalse(ConfigLayout.formatIfLegacy(file));
        assertEquals(old,Files.readString(file,StandardCharsets.UTF_8));
        assertFalse(Files.exists(folder.resolve("config.properties.backup-before-layout-v2")));
    }

    @Test void newTemplateContainsEveryOperationalSettingOnlyOnce() throws Exception {
        Path file=folder.resolve("config.properties");
        try(var stream=getClass().getResourceAsStream("/config.properties")) {
            assertNotNull(stream);
            Files.copy(stream,file);
        }
        assertFalse(ConfigLayout.formatIfLegacy(file));
        Properties values=new Properties();
        try(var in=Files.newBufferedReader(file,StandardCharsets.UTF_8)){values.load(in);}
        assertTrue(values.containsKey("squish-enabled"));
        assertTrue(values.containsKey("hand-motion-enabled"));
        assertTrue(values.containsKey("camera-shake-enabled"));
        assertTrue(values.containsKey("camera-shake-intensity"));
        assertEquals("images/hand.png",values.getProperty("image-file"));
        assertEquals("5",values.getProperty("image-sprite-columns"));
        assertEquals("50",values.getProperty("image-sprite-frame-ms"));
        assertTrue(values.containsKey("auto-pack"));
        assertTrue(values.containsKey("required-permission"));
        assertEquals(38,values.size(),"New config should contain every documented setting");
    }
}
