package com.nobodiiiii.createbiotech.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerContraptionAnimationPacket;
import com.nobodiiiii.createbiotech.content.dingdongchicken.DingDongChickenVoiceSoundPacket;
import com.nobodiiiii.createbiotech.content.giantfrog.GiantFrogEatPacket;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastBalloonMagnetTargetPacket;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltEntityAnimationPacket;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltSurfaceMovementPacket;
import com.nobodiiiii.createbiotech.content.shulkerpackager.ShulkerPackagerPlacementPacket;
import com.nobodiiiii.createbiotech.content.shulkerteleporter.ShulkerTeleporterConfigPacket;
import com.nobodiiiii.createbiotech.content.smartglue.SmartSuperGlueRemovalPacket;
import com.nobodiiiii.createbiotech.content.smartglue.SmartSuperGlueSelectionPacket;
import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicDogCannonChargeSoundPacket;
import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicDogCannonFirePacket;
import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicDogCannonGearAnimationPacket;
import com.nobodiiiii.createbiotech.content.allay.block.allayport.AllayPortConfigurationPacket;
import com.nobodiiiii.createbiotech.content.allay.block.allayport.AllayPortFlapPacket;
import com.nobodiiiii.createbiotech.content.allay.logistics.courier.hud.AllayCourierHudPacket;
import com.nobodiiiii.createbiotech.content.allay.network.allay.AllayCourierConfirmPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableInteractionPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitSelectionPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBatchCutPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableGluePacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableLimbPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableLimbRemovalPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlacementPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableReleaseGeometryPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableSlimeSeamPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableShovelPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableSymmetryPacket;
import com.nobodiiiii.createbiotech.entity.SlimeBionicBodyBoundsPacket;
import com.nobodiiiii.createbiotech.entity.SlimeBionicAttackActionPacket;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicDeathGeometryPacket;

