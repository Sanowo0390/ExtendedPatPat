package jp.mintymc.patpat.extension;

import org.cloudburstmc.protocol.bedrock.packet.AnimateEntityPacket;
import org.geysermc.geyser.entity.type.Entity;
import java.util.*;

/**
 * Client-side squeeze animation for Bedrock viewers.
 *
 * All motion is generated here; no third-party animation assets are copied.
 * A model's bone names differ per mob, so select a conservative primary bone.
 */
final class SquishAnimations {
    static final String PREFIX="animation.patpatbedrock.squish.";
    private static final String ROOT="root",BODY="body",WAIST="waist",HEAD="head",CUBE="cube",BODY0="body0",GENERIC="generic";

    private static final Map<String,String> BONES=types();

    private SquishAnimations() {}

    private static Map<String,String> types() {
        Map<String,String> result=new HashMap<>();
        group(result,ROOT,"player allay axolotl camel camel_husk copper_golem cow creaking fox frog happy_ghast pig sheep sniffer warden snow_golem");
        group(result,BODY,"bat bee cat chicken cod creeper dolphin drowned enderman ghast glow_squid hoglin horse husk iron_golem magma_cube mooshroom ocelot panda parrot phantom piglin piglin_brute pillager polar_bear rabbit ravager salmon silverfish squid stray strider turtle vex villager villager_v2 vindicator wandering_trader witch wither_skeleton wolf zombie zombie_horse zombie_villager zombie_villager_v2");
        group(result,WAIST,"bogged skeleton");
        group(result,HEAD,"blaze elder_guardian guardian");
        group(result,CUBE,"slime");
        group(result,BODY0,"cave_spider spider");
        return Map.copyOf(result);
    }

    private static void group(Map<String,String> map,String bone,String names) {
        for(String name:names.split(" "))map.put("minecraft:"+name,bone);
    }

    static String animationFor(String bedrockIdentifier) {
        // Unknown custom mobs try generic root; models without that bone simply
        // ignore the animation rather than corrupting their entity state.
        return PREFIX+BONES.getOrDefault(bedrockIdentifier,GENERIC);
    }

    static AnimateEntityPacket packet(Entity entity) {
        AnimateEntityPacket result=new AnimateEntityPacket();
        result.setAnimation(animationFor(entity.bedrockDefinition().identifier().toString()));
        result.setNextState("default");
        result.setBlendOutTime(0.0f);
        result.setStopExpression("query.any_animation_finished");
        result.setController("__runtime_controller");
        result.getRuntimeEntityIds().add(entity.geyserId());
        return result;
    }

    static Map<String,Object> resource() {
        Map<String,Object> animations=new TreeMap<>();
        for(String bone:List.of(ROOT,BODY,WAIST,HEAD,CUBE,BODY0,GENERIC)) {
            Map<String,Object> bones;
            if(bone.equals(GENERIC)) {
                // Generic fallback: try two common root bone conventions.
                bones=Map.of(ROOT,squeeze(), "bb_main",squeeze());
            }else bones=Map.of(bone,squeeze());
            animations.put(PREFIX+bone,Map.of("animation_length",0.3,"loop",false,"bones",bones));
        }
        return Map.of("format_version","1.8.0","animations",animations);
    }

    private static Map<String,Object> squeeze() {
        // The bend rises and returns to zero at 0.3 seconds. Values are
        // intentionally mild to avoid clipping mounted/complex mob models.
        String wave="math.sin(math.min(query.anim_time / 0.3, 1.0) * 180.0)";
        return Map.of("scale",List.of(
                "1.0 + 0.04 * "+wave,
                "1.0 - 0.26 * "+wave,
                "1.0 + 0.04 * "+wave));
    }
}
