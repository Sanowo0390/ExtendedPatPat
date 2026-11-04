package jp.mintymc.patpat.velocity;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Immutable, pre-quantized 128x128 maps for sprite frames and vertical poses. */
final class MapFrames {
    private static final int MAP_SIZE=128*128;
    private static final int IDS_PER_SKIN=256; // Up to 48 sprite frames * 5 poses = 240.
    private final Map<String,byte[][][]> frames=new HashMap<>();
    private final Map<String,Integer> mapBases=new HashMap<>();

    MapFrames(SkinCatalog catalog) {
        int styleIndex=0;
        for(String id:catalog.ids()){
            mapBases.put(id,1_700_000_000+styleIndex++*IDS_PER_SKIN);
            int count=catalog.get(id).frames();
            byte[][][] art=new byte[count][MapMotion.POSES][];
            for(int sprite=0;sprite<count;sprite++){
                for(int pose=0;pose<MapMotion.POSES;pose++){
                    String resource="/maps/"+id+"/"+sprite+"_"+pose+".bin";
                    try(InputStream in=MapFrames.class.getResourceAsStream(resource)){
                        if(in==null)throw new IOException("Missing map animation "+resource);
                        art[sprite][pose]=in.readAllBytes();
                        if(art[sprite][pose].length!=MAP_SIZE)
                            throw new IOException("Invalid map length "+resource);
                    }catch(IOException error){
                        throw new IllegalStateException("Unable to load animated map "+resource,error);
                    }
                }
            }
            frames.put(id,art);
        }
    }

    byte[] frame(SkinCatalog.Skin style,int sprite,int pose){
        check(style,sprite,pose);
        return frames.get(style.id())[sprite][pose];
    }

    int mapId(SkinCatalog.Skin style,int sprite,int pose){
        check(style,sprite,pose);
        return mapBases.get(style.id())+sprite*MapMotion.POSES+pose;
    }

    private void check(SkinCatalog.Skin style,int sprite,int pose){
        if(!frames.containsKey(style.id()) ||
                sprite<0 || sprite>=style.frames() ||
                pose<0 || pose>=MapMotion.POSES)
            throw new IllegalArgumentException("Unknown map sprite/pose: "+style.id()+
                " "+sprite+"/"+pose);
    }
}
