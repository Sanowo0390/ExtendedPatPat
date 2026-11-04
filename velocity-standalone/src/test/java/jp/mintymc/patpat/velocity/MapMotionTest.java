package jp.mintymc.patpat.velocity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapMotionTest {
    @Test void geyserStyleEasingMovesDownEarlyThenReturns() {
        assertEquals(0,MapMotion.poseAt(0,6,0.425));
        assertEquals(4,MapMotion.poseAt(2,6,0.425));
        assertEquals(3,MapMotion.poseAt(3,6,0.425));
        assertEquals(0,MapMotion.poseAt(6,6,0.425));
        assertTrue(MapMotion.poseAt(1,6,0.425)>0);
        assertTrue(MapMotion.poseAt(4,6,0.425)<MapMotion.poseAt(2,6,0.425));
    }
    @Test void depthAndDurationControlMotionSafely() {
        assertEquals(0,MapMotion.poseAt(3,6,0));
        assertEquals(1,MapMotion.poseAt(3,6,0.2125));
        assertEquals(0,MapMotion.poseAt(-1,6,0.425));
        assertEquals(0,MapMotion.poseAt(7,6,0.425));
        assertEquals(0,MapMotion.poseAt(2,6,Double.NaN));
        assertEquals(4,MapMotion.poseAt(2,4,2.0));
    }
}
