package jp.mintymc.patpat.extension;

import net.kyori.adventure.key.Key;
import org.cloudburstmc.math.vector.Vector3f;
import org.geysermc.geyser.api.network.RemoteServer;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.EntityCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StandaloneRelayTest {
    @TempDir Path directory;
    // Real preference storage is required: a relay is now forbidden for
    // either a muted sender or muted recipient.
    private void configure(PatPatExtension extension,GeyserSession... connections) throws Exception {
        Field field=PatPatExtension.class.getDeclaredField("preferences");
        field.setAccessible(true);
        field.set(extension,new PatPreferences(directory.resolve("disabled-players.txt")));
        for(GeyserSession connection:connections){
            if(connection.getPlayerEntity()!=null)continue;
            SessionPlayerEntity player=mock(SessionPlayerEntity.class);
            when(player.uuid()).thenReturn(UUID.randomUUID());
            when(connection.getPlayerEntity()).thenReturn(player);
        }
    }
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
        configure(extension,source,viewer,wrongWorld,wrongServer);
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
        configure(extension,source,viewer);
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
    @Test void receivingBedrockPlayerGetsCameraFeedbackButPatterDoesNot() throws Exception {
        var extension=new PatPatExtension();
        UUID targetUuid=UUID.randomUUID();
        Key world=Key.key("minecraft:overworld");
        RemoteServer remote=mock(RemoteServer.class);
        GeyserSession source=session(world,remote),recipient=session(world,remote),observer=session(world,remote);
        PatSession initiatorState=mock(PatSession.class),recipientState=mock(PatSession.class),observerState=mock(PatSession.class);
        when(initiatorState.session()).thenReturn(source);
        when(recipientState.session()).thenReturn(recipient);
        when(observerState.session()).thenReturn(observer);
        sessions(extension).put(source,initiatorState);
        sessions(extension).put(recipient,recipientState);
        sessions(extension).put(observer,observerState);

        Entity target=mock(Entity.class);
        Vector3f at=Vector3f.from(2,70,3);
        when(target.uuid()).thenReturn(targetUuid);
        when(target.position()).thenReturn(at);

        org.geysermc.geyser.entity.type.player.SessionPlayerEntity self=
                mock(org.geysermc.geyser.entity.type.player.SessionPlayerEntity.class);
        when(self.uuid()).thenReturn(targetUuid);
        when(self.position()).thenReturn(at);
        when(self.javaId()).thenReturn(29);
        when(self.isValid()).thenReturn(true);
        when(recipient.getPlayerEntity()).thenReturn(self);
        configure(extension,source,recipient,observer);

        var observerCache=mock(EntityCache.class);
        when(observer.getEntityCache()).thenReturn(observerCache);
        Entity visible=mock(Entity.class);
        when(observerCache.getEntityByUuid(targetUuid)).thenReturn(visible);
        when(visible.uuid()).thenReturn(targetUuid);
        when(visible.isValid()).thenReturn(true);
        when(visible.position()).thenReturn(at);
        when(visible.javaId()).thenReturn(29);

        extension.relayStandalone(initiatorState,target);
        verify(recipientState).showStandalone(29);
        verify(recipientState).receivePatFeedback();
        verify(observerState).showStandalone(29);
        verify(observerState,never()).receivePatFeedback();
        verify(initiatorState,never()).receivePatFeedback();
    }

    @Test void disabledViewerAndSenderNeverReceiveOrBroadcastPat() throws Exception {
        var extension=new PatPatExtension();
        Key world=Key.key("minecraft:overworld");
        RemoteServer remote=mock(RemoteServer.class);
        GeyserSession sender=session(world,remote),receiver=session(world,remote);
        configure(extension,sender,receiver);
        PatSession patter=mock(PatSession.class),viewer=mock(PatSession.class);
        when(patter.session()).thenReturn(sender);
        when(viewer.session()).thenReturn(receiver);
        sessions(extension).put(sender,patter);sessions(extension).put(receiver,viewer);
        UUID targetId=UUID.randomUUID();
        Entity target=mock(Entity.class);
        when(target.uuid()).thenReturn(targetId);
        when(target.position()).thenReturn(Vector3f.from(0,64,0));
        EntityCache cache=mock(EntityCache.class);
        when(receiver.getEntityCache()).thenReturn(cache);
        Entity candidate=mock(Entity.class);
        when(cache.getEntityByUuid(targetId)).thenReturn(candidate);
        when(candidate.uuid()).thenReturn(targetId);
        when(candidate.isValid()).thenReturn(true);
        when(candidate.position()).thenReturn(Vector3f.from(0,64,0));
        when(candidate.javaId()).thenReturn(42);
        extension.setPatEnabled(receiver,false);
        extension.relayStandalone(patter,target);
        verify(viewer,never()).showStandalone(anyInt());
        extension.setPatEnabled(receiver,true);
        extension.setPatEnabled(sender,false);
        extension.relayStandalone(patter,target);
        verify(viewer,never()).showStandalone(anyInt());
        extension.setPatEnabled(sender,true);
        extension.relayStandalone(patter,target);
        verify(viewer).showStandalone(42);
    }

}
