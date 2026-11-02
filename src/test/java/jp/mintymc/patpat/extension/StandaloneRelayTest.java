package jp.mintymc.patpat.extension;

import net.kyori.adventure.key.Key;
import org.cloudburstmc.math.vector.Vector3f;
import org.geysermc.geyser.api.network.RemoteServer;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.EntityCache;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StandaloneRelayTest {
    @SuppressWarnings("unchecked")
    private static Map<GeyserSession,PatSession> sessions(PatPatExtension extension) throws Exception {
        Field f=PatPatExtension.class.getDeclaredField("sessions");
        f.setAccessible(true);
        return (Map<GeyserSession,PatSession>)f.get(extension);
    }
    private static GeyserSession session(Key world,RemoteServer remote) {
        GeyserSession s=mock(GeyserSession.class);
        when(s.getWorldName()).thenReturn(world);
        when(s.remoteServer()).thenReturn(remote);
        doAnswer(call->{((Runnable)call.getArgument(0)).run();return null;})
                .when(s).executeInEventLoop(any());
        return s;
    }
    @Test void relaysByUuidOnlyWithinMatchingWorldAndRemoteServer() throws Exception {
        var extension=new PatPatExtension();
        UUID id=UUID.randomUUID();
        Key world=Key.key("minecraft:overworld");
        Key otherWorld=Key.key("minecraft:the_nether");
        RemoteServer remote=mock(RemoteServer.class);
        RemoteServer another=mock(RemoteServer.class);
        var source=session(world,remote);
        var viewer=session(world,remote);
        var wrongWorld=session(otherWorld,remote);
        var wrongServer=session(world,another);
        PatSession sourceState=mock(PatSession.class),viewerState=mock(PatSession.class),
                worldState=mock(PatSession.class),serverState=mock(PatSession.class);
        when(sourceState.session()).thenReturn(source);
        when(viewerState.session()).thenReturn(viewer);
        when(worldState.session()).thenReturn(wrongWorld);
        when(serverState.session()).thenReturn(wrongServer);
        sessions(extension).put(source,sourceState);
        sessions(extension).put(viewer,viewerState);
        sessions(extension).put(wrongWorld,worldState);
        sessions(extension).put(wrongServer,serverState);
        Entity target=mock(Entity.class);
        when(target.uuid()).thenReturn(id);
        when(target.position()).thenReturn(Vector3f.from(4,65,6));
        EntityCache cache=mock(EntityCache.class);
        Entity matching=mock(Entity.class);
        when(viewer.getEntityCache()).thenReturn(cache);
        when(cache.getEntityByUuid(id)).thenReturn(matching);
        when(matching.isValid()).thenReturn(true);
        when(matching.uuid()).thenReturn(id);
        when(matching.position()).thenReturn(Vector3f.from(4,65,6));
        when(matching.javaId()).thenReturn(105);
        extension.relayStandalone(sourceState,target);
        verify(viewerState).showStandalone(105);
        verify(worldState,never()).showStandalone(anyInt());
        verify(serverState,never()).showStandalone(anyInt());
        verify(sourceState,never()).showStandalone(anyInt());
    }
    @Test void entityUuidAndPositionMustMatchBeforeRelaying() throws Exception {
        var extension=new PatPatExtension();
        var world=Key.key("minecraft:overworld");
        var remote=mock(RemoteServer.class);
        var source=session(world,remote);
        var viewer=session(world,remote);
        PatSession actor=mock(PatSession.class),recipient=mock(PatSession.class);
        when(actor.session()).thenReturn(source);when(recipient.session()).thenReturn(viewer);
        sessions(extension).put(source,actor);sessions(extension).put(viewer,recipient);
        UUID id=UUID.randomUUID();
        Entity target=mock(Entity.class);
        when(target.uuid()).thenReturn(id);
        when(target.position()).thenReturn(Vector3f.from(4,65,6));
        var cache=mock(EntityCache.class);when(viewer.getEntityCache()).thenReturn(cache);
        var candidate=mock(Entity.class);when(cache.getEntityByUuid(id)).thenReturn(candidate);
        when(candidate.isValid()).thenReturn(true);
        when(candidate.uuid()).thenReturn(UUID.randomUUID()); // Defensive check
        extension.relayStandalone(actor,target);
        verify(recipient,never()).showStandalone(anyInt());
    }
}
