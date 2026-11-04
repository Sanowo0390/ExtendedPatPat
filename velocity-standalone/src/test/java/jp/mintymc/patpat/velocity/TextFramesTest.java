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

    @Test void pixelArtUsesUniformFontWithoutBrailleArtifacts(){
        BufferedImage image=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(1,1,0xFFFF0000);
        image.setRGB(2,1,0xFF00FF00);
        Component art=TextFrames.render(image,8);
        assertTrue(art instanceof TextComponent);
        String content=art.children().toString();
        assertTrue(content.contains("█"),content);
        assertFalse(content.contains("⠀"),"Braille pseudo-blank must never leak into rendered text");
        assertTrue(content.contains("  "),"Transparent pixels must use invisible ASCII spaces");
        assertTrue(content.contains("bold=set_by_true"),"Opaque cells should cover horizontal gaps");
        assertTrue(content.contains("underlined=set_by_true"),"Opaque cells should cover row gaps");
        assertTrue(content.contains("#FF0000"),content);
        assertTrue(content.contains("#00FF00"),content);
    }

    @Test void transparentRunUsesOnlyAsciiSpacesAndPredictableAdvance() {
        assertEquals("",TextFrames.transparentRun(0));
        for(int length=1;length<=32;length++){
            String blank=TextFrames.transparentRun(length);
            assertTrue(blank.chars().allMatch(c->c==' '));
            assertTrue(Math.abs(blank.length()*4-length*10)<=2);
        }
        assertThrows(IllegalArgumentException.class,()->TextFrames.transparentRun(-1));
        assertThrows(IllegalArgumentException.class,()->TextFrames.transparentRun(33));
    }

    @Test void blankFrameContainsNoVisiblePlaceholderCharacters() {
        BufferedImage blank=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);
        Component result=TextFrames.render(blank,8);
        String content=result.children().toString();
        assertFalse(content.contains("█"));
        assertFalse(content.contains("⠀"));
        assertFalse(content.contains("TextColorImpl"));
    }

    @Test void centerBillboardMetadataDoesNotRegress() throws Exception {
        // PacketEvents metadata registries need the running PacketEvents API.
        // Assert source-level constants here; full packet serialization is
        // exercised on a real Velocity server, not in a bare JUnit JVM.
        String source=java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/jp/mintymc/patpat/velocity/TextFrames.java"));
        assertTrue(source.contains("EntityDataTypes.BYTE,(byte)3"),"Billboard must be CENTER");
        assertTrue(source.contains("EntityDataTypes.INT,0"),"Background must be transparent");
        assertTrue(source.contains("EntityDataTypes.ADV_COMPONENT"),"Text must be an Adventure Component");
        Vector3d at=TextFrames.position(new Vector3d(3,12,5),0.6,-0.28);
        assertEquals(11.42,at.getY(),1e-6);
        assertEquals(3,at.getX(),1e-6);
    }

    @Test void bundledDefaultFramesLoad(){
        SkinCatalog catalog=SkinCatalog.load();
        TextFrames art=new TextFrames(catalog,16);
        assertEquals(16,art.resolution());
        assertNotEquals(art.frame(catalog.get("default"),0),
                        art.frame(catalog.get("default"),2));
    }
}
