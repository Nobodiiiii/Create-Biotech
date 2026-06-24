package com.nobodiiiii.createbiotech.network;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerContraptionAnimationPacket;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastBalloonMagnetTargetPacket;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltEntityAnimationPacket;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltSurfaceMovementPacket;
import com.nobodiiiii.createbiotech.content.shulkerpackager.ShulkerPackagerPlacementPacket;
import com.nobodiiiii.createbiotech.content.shulkerteleporter.ShulkerTeleporterConfigPacket;
import com.nobodiiiii.createbiotech.content.smartglue.SmartSuperGlueRemovalPacket;
import com.nobodiiiii.createbiotech.content.smartglue.SmartSuperGlueSelectionPacket;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent.Context;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class CBPackets {

	private static final String NETWORK_VERSION = "5";
	private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder.named(CreateBiotech.asResource("main"))
		.serverAcceptedVersions(NETWORK_VERSION::equals)
		.clientAcceptedVersions(NETWORK_VERSION::equals)
		.networkProtocolVersion(() -> NETWORK_VERSION)
		.simpleChannel();

	private static int packetId;

	private CBPackets() {}

	public static void register() {
		register(PowerBeltSurfaceMovementPacket.class, PowerBeltSurfaceMovementPacket::new,
			PowerBeltSurfaceMovementPacket::write, PowerBeltSurfaceMovementPacket::handle, NetworkDirection.PLAY_TO_SERVER);
		register(PowerBeltEntityAnimationPacket.class, PowerBeltEntityAnimationPacket::new,
			PowerBeltEntityAnimationPacket::write, PowerBeltEntityAnimationPacket::handle,
			NetworkDirection.PLAY_TO_CLIENT);
		register(BioPackagerContraptionAnimationPacket.class, BioPackagerContraptionAnimationPacket::new,
			BioPackagerContraptionAnimationPacket::write, BioPackagerContraptionAnimationPacket::handle,
			NetworkDirection.PLAY_TO_CLIENT);
		register(GhastBalloonMagnetTargetPacket.class, GhastBalloonMagnetTargetPacket::new,
			GhastBalloonMagnetTargetPacket::write, GhastBalloonMagnetTargetPacket::handle,
			NetworkDirection.PLAY_TO_SERVER);
		register(SmartSuperGlueSelectionPacket.class, SmartSuperGlueSelectionPacket::new,
			SmartSuperGlueSelectionPacket::write, SmartSuperGlueSelectionPacket::handle,
			NetworkDirection.PLAY_TO_SERVER);
		register(SmartSuperGlueRemovalPacket.class, SmartSuperGlueRemovalPacket::new,
			SmartSuperGlueRemovalPacket::write, SmartSuperGlueRemovalPacket::handle,
			NetworkDirection.PLAY_TO_SERVER);
		register(ShulkerPackagerPlacementPacket.class, ShulkerPackagerPlacementPacket::new,
			ShulkerPackagerPlacementPacket::write, ShulkerPackagerPlacementPacket::handle,
			NetworkDirection.PLAY_TO_SERVER);
		register(ShulkerPackagerPlacementPacket.ClientBoundRequest.class,
			ShulkerPackagerPlacementPacket.ClientBoundRequest::new,
			ShulkerPackagerPlacementPacket.ClientBoundRequest::write,
			ShulkerPackagerPlacementPacket.ClientBoundRequest::handle, NetworkDirection.PLAY_TO_CLIENT);
		register(ShulkerTeleporterConfigPacket.class, ShulkerTeleporterConfigPacket::new,
			ShulkerTeleporterConfigPacket::write, ShulkerTeleporterConfigPacket::handle,
			NetworkDirection.PLAY_TO_SERVER);
	}

	public static void sendToServer(Object packet) {
		CHANNEL.sendToServer(packet);
	}

	public static void sendToTrackingEntity(Object packet, Entity entity) {
		CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet);
	}

	public static void sendToPlayer(Object packet, net.minecraft.server.level.ServerPlayer player) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
	}

	private static <T> void register(Class<T> type, Function<FriendlyByteBuf, T> decoder,
		BiConsumer<T, FriendlyByteBuf> encoder, BiConsumer<T, Context> handler, NetworkDirection direction) {
		BiConsumer<T, Supplier<Context>> consumer = (packet, contextSupplier) -> handle(packet, contextSupplier, handler);
		CHANNEL.messageBuilder(type, packetId++, direction)
			.encoder(encoder)
			.decoder(decoder)
			.consumerNetworkThread(consumer)
			.add();
	}

	private static <T> void handle(T packet, Supplier<Context> contextSupplier, BiConsumer<T, Context> handler) {
		Context context = contextSupplier.get();
		handler.accept(packet, context);
		context.setPacketHandled(true);
	}
}
