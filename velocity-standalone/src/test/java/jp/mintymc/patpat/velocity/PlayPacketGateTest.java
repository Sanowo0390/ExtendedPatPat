package jp.mintymc.patpat.velocity;

import com.github.retrooper.packetevents.protocol.ConnectionState;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PlayPacketGateTest {
    @Test void sendsOnlyToCurrentStablePlayConnection() {
        assertTrue(PlayPacketGate.permits(false,true,true,true,ConnectionState.PLAY));
        assertFalse(PlayPacketGate.permits(true,true,true,true,ConnectionState.PLAY),
            "ServerPreConnectEvent must stop an old backend's queued animation");
        assertFalse(PlayPacketGate.permits(false,true,true,true,ConnectionState.CONFIGURATION),
            "A PLAY packet in CONFIGURATION disconnects Minecraft with an unknown packet ID");
        assertFalse(PlayPacketGate.permits(false,false,true,true,ConnectionState.PLAY),
            "No packets from the stale View after ServerConnectedEvent replaces it");
        assertFalse(PlayPacketGate.permits(false,true,true,false,ConnectionState.PLAY),
            "The target backend must match this view");
        assertFalse(PlayPacketGate.permits(false,true,false,true,ConnectionState.PLAY),
            "Disconnected players cannot receive fake entities");
        assertFalse(PlayPacketGate.permits(false,true,true,true,ConnectionState.LOGIN));
    }

    @Test void everyFakePacketGoesThroughTheOutboundPlayGate() throws Exception {
        String source=Files.readString(Path.of("src/main/java/jp/mintymc/patpat/velocity/PatPatVelocity.java"));
        assertEquals(1,source.split("view.user.sendPacketSilently\\(", -1).length-1,
            "No raw sends outside the centralized PLAY-state guard");
        assertTrue(source.contains("view.user.getEncoderState()"),
            "Client-bound writes must use encoder, not decoder protocol state");
        assertTrue(source.contains("ServerPreConnectEvent"),
            "Stop effects at start of a backend switch, not only when it completes");
        assertTrue(source.contains("replacement.backend=event.getServer().getServerInfo().getName()"),
            "ServerConnectedEvent must use event destination; getCurrentServer may be null");
        assertFalse(source.contains("replacement.backend=serverOf(player)"),
            "This bug made canSendPlay reject all Pat effects after joining a backend");
        assertTrue(source.contains("previous.switching=false;"),
            "Recover Pat rendering when an NPC's backend switch fails");
        assertTrue(source.contains("delay(75L,TimeUnit.MILLISECONDS)"),
            "Give NPC server transfer handlers an opportunity to begin before spawning maps");
        assertTrue(source.contains("if(event.getConnectionState()!=ConnectionState.PLAY)return;"),
            "No PacketEvents callbacks in configuration should rebuild a play-only view");
        assertTrue(source.contains("sendPlay(view,new WrapperPlayServerDestroyEntities"),
            "Even cleanup packets must be suppressed in CONFIGURATION");
        assertTrue(source.contains("event.getConnectionState()!=ConnectionState.PLAY"),
            "Never interpret non-PLAY packets as Pat interactions");
    }
}
