package com.nobodiiiii.createbiotech.entity.trait;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Collections;
import java.util.function.Predicate;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.monster.piglin.Piglin;

/** Species avoidance relationships verified against Minecraft 1.21.1 goals and sensors. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
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
	private static final Map<Mob, Boolean> INSTALLED = Collections.synchronizedMap(new WeakHashMap<>());
	private static boolean threatens(SlimeBionicEntity donor, Mob target) {
		return donor.isAlive() && !donor.isNoAi() && !donor.isAlliedTo(target)
			&& donor.getBionicTraits().has(BionicTrait.DETERRENCE)
			&& donor.getBionicTraits().result(BionicTrait.DETERRENCE).deterrenceTargets()
				.contains(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()));
	}
	private static void avoid(PathfinderMob mob, int priority, float range, double walk, double sprint,
		Predicate<SlimeBionicEntity> predicate, boolean clearTarget) {
		mob.goalSelector.addGoal(priority, new AvoidEntityGoal<>(mob, SlimeBionicEntity.class,
			candidate -> candidate instanceof SlimeBionicEntity bionic && threatens(bionic, mob) && predicate.test(bionic),
			range, walk, sprint, candidate -> canFlee(mob)) {
			@Override public void start() { if (clearTarget) mob.setTarget(null); super.start(); }
			@Override public void tick() { if (clearTarget) mob.setTarget(null); super.tick(); }
		});
	}
	@SubscribeEvent public static void joined(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide() || !(event.getEntity() instanceof PathfinderMob mob)
			|| INSTALLED.putIfAbsent(mob, Boolean.TRUE) != null) return;
		if (mob instanceof net.minecraft.world.entity.monster.Creeper
			|| mob instanceof net.minecraft.world.entity.monster.AbstractSkeleton)
			avoid(mob, 3, 6, 1, 1.2, bionic -> true, false);
		else if (mob instanceof Rabbit) avoid(mob, 4, 10, 2.2, 2.2, bionic -> true, false);
		else if (mob instanceof net.minecraft.world.entity.animal.Fox fox)
			avoid(mob, 4, 8, 1.6, 1.4, bionic -> !fox.isDefending()
				&& (bionic.getBionicOwnerId() == null || hasDonor(bionic, "polar_bear")), false);
		else if (mob instanceof Wolf) avoid(mob, 3, 24, 1.5, 1.5, bionic -> {
			var assembly = bionic.getAssembly();
			return assembly != null && bionic.getBionicTraits().members(BionicTrait.DETERRENCE).stream().map(arm -> assembly.sources().get(arm.source()))
				.filter(source -> source.profile().entityTypeId().getPath().equals("llama") || source.profile().entityTypeId().getPath().equals("trader_llama"))
				.mapToInt(source -> Math.clamp(source.profile().stableInt("Strength"), 1, 5)).max().orElse(0) >= mob.getRandom().nextInt(5);
		}, true);
		else if (mob instanceof net.minecraft.world.entity.monster.Spider)
			avoid(mob, 2, 6, 1, 1.2, bionic -> !bionic.isShiftKeyDown(), false);
		else if (mob instanceof net.minecraft.world.entity.animal.Dolphin)
			avoid(mob, 9, 8, 1, 1, bionic -> true, false);
		else if (mob instanceof net.minecraft.world.entity.npc.WanderingTrader) {
			for (float radius : new float[]{8, 10, 12, 15})
				avoid(mob, 1, radius, 0.5, 0.5, bionic -> threatRange(bionic, false) == radius, false);
		}
	}
	/** Villagers and piglins consume these threats through their own panic/avoid Brain activities. */
	@SubscribeEvent public static void tick(EntityTickEvent.Post event) {
		if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide || mob.isNoAi() || mob.tickCount % 20 != 0) return;
		if (!(mob instanceof Villager) && !(mob instanceof Piglin) && !(mob instanceof net.minecraft.world.entity.monster.Phantom)) return;
		var threat = mob.level().getEntitiesOfClass(SlimeBionicEntity.class, mob.getBoundingBox().inflate(16),
			candidate -> threatens(candidate, mob) && mob.hasLineOfSight(candidate)
				&& mob.distanceToSqr(candidate) <= Math.pow(mob instanceof Villager ? threatRange(candidate, true) : 16, 2))
			.stream().min(java.util.Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
		if (threat == null) return;
		if (mob instanceof Villager) {
			if (mob.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE).filter(nativeThreat ->
				mob.distanceToSqr(nativeThreat) <= mob.distanceToSqr(threat)).isEmpty())
				mob.getBrain().setMemoryWithExpiry(MemoryModuleType.NEAREST_HOSTILE, threat, 20);
		} else if (mob instanceof Piglin) {
			if (mob.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_ZOMBIFIED).filter(nativeThreat ->
				mob.distanceToSqr(nativeThreat) <= mob.distanceToSqr(threat)).isEmpty())
				mob.getBrain().setMemoryWithExpiry(MemoryModuleType.NEAREST_VISIBLE_ZOMBIFIED, threat, 20);
		}
		else mob.goalSelector.getAvailableGoals().stream().filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning)
			.filter(goal -> goal.getGoal().getClass().getSimpleName().equals("PhantomSweepAttackGoal"))
			.toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
	}
	private static boolean hasDonor(SlimeBionicEntity donor, String type) {
		var assembly = donor.getAssembly();
		return assembly != null && donor.getBionicTraits().members(BionicTrait.DETERRENCE).stream()
			.anyMatch(member -> assembly.sources().get(member.source()).profile().entityTypeId().getPath().equals(type));
	}
	private static float threatRange(SlimeBionicEntity donor, boolean villager) {
		var assembly = donor.getAssembly();
		if (assembly == null) return 0;
		float maximum = 0;
		for (var member : donor.getBionicTraits().members(BionicTrait.DETERRENCE)) {
			String type = assembly.sources().get(member.source()).profile().entityTypeId().getPath();
			float radius = switch (type) {
			case "pillager" -> 15;
			case "evoker", "illusioner", "ravager" -> 12;
			case "zoglin" -> 10;
			case "vindicator" -> villager ? 10 : 8;
			case "drowned", "husk", "vex", "zombie", "zombie_villager", "zombified_piglin" -> 8;
			default -> 0;
			};
			maximum = Math.max(maximum, radius);
		}
		return maximum;
	}
}
