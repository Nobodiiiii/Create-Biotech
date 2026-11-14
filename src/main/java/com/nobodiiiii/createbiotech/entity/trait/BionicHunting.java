package com.nobodiiiii.createbiotech.entity.trait;

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;

/** Species relations from the 1.21.1 vanilla target goals/sensors, independent of disposition. */
public final class BionicHunting {
	private static final Map<String, Set<String>> PREY = relations();
	private final SlimeBionicEntity mob;
	private LivingEntity previousTarget;
	private boolean axolotlHunt;
	private boolean hoglinHunt;
	private int axolotlCooldown;
	private int hoglinCooldown;

	public BionicHunting(SlimeBionicEntity mob) { this.mob = mob; }

	private static Map<String, Set<String>> relations() {
		Map<String, Set<String>> result = new HashMap<>();
		put(result, "wolf", "sheep rabbit fox turtle skeleton stray wither_skeleton bogged");
		put(result, "cat", "rabbit turtle");
		put(result, "ocelot", "chicken turtle");
		put(result, "fox", "chicken rabbit turtle cod salmon tropical_fish");
		put(result, "polar_bear", "fox");
		put(result, "axolotl", "drowned guardian elder_guardian tropical_fish pufferfish salmon cod squid glow_squid tadpole");
		put(result, "iron_golem snow_golem", "hostiles");
		put(result, "guardian elder_guardian", "squid glow_squid axolotl");
		put(result, "enderman", "endermite");
		put(result, "spider cave_spider slime magma_cube breeze", "iron_golem");
		put(result, "piglin", "wither_skeleton wither hoglin");
		put(result, "piglin_brute", "wither_skeleton wither");
		put(result, "zombie husk zombie_villager", "villager wandering_trader iron_golem turtle");
		put(result, "drowned", "villager wandering_trader iron_golem axolotl turtle");
		put(result, "skeleton stray bogged", "iron_golem turtle");
		put(result, "wither_skeleton", "iron_golem turtle piglin piglin_brute");
		put(result, "vindicator pillager evoker illusioner", "villager wandering_trader iron_golem");
		put(result, "zoglin", "attackable_creatures");
		put(result, "wither", "non_wither_friends");
		put(result, "llama trader_llama", "wolf");
		return Map.copyOf(result);
	}

	private static void put(Map<String, Set<String>> table, String donors, String prey) {
		for (String donor : donors.split(" ")) table.put(donor, Set.of(prey.split(" ")));
	}

	public static boolean hasRelations(EntityType<?> type) {
		ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		return id.getNamespace().equals("minecraft") && PREY.containsKey(id.getPath());
	}

	public static Set<ResourceLocation> targets(Set<ResourceLocation> donors) {
		Set<ResourceLocation> result = new HashSet<>();
		for (var donor : donors)
			if (donor.getNamespace().equals("minecraft"))
				for (String prey : PREY.getOrDefault(donor.getPath(), Set.of()))
					result.add(ResourceLocation.withDefaultNamespace(prey));
		return Set.copyOf(result);
	}

	public boolean matches(LivingEntity target) {
		if (!mob.getBionicTraits().has(BionicTrait.HUNTING_INSTINCT) || target == mob
			|| target instanceof Player || !target.isAlive() || mob.isAlliedTo(target)) return false;
		if (BionicGameplayRelations.matches(mob, BionicTrait.HUNTING_INSTINCT, target)) return true;
		if (BionicGameplayRules.get(mob.level(), BionicTrait.HUNTING_INSTINCT).replaceRelations) return false;
		for (ResourceLocation donor : mob.getBionicTraits().result(BionicTrait.HUNTING_INSTINCT).sourceTypes())
			if (donor.getNamespace().equals("minecraft") && matches(donor.getPath(), target)) return true;
		return false;
	}

	@SuppressWarnings("deprecation")
	private boolean matches(String donor, LivingEntity target) {
		ResourceLocation prey = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
		boolean special = switch (donor) {
		case "iron_golem" -> target instanceof Enemy && target.getType() != EntityType.CREEPER;
		case "snow_golem" -> target instanceof Enemy;
		case "zoglin" -> target.getType() != EntityType.ZOGLIN && target.getType() != EntityType.CREEPER;
		case "wither" -> !target.getType().is(EntityTypeTags.WITHER_FRIENDS);
		default -> false;
		};
		if (special) return true;
		if (!prey.getNamespace().equals("minecraft") || !PREY.getOrDefault(donor, Set.of()).contains(prey.getPath()))
			return false;
		if (target.getType() == EntityType.TURTLE && (!target.isBaby() || target.isInWater())) return false;
		return switch (donor) {
		case "wolf" -> mob.getBionicOwnerId() == null || target.getType().is(EntityTypeTags.SKELETONS);
		case "cat" -> mob.getBionicOwnerId() == null;
		case "axolotl" -> target.isInWaterOrBubble() && mob.distanceToSqr(target) <= 64
			&& (target.getType().is(EntityTypeTags.AXOLOTL_ALWAYS_HOSTILES) || axolotlCooldown == 0);
		case "guardian", "elder_guardian" -> mob.distanceToSqr(target) > 9;
		case "spider", "cave_spider" -> mob.getLightLevelDependentMagicValue() < 0.5f;
		case "piglin" -> target.getType() != EntityType.HOGLIN || !target.isBaby() && hoglinCooldown == 0;
		case "llama", "trader_llama" -> target instanceof Wolf wolf && !wolf.isTame() && mob.distanceToSqr(target) <= 100;
		default -> true;
		};
	}

	public void tick() {
		if (axolotlCooldown > 0) axolotlCooldown--;
		if (hoglinCooldown > 0) hoglinCooldown--;
		LivingEntity target = mob.getTarget();
		if (previousTarget != null && (target != previousTarget || !previousTarget.isAlive())) {
			BionicGameplayRelations.huntingEnded(mob, previousTarget);
			if (axolotlHunt) axolotlCooldown = 2400;
			if (hoglinHunt) hoglinCooldown = 600 + mob.getRandom().nextInt(1801);
			axolotlHunt = hoglinHunt = false;
		}
		previousTarget = target != null && target.isAlive() ? target : null;
		if (previousTarget != null && matches(previousTarget)) {
			var donors = mob.getBionicTraits().result(BionicTrait.HUNTING_INSTINCT).sourceTypes();
			axolotlHunt |= donors.contains(ResourceLocation.withDefaultNamespace("axolotl"))
				&& target.getType().is(EntityTypeTags.AXOLOTL_HUNT_TARGETS);
			hoglinHunt |= donors.contains(ResourceLocation.withDefaultNamespace("piglin")) && target.getType() == EntityType.HOGLIN;
		}
	}

	public void save(CompoundTag tag) {
		tag.putInt("BionicAxolotlHuntCooldown", axolotlHunt ? 2400 : axolotlCooldown);
		tag.putInt("BionicHoglinHuntCooldown", hoglinHunt ? 2400 : hoglinCooldown);
	}
	public void load(CompoundTag tag) {
		axolotlCooldown = Math.clamp(tag.getInt("BionicAxolotlHuntCooldown"), 0, 2400);
		hoglinCooldown = Math.clamp(tag.getInt("BionicHoglinHuntCooldown"), 0, 2400);
		previousTarget = null;
		axolotlHunt = hoglinHunt = false;
	}
}
