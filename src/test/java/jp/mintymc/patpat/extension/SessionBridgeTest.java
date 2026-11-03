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
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SessionBridgeTest {
    PatPatExtension owner;GeyserSession session;ClientSession network;PatSession bridge;EntityCache cache;
    @BeforeEach void setup() {
        owner=mock(PatPatExtension.class);
        Properties props=new Properties();props.setProperty("local-preview","false");
        // Item registries need a real running Geyser. These session tests focus on the
        // protocol bridge; actual empty-hand behavior is an in-game verification item.
        props.setProperty("require-empty-hand","false");
        when(owner.settings()).thenReturn(Settings.from(props));
        session=mock(GeyserSession.class);network=mock(ClientSession.class);cache=mock(EntityCache.class);
        when(owner.patEnabled(session)).thenReturn(true);
        when(owner.cameraSupported()).thenReturn(true);
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
    @Test void rejectsInvalidStandaloneInteractionAndResendsVanillaPacket() {
        var packet=interact();
        PacketSendingEvent event=new PacketSendingEvent(network,packet);bridge.packetSending(event);
        assertTrue(event.isCancelled());
        verify(session).sendDownstreamGamePacket(same(packet));
        verify(session,never()).sendUpstreamPacket(any());
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
        when(mob.position()).thenReturn(Vector3f.from(0,0,1));when(mob.getBoundingBoxWidth()).thenReturn(1f);when(mob.getBoundingBoxHeight()).thenReturn(2f);
        var player=mock(SessionPlayerEntity.class);when(session.getPlayerEntity()).thenReturn(player);
        when(player.javaId()).thenReturn(1);when(player.getHealth()).thenReturn(20f);when(player.position()).thenReturn(Vector3f.ZERO);
        // Simulate a normal Velocity player with no extra permission nodes.
        when(session.getGameMode()).thenReturn(GameMode.SURVIVAL);
        var a=new PacketSendingEvent(network,interact());var b=new PacketSendingEvent(network,interact());bridge.packetSending(a);bridge.packetSending(b);
        assertTrue(a.isCancelled());assertTrue(b.isCancelled());
        ArgumentCaptor<Packet> captor=ArgumentCaptor.forClass(Packet.class);verify(session,times(1)).sendDownstreamGamePacket(captor.capture());
        var payload=(ServerboundCustomPayloadPacket)captor.getValue();assertEquals(PatProtocol.PAT_C2S,payload.getChannel().asString());assertArrayEquals(new byte[]{(byte)0xAC,2},payload.getData());
    }
    private void setupAimedTarget() {
        var mob=mock(LivingEntity.class);when(cache.getEntityByJavaId(300)).thenReturn(mob);
        when(mob.isValid()).thenReturn(true);when(mob.javaId()).thenReturn(300);
        when(mob.position()).thenReturn(Vector3f.from(0,0,1));
        when(mob.getBoundingBoxWidth()).thenReturn(1f);when(mob.getBoundingBoxHeight()).thenReturn(2f);
        var player=mock(SessionPlayerEntity.class);when(session.getPlayerEntity()).thenReturn(player);
        when(player.javaId()).thenReturn(1);when(player.getHealth()).thenReturn(20f);when(player.position()).thenReturn(Vector3f.ZERO);
        when(session.getGameMode()).thenReturn(GameMode.SURVIVAL);
    }
    @Test void offTargetEntityUseIsNotConvertedIntoPat() {
        hello();clearInvocations(session);setupAimedTarget();
        when(session.getPlayerEntity().getYaw()).thenReturn(90f);
        var packet=interact();var event=new PacketSendingEvent(network,packet);
        bridge.packetSending(event);
        assertTrue(event.isCancelled());
        verify(session).sendDownstreamGamePacket(same(packet)); // vanilla action still proceeds
        verify(session,never()).sendDownstreamGamePacket(argThat(p -> p instanceof ServerboundCustomPayloadPacket));
    }
    @Test void sneakingSessionStateAllowsStaleInteractSneakFlag() {
        hello();clearInvocations(session);setupAimedTarget();
        when(session.isSneaking()).thenReturn(true);
        var packet=new ServerboundInteractPacket(300,Hand.MAIN_HAND,Vector3d.ZERO,false);
        var event=new PacketSendingEvent(network,packet);bridge.packetSending(event);
        assertTrue(event.isCancelled());
        verify(session).sendDownstreamGamePacket(argThat(p -> p instanceof ServerboundCustomPayloadPacket));
        verify(session,never()).sendDownstreamGamePacket(same(packet));
    }

    @Test void sneakRightClickAirCanPatHoveredEntity() {
        hello();clearInvocations(session);setupAimedTarget();
        when(session.isSneaking()).thenReturn(true);
        var hovered=cache.getEntityByJavaId(300);
        when(session.getMouseoverEntity()).thenReturn(hovered);
        var packet=new ServerboundUseItemPacket(Hand.MAIN_HAND,0,0f,0f);
        var event=new PacketSendingEvent(network,packet);bridge.packetSending(event);
        assertTrue(event.isCancelled());
        verify(session).sendDownstreamGamePacket(argThat(p -> p instanceof ServerboundCustomPayloadPacket));
        verify(session,never()).sendDownstreamGamePacket(same(packet));
    }
    @Test void sneakRightClickAirWithoutHoveredEntityPreservesVanillaBehavior() {
        hello();clearInvocations(session);setupAimedTarget();
        when(session.isSneaking()).thenReturn(true);
        var packet=new ServerboundUseItemPacket(Hand.MAIN_HAND,0,0f,0f);
        var event=new PacketSendingEvent(network,packet);bridge.packetSending(event);
        assertTrue(event.isCancelled());
        verify(session).sendDownstreamGamePacket(same(packet));
        verify(session,never()).sendDownstreamGamePacket(argThat(p->p instanceof ServerboundCustomPayloadPacket));
    }

    @Test void testCommandDoesNotFallBackToSelfWhenNothingIsAimed() {
        setupAimedTarget();
        bridge.test(false);
        verify(session,never()).sendUpstreamPacket(any());
        verify(session).sendMessage(contains("照準"));
    }
    @Test void testCommandRejectsStaleHoverWhenLookDirectionChanged() {
        setupAimedTarget();
        var hovered=cache.getEntityByJavaId(300);
        when(session.getMouseoverEntity()).thenReturn(hovered);
        when(session.getPlayerEntity().getYaw()).thenReturn(90f);
        bridge.test(false);
        verify(session,never()).sendUpstreamPacket(any());
    }
    @Test void testCommandAcceptsAimedTargetWithoutBackend() {
        setupAimedTarget();
        var hovered=cache.getEntityByJavaId(300);
        when(session.getMouseoverEntity()).thenReturn(hovered);
        bridge.test(true);
        verify(session,atLeastOnce()).sendUpstreamPacket(any());
        verify(session).sendMessage(contains("表示テスト"));
    }
    @Test void standalonePatWorksWithoutBackendHello() {
        setupAimedTarget();
        var original=interact();var event=new PacketSendingEvent(network,original);
        bridge.packetSending(event);
        assertTrue(event.isCancelled());
        verify(session,never()).sendDownstreamGamePacket(any());
        verify(session,atLeastOnce()).sendUpstreamPacket(any());
    }
    @Test void standaloneCanBeDisabledToRestoreOldBackendRequirement() {
        PatPatExtension disabledOwner=mock(PatPatExtension.class);
        Properties p=new Properties();p.setProperty("standalone-mode","false");
        when(disabledOwner.settings()).thenReturn(Settings.from(p));
        PatSession disabled=new PatSession(disabledOwner,session);
        var event=new PacketSendingEvent(network,interact());
        disabled.packetSending(event);
        assertFalse(event.isCancelled());
        verify(session,never()).sendDownstreamGamePacket(any());
        verify(session,never()).sendUpstreamPacket(any());
    }


    @Test void personalToggleMutesInteractionsAndTestsUntilReenabled() throws Exception {
        setupAimedTarget();
        var enabled=new java.util.concurrent.atomic.AtomicBoolean(true);
        when(owner.patEnabled(session)).thenAnswer(inv->enabled.get());
        when(owner.setPatEnabled(eq(session),anyBoolean())).thenAnswer(inv->{
            boolean value=inv.getArgument(1);enabled.set(value);return value;
        });
        bridge.toggle(new String[]{"off"});
        assertFalse(enabled.get());
        verify(session).sendMessage(contains("OFF"));
        clearInvocations(session);
        var gesture=new PacketSendingEvent(network,interact());bridge.packetSending(gesture);
        assertFalse(gesture.isCancelled(),"Disabled users retain ordinary vanilla interactions");
        bridge.test(false);
        verify(session,never()).sendUpstreamPacket(any());
        bridge.toggle(new String[]{"on"});
        assertTrue(enabled.get());
        clearInvocations(session);
        var active=new PacketSendingEvent(network,interact());bridge.packetSending(active);
        assertTrue(active.isCancelled(),"Re-enabled users can Pat again");
        verify(session,atLeastOnce()).sendUpstreamPacket(any());
    }
    @Test void invalidToggleArgumentsDoNotChangePreference() throws Exception {
        bridge.toggle(new String[]{"perhaps"});
        verify(owner,never()).setPatEnabled(any(),anyBoolean());
        verify(session).sendMessage(contains("使い方"));
    }

    @Test void disabledPlayerIgnoresBackendPatAnimationsAndStandaloneRelays() {
        setupAimedTarget();
        when(owner.patEnabled(session)).thenReturn(false);
        bridge.packetReceived(network,new ClientboundCustomPayloadPacket(
                Key.key(PatProtocol.PAT_S2C),new byte[]{(byte)0xAC,2,1}));
        bridge.showStandalone(300);
        verify(session,never()).sendUpstreamPacket(any());
    }

    @Test void successfulPatTriggersVanillaSwingAndRecordsStats() {
        setupAimedTarget();
        var event=new PacketSendingEvent(network,interact());
        bridge.packetSending(event);
        assertTrue(event.isCancelled());
        verify(session).sendUpstreamPacket(argThat(p ->
                p instanceof org.cloudburstmc.protocol.bedrock.packet.AnimatePacket swing
                && swing.getAction()==org.cloudburstmc.protocol.bedrock.packet.AnimatePacket.Action.SWING_ARM));
        verify(owner).recordPat(session);
    }

    @Test void ignoredMobTypePreservesVanillaInteractionWithoutPatOrDamage() {
        setupAimedTarget();
        var mob=cache.getEntityByJavaId(300);
        var type=mock(org.geysermc.geyser.entity.BedrockEntityDefinition.class);
        var id=mock(org.geysermc.geyser.api.util.Identifier.class);
        when(id.toString()).thenReturn("minecraft:cow");
        when(type.identifier()).thenReturn(id);
        when(mob.bedrockDefinition()).thenReturn(type);
        Properties props=new Properties();
        props.setProperty("require-empty-hand","false");
        props.setProperty("ignored-entity-types","cow");
        when(owner.settings()).thenReturn(Settings.from(props));
        var packet=interact();
        var event=new PacketSendingEvent(network,packet);
        bridge.packetSending(event);
        assertTrue(event.isCancelled());
        verify(session).sendDownstreamGamePacket(same(packet));
        verify(owner,never()).recordPat(any());
    }

    @Test void backendPatOnlyBobsTheRecipientAndNotOtherViewers() {
        setupAimedTarget();
        Properties p=new Properties();
        p.setProperty("camera-shake-enabled","true");
        p.setProperty("require-empty-hand","false");
        when(owner.settings()).thenReturn(Settings.from(p));
        when(session.camera()).thenReturn(mock(org.geysermc.geyser.api.bedrock.camera.CameraData.class));

        // Local player Java ID is 1, and the incoming Pat was sent by player 2.
        bridge.packetReceived(network,new ClientboundCustomPayloadPacket(
                Key.key(PatProtocol.PAT_S2C),new byte[]{1,2}));
        verify(session).sendUpstreamPacket(argThat(packet ->
                packet instanceof org.cloudburstmc.protocol.bedrock.packet.CameraInstructionPacket camera
                        && camera.getSetInstruction()!=null));
        clearInvocations(session);

        // Different entity is patted: the player may see the hand but never bobs.
        bridge.packetReceived(network,new ClientboundCustomPayloadPacket(
                Key.key(PatProtocol.PAT_S2C),new byte[]{(byte)0xAC,2,2}));
        verify(session,never()).sendUpstreamPacket(argThat(packet ->
                packet instanceof org.cloudburstmc.protocol.bedrock.packet.CameraInstructionPacket));
    }

    @Test void pattingAnotherEntityNeverStartsCameraFeedbackForThePatter() {
        setupAimedTarget();
        Properties p=new Properties();
        p.setProperty("camera-shake-enabled","true");
        p.setProperty("require-empty-hand","false");
        when(owner.settings()).thenReturn(Settings.from(p));

        bridge.packetSending(new PacketSendingEvent(network,interact()));
        verify(session,never()).sendUpstreamPacket(argThat(packet ->
                packet instanceof org.cloudburstmc.protocol.bedrock.packet.CameraInstructionPacket));
    }

}
