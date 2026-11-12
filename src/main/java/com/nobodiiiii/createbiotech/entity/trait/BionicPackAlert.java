package com.nobodiiiii.createbiotech.entity.trait;

import java.util.HashSet;
import java.util.Set;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;

/** Reinforcement classes and their display labels share the same donor classification. */
public final class BionicPackAlert {
	private static final ResourceLocation ZOMBIES = CreateBiotech.asResource("zombies");
	private static final ResourceLocation PIGLINS = CreateBiotech.asResource("piglins");
	private static final Set<ResourceLocation> ZOMBIE_VARIANTS = Set.of(
		ResourceLocation.withDefaultNamespace("zombie"), ResourceLocation.withDefaultNamespace("husk"),
		ResourceLocation.withDefaultNamespace("drowned"), ResourceLocation.withDefaultNamespace("zombie_villager"));

	private BionicPackAlert() {}

	public static Group group(Mob donor) {
		boolean piglin = donor instanceof AbstractPiglin;
		ResourceLocation target = piglin ? PIGLINS : donor.getClass() == Zombie.class ? ZOMBIES
			: BuiltInRegistries.ENTITY_TYPE.getKey(donor.getType());
		return new Group(piglin ? AbstractPiglin.class : donor.getClass(),
			donor instanceof Zombie && !(donor instanceof ZombifiedPiglin), target);
	}

	public static Set<ResourceLocation> targets(LivingEntity donor) {
		return donor instanceof Mob mob && !(donor instanceof SlimeBionicEntity)
			? Set.of(group(mob).target()) : Set.of();
	}

	/** A broad reinforcement class already includes its individual vanilla variants. */
	public static Set<ResourceLocation> compactTargets(Set<ResourceLocation> targets) {
		Set<ResourceLocation> result = new HashSet<>(targets);
		if (result.contains(ZOMBIES)) result.removeAll(ZOMBIE_VARIANTS);
		if (result.contains(PIGLINS)) {
			result.remove(ResourceLocation.withDefaultNamespace("piglin"));
			result.remove(ResourceLocation.withDefaultNamespace("piglin_brute"));
		}
		return Set.copyOf(result);
	}

	public record Group(Class<? extends Mob> type, boolean ignoreZombifiedPiglins, ResourceLocation target) {}
}
