package jp.mintymc.patpat.velocity;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import java.util.List;

/**
 * Minecraft 26.3 item-frame metadata layout:
 *
 * <ul>
 *   <li>0 = Entity flags (BYTE; invisibility = 0x20)</li>
 *   <li>8 = HangingEntity direction (DIRECTION; NEVER an item)</li>
 *   <li>9 = ItemFrame displayed item (ITEMSTACK)</li>
 *   <li>10 = ItemFrame item rotation (INT)</li>
 * </ul>
 *
 * Keep all ItemFrame item metadata packet creation here so the
 * base-class direction field cannot accidentally be overwritten.
 */
final class ItemFrameMetadata {
    static final int ENTITY_FLAGS=0;
    static final int HANGING_DIRECTION=8;
    static final int DISPLAYED_ITEM=9;
    static final int ITEM_ROTATION=10;

    private ItemFrameMetadata() {}

    static EntityData<ItemStack> mapItem(ItemStack item) {
        return new EntityData<>(DISPLAYED_ITEM,EntityDataTypes.ITEMSTACK,item);
    }

    static List<EntityData<?>> onSpawn(ItemStack item) {
        return List.of(
            new EntityData<>(ENTITY_FLAGS,EntityDataTypes.BYTE,(byte)0x20),
            mapItem(item)
        );
    }
}
