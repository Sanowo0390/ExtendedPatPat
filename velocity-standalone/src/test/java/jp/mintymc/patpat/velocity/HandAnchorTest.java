package jp.mintymc.patpat.velocity;

import com.github.retrooper.packetevents.util.Vector3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandAnchorTest {
    private static Vector3d xyz(double x,double y,double z) {return new Vector3d(x,y,z);}

    @Test void viewSpaceOffsetMatchesGeyserHandRendererAnchor() {
        var feet=xyz(0,64,0);
        var viewer=xyz(0,64,5);
        var anchored=HandAnchor.anchor(feet,1.8,viewer,0.5,0,0,true,0);
        assertEquals(0,anchored.getX(),1e-7);
        assertEquals(65.8,anchored.getY(),1e-7);
        assertEquals(0.5,anchored.getZ(),1e-7);
    }

    @Test void worldSpaceOffsetKeepsAbsoluteAxes() {
        var anchored=HandAnchor.anchor(xyz(1,60,2),0.5,xyz(10,60,10),
            0.5,0.0,-0.2,false,0);
        assertEquals(1.5,anchored.getX(),1e-7);
        assertEquals(60.5,anchored.getY(),1e-7);
        assertEquals(1.8,anchored.getZ(),1e-7);
    }

    @Test void phantomInBoatAnchorsToVehicleRatherThanStaleSpawnPosition() {
        assertEquals(0.5,HandAnchor.height("minecraft:phantom"),1e-7);
        var feet=HandAnchor.feet(xyz(100,70,100),xyz(5,64,-3),"minecraft:oak_boat");
        var anchored=HandAnchor.anchor(feet,HandAnchor.height("phantom"),xyz(6,64,-3),
            0.5,0,0,true,0);
        assertEquals(5.5,anchored.getX(),1e-7);
        assertEquals(64.75,anchored.getY(),1e-7);
        assertEquals(-3,anchored.getZ(),1e-7);
    }

    @Test void sittingOffsetAppliesOnlyWhenPassedInByCaller() {
        var head=HandAnchor.anchor(xyz(1,70,1),1.8,xyz(3,70,1),
            .5,0,0,true,.2);
        assertEquals(72.0,head.getY(),1e-7);
        assertEquals(1.8,HandAnchor.height("minecraft:player"),1e-7);
    }
}
