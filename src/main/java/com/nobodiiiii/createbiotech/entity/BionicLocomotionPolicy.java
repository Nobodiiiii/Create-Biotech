package com.nobodiiiii.createbiotech.entity;

import com.nobodiiiii.createbiotech.api.bionic.BionicStat;
import com.nobodiiiii.createbiotech.content.surgery.stats.BionicStats;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGait;
import com.nobodiiiii.createbiotech.entity.ai.SlimeBionicMoveControl;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitSet;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;

/** BionicLocomotionPolicy algorithms over the canonical SlimeBionicEntity state. */
final class BionicLocomotionPolicy {
	private BionicLocomotionPolicy() {}

	static boolean hasFlightLocomotion(SlimeBionicEntity operationOwner) {
		return !operationOwner.devourGroundAttack && !operationOwner.isSwimming() && (operationOwner.getBionicTraits().has(BionicTrait.WINGLESS_FLIGHT) || operationOwner.getBionicTraits().has(BionicTrait.WING_FLIGHT));
	}

	static boolean prefersLowFlight(SlimeBionicEntity bionic) {
		return getLocomotionLegCount(bionic) == 0 && hasFlightLocomotion(bionic);
	}

	/** Aquatic speed has its own baseline; land movement retains the anatomical gait calibration. */
	static void refreshMovementSpeed(SlimeBionicEntity operationOwner, SurgicalAssembly assembly) {
		if (operationOwner.level().isClientSide)
			return;
		double speed = operationOwner.organSwimEnabled ? BionicStats.evaluate(
			BionicStat.SWIMMING_SPEED, operationOwner.level(), java.util.Map.of())
			: operationOwner.wildSlimeSize() > 0 ? 0.2f + 0.1f * operationOwner.wildSlimeSize()
			: SurgicalGait.movementSpeed(assembly.bodyBounds(), operationOwner.level());
		var movement = operationOwner.getAttribute(Attributes.MOVEMENT_SPEED);
		if (movement != null && movement.getBaseValue() != speed)
			movement.setBaseValue(speed);
	}

	static void refreshFlyingSpeed(SlimeBionicEntity operationOwner, @Nullable SurgicalAssembly assembly, BionicTraitSet traits) {
		if (operationOwner.level().isClientSide)
			return;
		double speed = assembly == null ? 0.0d : SurgicalGait.flyingSpeed(traits.flightVolumeRatio(assembly), operationOwner.level());
		var flying = operationOwner.getAttribute(Attributes.FLYING_SPEED);
		if (flying != null && flying.getBaseValue() != speed)
			flying.setBaseValue(speed);
	}

	/**
	 * Water touching the outer hitbox is not a stable medium for a large assembled body. Keep the
	 * controller selected by the path's next node while crossing the shoreline, and require a
	 * meaningful water depth before returning to land locomotion.
	 */
	static boolean shouldUseSwimmingLocomotion(SlimeBionicEntity operationOwner, boolean aquatic) {
		if (!aquatic || !operationOwner.isAlive() || operationOwner.isPassenger())
			return false;
		if (!operationOwner.isInWaterOrBubble())
			return false;
		return BionicLocomotionPolicy.isInStableWater(operationOwner) || BionicLocomotionPolicy.pathLeadsThroughWater(operationOwner);
	}

	static @SuppressWarnings("deprecation")
	boolean isInStableWater(SlimeBionicEntity operationOwner) {
		double minimumDepth = operationOwner.organSwimEnabled ? 0.05d : 0.25d;
		return operationOwner.getFluidHeight(FluidTags.WATER) >= minimumDepth
			|| operationOwner.level().getBlockState(operationOwner.blockPosition()).is(Blocks.BUBBLE_COLUMN);
	}

	static boolean pathLeadsThroughWater(SlimeBionicEntity operationOwner) {
		Path currentPath = operationOwner.getNavigation() == null ? null : operationOwner.getNavigation().getPath();
		if (currentPath == null || currentPath.isDone())
			return false;
		PathType nextType = currentPath.getNextNode().type;
		if (nextType == PathType.WATER || nextType == PathType.WATER_BORDER)
			return true;
		BlockPos target = currentPath.getTarget();
		return target != null && operationOwner.level().getFluidState(target).is(FluidTags.WATER);
	}

	static void updateSwimming(SlimeBionicEntity operationOwner) {
		// Axolotl activity is water-based, not dependent on sprinting or submerged eyes.
		// Entity.baseTick calls this after updating fluids and before ticking the AI.
		if (!operationOwner.level().isClientSide)
			operationOwner.refreshLocomotion(operationOwner.getBionicTraits());
	}

	/** Only rest-pose hips touching the ground can drive locomotion or leg animation. */
	static int getLocomotionLegCount(SlimeBionicEntity operationOwner) {
		SurgicalAssembly assembly = operationOwner.getAssembly();
		SurgicalAssembly.BodyBounds bounds = assembly == null ? null : assembly.bodyBounds();
		return bounds == null ? 0 : bounds.groundedLegCount();
	}

	/** Whether the currently installed movement controller makes this body travel by hopping. */
	static boolean usesHopLocomotion(SlimeBionicEntity operationOwner) {
		return operationOwner.getMoveControl() instanceof SlimeBionicMoveControl && BionicLocomotionPolicy.getLocomotionLegCount(operationOwner) < 2;
	}
}
