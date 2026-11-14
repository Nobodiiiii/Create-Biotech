package com.nobodiiiii.createbiotech.entity.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.trait.BionicPackAlert;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitSet;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.phys.AABB;

/** Vanilla retaliation timing, with the retained donors' reinforcement species. */
public final class BionicPackAlertGoal extends HurtByTargetGoal {
	private final SlimeBionicEntity bionic;
	private BionicTraitSet resolvedTraits;
	private List<BionicPackAlert.Group> groups = List.of();

	public BionicPackAlertGoal(SlimeBionicEntity bionic) {
		super(bionic);
		this.bionic = bionic;
		setAlertOthers();
	}

	@Override public boolean canUse() {
		return bionic.getDisposition() != BionicDisposition.FRIENDLY && super.canUse();
	}

	@Override protected void alertOthers() {
		BionicTraitSet traits = bionic.getBionicTraits();
		LivingEntity attacker = bionic.getLastHurtByMob();
		if (!traits.has(BionicTrait.PACK_ALERT) || attacker == null) return;
		if (resolvedTraits != traits) resolveGroups(traits);
		double range = getFollowDistance();
		AABB area = AABB.unitCubeFromLowerCorner(bionic.position()).inflate(range, 10.0d, range);
		var customRules = com.nobodiiiii.createbiotech.entity.trait.BionicGameplayRules.get(bionic.level(), BionicTrait.PACK_ALERT);
		double customRange = customRules.relations().stream().mapToDouble(r -> r.range()).max().orElse(0);
		if (customRange > 0) for (Mob other : bionic.level().getEntitiesOfClass(Mob.class, bionic.getBoundingBox().inflate(customRange), EntitySelector.NO_SPECTATORS)) {
			if (other == bionic || other.getTarget() != null || !other.canAttack(attacker) || other.isAlliedTo(attacker)) continue;
			if (other instanceof TamableAnimal pet && (pet.isOrderedToSit() || !Objects.equals(bionic.getBionicOwnerId(), pet.getOwnerUUID()))) continue;
			if (com.nobodiiiii.createbiotech.entity.trait.BionicGameplayRelations.matches(bionic, BionicTrait.PACK_ALERT, other)) alertOther(other, attacker);
		}
		if (com.nobodiiiii.createbiotech.entity.trait.BionicGameplayRules.get(bionic.level(), BionicTrait.PACK_ALERT).replacesRelations()) return;
		for (BionicPackAlert.Group group : groups)
			for (Mob other : bionic.level().getEntitiesOfClass(group.type(), area, EntitySelector.NO_SPECTATORS)) {
				if (other instanceof SlimeBionicEntity || other.getTarget() != null || !other.canAttack(attacker)
					|| other.isAlliedTo(attacker)) continue;
				// Zombie's base-class search includes its variants, except zombified piglins.
				if (group.ignoreZombifiedPiglins() && other.getClass() == ZombifiedPiglin.class) continue;
				if (other instanceof TamableAnimal pet && (pet.isOrderedToSit()
					|| !Objects.equals(bionic.getBionicOwnerId(), pet.getOwnerUUID()))) continue;
				if (other instanceof Bee && !bionic.hasLineOfSight(attacker)) continue;
				alertOther(other, attacker);
			}
	}

	/** Only active, correctly installed trait carriers contribute a reinforcement group. */
	private void resolveGroups(BionicTraitSet traits) {
		var assembly = bionic.getAssembly();
		List<BionicPackAlert.Group> result = new ArrayList<>();
		if (assembly != null) {
			var donors = new HashSet<EntityType<?>>();
			for (var member : traits.members(BionicTrait.PACK_ALERT)) {
				var id = assembly.sources().get(member.source()).profile().entityTypeId();
				BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(donors::add);
			}
			for (EntityType<?> type : donors)
				if (type.create(bionic.level()) instanceof Mob donor && !(donor instanceof SlimeBionicEntity))
					result.add(BionicPackAlert.group(donor));
		}
		groups = List.copyOf(result);
		resolvedTraits = traits;
	}

	@Override protected void alertOther(Mob other, LivingEntity attacker) {
		// Brain-driven donors do not keep a target assigned only through Mob.setTarget.
		if (other instanceof AbstractPiglin piglin) {
			if (piglin.isBaby()) return;
			var brain = piglin.getBrain();
			if (brain.hasMemoryValue(MemoryModuleType.ANGRY_AT)
				|| brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) return;
			brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
			brain.setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, attacker.getUUID(), 600L);
		} else if (other instanceof Hoglin hoglin) {
			var brain = hoglin.getBrain();
			if (hoglin.isBaby() || !bionic.hasLineOfSight(hoglin)
				|| brain.hasMemoryValue(MemoryModuleType.PACIFIED)
				|| brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) return;
			brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
			brain.eraseMemory(MemoryModuleType.BREED_TARGET);
			brain.setMemoryWithExpiry(MemoryModuleType.ATTACK_TARGET, attacker, 200L);
		}
		super.alertOther(other, attacker);
	}
}
