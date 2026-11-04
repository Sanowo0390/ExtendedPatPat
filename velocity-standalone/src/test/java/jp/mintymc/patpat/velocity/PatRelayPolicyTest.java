package jp.mintymc.patpat.velocity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PatRelayPolicyTest {
    @Test void aDeferredRelayMustStillBelongToTheOriginalBackendAndView(){
        assertTrue(PatRelayPolicy.sameOrigin("survival","survival",true,false));
        assertFalse(PatRelayPolicy.sameOrigin("survival","lobby",true,false));
        assertFalse(PatRelayPolicy.sameOrigin("survival","survival",false,false));
        assertFalse(PatRelayPolicy.sameOrigin("survival","survival",true,true));
        assertFalse(PatRelayPolicy.sameOrigin(null,"survival",true,false));
        assertFalse(PatRelayPolicy.sameOrigin("","survival",true,false));
    }
}
