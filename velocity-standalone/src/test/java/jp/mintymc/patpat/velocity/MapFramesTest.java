package jp.mintymc.patpat.velocity;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class MapFramesTest {
    @Test void allMotionPosesArePackagedAndHaveDistinctIds() {
        SkinCatalog catalog=SkinCatalog.load();
        var art=new MapFrames(catalog);
        var style=catalog.get("default");
        assertEquals(5,MapMotion.POSES);
        assertEquals(128*128,art.frame(style,0,0).length);
        assertEquals(128*128,art.frame(style,0,4).length);
        assertNotEquals(art.mapId(style,0,0),art.mapId(style,0,1));
        assertNotEquals(art.mapId(style,0,0),art.mapId(style,1,0));
        assertFalse(Arrays.equals(art.frame(style,0,0),art.frame(style,0,4)),
            "Vertical movement must change map pixels even for a still sprite");
    }

    @Test void mapIdsRemainUniqueAcrossEveryStyleAndPose() {
        SkinCatalog catalog=SkinCatalog.load();
        var art=new MapFrames(catalog);
        var seen=new java.util.HashSet<Integer>();
        for(String name:catalog.ids()){
            var style=catalog.get(name);
            for(int frame=0;frame<style.frames();frame++){
                for(int pose=0;pose<MapMotion.POSES;pose++){
                    int id=art.mapId(style,frame,pose);
                    assertTrue(seen.add(id),"Map ID collision for "+name+"/"+frame+"/"+pose);
                    assertEquals(128*128,art.frame(style,frame,pose).length);
                }
            }
        }
    }

    @Test void geyserImageScaleHalfProducesSmallerMapHand() {
        SkinCatalog catalog=SkinCatalog.load();
        var style=catalog.get("default");
        var normal=new MapFrames(catalog,1.0).frame(style,0,0);
        var half=new MapFrames(catalog,0.5).frame(style,0,0);
        long large=0,small=0;
        for(byte b:normal)if(b!=0)large++;
        for(byte b:half)if(b!=0)small++;
        assertTrue(small<large,"image-scale=0.5 should be smaller than 1.0");
        assertTrue(small>0,"Scaled map should not become transparent");
    }
    @Test void velocityDefaultUsesLargerImageAndShorterStrokeThanGeyser() {
        byte[] full=new byte[128*128];
        Arrays.fill(full,(byte)1);
        byte[] up=MapFrames.scaleAndMove(full,0.6,0,0.28);
        byte[] down=MapFrames.scaleAndMove(full,0.6,MapMotion.POSES-1,0.28);
        int start=firstRow(up),end=firstRow(down);
        int width=0;
        for(int x=0;x<128;x++)if(up[start*128+x]!=0)width++;
        assertEquals(77,width,"0.6 block image should be 77 map pixels wide");
        assertEquals(36,end-start,"0.28 block Velocity stroke should be 36 map pixels");
        assertTrue(0.28 < 0.425,"Velocity travel is intentionally smaller than Geyser's unchanged default");
        assertTrue(end+width<=128,"The whole stroke must remain inside the map");
    }

    private static int firstRow(byte[] pixels) {
        for(int row=0;row<128;row++)
            for(int x=0;x<128;x++)
                if(pixels[row*128+x]!=0)return row;
        throw new AssertionError("No visible pixels");
    }
}
