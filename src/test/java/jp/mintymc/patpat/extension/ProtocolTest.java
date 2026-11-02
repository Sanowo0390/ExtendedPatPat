package jp.mintymc.patpat.extension;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;

class ProtocolTest {
    @Test void matchesPatPatV2GoldenBytes() {
        assertArrayEquals(new byte[]{1,2,0},PatProtocol.hello());
        assertArrayEquals(new byte[]{(byte)0xAC,2},PatProtocol.pat(300));
        assertArrayEquals(new byte[]{(byte)0x80,(byte)0x80,(byte)0x80,(byte)0x80,8},PatProtocol.pat(Integer.MIN_VALUE));
    }
    @Test void decodesJavaAndNonPlayerActors() {
        for(int actor:new int[]{1,300,Integer.MIN_VALUE}) {
            ByteArrayOutputStream b=new ByteArrayOutputStream();b.writeBytes(PatProtocol.pat(300));b.writeBytes(PatProtocol.pat(actor));
            assertEquals(new PatProtocol.Pat(300,actor),PatProtocol.decode(b.toByteArray()));
        }
    }
    @Test void rejectsTruncationOverflowAndTrailingPayload() {
        for(byte[] data:new byte[][]{ {},{1},{(byte)128,1},{1,1,0},{1,(byte)128,(byte)128,(byte)128,(byte)128,16},new byte[11] })
            assertThrows(IllegalArgumentException.class,()->PatProtocol.decode(data));
    }
    @Test void requiresV2Hello() {
        assertTrue(PatProtocol.supportedHello(new byte[]{1,2,5}));
        assertFalse(PatProtocol.supportedHello(new byte[]{1,1,9}));
        assertFalse(PatProtocol.supportedHello(new byte[]{2,2,0}));
        assertFalse(PatProtocol.supportedHello(new byte[]{1,2}));
    }
}
