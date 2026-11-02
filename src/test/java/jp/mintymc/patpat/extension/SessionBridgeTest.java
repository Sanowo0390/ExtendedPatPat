package jp.mintymc.patpat.extension;

import net.kyori.adventure.key.Key;
import org.cloudburstmc.math.vector.*;
import org.geysermc.geyser.entity.type.LivingEntity;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import org.geysermc.geyser.session.*;
import org.geysermc.geyser.session.cache.EntityCache;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.event.session.PacketSendingEvent;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.*;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SessionBridgeTest {
    GeyserSession session;ClientSession network;PatSession bridge;EntityCache cache;
    @BeforeEach void setup() {
        PatPatExtension owner=mock(PatPatExtension.class);
        Properties props=new Properties();props.setProperty("local-preview","false");
        // Item registries need a real running Geyser. These session tests focus on the
        // protocol bridge; actual empty-hand behavior is an in-game verification item.
        props.setProperty("require-empty-hand","false");
        when(owner.settings()).thenReturn(Settings.from(props));
        session=mock(GeyserSession.class);network=mock(ClientSession.class);cache=mock(EntityCache.class);
        var downstream=mock(DownstreamSession.class);
        when(session.getDownstream()).thenReturn(downstream);when(downstream.getSession()).thenReturn(network);
        when(downstream.isState(ProtocolState.GAME)).thenReturn(true);when(session.getEntityCache()).thenReturn(cache);
        doAnswer(call->{((Runnable)call.getArgument(0)).run();return null;}).when(session).executeInEventLoop(any());
        bridge=new PatSession(owner,session);
    }
    void hello() {bridge.packetReceived(network,new ClientboundCustomPayloadPacket(Key.key(PatProtocol.HELLO_S2C),new byte[]{1,2,5}));}
    ServerboundInteractPacket interact(){return new ServerboundInteractPacket(300,Hand.MAIN_HAND,Vector3d.ZERO,true);}
    @Test void waitsForBackendHelloAndRespondsWithClientVersion() {
        assertFalse(bridge.ready());hello();assertTrue(bridge.ready());
        ArgumentCaptor<Packet> captor=ArgumentCaptor.forClass(Packet.class);verify(session).sendDownstreamGamePacket(captor.capture());
        var payload=(ServerboundCustomPayloadPacket)captor.getValue();
        assertEquals(PatProtocol.HELLO_C2S,payload.getChannel().asString());assertArrayEquals(new byte[]{1,2,0},payload.getData());
    }
    @Test void doesNotChangeVanillaInteractionWithoutBackendHello() {
        PacketSendingEvent event=new PacketSendingEvent(network,interact());bridge.packetSending(event);assertFalse(event.isCancelled());
        verify(session,never()).sendDownstreamGamePacket(any());
    }
    @Test void invalidTargetIsResentOnceWithoutRecursion() {
        hello();clearInvocations(session);
        var original=interact();PacketSendingEvent event=new PacketSendingEvent(network,original);bridge.packetSending(event);
        assertTrue(event.isCancelled());verify(session).sendDownstreamGamePacket(same(original));
        PacketSendingEvent resent=new PacketSendingEvent(network,original);bridge.packetSending(resent);assertFalse(resent.isCancelled());
    }
    @Test void offHandAndStandingInteractionsAreUntouched() {
        hello();clearInvocations(session);
        var off=new PacketSendingEvent(network,new ServerboundInteractPacket(300,Hand.OFF_HAND,Vector3d.ZERO,true));
        var standing=new PacketSendingEvent(network,new ServerboundInteractPacket(300,Hand.MAIN_HAND,Vector3d.ZERO,false));
        bridge.packetSending(off);bridge.packetSending(standing);
        assertFalse(off.isCancelled());assertFalse(standing.isCancelled());
        verify(session,never()).sendDownstreamGamePacket(any());
    }
    @Test void acceptedGestureSendsV2AndDeduplicatesRepeatedInteraction() {
        hello();clearInvocations(session);
        var mob=mock(LivingEntity.class);when(cache.getEntityByJavaId(300)).thenReturn(mob);
        when(mob.isValid()).thenReturn(true);when(mob.javaId()).thenReturn(300);
        when(mob.position()).thenReturn(Vector3f.from(0,0,1));when(mob.getBoundingBoxWidth()).thenReturn(1f);when(mob.getBoundingBoxHeight()).thenReturn(1f);
        var player=mock(SessionPlayerEntity.class);when(session.getPlayerEntity()).thenReturn(player);
        when(player.javaId()).thenReturn(1);when(player.getHealth()).thenReturn(20f);when(player.position()).thenReturn(Vector3f.ZERO);
        // Simulate a normal Velocity player with no extra permission nodes.
        when(session.getGameMode()).thenReturn(GameMode.SURVIVAL);
        var a=new PacketSendingEvent(network,interact());var b=new PacketSendingEvent(network,interact());bridge.packetSending(a);bridge.packetSending(b);
        assertTrue(a.isCancelled());assertTrue(b.isCancelled());
        ArgumentCaptor<Packet> captor=ArgumentCaptor.forClass(Packet.class);verify(session,times(1)).sendDownstreamGamePacket(captor.capture());
        var payload=(ServerboundCustomPayloadPacket)captor.getValue();assertEquals(PatProtocol.PAT_C2S,payload.getChannel().asString());assertArrayEquals(new byte[]{(byte)0xAC,2},payload.getData());
    }
}
