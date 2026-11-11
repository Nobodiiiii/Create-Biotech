package com.nobodiiiii.createbiotech.entity.trait;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.network.CBPackets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/** Thresholds and replacement rules from the same data snapshot used by trait acquisition. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public record BionicTraitRulesPacket(Map<BionicTrait, Double> minCoverage,
	Map<BionicTrait, Set<BionicTrait>> overrides) {
	public BionicTraitRulesPacket {
		minCoverage = Map.copyOf(minCoverage);
		minCoverage.forEach((trait, value) -> new BionicTraitRule(trait.rule().type(), value));
		overrides = BionicTraitOverrides.validate(overrides);
	}

	public BionicTraitRulesPacket(RegistryFriendlyByteBuf buffer) {
		this(read(buffer), readOverrides(buffer));
	}

	public void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(minCoverage.size());
		minCoverage.forEach((trait, value) -> {
			buffer.writeEnum(trait);
			buffer.writeDouble(value);
		});
		buffer.writeVarInt(overrides.size());
		overrides.forEach((trait, targets) -> {
			buffer.writeEnum(trait);
			var sorted = java.util.EnumSet.noneOf(BionicTrait.class);
			sorted.addAll(targets);
			buffer.writeEnumSet(sorted, BionicTrait.class);
		});
	}

	@SubscribeEvent
	public static void onDatapackSync(OnDatapackSyncEvent event) {
		var data = BionicTraitRegistry.snapshot();
		Map<BionicTrait, Double> values = new EnumMap<>(BionicTrait.class);
		Map<BionicTrait, Set<BionicTrait>> overrides = new EnumMap<>(BionicTrait.class);
		for (BionicTrait trait : BionicTrait.values()) {
			values.put(trait, data.get(trait).rule().minCoverage());
			overrides.put(trait, data.get(trait).overrides());
		}
		BionicTraitRulesPacket packet = new BionicTraitRulesPacket(values, overrides);
		event.getRelevantPlayers().forEach(player -> CBPackets.sendToPlayer(packet, player));
	}

	private static Map<BionicTrait, Double> read(RegistryFriendlyByteBuf buffer) {
		int count = buffer.readVarInt();
		if (count < 0 || count > BionicTrait.values().length)
			throw new IllegalArgumentException("Invalid bionic trait rule count");
		Map<BionicTrait, Double> values = new EnumMap<>(BionicTrait.class);
		for (int index = 0; index < count; index++) {
			BionicTrait trait = buffer.readEnum(BionicTrait.class);
			if (values.put(trait, buffer.readDouble()) != null)
				throw new IllegalArgumentException("Duplicate bionic trait rule");
		}
		return values;
	}

	private static Map<BionicTrait, Set<BionicTrait>> readOverrides(RegistryFriendlyByteBuf buffer) {
		int count = buffer.readVarInt();
		if (count < 0 || count > BionicTrait.values().length)
			throw new IllegalArgumentException("Invalid bionic trait override count");
		Map<BionicTrait, Set<BionicTrait>> rules = new EnumMap<>(BionicTrait.class);
		for (int index = 0; index < count; index++) {
			BionicTrait trait = buffer.readEnum(BionicTrait.class);
			if (rules.put(trait, buffer.readEnumSet(BionicTrait.class)) != null)
				throw new IllegalArgumentException("Duplicate bionic trait overrides");
		}
		return rules;
	}
}
