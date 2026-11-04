package jp.mintymc.patpat.extension;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class HandAnimationSyncTest {
    @TempDir Path directory;

    private static JsonObject readJson(ZipFile zip,String name) throws Exception {
        return JsonParser.parseString(new String(
                zip.getInputStream(zip.getEntry(name)).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
    }
    @Test void handStartsOnSpawnAndUsesSame240msEasingAsTargetSquish() throws Exception {
        var gif=new ImageAnimation(List.of(new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB)),List.of(800));
        var cfg=Settings.from(new Properties());
        try(ZipFile pack=new ZipFile(ImagePack.build(directory,gif,null,cfg).toFile())) {
            JsonObject entity=readJson(pack,"entity/hand.entity.json")
                    .getAsJsonObject("minecraft:client_entity").getAsJsonObject("description");
            assertEquals("animation.patpatbedrock.hand_stroke",
                    entity.getAsJsonObject("animations").get("stroke").getAsString());
            JsonArray auto=entity.getAsJsonObject("scripts").getAsJsonArray("animate");
            assertTrue(auto.toString().contains("\"stroke\""),
                    "The hand must begin animating as soon as the Bedrock entity spawns");

            JsonObject hand=readJson(pack,"animations/hand.json").getAsJsonObject("animations")
                    .getAsJsonObject("animation.patpatbedrock.hand_stroke");
            JsonObject squish=readJson(pack,"animations/squish.json").getAsJsonObject("animations")
                    .getAsJsonObject("animation.patpatbedrock.squish.root");
            assertEquals(squish.get("animation_length").getAsDouble(),
                    hand.get("animation_length").getAsDouble(),1e-8);
            assertEquals(.24,hand.get("animation_length").getAsDouble(),1e-8);
            assertFalse(hand.get("loop").getAsBoolean());
            String y=hand.getAsJsonObject("bones").getAsJsonObject("billboard")
                    .getAsJsonArray("position").get(1).getAsString();
            assertTrue(y.contains("query.anim_time / 0.24"));
            assertTrue(y.contains("math.pow(1.0 - "));
            assertTrue(y.startsWith(Double.toString(-16 * cfg.handMotionDepth() / cfg.scale()) + " * math.sin("),
                    "Model-space stroke must account for image scale exactly once");
            assertTrue(squish.toString().contains("query.anim_time / 0.24"));
        }
    }

    @Test void disablingOrAdjustingMotionChangesPackAndOnlyChangesClientStroke() throws Exception {
        var gif=new ImageAnimation(List.of(new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB)),List.of(800));
        Path first=ImagePack.build(directory,gif,null,Settings.from(new Properties()));
        Properties p=new Properties();
        p.setProperty("hand-motion-enabled","false");
        Path disabled=ImagePack.build(directory,gif,null,Settings.from(p));
        assertNotEquals(first,disabled);
        try(ZipFile pack=new ZipFile(disabled.toFile())) {
            JsonObject entity=readJson(pack,"entity/hand.entity.json").getAsJsonObject("minecraft:client_entity").getAsJsonObject("description");
            assertFalse(entity.getAsJsonObject("animations").has("stroke"));
            assertEquals(1,entity.getAsJsonObject("scripts").getAsJsonArray("animate").size());
        }
        p.setProperty("hand-motion-enabled","true");
        p.setProperty("hand-motion-depth","0.6");
        p.setProperty("image-scale","1.0");
        p.setProperty("hand-motion-duration-ms","400");
        Path customized=ImagePack.build(directory,gif,null,Settings.from(p));
        assertNotEquals(first,customized);
        try(ZipFile pack=new ZipFile(customized.toFile())) {
            JsonObject stroke=readJson(pack,"animations/hand.json").getAsJsonObject("animations")
                    .getAsJsonObject("animation.patpatbedrock.hand_stroke");
            assertEquals(.4,stroke.get("animation_length").getAsDouble(),1e-8);
            String y=stroke.getAsJsonObject("bones").getAsJsonObject("billboard")
                    .getAsJsonArray("position").get(1).getAsString();
            assertTrue(y.contains("query.anim_time / 0.4"));
            assertTrue(y.contains("-9.6"));
        }
    }
}
