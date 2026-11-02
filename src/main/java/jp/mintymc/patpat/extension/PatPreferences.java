package jp.mintymc.patpat.extension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent per-player Pat send/receive preferences. Players are enabled by default. */
final class PatPreferences {
    private final Path file;
    private final Set<UUID> disabled=ConcurrentHashMap.newKeySet();

    PatPreferences(Path file) throws IOException {
        this.file=file;
        if(Files.isRegularFile(file)) {
            for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)) {
                String value=line.trim();
                if(value.isBlank() || value.startsWith("#"))continue;
                try { disabled.add(UUID.fromString(value)); }
                catch(IllegalArgumentException ignored) { /* Ignore invalid legacy entries. */ }
            }
        }
    }
    boolean enabled(UUID uuid) {return uuid==null || !disabled.contains(uuid);}

    synchronized boolean setEnabled(UUID uuid,boolean enabled) throws IOException {
        if(uuid==null)throw new IOException("Player UUID is unavailable");
        boolean previous=enabled(uuid);
        if(previous==enabled)return enabled;
        if(enabled)disabled.remove(uuid);else disabled.add(uuid);
        try { save(); }
        catch(IOException e) {
            if(previous)disabled.remove(uuid);else disabled.add(uuid);
            throw e;
        }
        return enabled;
    }

    private void save() throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary=Files.createTempFile(file.getParent(),"pat-disabled-",".tmp");
        try {
            List<String> lines=disabled.stream().map(UUID::toString).sorted().toList();
            Files.write(temporary,lines,StandardCharsets.UTF_8,StandardOpenOption.TRUNCATE_EXISTING);
            try {Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e) {Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
    }
}
