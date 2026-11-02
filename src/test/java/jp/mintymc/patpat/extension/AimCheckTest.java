package jp.mintymc.patpat.extension;

import org.cloudburstmc.math.vector.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AimCheckTest {
    private static final Vector3f EYE=Vector3f.from(0,1.27f,0);
    private static final Vector3f TARGET=Vector3f.from(0,0,2);
    @Test void onlyAcceptsTargetUnderCrosshair() {
        assertTrue(AimCheck.hits(EYE,0,0,TARGET,0.6,1.8,3));
        assertFalse(AimCheck.hits(EYE,90,0,TARGET,0.6,1.8,3),
                "Being near an entity is not enough if the player faces away");
        assertFalse(AimCheck.hits(EYE,0,-70,TARGET,0.6,1.8,3),
                "Looking above the target must not count");
    }
    @Test void checksRangeAndHitboxInsteadOfEntityCenter() {
        assertTrue(AimCheck.hits(EYE,0,0,Vector3f.from(0,0,3.2f),1,1.8,3));
        assertFalse(AimCheck.hits(EYE,0,0,Vector3f.from(0,0,4),1,1.8,3));
        assertFalse(AimCheck.hits(EYE,0,0,Vector3f.from(1.5f,0,2),0.6,1.8,3));
    }
    @Test void supportsVerticalLookAndInsideHitbox() {
        assertTrue(AimCheck.hits(Vector3f.from(0,1,0),0,0,Vector3f.from(0,0,0),1,2,3));
        assertTrue(AimCheck.hits(EYE,0,45,Vector3f.from(0,-2,2),1,2,3));
    }
    @Test void returnsClosestRayIntersectionAndInfinityOutsideAim() {
        double near=AimCheck.intersectionDistance(EYE,0,0,Vector3f.from(0,0,1.2f),0.6,1.8,3);
        double far=AimCheck.intersectionDistance(EYE,0,0,Vector3f.from(0,0,2.5f),0.6,1.8,3);
        assertTrue(near<far);
        assertEquals(Double.POSITIVE_INFINITY,
                AimCheck.intersectionDistance(EYE,90,0,TARGET,0.6,1.8,3));
    }

}
