package jp.mintymc.patpat.extension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TogglePreferencesTest {
    @TempDir Path folder;

    @Test void togglingOffSurvivesReloadAndOnClearsTheSavedMute() throws Exception {
        Path file=folder.resolve("disabled-players.txt");
        UUID player=UUID.randomUUID();
        UUID other=UUID.randomUUID();
        var preferences=new PatPreferences(file);
        assertTrue(preferences.enabled(player));
        // setEnabled returns the resulting state, not a success flag.
        assertFalse(preferences.setEnabled(player,false));
        assertFalse(preferences.enabled(player));
        assertTrue(preferences.enabled(other));

        var reloaded=new PatPreferences(file);
        assertFalse(reloaded.enabled(player),"OFF must persist after reconnect/restart");
        assertTrue(reloaded.enabled(other));
        assertFalse(reloaded.setEnabled(player,false) || reloaded.enabled(player),
            "Explicit OFF must not accidentally toggle the preference back ON");
        assertTrue(reloaded.setEnabled(player,true));
        assertTrue(new PatPreferences(file).enabled(player),"ON must persist too");
    }

    @Test void proxyBridgeReportsSuccessForOffInsteadOfMistakingFalseForFailure() throws Exception {
        Path file=folder.resolve("disabled-players.txt");
        var extension=new PatPatExtension();
        var storage=PatPatExtension.class.getDeclaredField("preferences");
        storage.setAccessible(true);
        storage.set(extension,new PatPreferences(file));
        var callback=PatPatExtension.class.getDeclaredMethod("toggleFromProxy",Object[].class);
        callback.setAccessible(true);
        UUID id=UUID.randomUUID();
        Object muted=callback.invoke(extension,(Object)new Object[]{id,false});
        assertEquals(true,muted,"Saving OFF is a successful operation");
        assertFalse(new PatPreferences(file).enabled(id));
        Object enabled=callback.invoke(extension,(Object)new Object[]{id,true});
        assertEquals(true,enabled);
        assertTrue(new PatPreferences(file).enabled(id));
    }

    @Test void twoUsersStayIndependentAndRepeatedCommandsAreIdempotent() throws Exception {
        var prefs=new PatPreferences(folder.resolve("disabled-players.txt"));
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        prefs.setEnabled(a,false);
        prefs.setEnabled(a,false);
        assertFalse(prefs.enabled(a));
        assertTrue(prefs.enabled(b));
        prefs.setEnabled(b,false);
        prefs.setEnabled(a,true);
        assertTrue(prefs.enabled(a));
        assertFalse(new PatPreferences(folder.resolve("disabled-players.txt")).enabled(b));
    }
}
