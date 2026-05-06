package com.nobodiiiii.createbiotech.network;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltSurfaceMovementPacket;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent.Context;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class CBPackets {

	private static final String NETWORK_VERSION = "1";
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
	}

	public static void sendToServer(Object packet) {
		CHANNEL.sendToServer(packet);
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
