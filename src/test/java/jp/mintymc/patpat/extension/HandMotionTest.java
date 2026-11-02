package jp.mintymc.patpat.extension;

import org.cloudburstmc.math.vector.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class HandMotionTest {
    @Test void defaultStrokeStartsAtRestPressesDownThenReturnsToRest() {
        Settings cfg=Settings.from(new Properties());
        assertTrue(cfg.handMotionEnabled());
        assertEquals(240,cfg.handMotionDuration());
        assertEquals(0.425,cfg.handMotionDepth(),1e-6);
        assertEquals(0,HandMotion.displacement(0,240,.425),1e-6);
        float first=HandMotion.displacement(20,240,.425);
        float deepest=HandMotion.displacement(70,240,.425);
        float returning=HandMotion.displacement(160,240,.425);
        assertTrue(first<0,"Hand must start by lowering");
        assertTrue(deepest<first,"Hand must continue lowering before reversing");
        assertEquals(-.425,deepest,.002,"PatPat's ease-then-sine peaks near 70 ms");
        assertTrue(returning>deepest,"Hand must rise after pressing");
        assertTrue(returning<0);
        assertEquals(0,HandMotion.displacement(240,240,.425),1e-6);
        assertEquals(0,HandMotion.displacement(1000,240,.425),1e-6);
    }

    @Test void movementOnlyChangesYAndPreservesAbsoluteOffsets() {
        Properties p=new Properties();
        p.setProperty("offset-x","0.5");
        p.setProperty("offset-y","0.2");
        p.setProperty("offset-z","-0.3");
        Settings cfg=Settings.from(p);
        Vector3f start=HandRenderer.anchor(Vector3f.from(10,64,20),1.8f,Vector3f.from(20,64,20),0,cfg);
        Vector3f down=HandRenderer.motionPosition(start,70,false,cfg);
        assertEquals(start.getX(),down.getX(),1e-6);
        assertEquals(start.getZ(),down.getZ(),1e-6);
        assertEquals(start.getY()-.425,down.getY(),.003);
        assertEquals(start,HandRenderer.motionPosition(start,70,true,cfg),"Text diagnostics should remain stationary");
        assertEquals(start,HandRenderer.motionPosition(start,240,false,cfg));
    }

    @Test void featureCanBeDisabledAndTimedIndependentlyOfGifFrames() {
        Properties p=new Properties();
        p.setProperty("hand-motion-enabled","false");
        p.setProperty("hand-motion-depth","0.9");
        p.setProperty("hand-motion-duration-ms","480");
        Settings cfg=Settings.from(p);
        Vector3f base=Vector3f.from(0,2,0);
        assertFalse(cfg.handMotionEnabled());
        assertEquals(base,HandRenderer.motionPosition(base,140,false,cfg));
        p.setProperty("hand-motion-enabled","true");
        cfg=Settings.from(p);
        assertEquals(-.9,HandMotion.displacement(141,cfg.handMotionDuration(),cfg.handMotionDepth()),.002);
        assertEquals(base,HandRenderer.motionPosition(base,480,false,cfg));
    }

    @Test void invalidMovementValuesAreClampedOrIgnored() {
        Properties p=new Properties();
        p.setProperty("hand-motion-depth","NaN");
        p.setProperty("hand-motion-duration-ms","-5");
        Settings cfg=Settings.from(p);
        assertEquals(.425,cfg.handMotionDepth(),1e-6);
        assertEquals(50,cfg.handMotionDuration());
        assertEquals(0,HandMotion.displacement(-5,240,.425));
        assertEquals(0,HandMotion.displacement(40,0,.425));
        assertEquals(0,HandMotion.displacement(40,240,0));
    }
}
