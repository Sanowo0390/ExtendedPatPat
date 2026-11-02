package jp.mintymc.patpat.extension;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class SquishAnimationsTest {
    @Test void selectsPrimaryBonesForCommonMobTypes() {
        assertEquals("animation.patpatbedrock.squish.root",
                SquishAnimations.animationFor("minecraft:player"));
        assertEquals(SquishAnimations.PREFIX+"body",SquishAnimations.animationFor("minecraft:wolf"));
        assertEquals(SquishAnimations.PREFIX+"body",SquishAnimations.animationFor("minecraft:cat"));
        assertEquals(SquishAnimations.PREFIX+"root",SquishAnimations.animationFor("minecraft:pig"));
        assertEquals(SquishAnimations.PREFIX+"waist",SquishAnimations.animationFor("minecraft:skeleton"));
        assertEquals(SquishAnimations.PREFIX+"generic",SquishAnimations.animationFor("example:custom_mob"));
    }

    @Test void squishIsEnabledByDefaultButConfigurable() {
        var props=new Properties();
        assertTrue(Settings.from(props).squishEnabled());
        props.setProperty("squish-enabled","false");
        assertFalse(Settings.from(props).squishEnabled());
    }

    @Test void animationEndsAtRestWithoutLooping() {
        var json=JsonParser.parseString(new com.google.gson.Gson().toJson(SquishAnimations.resource())).getAsJsonObject();
        var animations=json.getAsJsonObject("animations");
        var root=animations.getAsJsonObject(SquishAnimations.PREFIX+"root");
        assertFalse(root.get("loop").getAsBoolean());
        assertEquals(0.3,root.get("animation_length").getAsDouble(),1e-9);
        var scale=root.getAsJsonObject("bones").getAsJsonObject("root").getAsJsonArray("scale");
        assertTrue(scale.get(1).getAsString().contains("query.anim_time"));
    }
}