import net.createmod.catnip.annotations.ClientOnly;
import net.createmod.catnip.net.base.BasePacketPayload.PacketTypeProvider;
import net.createmod.catnip.net.base.CatnipPacketRegistry;
import net.createmod.catnip.net.base.ClientboundPacketPayload;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public final class CBPackets {

	private static final String NETWORK_VERSION = "40";
	private static final List<ServerRegistration<?>> SERVERBOUND = new ArrayList<>();
	private static final List<ClientRegistration<?>> CLIENTBOUND = new ArrayList<>();
	private static final Map<Class<?>, Integer> SERVERBOUND_IDS = new HashMap<>();
	private static final Map<Class<?>, Integer> CLIENTBOUND_IDS = new HashMap<>();
	private static boolean registered;

	private CBPackets() {}

	public static void register() {
		if (registered)
			return;
		registered = true;

		registerServer(PowerBeltSurfaceMovementPacket.class, PowerBeltSurfaceMovementPacket::new,
			PowerBeltSurfaceMovementPacket::write, PowerBeltSurfaceMovementPacket::handle);
		registerServer(GhastBalloonMagnetTargetPacket.class, GhastBalloonMagnetTargetPacket::new,
			GhastBalloonMagnetTargetPacket::write, GhastBalloonMagnetTargetPacket::handle);
		registerServer(SmartSuperGlueSelectionPacket.class, SmartSuperGlueSelectionPacket::new,
			SmartSuperGlueSelectionPacket::write, SmartSuperGlueSelectionPacket::handle);
		registerServer(SmartSuperGlueRemovalPacket.class, SmartSuperGlueRemovalPacket::new,
			SmartSuperGlueRemovalPacket::write, SmartSuperGlueRemovalPacket::handle);
		registerServer(ShulkerPackagerPlacementPacket.class, ShulkerPackagerPlacementPacket::new,
			ShulkerPackagerPlacementPacket::write, ShulkerPackagerPlacementPacket::handle);
		registerServer(ShulkerTeleporterConfigPacket.class, ShulkerTeleporterConfigPacket::new,
			ShulkerTeleporterConfigPacket::write, ShulkerTeleporterConfigPacket::handle);
		registerServer(AllayCourierConfirmPacket.class, AllayCourierConfirmPacket::new,
			AllayCourierConfirmPacket::write, AllayCourierConfirmPacket::handle);
		registerServer(AllayPortConfigurationPacket.class, AllayPortConfigurationPacket::new,
			AllayPortConfigurationPacket::write, AllayPortConfigurationPacket::handle);
		// Keep every existing packet id stable; new packets are appended.
		registerServer(SurgicalTableInteractionPacket.class, SurgicalTableInteractionPacket::new,
			SurgicalTableInteractionPacket::write, SurgicalTableInteractionPacket::handle);
		registerServer(SurgicalTableGluePacket.class, SurgicalTableGluePacket::new,
			SurgicalTableGluePacket::write, SurgicalTableGluePacket::handle);
		registerServer(SurgicalTablePlacementPacket.class, SurgicalTablePlacementPacket::new,
			SurgicalTablePlacementPacket::write, SurgicalTablePlacementPacket::handle);
		registerServer(SurgicalTableLimbPacket.class, SurgicalTableLimbPacket::new,
			SurgicalTableLimbPacket::write, SurgicalTableLimbPacket::handle);
		registerServer(SlimeBionicBodyBoundsPacket.class, SlimeBionicBodyBoundsPacket::new,
			SlimeBionicBodyBoundsPacket::write, SlimeBionicBodyBoundsPacket::handle);
		registerServer(SlimeMimicDeathGeometryPacket.class, SlimeMimicDeathGeometryPacket::new,
			SlimeMimicDeathGeometryPacket::write, SlimeMimicDeathGeometryPacket::handle);
		registerServer(SurgicalTableBatchCutPacket.class, SurgicalTableBatchCutPacket::new,
			SurgicalTableBatchCutPacket::write, SurgicalTableBatchCutPacket::handle);
		registerServer(SurgicalTableLimbRemovalPacket.class, SurgicalTableLimbRemovalPacket::new,
			SurgicalTableLimbRemovalPacket::write, SurgicalTableLimbRemovalPacket::handle);
		registerServer(SurgicalTableSymmetryPacket.class, SurgicalTableSymmetryPacket::new,
			SurgicalTableSymmetryPacket::write, SurgicalTableSymmetryPacket::handle);
		registerServer(SurgicalTableSlimeSeamPacket.class, SurgicalTableSlimeSeamPacket::new,
			SurgicalTableSlimeSeamPacket::write, SurgicalTableSlimeSeamPacket::handle);
		registerServer(SurgicalKitSelectionPacket.class, SurgicalKitSelectionPacket::new,
			SurgicalKitSelectionPacket::write, SurgicalKitSelectionPacket::handle);
		registerServer(SurgicalTableReleaseGeometryPacket.class, SurgicalTableReleaseGeometryPacket::new,
			SurgicalTableReleaseGeometryPacket::write, SurgicalTableReleaseGeometryPacket::handle);
		registerServer(SurgicalTableShovelPacket.class, SurgicalTableShovelPacket::new,
			SurgicalTableShovelPacket::write, SurgicalTableShovelPacket::handle);

		registerClient(PowerBeltEntityAnimationPacket.class, PowerBeltEntityAnimationPacket::new,
			PowerBeltEntityAnimationPacket::write);
		registerClient(BioPackagerContraptionAnimationPacket.class, BioPackagerContraptionAnimationPacket::new,
			BioPackagerContraptionAnimationPacket::write);
		registerClient(ShulkerPackagerPlacementPacket.ClientBoundRequest.class,
			ShulkerPackagerPlacementPacket.ClientBoundRequest::new,
			ShulkerPackagerPlacementPacket.ClientBoundRequest::write);
		registerClient(AllayPortFlapPacket.class, AllayPortFlapPacket::new,
			AllayPortFlapPacket::write);
		registerClient(AllayCourierHudPacket.class, AllayCourierHudPacket::new,
			AllayCourierHudPacket::write);
		registerClient(GiantFrogEatPacket.class, GiantFrogEatPacket::new,
			GiantFrogEatPacket::write);
		// Keep every existing packet id stable; new packets are appended.
		registerClient(ShulkerPackagerPlacementPacket.ClientBoundResult.class,
			ShulkerPackagerPlacementPacket.ClientBoundResult::new,
			ShulkerPackagerPlacementPacket.ClientBoundResult::write);
		registerClient(SonicDogCannonFirePacket.class, SonicDogCannonFirePacket::new,
			SonicDogCannonFirePacket::write);
		registerClient(SonicDogCannonChargeSoundPacket.class, SonicDogCannonChargeSoundPacket::new,
			SonicDogCannonChargeSoundPacket::write);
		registerClient(SonicDogCannonGearAnimationPacket.class, SonicDogCannonGearAnimationPacket::new,
			SonicDogCannonGearAnimationPacket::write);
		registerClient(DingDongChickenVoiceSoundPacket.class, DingDongChickenVoiceSoundPacket::new,
			DingDongChickenVoiceSoundPacket::write);
		registerClient(ContainedEntityHandoffPacket.class, ContainedEntityHandoffPacket::new,
			ContainedEntityHandoffPacket::write);
		registerClient(SlimeBionicAttackActionPacket.class, SlimeBionicAttackActionPacket::new,
			SlimeBionicAttackActionPacket::write);
		registerClient(SurgicalTableReleaseGeometryPacket.ClientBoundRequest.class,
			SurgicalTableReleaseGeometryPacket.ClientBoundRequest::new,
			SurgicalTableReleaseGeometryPacket.ClientBoundRequest::write);

		CatnipPacketRegistry registry = new CatnipPacketRegistry(CreateBiotech.MOD_ID, NETWORK_VERSION);
		registry.registerPacket(new CatnipPacketRegistry.PacketType<>(
			PacketType.SERVERBOUND.type(), ServerPayload.class, ServerPayload.STREAM_CODEC));
		registry.registerPacket(new CatnipPacketRegistry.PacketType<>(
			PacketType.CLIENTBOUND.type(), ClientPayload.class, ClientPayload.STREAM_CODEC));
		registry.registerAllPackets();
	}

	public static void sendToServer(Object packet) {
		CatnipServices.NETWORK.sendToServer(new ServerPayload(packet));
	}

	public static void sendToTrackingEntity(Object packet, Entity entity) {
		CatnipServices.NETWORK.sendToClientsTrackingEntity(entity, new ClientPayload(packet));
	}

	public static void sendToTrackingChunk(Object packet, ServerLevel level, BlockPos pos) {
		CatnipServices.NETWORK.sendToClientsTrackingChunk(level, new ChunkPos(pos), new ClientPayload(packet));
	}

	public static void sendToPlayer(Object packet, ServerPlayer player) {
		CatnipServices.NETWORK.sendToClient(player, new ClientPayload(packet));
	}

	public static PacketTypeProvider serverboundType() {
		return PacketType.SERVERBOUND;
	}

	public static PacketTypeProvider clientboundType() {
		return PacketType.CLIENTBOUND;
	}

	private static <T> void registerServer(Class<T> type, Function<RegistryFriendlyByteBuf, T> decoder,
		BiConsumer<T, RegistryFriendlyByteBuf> encoder, BiConsumer<T, ServerPlayer> handler) {
		SERVERBOUND_IDS.put(type, SERVERBOUND.size());
		SERVERBOUND.add(new ServerRegistration<>(type, decoder, encoder, handler));
	}

	private static <T> void registerClient(Class<T> type, Function<RegistryFriendlyByteBuf, T> decoder,
		BiConsumer<T, RegistryFriendlyByteBuf> encoder) {
		CLIENTBOUND_IDS.put(type, CLIENTBOUND.size());
		CLIENTBOUND.add(new ClientRegistration<>(type, decoder, encoder));
	}

	private static ServerPayload decodeServer(RegistryFriendlyByteBuf buffer) {
		int id = buffer.readVarInt();
		if (id < 0 || id >= SERVERBOUND.size())
			throw new IllegalArgumentException("Unknown Create Biotech serverbound packet id " + id);
		return new ServerPayload(SERVERBOUND.get(id).decode(buffer));
	}

	private static void encodeServer(RegistryFriendlyByteBuf buffer, ServerPayload payload) {
		Integer id = SERVERBOUND_IDS.get(payload.packet.getClass());
		if (id == null)
			throw new IllegalArgumentException("Unregistered Create Biotech serverbound packet "
				+ payload.packet.getClass().getName());
		buffer.writeVarInt(id);
		SERVERBOUND.get(id).encode(payload.packet, buffer);
	}

	private static ClientPayload decodeClient(RegistryFriendlyByteBuf buffer) {
		int id = buffer.readVarInt();
		if (id < 0 || id >= CLIENTBOUND.size())
			throw new IllegalArgumentException("Unknown Create Biotech clientbound packet id " + id);
		return new ClientPayload(CLIENTBOUND.get(id).decode(buffer));
	}

	private static void encodeClient(RegistryFriendlyByteBuf buffer, ClientPayload payload) {
		Integer id = CLIENTBOUND_IDS.get(payload.packet.getClass());
		if (id == null)
			throw new IllegalArgumentException("Unregistered Create Biotech clientbound packet "
				+ payload.packet.getClass().getName());
		buffer.writeVarInt(id);
		CLIENTBOUND.get(id).encode(payload.packet, buffer);
	}

	private record ServerPayload(Object packet) implements ServerboundPacketPayload {
		private static final StreamCodec<RegistryFriendlyByteBuf, ServerPayload> STREAM_CODEC =
			StreamCodec.of(CBPackets::encodeServer, CBPackets::decodeServer);

		@Override
		public void handle(ServerPlayer player) {
			Integer id = SERVERBOUND_IDS.get(packet.getClass());
			if (id == null)
				throw new IllegalArgumentException("Unregistered Create Biotech serverbound packet "
					+ packet.getClass().getName());
			SERVERBOUND.get(id).handle(packet, player);
		}

		@Override
		public PacketTypeProvider getTypeProvider() {
			return PacketType.SERVERBOUND;
		}
	}

	private record ClientPayload(Object packet) implements ClientboundPacketPayload {
		private static final StreamCodec<RegistryFriendlyByteBuf, ClientPayload> STREAM_CODEC =
			StreamCodec.of(CBPackets::encodeClient, CBPackets::decodeClient);

		@Override
		@ClientOnly
		@OnlyIn(Dist.CLIENT)
		public void handle(LocalPlayer player) {
			Integer id = CLIENTBOUND_IDS.get(packet.getClass());
			if (id == null)
				throw new IllegalArgumentException("Unregistered Create Biotech clientbound packet "
					+ packet.getClass().getName());
			CBClientPacketHandlers.handle(packet, player);
		}

		@Override
		public PacketTypeProvider getTypeProvider() {
			return PacketType.CLIENTBOUND;
		}
	}

	private record ServerRegistration<T>(Class<T> type, Function<RegistryFriendlyByteBuf, T> decoder,
										 BiConsumer<T, RegistryFriendlyByteBuf> encoder,
										 BiConsumer<T, ServerPlayer> handler) {
		private Object decode(RegistryFriendlyByteBuf buffer) {
			return decoder.apply(buffer);
		}

		private void encode(Object packet, RegistryFriendlyByteBuf buffer) {
			encoder.accept(type.cast(packet), buffer);
		}

		private void handle(Object packet, ServerPlayer player) {
			handler.accept(type.cast(packet), player);
		}
	}

	private record ClientRegistration<T>(Class<T> type, Function<RegistryFriendlyByteBuf, T> decoder,
										 BiConsumer<T, RegistryFriendlyByteBuf> encoder) {
		private Object decode(RegistryFriendlyByteBuf buffer) {
			return decoder.apply(buffer);
		}

		private void encode(Object packet, RegistryFriendlyByteBuf buffer) {
			encoder.accept(type.cast(packet), buffer);
		}
	}

	private enum PacketType implements PacketTypeProvider {
		SERVERBOUND("serverbound"),
		CLIENTBOUND("clientbound");

		private final CustomPacketPayload.Type<? extends CustomPacketPayload> type;

		PacketType(String path) {
			type = new CustomPacketPayload.Type<>(CreateBiotech.asResource(path));
		}

		@SuppressWarnings("unchecked")
		private <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type() {
			return (CustomPacketPayload.Type<T>) type;
		}

		@Override
		public <T extends CustomPacketPayload> CustomPacketPayload.Type<T> getType() {
			return type();
		}
	}
}
