package jp.mintymc.patpat.velocity;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 128x128 viewer-local map art. Unlike Geyser's billboard, an ItemFrame
 * cannot be scaled as an entity. Scale its map pixels in memory instead;
 * image-scale=0.5 means half the 1-block map width (the Geyser default).
 *
 * The hand's world-space travel is converted to pixels independently of image size.
 */
final class MapFrames {
    private static final int MAP_SIDE=128;
    private static final int MAP_SIZE=MAP_SIDE*MAP_SIDE;
    private static final int IDS_PER_SKIN=256; // 48 frames * 5 poses <= 240
    private final Map<String,byte[][][]> frames=new HashMap<>();
    private final Map<String,Integer> mapBases=new HashMap<>();

    MapFrames(SkinCatalog catalog) {this(catalog,0.6,0.28);}
    MapFrames(SkinCatalog catalog,double scale) {this(catalog,scale,0.28);}
    MapFrames(SkinCatalog catalog,double scale,double depth) {
        int styleIndex=0;
        for(String id:catalog.ids()){
            mapBases.put(id,1_700_000_000+styleIndex++*IDS_PER_SKIN);
            int count=catalog.get(id).frames();
            byte[][][] art=new byte[count][MapMotion.POSES][];
            for(int sprite=0;sprite<count;sprite++){
                String resource="/maps/"+id+"/"+sprite+".bin";
                try(InputStream in=MapFrames.class.getResourceAsStream(resource)){
                    if(in==null)throw new IOException("Missing map source "+resource);
                    byte[] base=in.readAllBytes();
                    if(base.length!=MAP_SIZE)throw new IOException("Invalid map length "+resource);
                    for(int pose=0;pose<MapMotion.POSES;pose++)
                        art[sprite][pose]=scaleAndMove(base,scale,pose,depth);
                }catch(IOException error){
                    throw new IllegalStateException("Unable to load animated map "+resource,error);
                }
            }
            frames.put(id,art);
        }
    }

    /** Rebuild from the original sprite, so configured image-scale isn't multiplied
     * on every reload. Nearest-neighbor keeps vanilla map palette indices intact.
     */
    static byte[] scaleAndMove(byte[] original,double configuredScale,int pose) {
        return scaleAndMove(original,configuredScale,pose,0.28);
    }

    static byte[] scaleAndMove(byte[] original,double configuredScale,int pose,double configuredDepth) {
        if(original.length!=MAP_SIZE)throw new IllegalArgumentException("Invalid map palette");
        if(pose<0 || pose>=MapMotion.POSES)throw new IllegalArgumentException("Invalid pose");
        double safe=Double.isFinite(configuredScale)?Math.clamp(configuredScale,0.05,1.0):0.6;
        double depth=Double.isFinite(configuredDepth)?Math.clamp(configuredDepth,0,2):0.28;
        int size=Math.max(1,(int)Math.round(MAP_SIDE*safe));
        // One map pixel = 1/128 block. Keep the full stroke inside the map.
        int travel=Math.min((int)Math.round(MAP_SIDE*depth),
            Math.max(0,MAP_SIDE-size-4));
        // Center the entire downward stroke in the fixed 1-block ItemFrame.
        int left=(MAP_SIDE-size)/2;
        int top=(MAP_SIDE-size-travel)/2+
                (int)Math.round((double)travel*pose/(MapMotion.POSES-1));
        byte[] out=new byte[MAP_SIZE];
        for(int y=0;y<size;y++){
            int sourceY=Math.min(MAP_SIDE-1,(int)((y+0.5)*MAP_SIDE/size));
            for(int x=0;x<size;x++){
                int sourceX=Math.min(MAP_SIDE-1,(int)((x+0.5)*MAP_SIDE/size));
                out[(top+y)*MAP_SIDE+left+x]=original[sourceY*MAP_SIDE+sourceX];
            }
        }
        return out;
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
        if(!frames.containsKey(style.id()) || sprite<0 || sprite>=style.frames() ||
                pose<0 || pose>=MapMotion.POSES)
            throw new IllegalArgumentException("Unknown map sprite/pose: "+style.id()+
                " "+sprite+"/"+pose);
    }
}
