package jp.mintymc.patpat.extension;

import com.google.gson.*;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageSupportTest {
    @TempDir Path directory;
    Path fixture(String name) throws Exception {
        Path path=directory.resolve(name);
        try(var in=getClass().getResourceAsStream('/'+name)){assertNotNull(in);Files.copy(in,path);}
        return path;
    }
    @Test void gifCompositesPartialFramesAndRestoreToPreviousWithoutGhostPixels() throws Exception {
        ImageAnimation a=ImageAnimation.load(fixture("disposal.gif"));
        assertEquals(3,a.frames().size());assertEquals(List.of(100,200,300),a.delays());
        // 20x12 image, aspect-preserved in 128x128: source (2,2)->(16,41).
        for(var f:a.frames())assertEquals(0xFFFF0000,f.getRGB(16,41));
        assertEquals(0,a.frames().get(0).getRGB(54,41)>>>24);
        assertEquals(0xFF00FF00,a.frames().get(1).getRGB(54,41));
        assertEquals(0,a.frames().get(2).getRGB(54,41)>>>24,"Green frame must be restored away");
        assertEquals(0xFF0000FF,a.frames().get(2).getRGB(16,80));
    }
    @Test void gifTimingLoopsOrHoldsTheLastFrame() throws Exception {
        ImageAnimation a=ImageAnimation.load(fixture("disposal.gif"));
        assertEquals(600,a.duration());assertEquals(0,a.frameAt(99,true));assertEquals(1,a.frameAt(100,true));
        assertEquals(2,a.frameAt(300,true));assertEquals(0,a.frameAt(600,true));assertEquals(2,a.frameAt(900,false));
    }
    @Test void pngPreservesTransparencyAndAspectRatio() throws Exception {
        BufferedImage image=new BufferedImage(24,8,BufferedImage.TYPE_INT_ARGB);
        for(int x=0;x<12;x++)for(int y=0;y<8;y++)image.setRGB(x,y,0xFFFF0000);
        Path path=directory.resolve("custom.png");ImageIO.write(image,"PNG",path.toFile());
        ImageAnimation a=ImageAnimation.load(path);assertEquals(1,a.frames().size());
        BufferedImage f=a.frames().getFirst();assertEquals(0xFFFF0000,f.getRGB(20,64));
        assertEquals(0,f.getRGB(90,64)>>>24);assertEquals(0,f.getRGB(20,10)>>>24);
    }
    @Test void generatedPackUsesEntityTexturesWithoutReplacingFontsAndChangesCacheVersion() throws Exception {
        ImageAnimation a=ImageAnimation.load(fixture("disposal.gif"));Path first=ImagePack.build(directory,a);
        assertEquals(first,ImagePack.build(directory,a));
        try(ZipFile zip=new ZipFile(first.toFile())) {
            assertFalse(zip.stream().anyMatch(e->e.getName().startsWith("font/")));
            assertNotNull(zip.getEntry("textures/patpatbedrock/f2.png"));
            String json=new String(zip.getInputStream(zip.getEntry("render_controllers/hand.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            JsonArray frames=JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("render_controllers")
                    .getAsJsonObject("controller.render.patpatbedrock.hand").getAsJsonObject("arrays").getAsJsonObject("textures").getAsJsonArray("Array.frames");
            assertEquals(3,frames.size());
        }
        Path changed=ImagePack.build(directory,new ImageAnimation(a.frames(),List.of(200,200,300)));
        assertNotEquals(first,changed);
    }
    @Test void bundledDefaultGifIsDecodable() throws Exception {
        ImageAnimation a=ImageAnimation.load(fixture("default-hand.gif"));assertEquals(4,a.frames().size());
    }
    @Test void imageScaleIsIndependentOfPositionAndHasNoNameplateOffset() {
        Properties p=new Properties();p.setProperty("offset-x","0.2");p.setProperty("offset-y","-0.4");p.setProperty("offset-z","-0.3");p.setProperty("image-scale","1.5");
        p.setProperty("offset-space","world");
        Settings cfg=Settings.from(p);Vector3f anchor=HandRenderer.anchor(Vector3f.from(10,20,30),2,Vector3f.ZERO,0,cfg);
        assertEquals(10.2,anchor.getX(),.0001);assertEquals(21.6,anchor.getY(),.0001);assertEquals(29.7,anchor.getZ(),.0001);
        var packet=HandRenderer.spawnImagePacket(50,anchor,(float)cfg.scale(),2);
        assertEquals(ImagePack.ENTITY,packet.getIdentifier());assertEquals(1.5f,packet.getMetadata().get(EntityDataTypes.SCALE));
        assertEquals(2,packet.getMetadata().get(EntityDataTypes.VARIANT));assertFalse(packet.getMetadata().containsKey(EntityDataTypes.NAME));
    }
    @Test void rejectsUnsupportedOrOverlargeImages() throws Exception {
        Path text=directory.resolve("invalid.gif");Files.writeString(text,"not an image");assertThrows(java.io.IOException.class,()->ImageAnimation.load(text));
        Path huge=directory.resolve("huge.png");ImageIO.write(new BufferedImage(1025,1,BufferedImage.TYPE_INT_ARGB),"PNG",huge.toFile());
        assertThrows(java.io.IOException.class,()->ImageAnimation.load(huge));
    }
    @Test void upgradesExistingConfigAndPreparesDefaultGifWithoutOverwritingUserValues() throws Exception {
        Files.writeString(directory.resolve("config.properties"),"height-offset=-0.25\nduration-ms=1400\n");
        PatPatExtension extension=mock(PatPatExtension.class,CALLS_REAL_METHODS);
        doReturn(directory).when(extension).dataFolder();
        doReturn(mock(org.geysermc.geyser.api.extension.ExtensionLogger.class)).when(extension).logger();
        extension.pre(null);
        assertNotNull(extension.animation());assertEquals(4,extension.animation().frames().size());
        assertEquals(-.25,extension.settings().height());assertEquals(1400,extension.settings().duration());
        assertTrue(Files.isRegularFile(directory.resolve("images/hand.gif")));
        assertTrue(Files.readString(directory.resolve("config.properties")).contains("offset-y=-0.25"));
        try(var files=Files.list(directory.resolve("generated"))){assertTrue(files.anyMatch(p->p.getFileName().toString().endsWith(".mcpack")));}
    }
}
