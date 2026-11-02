package jp.mintymc.patpat.extension;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player accepted Pat requests. Buffered persistence avoids disk I/O on each pat. */
final class PatStatistics {
    private final Path path;
    private final Map<UUID,Long> counts=new ConcurrentHashMap<>();
    private boolean dirty;
    private long lastSavedNanos=System.nanoTime();

    PatStatistics(Path path) throws IOException {
        this.path=path;
        if(Files.isRegularFile(path)){
            Properties p=new Properties();
            try(InputStream input=Files.newInputStream(path)){p.load(input);}
            for(String key:p.stringPropertyNames())try {
                long value=Long.parseLong(p.getProperty(key));
                if(value>0)counts.put(UUID.fromString(key),value);
            }catch(IllegalArgumentException ignored){}
        }
    }
    synchronized long record(UUID uuid) {
        if(uuid==null)return 0;
        long total=counts.merge(uuid,1L,(oldValue,add)->oldValue==Long.MAX_VALUE?oldValue:oldValue+add);
        dirty=true;
        return total;
    }
    long total(UUID uuid) {return uuid==null?0:counts.getOrDefault(uuid,0L);}
    synchronized void flush() throws IOException {
        if(!dirty)return;
        Files.createDirectories(path.getParent());
        Properties p=new Properties();
        counts.forEach((uuid,count)->p.setProperty(uuid.toString(),count.toString()));
        Path temporary=Files.createTempFile(path.getParent(),"pat-stats-",".tmp");
        try {
            try(OutputStream output=Files.newOutputStream(temporary)){p.store(output,"PatPat Bedrock accepted requests");}
            try {Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}
            dirty=false;lastSavedNanos=System.nanoTime();
        }finally {Files.deleteIfExists(temporary);}
    }
    synchronized boolean shouldFlush() {
        return dirty && System.nanoTime()-lastSavedNanos>=5_000_000_000L;
    }
}
