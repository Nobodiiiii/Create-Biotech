package com.nobodiiiii.createbiotech.entity.trait;

import java.util.*;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Shared relationship evaluation for gameplay and donor detail displays. */
public final class BionicGameplayRelations {
	private BionicGameplayRelations() {}
	public static Set<ResourceLocation> targets(BionicTrait trait, ResourceLocation donor, Set<ResourceLocation> nativeTargets,
		net.minecraft.world.level.Level level) {
		return targets(BionicGameplayRules.get(level, trait), donor, nativeTargets);
	}
	static Set<ResourceLocation> targets(BionicGameplayRules rules, ResourceLocation donor, Set<ResourceLocation> nativeTargets) {
		Set<ResourceLocation> result = new HashSet<>(rules.replaceRelations ? Set.of() : nativeTargets);
		result.addAll(rules.relationTargets(donor)); return Set.copyOf(result);
	}
	public static boolean matches(SlimeBionicEntity entity, BionicTrait trait, LivingEntity target) {
		var rules = BionicGameplayRules.get(entity.level(), trait);
		var assembly = entity.getAssembly(); if (assembly == null) return false;
		Set<ResourceLocation> donors = new HashSet<>();
		entity.getBionicTraits().members(trait).forEach(m -> donors.add(assembly.sources().get(m.source()).profile().entityTypeId()));
		for (int i = 0; i < rules.relations.size(); i++) {
			var relation = rules.relations.get(i);
			if (trait.equals(BionicTrait.HUNTING_INSTINCT) && entity.getPersistentData().getCompound("BionicRelationCooldowns")
				.getLong(trait.id() + "/" + i) > entity.level().getGameTime()) continue;
			if (donors.stream().anyMatch(d -> relation.matches(entity, d, target))) return true;
		}
		return false;
	}
	public static double avoidanceSpeed(SlimeBionicEntity entity, LivingEntity target) {
		var rules = BionicGameplayRules.get(entity.level(), BionicTrait.DETERRENCE);
		var assembly = entity.getAssembly(); if (assembly == null) return 1;
		double speed = 0;
		for (var member : entity.getBionicTraits().members(BionicTrait.DETERRENCE))
			for (var rule : rules.relations) if (rule.matches(entity, assembly.sources().get(member.source()).profile().entityTypeId(), target)) speed = Math.max(speed, rule.speed());
		return speed > 0 ? speed : 1;
	}
	public static void huntingEnded(SlimeBionicEntity entity, LivingEntity target) {
		var rules = BionicGameplayRules.get(entity.level(), BionicTrait.HUNTING_INSTINCT);
		var assembly = entity.getAssembly(); if (assembly == null) return;
		var cooldowns = entity.getPersistentData().getCompound("BionicRelationCooldowns");
		for (int i = 0; i < rules.relations.size(); i++) {
			var rule = rules.relations.get(i);
			if (entity.getBionicTraits().members(BionicTrait.HUNTING_INSTINCT).stream().anyMatch(m ->
				rule.matches(entity, assembly.sources().get(m.source()).profile().entityTypeId(), target)))
				cooldowns.putLong(BionicTrait.HUNTING_INSTINCT.id() + "/" + i, entity.level().getGameTime() + rule.cooldown());
		}
		entity.getPersistentData().put("BionicRelationCooldowns", cooldowns);
	}
}
