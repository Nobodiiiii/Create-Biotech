package com.nobodiiiii.createbiotech.entity.ai;

import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;

/** Adapts LandOnOwnersShoulderGoal and ShoulderRidingEntity to a preserved surgical assembly. */
public final class BionicShoulderRidingGoal extends Goal {
	private final SlimeBionicEntity entity;

	public BionicShoulderRidingGoal(SlimeBionicEntity entity) {
		this.entity = entity;
	}

	@Override
	public boolean canUse() {
		return entity.tickCount > 100 && entity.isAlive() && !entity.isOrderedToSit()
			&& !entity.isLeashed() && !entity.isPassenger() && !entity.isVehicle()
			&& entity.getBionicTraits().has(BionicTrait.SHOULDER_RIDING)
			&& entity.getBionicOwner() instanceof ServerPlayer owner && owner.isAlive()
			&& !owner.isSpectator() && !owner.getAbilities().flying
			&& !owner.isPassenger() && owner.onGround() && !owner.isInWater() && !owner.isInPowderSnow
			&& (owner.getShoulderEntityLeft().isEmpty() || owner.getShoulderEntityRight().isEmpty());
	}

	@Override
	public boolean requiresUpdateEveryTick() { return true; }

	@Override
	public void tick() {
		if (!canUse() || !(entity.getBionicOwner() instanceof ServerPlayer owner)
			|| !entity.getBoundingBox().intersects(owner.getBoundingBox()))
			return;
		CompoundTag saved = new CompoundTag();
		saved.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
		entity.saveWithoutId(saved);
		// The player stores the complete entity, synchronizes both shoulders and restores it on dismount.
		if (owner.setEntityOnShoulder(saved))
			entity.discard();
	}
}
