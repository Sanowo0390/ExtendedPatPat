package jp.mintymc.patpat.velocity;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.util.Vector3d;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class TextFramesTest {
    @Test void rendererSelectionAndFallback(){
        assertFalse(TextFrames.useText("item_frame",ClientVersion.V_26_3));
        assertTrue(TextFrames.useText("text_display",ClientVersion.V_26_3));
        assertTrue(TextFrames.useText("TEXT_DISPLAY",ClientVersion.V_1_19_4));
        assertFalse(TextFrames.useText("text_display",ClientVersion.V_1_19_3));
    }

    @Test void pixelArtUsesUniformFontAndTransparentCell(){
        BufferedImage image=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(1,1,0xFFFF0000);
        image.setRGB(2,1,0xFF00FF00);
        Component art=TextFrames.render(image,8);
        assertTrue(art instanceof TextComponent);
        String content=art.children().toString();
        assertTrue(content.contains("█"),content);
        assertTrue(content.contains("⠀"),content);
        assertTrue(content.contains("ff0000"),content);
        assertTrue(content.contains("00ff00"),content);
    }

    @Test void centerBillboardHasTransparentBackground(){
        var metadata=TextFrames.spawnMetadata(Component.text("█"),0.6,24);
        Map<Integer,Object> values=metadata.stream().collect(Collectors.toMap(
            EntityData::getIndex,EntityData::getValue));
        assertEquals((byte)3,values.get(15));
        assertEquals(0,values.get(25));
        assertEquals((byte)0,values.get(27));
        assertEquals(1024,values.get(24));
        assertEquals(Component.text("█"),values.get(23));
        Vector3d at=TextFrames.position(new Vector3d(3,12,5),0.6,-0.28);
        assertEquals(11.42,at.getY(),1e-6);
    }

    @Test void bundledDefaultFramesLoad(){
        SkinCatalog catalog=SkinCatalog.load();
        TextFrames art=new TextFrames(catalog,16);
        assertEquals(16,art.resolution());
        assertNotEquals(art.frame(catalog.get("default"),0),
                        art.frame(catalog.get("default"),2));
    }
}
