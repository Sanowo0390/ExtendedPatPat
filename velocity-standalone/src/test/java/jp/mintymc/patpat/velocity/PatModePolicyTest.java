package jp.mintymc.patpat.velocity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PatModePolicyTest {
    @Test void standaloneModeHasNoBackendWaitEvenIfHelloArrives() {
        assertEquals(PatModePolicy.Mode.STANDALONE,
            PatModePolicy.resolve(false,false,true,false,0,3000));
    }
    @Test void optionalOfficialWaitsAndFallsBack() {
        assertEquals(PatModePolicy.Mode.DETECTING,
            PatModePolicy.resolve(false,true,false,true,2900,3000));
        assertEquals(PatModePolicy.Mode.STANDALONE,
            PatModePolicy.resolve(false,true,false,true,3000,3000));
        assertEquals(PatModePolicy.Mode.OFFICIAL_BACKEND,
            PatModePolicy.resolve(false,true,true,true,100,3000));
    }
    @Test void strictOfficialRequiresBackend() {
        assertEquals(PatModePolicy.Mode.DISABLED,
            PatModePolicy.resolve(false,true,false,false,3001,3000));
    }
    @Test void nativeClientModSuppressesDuplicateVanillaEffect() {
        assertEquals(PatModePolicy.Mode.NATIVE_CLIENT_MOD,
            PatModePolicy.resolve(true,false,false,true,5000,3000));
    }
}
