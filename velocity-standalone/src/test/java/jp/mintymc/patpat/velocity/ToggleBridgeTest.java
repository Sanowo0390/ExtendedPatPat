package jp.mintymc.patpat.velocity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class ToggleBridgeTest {
    private static final String USERS="jp.mintymc.patpat.bridge.v1.bedrock-users";
    private static final String READ="jp.mintymc.patpat.bridge.v1.toggle-read";
    private static final String WRITE="jp.mintymc.patpat.bridge.v1.toggle-write";
    @TempDir Path folder;

    private static Object invoke(PatPatVelocity plugin,String method,UUID player) throws Exception {
        Method m=PatPatVelocity.class.getDeclaredMethod(method,UUID.class);
        m.setAccessible(true);
        return m.invoke(plugin,player);
    }
    private static Object write(PatPatVelocity plugin,UUID player,boolean enabled) throws Exception {
        Method m=PatPatVelocity.class.getDeclaredMethod("writeBedrockToggle",UUID.class,boolean.class);
        m.setAccessible(true);
        return m.invoke(plugin,player,enabled);
    }
    @Test void proxyAndGeyserToggleSameBedrockPreference() throws Exception {
        UUID player=UUID.randomUUID();
        Object previousUsers=System.getProperties().get(USERS);
        Object previousRead=System.getProperties().get(READ);
        Object previousWrite=System.getProperties().get(WRITE);
        AtomicBoolean enabled=new AtomicBoolean(true);
        try {
            Set<UUID> bedrock=ConcurrentHashMap.newKeySet();
            bedrock.add(player);
            System.getProperties().put(USERS,bedrock);
            System.getProperties().put(READ,(Function<UUID,Boolean>)id->enabled.get());
            System.getProperties().put(WRITE,(Function<Object[],Boolean>)args->{
                if(!player.equals(args[0]))return false;
                enabled.set((Boolean)args[1]);
                return true;
            });
            var plugin=new PatPatVelocity(null,null,folder);
            assertEquals(true,invoke(plugin,"personalEnabled",player));
            assertEquals(true,write(plugin,player,false));
            assertEquals(false,invoke(plugin,"personalEnabled",player));
            assertEquals(true,write(plugin,player,true));
            assertEquals(true,invoke(plugin,"personalEnabled",player));
        } finally {
            restore(USERS,previousUsers);restore(READ,previousRead);restore(WRITE,previousWrite);
        }
    }
    @Test void missingGeyserCallbackFailsClosedInsteadOfReportingToggleSuccess() throws Exception {
        Object previous=System.getProperties().remove(WRITE);
        try {
            var plugin=new PatPatVelocity(null,null,folder);
            assertEquals(false,write(plugin,UUID.randomUUID(),false));
        } finally {restore(WRITE,previous);}
    }
    private static void restore(String key,Object value){
        if(value==null)System.getProperties().remove(key);
        else System.getProperties().put(key,value);
    }
}
