package jp.mintymc.patpat.velocity;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class PatPatProtocolTest {
    @Test void registersOnlyUpstreamClientboundChannels() {
        assertEquals(PatPatProtocol.HELLO_S2C+"\0"+PatPatProtocol.PAT_S2C,
            new String(PatPatProtocol.REGISTERED_CHANNELS,StandardCharsets.UTF_8));
    }
    @Test void v2HandshakeIsExact() {
        assertArrayEquals(new byte[]{1,2,0},PatPatProtocol.hello());
        assertTrue(PatPatProtocol.supportedHello(new byte[]{1,2,0}));
        assertTrue(PatPatProtocol.supportedHello(new byte[]{1,3,2}));
        assertFalse(PatPatProtocol.supportedHello(new byte[]{1,1,9}));
        assertFalse(PatPatProtocol.supportedHello(new byte[]{2,2,0}));
        assertFalse(PatPatProtocol.supportedHello(new byte[]{1,2}));
    }
    @Test void encodeVarIntAndReadApprovedPacket() {
        assertArrayEquals(new byte[]{(byte)0xac,2},PatPatProtocol.request(300));
        assertEquals(new PatPatProtocol.Pat(300,42),PatPatProtocol.decode(new byte[]{(byte)0xac,2,42}));
        byte[] actorNegative=PatPatProtocol.request(Integer.MIN_VALUE);
        byte[] withActor=new byte[actorNegative.length+1];
        withActor[0]=9;
        System.arraycopy(actorNegative,0,withActor,1,actorNegative.length);
        assertEquals(Integer.MIN_VALUE,PatPatProtocol.decode(withActor).actor());
    }
    @Test void nativeModPacketsCanBeBridgedBothWays() {
        assertEquals(300,PatPatProtocol.readRequest(new byte[]{(byte)0xac,2}));
        assertEquals(new PatPatProtocol.Pat(300,42),
            PatPatProtocol.decode(PatPatProtocol.notification(300,42)));
        assertThrows(IllegalArgumentException.class,
            ()->PatPatProtocol.readRequest(new byte[]{(byte)0xac,2,1}));
        assertThrows(IllegalArgumentException.class,
            ()->PatPatProtocol.readRequest(new byte[]{0}));
        assertThrows(IllegalArgumentException.class,
            ()->PatPatProtocol.notification(-1,5));
    }
    @Test void rejectsInvalidPackets() {
        assertThrows(IllegalArgumentException.class,()->PatPatProtocol.decode(new byte[]{1}));
        assertThrows(IllegalArgumentException.class,()->PatPatProtocol.decode(new byte[]{(byte)0x80,1}));
        assertThrows(IllegalArgumentException.class,()->PatPatProtocol.decode(new byte[]{1,2,3}));
        assertThrows(IllegalArgumentException.class,()->PatPatProtocol.decode(new byte[]{1,(byte)0x80,(byte)0x80,
            (byte)0x80,(byte)0x80,(byte)0x80,0}));
    }
}
