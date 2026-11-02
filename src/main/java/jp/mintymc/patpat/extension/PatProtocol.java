package jp.mintymc.patpat.extension;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

final class PatProtocol {
    static final String HELLO_C2S="patpat:hello_patpat_server_c2s_packet";
    static final String HELLO_S2C="patpat:hello_patpat_player_s2c_packet";
    static final String PAT_C2S="patpat:pat_entity_c2s_packet_v2";
    static final String PAT_S2C="patpat:pat_entity_s2c_packet_v2";
    static final byte[] CHANNELS=(HELLO_S2C+'\0'+PAT_S2C).getBytes(StandardCharsets.UTF_8);
    private PatProtocol() {}
    static byte[] hello() { return new byte[]{1,2,0}; }
    static boolean supportedHello(byte[] bytes) {
        return bytes.length>=3 && (bytes[0]&255)==1 && (bytes[1]&255)>=2;
    }
    static byte[] pat(int javaId) {
        ByteArrayOutputStream out=new ByteArrayOutputStream(5);
        do { int next=javaId&127; javaId >>>=7; out.write(javaId==0 ? next : next|128); } while (javaId!=0);
        return out.toByteArray();
    }
    record Pat(int target, int actor) {}
    static Pat decode(byte[] data) {
        if(data.length<2 || data.length>10) throw new IllegalArgumentException("Invalid PatPat payload length");
        int[] index={0}; int target=read(data,index); int actor=read(data,index);
        if (target<0 || index[0]!=data.length) throw new IllegalArgumentException("Invalid target/trailing data");
        return new Pat(target,actor);
    }
    private static int read(byte[] data,int[] index) {
        int result=0;
        for(int shift=0;shift<35;shift+=7) {
            if(index[0]>=data.length) throw new IllegalArgumentException("Truncated VarInt");
            int b=data[index[0]++]&255;
            if(shift==28 && (b&240)!=0) throw new IllegalArgumentException("VarInt overflow");
            result|=(b&127)<<shift;
            if((b&128)==0) return result;
        }
        throw new IllegalArgumentException("VarInt too long");
    }
}
