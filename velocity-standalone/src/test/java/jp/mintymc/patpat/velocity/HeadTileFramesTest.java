package jp.mintymc.patpat.velocity;

import com.google.gson.JsonParser;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.util.Vector3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class HeadTileFramesTest {
    @TempDir Path cache;

    @Test void missingCacheFallsBackAndVersionsAreGated() {
        HeadTileFrames frames=new HeadTileFrames(cache,SkinCatalog.load(),24);
        assertEquals(0,frames.readyCount());
        assertFalse(frames.ready(SkinCatalog.load().get("default")));
        assertTrue(HeadTileFrames.supported(ClientVersion.V_26_3));
        assertTrue(HeadTileFrames.supported(ClientVersion.V_1_21_9));
        assertFalse(HeadTileFrames.supported(ClientVersion.V_1_21_8));
    }

    @Test void cachedSignedHeadsRenderAsNativeObjectComponents() throws Exception {
        SkinCatalog catalog=SkinCatalog.load();
        var style=catalog.get("default");
        Path dir=Files.createDirectories(cache.resolve("default"));
        String token=Base64.getEncoder().encodeToString(
            "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/test\"}}}"
                .getBytes(StandardCharsets.UTF_8));
        for(int i=0;i<style.frames();i++){
            Properties p=new Properties();
            p.setProperty("resolution","24");
            p.setProperty("tile.0.0",token);
            p.setProperty("tile.2.2",token);
            try(var out=Files.newOutputStream(dir.resolve(i+".properties"))){p.store(out,"test");}
        }
        HeadTileFrames ready=new HeadTileFrames(cache,catalog,24);
        assertEquals(3,ready.rows());
        assertTrue(ready.ready(style));
        assertTrue(ready.row(style,0,0).contains("\"object\":\"player\""));
        assertTrue(ready.row(style,0,0).contains("\"hat\":false"));
        assertFalse(ready.row(style,0,0).contains("█"));
        assertFalse(ready.row(style,0,0).contains("⠀"));
        // One row per entity: no Minecraft text line spacing / newline.
        assertFalse(ready.row(style,0,0).contains("\\n"));
        assertTrue(ready.row(style,1,1).contains("\"text\":\"  \""));
        assertTrue(ready.row(style,2,2).contains(token));
    }

    @Test void invalidOrIncompleteTokensNeverEnterPackets() throws Exception {
        assertThrows(java.io.IOException.class,()->HeadTileFrames.checkedBase64("!invalid",50));
        assertThrows(java.io.IOException.class,()->HeadTileFrames.checkedBase64("",50));
        assertEquals(24,HeadTileFrames.normalizeResolution(7));
        assertEquals(16,HeadTileFrames.normalizeResolution(16));
        assertEquals(32,HeadTileFrames.normalizeResolution(32));
        assertEquals(24,HeadTileFrames.normalizeResolution(24));
    }

    @Test void rowsAlignExactlyWithoutFontLineGap(){
        Vector3d base=new Vector3d(5,7,8);
        Vector3d top=HeadTileFrames.position(base,0.6,0,0,3);
        Vector3d middle=HeadTileFrames.position(base,0.6,0,1,3);
        Vector3d bottom=HeadTileFrames.position(base,0.6,0,2,3);
        assertEquals(0.2,top.getY()-middle.getY(),1e-6);
        assertEquals(0.2,middle.getY()-bottom.getY(),1e-6);
        assertEquals(0.6,top.getY()-bottom.getY()+0.2,1e-6);
    }

    @Test void skinConversionAndMineSkinResultParsing() throws Exception {
        BufferedImage tile=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);
        assertTrue(HeadTileGenerator.invisible(tile));
        tile.setRGB(2,3,0xFFFFAA11);
        assertFalse(HeadTileGenerator.invisible(tile));
        BufferedImage skin=HeadTileGenerator.prepareSkin(tile);
        assertEquals(64,skin.getWidth());
        assertEquals(0xFFFFAA11,skin.getRGB(10,11));
        assertEquals(0xFFFFAA11,skin.getRGB(26,11));
        byte[] png=HeadTileGenerator.png(skin);
        assertEquals(32,HeadTileGenerator.sha256(png).length()/2);
        var json=JsonParser.parseString(
            "{\"skin\":{\"texture\":{\"data\":{\"value\":\"dGVzdA==\",\"signature\":\"c2ln\"}}}}").getAsJsonObject();
        var signed=HeadTileGenerator.skinFrom(json);
        assertEquals("dGVzdA==;c2ln",signed.encoded());
    }

    @Test void metadataUsesRawJsonForNewObjectComponents() throws Exception {
        String contents=Files.readString(Path.of(
            "src/main/java/jp/mintymc/patpat/velocity/HeadTileFrames.java"));
        assertTrue(contents.contains("EntityDataTypes.COMPONENT"));
        assertTrue(contents.contains("EntityDataTypes.BYTE,(byte)3"));
        assertFalse(contents.contains("EntityDataTypes.ADV_COMPONENT"));
    }
}
