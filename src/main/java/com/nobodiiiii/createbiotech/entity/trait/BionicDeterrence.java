package com.nobodiiiii.createbiotech.entity.trait;

import java.util.Set;
import java.util.HashSet;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.phys.Vec3;

/** Species avoidance relationships verified against Minecraft 1.21.1 goals and sensors. */
public final class BionicDeterrence {
	private BionicDeterrence() {}
	private static final Set<EntityType<?>> VILLAGER_THREATS = Set.of(EntityType.DROWNED, EntityType.EVOKER,
		EntityType.HUSK, EntityType.ILLUSIONER, EntityType.PILLAGER, EntityType.RAVAGER, EntityType.VEX,
		EntityType.VINDICATOR, EntityType.ZOGLIN, EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER);
	private static final Set<EntityType<?>> TRADER_THREATS = Set.of(EntityType.DROWNED, EntityType.EVOKER,
		EntityType.HUSK, EntityType.ILLUSIONER, EntityType.PILLAGER, EntityType.VEX,
		EntityType.VINDICATOR, EntityType.ZOGLIN, EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER,
		EntityType.ZOMBIFIED_PIGLIN);

	public static Set<EntityType<?>> targets(EntityType<?> donor) {
		Set<EntityType<?>> result = new HashSet<>();
		if (donor == EntityType.CAT || donor == EntityType.OCELOT) result.add(EntityType.CREEPER);
		if (donor == EntityType.CAT || donor == EntityType.OCELOT) result.add(EntityType.PHANTOM);
		if (donor == EntityType.WOLF) result.addAll(Set.of(EntityType.SKELETON, EntityType.STRAY,
			EntityType.WITHER_SKELETON, EntityType.BOGGED, EntityType.RABBIT, EntityType.FOX));
		if (donor == EntityType.POLAR_BEAR) result.add(EntityType.FOX);
		if (donor == EntityType.LLAMA || donor == EntityType.TRADER_LLAMA) result.add(EntityType.WOLF);
		if (donor == EntityType.GUARDIAN || donor == EntityType.ELDER_GUARDIAN) result.add(EntityType.DOLPHIN);
		if (donor == EntityType.ARMADILLO) result.addAll(Set.of(EntityType.SPIDER, EntityType.CAVE_SPIDER));
		if (donor == EntityType.ZOMBIFIED_PIGLIN || donor == EntityType.ZOGLIN) result.add(EntityType.PIGLIN);
		if (VILLAGER_THREATS.contains(donor)) result.add(EntityType.VILLAGER);
		if (TRADER_THREATS.contains(donor)) result.add(EntityType.WANDERING_TRADER);
		return Set.copyOf(result);
	}
	public static boolean canFlee(Mob mob) {
		return !(mob instanceof Wolf wolf && wolf.isTame())
			&& !(mob instanceof Rabbit rabbit && rabbit.getVariant() == Rabbit.Variant.EVIL);
	}
	public static void flee(Mob mob, LivingEntity threat, Vec3 destination) {
		var brain = mob.getBrain();
		if (mob instanceof Villager) brain.setMemoryWithExpiry(MemoryModuleType.NEAREST_HOSTILE, threat, 20);
		if (mob instanceof Piglin) brain.setMemoryWithExpiry(MemoryModuleType.AVOID_TARGET, threat, 20);
		if (brain.checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED))
			brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(destination, 1.2f, 1));
		mob.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.2d);
	}
}
