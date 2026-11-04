package jp.mintymc.patpat.velocity;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Pre-quantized 128x128 map pixels; one immutable frame per skin/animation frame. */
final class MapFrames {
    private final Map<String,byte[][]> frames=new HashMap<>();
    MapFrames(SkinCatalog catalog) {
        for(String id:catalog.ids()){
            int count=catalog.get(id).frames();
            byte[][] data=new byte[count][];
            for(int i=0;i<count;i++){
                String resource="/maps/"+id+"/"+i+".bin";
                try(InputStream in=MapFrames.class.getResourceAsStream(resource)){
                    if(in==null)throw new IOException("Missing bundled frame "+resource);
                    data[i]=in.readAllBytes();
                    if(data[i].length!=128*128)
                        throw new IOException("Bad map pixel frame: "+resource);
                }catch(IOException error){
                    throw new IllegalStateException("Unable to read map animation "+resource,error);
                }
            }
            frames.put(id,data);
        }
    }
    byte[] frame(SkinCatalog.Skin style,int index){
        byte[][] entries=frames.get(style.id());
        if(entries==null)throw new IllegalArgumentException("Unknown map style: "+style.id());
        return entries[Math.max(0,Math.min(entries.length-1,index))];
    }
    /** Stable across sessions and connections, collision-resistant vs normal map IDs.
     * The same ID may be used by different viewers, who have distinct client caches.
     */
    int mapId(SkinCatalog.Skin style,int index,SkinCatalog catalog){
        int order=catalog.ids().indexOf(style.id());
        if(order<0)throw new IllegalArgumentException(style.id());
        return 1_700_000_000+order*64+index;
    }
}
