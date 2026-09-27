package com.nobodiiiii.createbiotech.entity.trait;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.network.CBPackets;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** On-demand species index using the same donor detector and loaded data as surgery. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public final class BionicTraitDonorCatalog {
	private static final Map<MinecraftServer, Scan> SCANS = new HashMap<>();
	private static final long TICK_BUDGET_NANOS = 1_000_000L;
	private static final int MAX_TYPES_PER_TICK = 2;

	private BionicTraitDonorCatalog() {}

	static void request(ServerPlayer player, long generation, int offset) {
		MinecraftServer server = player.server;
		Scan scan = SCANS.computeIfAbsent(server, ignored -> new Scan());
		if (scan.generation != BionicTraitRegistry.generation()) {
			scan = new Scan();
			SCANS.put(server, scan);
		}
		int tick = server.getTickCount();
		Integer previous = scan.requests.put(player.getUUID(), tick);
		if (previous != null && tick - previous < 10)
			return;
		scan.lastRequestedTick = tick;
		int start = generation == scan.generation && offset >= 0 && offset <= scan.entries.size() ? offset : 0;
		int end = Math.min(start + BionicTraitDonorsPacket.MAX_ENTRIES, scan.entries.size());
		CBPackets.sendToPlayer(new BionicTraitDonorsPacket(scan.generation, start, scan.cursor,
			scan.types.size(), scan.failures, scan.cursor == scan.types.size() && end == scan.entries.size(),
			List.copyOf(scan.entries.subList(start, end))), player);
	}

	@SubscribeEvent
	public static void onTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		Scan scan = SCANS.get(server);
		if (scan == null || scan.generation != BionicTraitRegistry.generation())
			return;
		int tick = server.getTickCount();
		scan.requests.entrySet().removeIf(entry -> tick - entry.getValue() > 100);
		if (tick - scan.lastRequestedTick > 100)
			return;
		long start = System.nanoTime();
		for (int count = 0; count < MAX_TYPES_PER_TICK && scan.cursor < scan.types.size(); count++) {
			EntityType<?> type = scan.types.get(scan.cursor++);
			try {
				// Never spawn or tick the prototype; release it immediately after inspection.
				Entity prototype = type.create(server.overworld());
				if (prototype instanceof LivingEntity living) {
					BionicTraitDonors.Facts facts = BionicTraitDonors.detect(living);
					long mask = 0;
					for (BionicTrait trait : BionicTrait.values()) {
						boolean provides = switch (trait.valueKind()) {
						case ABILITY -> facts.has(trait);
						case NUMBER -> facts.value(trait) > 0;
						case EFFECT_SET -> !facts.immuneEffects().isEmpty();
						case ATTACK_EFFECT_SET -> !facts.attackEffects().isEmpty();
						};
						if (provides)
							mask |= 1L << trait.ordinal();
					}
					if (mask != 0)
						scan.entries.add(new BionicTraitDonorsPacket.Entry(BuiltInRegistries.ENTITY_TYPE.getKey(type), mask));
				}
			} catch (RuntimeException | StackOverflowError exception) {
				scan.failures++;
				LogUtils.getLogger().warn("Unable to inspect bionic donor {}", BuiltInRegistries.ENTITY_TYPE.getKey(type), exception);
			}
			if (System.nanoTime() - start >= TICK_BUDGET_NANOS)
				break;
		}
	}

	@SubscribeEvent
	public static void onStopped(ServerStoppedEvent event) {
		SCANS.remove(event.getServer());
	}

	private static final class Scan {
		private final long generation = BionicTraitRegistry.generation();
		private final List<EntityType<?>> types = BuiltInRegistries.ENTITY_TYPE.stream()
			.filter(type -> type != EntityType.PLAYER)
			// NeoForge includes mod-registered suppliers in this lookup as well.
			.filter(DefaultAttributes::hasSupplier)
			.sorted(Comparator.comparing(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString())).toList();
		private final List<BionicTraitDonorsPacket.Entry> entries = new ArrayList<>();
		private final Map<UUID, Integer> requests = new HashMap<>();
		private int cursor;
		private int failures;
		private int lastRequestedTick;
	}
}
