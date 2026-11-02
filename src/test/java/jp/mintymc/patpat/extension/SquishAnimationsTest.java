package jp.mintymc.patpat.extension;

import com.google.gson.JsonParser;
import org.geysermc.geyser.entity.BedrockEntityDefinition;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.api.util.Identifier;
import static org.mockito.Mockito.*;
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
        assertEquals(0.24,root.get("animation_length").getAsDouble(),1e-9);
        var scale=root.getAsJsonObject("bones").getAsJsonObject("root").getAsJsonArray("scale");
        assertTrue(scale.get(1).getAsString().contains("query.anim_time"));
    }
    @Test void createsOneShotPacketForTheTargetRuntimeId() {
        Entity target=mock(Entity.class);
        BedrockEntityDefinition definition=mock(BedrockEntityDefinition.class);
        when(target.bedrockDefinition()).thenReturn(definition);
        Identifier identifier=mock(Identifier.class);
        when(identifier.toString()).thenReturn("minecraft:wolf");
        when(definition.identifier()).thenReturn(identifier);
        when(target.geyserId()).thenReturn(123L);
        var packet=SquishAnimations.packet(target);
        assertEquals(SquishAnimations.PREFIX+"body",packet.getAnimation());
        assertEquals("__runtime_controller",packet.getController());
        assertEquals("query.any_animation_finished",packet.getStopExpression());
        assertEquals(1,packet.getRuntimeEntityIds().size());
        assertEquals(123L,packet.getRuntimeEntityIds().get(0));
    }

}
