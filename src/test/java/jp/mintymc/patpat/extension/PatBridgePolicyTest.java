package jp.mintymc.patpat.extension;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PatBridgePolicyTest {
    private final UUID actor=UUID.randomUUID(),target=UUID.randomUUID();

    @Test void sameTargetAndInvalidDistanceNeverMirror(){
        assertFalse(PatBridgePolicy.sameWorldRange(actor,actor,0,4));
        assertFalse(PatBridgePolicy.sameWorldRange(null,target,0,4));
        assertFalse(PatBridgePolicy.sameWorldRange(actor,null,0,4));
        assertFalse(PatBridgePolicy.sameWorldRange(actor,target,Double.NaN,4));
        assertFalse(PatBridgePolicy.sameWorldRange(actor,target,Double.POSITIVE_INFINITY,4));
        assertFalse(PatBridgePolicy.sameWorldRange(actor,target,-1,4));
        assertFalse(PatBridgePolicy.sameWorldRange(actor,target,0,0));
    }
    @Test void asyncToleranceOnlyAllowsNearbyPatter(){
        assertTrue(PatBridgePolicy.sameWorldRange(actor,target,16,4));
        assertTrue(PatBridgePolicy.sameWorldRange(actor,target,36,4));
        assertFalse(PatBridgePolicy.sameWorldRange(actor,target,36.01,4));
        assertFalse(PatBridgePolicy.sameWorldRange(actor,target,100,4));
    }
}
