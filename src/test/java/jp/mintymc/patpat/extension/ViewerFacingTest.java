package jp.mintymc.patpat.extension;

import com.google.gson.*;
import org.cloudburstmc.math.vector.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class ViewerFacingTest {
    Settings settings(double x,double y,double z) {
        Properties p=new Properties();p.setProperty("offset-x",""+x);p.setProperty("offset-y",""+y);p.setProperty("offset-z",""+z);
        return Settings.from(p);
    }
    @Test void positiveXAlwaysMovesTowardTheViewerFromEveryHorizontalDirection() {
        Settings cfg=settings(.5,-.2,0);
        for(int degrees=0;degrees<360;degrees+=15) {
            double angle=Math.toRadians(degrees);Vector3f viewer=Vector3f.from(10*Math.cos(angle),2,10*Math.sin(angle));
            Vector3f actual=HandRenderer.anchor(Vector3f.ZERO,2,viewer,0,cfg);
            assertEquals(.5*Math.cos(angle),actual.getX(),1e-6);
            assertEquals(.5*Math.sin(angle),actual.getZ(),1e-6);
            assertEquals(1.8,actual.getY(),1e-6,"X never changes height");
        }
    }
    @Test void positiveZMovesToViewersRightAndNotAlongWorldZ() {
        Settings cfg=settings(0,0,.3);
        Vector3f south=HandRenderer.anchor(Vector3f.ZERO,2,Vector3f.from(0,0,10),0,cfg);
        Vector3f west=HandRenderer.anchor(Vector3f.ZERO,2,Vector3f.from(-10,0,0),0,cfg);
        assertEquals(.3,south.getX(),1e-6);assertEquals(0,south.getZ(),1e-6);
        assertEquals(0,west.getX(),1e-6);assertEquals(.3,west.getZ(),1e-6);
    }
    @Test void coincidentSelfTestUsesCameraYawWithoutNaNs() {
        Vector3f actual=HandRenderer.anchor(Vector3f.ZERO,2,Vector3f.ZERO,90,settings(.5,0,0));
        assertEquals(.5,actual.getX(),1e-6);assertEquals(0,actual.getZ(),1e-6);assertEquals(2,actual.getY(),1e-6);
    }
    @Test void twoViewersGetTheirOwnOffsetDirection() {
        Settings cfg=settings(.4,0,0);
        Vector3f a=HandRenderer.anchor(Vector3f.ZERO,2,Vector3f.from(5,0,0),0,cfg);
        Vector3f b=HandRenderer.anchor(Vector3f.ZERO,2,Vector3f.from(-5,0,0),0,cfg);
        assertEquals(.4,a.getX(),1e-6);assertEquals(-.4,b.getX(),1e-6);
    }
    @Test void packUsesOfficialCameraBillboardSignsAndCancelsBodyYaw(@TempDir Path directory) throws Exception {
        var animation=new ImageAnimation(List.of(new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB)),List.of(800));
        try(ZipFile zip=new ZipFile(ImagePack.build(directory,animation).toFile())) {
            var json=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("animations/hand.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray rotation=json.getAsJsonObject("animations").getAsJsonObject("animation.patpatbedrock.billboard").getAsJsonObject("bones").getAsJsonObject("billboard").getAsJsonArray("rotation");
            assertEquals("query.camera_rotation(0)",rotation.get(0).getAsString());
            assertEquals("query.camera_rotation(1) - query.body_y_rotation",rotation.get(1).getAsString());
            assertEquals(0,rotation.get(2).getAsInt());
            var manifest=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("manifest.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(5,manifest.getAsJsonObject("header").getAsJsonArray("version").get(0).getAsInt(),"Force update of cached 0.3.0 billboard");
        }
    }
    @Test void legacyAutomaticFrontOptionsDoNotDisplaceHand() {
        // Older users may still have these values in config.properties.
        // They are intentionally ignored after reverting automatic placement.
        Properties p=new Properties();
        p.setProperty("offset-x","0.5");
        p.setProperty("offset-y","0.2");
        p.setProperty("hand-avoid-occlusion","true");
        p.setProperty("hand-front-clearance","1.5");
        Settings cfg=Settings.from(p);
        for(int degrees=0;degrees<360;degrees+=30) {
            double angle=Math.toRadians(degrees);
            Vector3f viewer=Vector3f.from((float)(12*Math.cos(angle)),2,(float)(12*Math.sin(angle)));
            Vector3f hand=HandRenderer.anchor(Vector3f.ZERO,2,viewer,0,cfg);
            assertEquals(.5*Math.cos(angle),hand.getX(),1e-6,"No automatic depth compensation");
            assertEquals(.5*Math.sin(angle),hand.getZ(),1e-6);
            assertEquals(2.2,hand.getY(),1e-6);
        }
    }
}
