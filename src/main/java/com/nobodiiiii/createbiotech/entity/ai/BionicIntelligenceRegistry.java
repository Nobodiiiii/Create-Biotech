package com.nobodiiiii.createbiotech.entity.ai;

import java.util.Map;

import com.nobodiiiii.createbiotech.entity.trait.BionicTraitRegistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.raid.Raider;

/**
 * Inherited entity families supply advanced intelligence; all other donors are normal.
 * Data packs override either result. Simple intelligence is never inferred automatically.
 */
public final class BionicIntelligenceRegistry {
	private static volatile Map<ResourceLocation, BionicIntelligence> DATA = Map.of();

	private BionicIntelligenceRegistry() {}

	static void replaceData(Map<ResourceLocation, BionicIntelligence> data) {
		if (DATA.equals(data)) return;
		DATA = Map.copyOf(data);
		BionicTraitRegistry.invalidate();
	}

	static Map<ResourceLocation, BionicIntelligence> data() { return DATA; }

	public static BionicIntelligence get(LivingEntity entity) {
		if (entity == null)
			return BionicIntelligence.SIMPLE;
		BionicIntelligence override = DATA.get(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
		if (override != null)
			return override;
		return entity instanceof AbstractVillager || entity instanceof AbstractPiglin || entity instanceof Raider
			? BionicIntelligence.ADVANCED : BionicIntelligence.NORMAL;
	}
}
