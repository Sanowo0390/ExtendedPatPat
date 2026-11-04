package jp.mintymc.patpat.velocity;

import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemFrameMetadataTest {
    private static ItemStack testMap() {
        return ItemStack.builder().type(ItemTypes.FILLED_MAP).amount(1).build();
    }

    @Test void spawnMustPlaceMapInField9NotHangingEntityDirectionField8() {
        ItemStack item=testMap();
        var metadata=ItemFrameMetadata.onSpawn(item);
        assertEquals(2,metadata.size());
        assertEquals(0,metadata.get(0).getIndex());
        assertSame(EntityDataTypes.BYTE,metadata.get(0).getType());
        assertEquals((byte)0x20,metadata.get(0).getValue());
        assertEquals(9,metadata.get(1).getIndex());
        assertSame(EntityDataTypes.ITEMSTACK,metadata.get(1).getType());
        assertSame(item,metadata.get(1).getValue());
        assertTrue(metadata.stream().noneMatch(entry->entry.getIndex()==8),
            "Field 8 is HangingEntity direction; using it for ItemStack disconnects Minecraft 26.3");
    }

    @Test void animationUpdateUsesTheSameItemField() {
        ItemStack item=testMap();
        var update=ItemFrameMetadata.mapItem(item);
        assertEquals(ItemFrameMetadata.DISPLAYED_ITEM,update.getIndex());
        assertEquals(9,update.getIndex());
        assertNotEquals(ItemFrameMetadata.HANGING_DIRECTION,update.getIndex());
        assertSame(EntityDataTypes.ITEMSTACK,update.getType());
        assertSame(item,update.getValue());
    }
}
