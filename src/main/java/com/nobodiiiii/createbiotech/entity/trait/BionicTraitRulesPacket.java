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

/** Thresholds, limb counts, wing lift and replacements from the trait acquisition snapshot. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public record BionicTraitRulesPacket(Map<BionicTrait, Double> minCoverage,
	Map<BionicTrait, Set<BionicTrait>> overrides, Map<BionicTrait, Integer> minLimbs,
	Map<BionicTrait, Double> wingLift) {
	public BionicTraitRulesPacket {
		minCoverage = Map.copyOf(minCoverage);
		minCoverage.forEach((trait, value) -> new BionicTraitRule(trait.rule().type(), value));
		overrides = BionicTraitOverrides.validate(overrides);
		minLimbs = Map.copyOf(minLimbs);
		minLimbs.forEach((trait, minimum) -> {
			boolean wings = trait.condition() == BionicTrait.Condition.WING_LIFT
				|| trait.condition() == BionicTrait.Condition.LIMB_COUNT;
			if ((!trait.scopes().contains(BionicTraitScope.LEG) && !wings) || minimum < 0)
				throw new IllegalArgumentException("Invalid bionic trait limb count");
		});
		wingLift = Map.copyOf(wingLift);
		wingLift.forEach((trait, limit) -> {
			if (trait.condition() != BionicTrait.Condition.WING_LIFT || !Double.isFinite(limit) || limit <= 0)
				throw new IllegalArgumentException("Invalid bionic trait wing lift");
		});
	}

	public BionicTraitRulesPacket(RegistryFriendlyByteBuf buffer) {
		this(read(buffer), readOverrides(buffer), readMinLimbs(buffer), read(buffer));
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
		buffer.writeVarInt(minLimbs.size());
		minLimbs.forEach((trait, minimum) -> {
			buffer.writeEnum(trait);
			buffer.writeVarInt(minimum);
		});
		buffer.writeVarInt(wingLift.size());
		wingLift.forEach((trait, limit) -> {
			buffer.writeEnum(trait);
			buffer.writeDouble(limit);
		});
	}

	@SubscribeEvent
	public static void onDatapackSync(OnDatapackSyncEvent event) {
		var data = BionicTraitRegistry.snapshot();
		Map<BionicTrait, Double> values = new EnumMap<>(BionicTrait.class);
		Map<BionicTrait, Set<BionicTrait>> overrides = new EnumMap<>(BionicTrait.class);
		Map<BionicTrait, Integer> minLimbs = new EnumMap<>(BionicTrait.class);
		Map<BionicTrait, Double> wingLift = new EnumMap<>(BionicTrait.class);
		for (BionicTrait trait : BionicTrait.values()) {
			values.put(trait, data.get(trait).rule().minCoverage());
			overrides.put(trait, data.get(trait).overrides());
			if (trait.scopes().contains(BionicTraitScope.LEG))
				minLimbs.put(trait, data.get(trait).minLegs());
			else if (trait.condition() == BionicTrait.Condition.WING_LIFT
				|| trait.condition() == BionicTrait.Condition.LIMB_COUNT)
				minLimbs.put(trait, (int) data.get(trait).parameter("min_wings", 2));
			if (trait.condition() == BionicTrait.Condition.WING_LIFT)
				wingLift.put(trait, data.get(trait).parameter("max_body_volume_per_wing_volume", 16));
		}
		BionicTraitRulesPacket packet = new BionicTraitRulesPacket(values, overrides, minLimbs, wingLift);
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

	private static Map<BionicTrait, Integer> readMinLimbs(RegistryFriendlyByteBuf buffer) {
		int count = buffer.readVarInt();
		if (count < 0 || count > BionicTrait.values().length)
			throw new IllegalArgumentException("Invalid bionic trait limb count size");
		Map<BionicTrait, Integer> values = new EnumMap<>(BionicTrait.class);
		for (int index = 0; index < count; index++) {
			BionicTrait trait = buffer.readEnum(BionicTrait.class);
			if (values.put(trait, buffer.readVarInt()) != null)
				throw new IllegalArgumentException("Duplicate bionic trait limb count");
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
