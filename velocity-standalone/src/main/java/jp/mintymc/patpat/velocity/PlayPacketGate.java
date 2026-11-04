package jp.mintymc.patpat.velocity;

import com.github.retrooper.packetevents.protocol.ConnectionState;

/**
 * Guard against sending PLAY packets while Velocity is connecting a player
 * to a different backend (CONFIGURATION is not allowed to decode PLAY packets).
 * The encoder state, not the decoder state, defines the client's packet format.
 */
final class PlayPacketGate {
    private PlayPacketGate() {}

    static boolean permits(boolean transferring, boolean currentView,
                           boolean connected, boolean sameBackend,
                           ConnectionState outboundState) {
        return !transferring && currentView && connected && sameBackend
                && outboundState == ConnectionState.PLAY;
    }
}
