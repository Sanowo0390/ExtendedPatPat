package jp.mintymc.patpat.extension;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HandPacketTest {
    @Test void hidesModelWithoutHidingNameAndHasNoCollisionBox() {
        var packet=HandRenderer.spawnPacket(123,Vector3f.from(1,2,3),"\uE2A0");
        assertEquals(123,packet.getRuntimeEntityId());assertEquals(123,packet.getUniqueEntityId());
        assertEquals("minecraft:armor_stand",packet.getIdentifier());
        assertEquals("\uE2A0",packet.getMetadata().get(EntityDataTypes.NAME));
        assertEquals(0f,packet.getMetadata().get(EntityDataTypes.SCALE));
        assertEquals(0f,packet.getMetadata().get(EntityDataTypes.WIDTH));
        assertEquals(0f,packet.getMetadata().get(EntityDataTypes.HEIGHT));
        assertFalse(packet.getMetadata().getFlag(EntityFlag.INVISIBLE));
        assertTrue(packet.getMetadata().getFlag(EntityFlag.ALWAYS_SHOW_NAME));
    }
}
