package jp.mintymc.patpat.velocity;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Upstream LopyMine/PatPat-Plugin v1.2+ plugin-message wire format.
 * Only a v2 hello is accepted; older protocols are not approximated.
 */
final class PatPatProtocol {
    static final String HELLO_C2S="patpat:hello_patpat_server_c2s_packet";
    static final String HELLO_S2C="patpat:hello_patpat_player_s2c_packet";
    static final String PAT_C2S="patpat:pat_entity_c2s_packet_v2";
    static final String PAT_S2C="patpat:pat_entity_s2c_packet_v2";
    static final byte[] REGISTERED_CHANNELS=(HELLO_S2C+"\0"+PAT_S2C).getBytes(StandardCharsets.UTF_8);

    record Pat(int target,int actor) {}
    private PatPatProtocol(){}
    static byte[] hello(){return new byte[]{1,2,0};}
    static boolean supportedHello(byte[] bytes) {
        return bytes!=null && bytes.length==3 &&
            (bytes[0]&255)==1 && (bytes[1]&255)>=2;
    }
    static byte[] request(int entityId) {
        ByteArrayOutputStream out=new ByteArrayOutputStream(5);
        do {
            int b=entityId & 0x7F;
            entityId >>>= 7;
            out.write(entityId==0?b:b|0x80);
        }while(entityId!=0);
        return out.toByteArray();
    }
    /** Strict target-only request from the native PatPat client. */
    static int readRequest(byte[] bytes) {
        if(bytes==null || bytes.length<1 || bytes.length>5)
            throw new IllegalArgumentException("Invalid native Pat request");
        int[] index={0};
        int target=readVarInt(bytes,index);
        if(target<=0 || index[0]!=bytes.length)
            throw new IllegalArgumentException("Invalid target entity ID or trailing bytes");
        return target;
    }
    /** Native PatPat v2 clientbound event: target and actor VarInts. */
    static byte[] notification(int target,int actor) {
        if(target<=0 || actor<=0)throw new IllegalArgumentException("Invalid Pat entity ID");
        ByteArrayOutputStream out=new ByteArrayOutputStream(10);
        out.writeBytes(request(target));
        out.writeBytes(request(actor));
        return out.toByteArray();
    }
    static Pat decode(byte[] bytes) {
        if(bytes==null || bytes.length<2 || bytes.length>10)
            throw new IllegalArgumentException("Invalid PatPat v2 packet length");
        int[] index={0};
        int target=readVarInt(bytes,index);
        int actor=readVarInt(bytes,index);
        if(index[0]!=bytes.length)
            throw new IllegalArgumentException("Unexpected PatPat v2 trailing data");
        return new Pat(target,actor);
    }
    private static int readVarInt(byte[] bytes,int[] index) {
        int value=0;
        for(int i=0;i<5;i++) {
            if(index[0]>=bytes.length)throw new IllegalArgumentException("Truncated PatPat VarInt");
            int b=bytes[index[0]++]&255;
            if(i==4 && (b&0xF0)!=0)throw new IllegalArgumentException("VarInt overflow");
            value|=(b&0x7F)<<(7*i);
            if((b&0x80)==0)return value;
        }
        throw new IllegalArgumentException("PatPat VarInt too long");
    }
}
