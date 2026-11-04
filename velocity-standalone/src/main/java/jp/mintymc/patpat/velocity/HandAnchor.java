package jp.mintymc.patpat.velocity;

import com.github.retrooper.packetevents.util.Vector3d;
import java.util.Locale;
import java.util.Map;

/**
 * Java-side counterpart to Geyser HandRenderer.anchor.
 * Velocity does not have Geyser's live bounding boxes, so known vanilla
 * entity heights are approximated here rather than all falling back to 1.6.
 */
final class HandAnchor {
    private static final Map<String,Double> HEIGHTS=Map.ofEntries(
        Map.entry("player",1.8),Map.entry("phantom",0.5),
        Map.entry("bat",0.9),Map.entry("bee",0.6),Map.entry("allay",0.6),
        Map.entry("chicken",0.7),Map.entry("rabbit",0.5),
        Map.entry("cat",0.7),Map.entry("ocelot",0.7),
        Map.entry("fox",0.7),Map.entry("wolf",0.85),
        Map.entry("pig",0.9),Map.entry("cow",1.4),
        Map.entry("sheep",1.3),Map.entry("horse",1.6),
        Map.entry("donkey",1.5),Map.entry("mule",1.6),
        Map.entry("creeper",1.7),Map.entry("zombie",1.95),
        Map.entry("skeleton",1.99),Map.entry("spider",0.9),
        Map.entry("cave_spider",0.5),Map.entry("iron_golem",2.7),
        Map.entry("snow_golem",1.9),Map.entry("wither",3.5),
        Map.entry("enderman",2.9),Map.entry("villager",1.95),
        Map.entry("slime",0.52),Map.entry("magma_cube",0.52),
        Map.entry("squid",0.8),Map.entry("dolphin",0.6),
        Map.entry("axolotl",0.42),Map.entry("turtle",0.4),
        Map.entry("frog",0.5),Map.entry("panda",1.25),
        Map.entry("goat",1.3),Map.entry("camel",2.375),
        Map.entry("ravager",2.2),Map.entry("ghast",4.0),
        Map.entry("blaze",1.8),Map.entry("hoglin",1.4),
        Map.entry("zoglin",1.4)
    );
    private HandAnchor() {}

    static double height(String type) {
        String key=type.toLowerCase(Locale.ROOT);
        key=key.substring(key.lastIndexOf(':')+1);
        return HEIGHTS.getOrDefault(key,1.6);
    }

    /**
     * Mounted passengers may not receive position packets at all; they move with
     * the vehicle on the client. Rebase on the vehicle before anchoring.
     * The seat offset is a best-effort approximation, not server hitbox data.
     */
    static Vector3d feet(Vector3d entityPos,Vector3d vehiclePos,String vehicleType) {
        if(vehiclePos==null)return entityPos;
        String t=vehicleType.toLowerCase(Locale.ROOT);
        double seat=t.contains("boat")||t.contains("raft")?0.25:
            t.contains("minecart")?0.25:0.5;
        return new Vector3d(vehiclePos.getX(),vehiclePos.getY()+seat,vehiclePos.getZ());
    }

    static Vector3d anchor(Vector3d feet,double height,Vector3d viewer,
                           double offsetX,double offsetY,double offsetZ,
                           boolean viewRelative,double sittingOffsetY) {
        double x=offsetX,z=offsetZ;
        if(viewRelative && viewer!=null){
            double tx=viewer.getX()-feet.getX();
            double tz=viewer.getZ()-feet.getZ();
            double len=Math.hypot(tx,tz);
            if(len>1e-6){tx/=len;tz/=len;}
            else {tx=0;tz=-1;}
            double dx=tx*x+tz*z,dz=tz*x-tx*z;
            x=dx;z=dz;
        }
        return new Vector3d(feet.getX()+x,feet.getY()+height+offsetY+sittingOffsetY,
            feet.getZ()+z);
    }
}
