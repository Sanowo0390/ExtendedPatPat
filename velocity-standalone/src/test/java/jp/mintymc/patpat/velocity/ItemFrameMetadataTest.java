package jp.mintymc.patpat.velocity;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Source/constant contract tests avoid initializing PacketEvents' live-server
 * serializers in a standalone Maven test process.
 */
class ItemFrameMetadataTest {
    @Test void minecraft263ItemFrameFieldsMustNotConflict() {
        assertEquals(0,ItemFrameMetadata.ENTITY_FLAGS);
        assertEquals(8,ItemFrameMetadata.HANGING_DIRECTION);
        assertEquals(9,ItemFrameMetadata.DISPLAYED_ITEM);
        assertEquals(10,ItemFrameMetadata.ITEM_ROTATION);
        assertNotEquals(ItemFrameMetadata.HANGING_DIRECTION,ItemFrameMetadata.DISPLAYED_ITEM);
    }

    @Test void bothSpawnAndAnimationUseCentralizedItemField() throws Exception {
        String base="src/main/java/jp/mintymc/patpat/velocity/";
        String renderer=Files.readString(Path.of(base+"PatPatVelocity.java"));
        String metadata=Files.readString(Path.of(base+"ItemFrameMetadata.java"));
        assertTrue(renderer.contains("ItemFrameMetadata.onSpawn("),
            "Initial spawn must use the tested map-item metadata factory");
        assertTrue(renderer.contains("ItemFrameMetadata.mapItem("),
            "Animated frame updates must use the same metadata factory");
        assertTrue(metadata.contains("new EntityData<>(DISPLAYED_ITEM,EntityDataTypes.ITEMSTACK,item)"),
            "Map ItemStack must be encoded using DISPLAYED_ITEM=9");
        assertFalse(renderer.contains("new EntityData<>(8,EntityDataTypes.ITEMSTACK"),
            "Index 8 is HangingEntity direction, not ItemStack: disconnects Minecraft 26.3");
    }
}
