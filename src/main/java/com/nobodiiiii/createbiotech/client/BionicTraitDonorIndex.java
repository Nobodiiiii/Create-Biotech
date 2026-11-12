package com.nobodiiiii.createbiotech.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitDonorsPacket;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitDonorsRequestPacket;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.nobodiiiii.createbiotech.registry.CBConfigs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Shared by trait pages and species lookups; synchronized while JEI's runtime is available. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public final class BionicTraitDonorIndex {
	private static final Map<BionicTrait, List<EntityType<?>>> DONORS = new EnumMap<>(BionicTrait.class);
	private static ClientPacketListener connection;
	private static long generation = -1;
	private static long nextRequest;
	private static int received;
	private static int checked;
	private static int total;
	private static int failures;
	private static boolean complete;
	private static boolean jeiRuntimeAvailable;

	private BionicTraitDonorIndex() {}

	public static void setJeiRuntimeAvailable(boolean available) {
		jeiRuntimeAvailable = available;
		if (!available)
			clear();
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		// Warm the index before the first egg/box lookup, which has no donor widget to poll it.
		if (jeiRuntimeAvailable)
			requestIfNeeded();
	}

	public static void requestIfNeeded() {
		if (!CBConfigs.CLIENT.showBionicTraitDonorsInJei.get()) {
			clear();
			return;
		}
		ClientPacketListener current = Minecraft.getInstance().getConnection();
		if (connection != current) {
			clear();
			connection = current;
		}
		long now = System.nanoTime();
		if (connection != null && now >= nextRequest) {
			nextRequest = now + (complete ? 3_000_000_000L : 750_000_000L);
			CBPackets.sendToServer(new BionicTraitDonorsRequestPacket(generation, received));
		}
	}

	public static void accept(BionicTraitDonorsPacket packet) {
		if (!CBConfigs.CLIENT.showBionicTraitDonorsInJei.get()) {
			clear();
			return;
		}
		if (connection == null || connection != Minecraft.getInstance().getConnection())
			return;
		if (generation != packet.generation()) {
			DONORS.clear();
			received = 0;
			generation = packet.generation();
		}
		if (packet.offset() != received)
			return;
		for (var entry : packet.entries()) {
			BuiltInRegistries.ENTITY_TYPE.getOptional(entry.entity()).ifPresent(type -> {
				for (BionicTrait trait : BionicTrait.values())
					if (entry.provides(trait))
						DONORS.computeIfAbsent(trait, ignored -> new ArrayList<>()).add(type);
			});
		}
		received += packet.entries().size();
		checked = packet.checked();
		total = packet.total();
		failures = packet.failures();
		complete = packet.complete();
	}

	public static List<EntityType<?>> donors(BionicTrait trait) { return DONORS.getOrDefault(trait, List.of()); }
	public static List<BionicTrait> traits(EntityType<?> donor) {
		return DONORS.entrySet().stream()
			.filter(entry -> entry.getValue().contains(donor))
			.map(Map.Entry::getKey)
			.toList();
	}
	public static boolean complete() { return complete; }
	public static int checked() { return checked; }
	public static int total() { return total; }
	public static int failures() { return failures; }

	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

	private static void clear() {
		DONORS.clear();
		connection = null;
		generation = -1;
		nextRequest = 0;
		received = checked = total = failures = 0;
		complete = false;
	}
}
