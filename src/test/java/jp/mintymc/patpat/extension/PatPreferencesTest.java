package jp.mintymc.patpat.extension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PatPreferencesTest {
    @TempDir Path directory;

    @Test void playerPreferenceSurvivesNewInstanceAndIsIsolated() throws Exception {
        Path path=directory.resolve("disabled-players.txt");
        UUID alice=UUID.randomUUID(),bob=UUID.randomUUID();
        PatPreferences first=new PatPreferences(path);
        assertTrue(first.enabled(alice));
        assertTrue(first.enabled(bob));
        assertFalse(first.setEnabled(alice,false));
        assertFalse(first.enabled(alice));
        assertTrue(first.enabled(bob));
        PatPreferences second=new PatPreferences(path);
        assertFalse(second.enabled(alice));
        assertTrue(second.enabled(bob));
        assertTrue(second.setEnabled(alice,true));
        assertTrue(new PatPreferences(path).enabled(alice));
        assertFalse(Files.readString(path,StandardCharsets.UTF_8).contains(alice.toString()));
    }

    @Test void malformedOrCommentedLinesDoNotBreakStartup() throws Exception {
        Path path=directory.resolve("disabled-players.txt");
        UUID id=UUID.randomUUID();
        Files.writeString(path,"# disabled players\nnot-a-uuid\n"+id+"\n",StandardCharsets.UTF_8);
        PatPreferences preferences=new PatPreferences(path);
        assertFalse(preferences.enabled(id));
        assertTrue(preferences.enabled(UUID.randomUUID()));
        assertTrue(preferences.enabled(null));
    }
}
