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
}
