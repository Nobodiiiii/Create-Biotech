package com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod;

import java.util.EnumSet;
import java.util.UUID;
import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class FixedCarrotFishingRodTemptGoal extends Goal {

	private static final double ITEM_XZ_OFFSET = 6.5 / 16.0;

	private final Animal animal;
	private int calmDown;
	private int searchCooldown;
	@Nullable
	private BlockPos rodPos;
	@Nullable
	private ItemStack cachedBait;

	public FixedCarrotFishingRodTemptGoal(Animal animal) {
		this.animal = animal;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (calmDown > 0) {
			calmDown--;
			return false;
		}
		if (searchCooldown > 0) {
			searchCooldown--;
			return false;
		}

		searchCooldown = adjustedTickDelay(getSearchCooldown());
		if (!animal.isAlive() || animal.isNoAi() || animal.isVehicle())
			return false;

		rodPos = findNearestRodWithMatchingBait();
		return rodPos != null;
	}

	@Override
	public boolean canContinueToUse() {
		return animal.isAlive() && !animal.isNoAi() && !animal.isVehicle()
			&& rodPos != null && cachedBait != null && !cachedBait.isEmpty()
			&& getBaitPosition(rodPos) != null;
	}

	@Override
	public void stop() {
		rodPos = null;
		cachedBait = null;
		animal.getNavigation().stop();
		calmDown = adjustedTickDelay(getStopCooldown());
	}

	@Override
	public void tick() {
		Vec3 baitPosition = getBaitPosition(rodPos);
		if (baitPosition == null)
			return;

		awardIfAnimalReachedPowerBelt();

		animal.getLookControl()
			.setLookAt(baitPosition.x, baitPosition.y, baitPosition.z,
				(float) (animal.getMaxHeadYRot() + 20), (float) animal.getMaxHeadXRot());

		if (animal.distanceToSqr(baitPosition) < getStopDistanceSqr()) {
			animal.getNavigation().stop();
			return;
		}

		animal.getNavigation().moveTo(baitPosition.x, baitPosition.y, baitPosition.z, getSpeedModifier());
	}

	@Nullable
	private BlockPos findNearestRodWithMatchingBait() {
		Level level = animal.level();
		BlockPos animalPos = animal.blockPosition();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		BlockPos nearest = null;
		ItemStack nearestBait = null;
		double nearestDistance = Double.MAX_VALUE;
		int searchRange = getSearchBlockRange();
		double searchRangeSqr = getSearchRangeSqr();

		for (int x = animalPos.getX() - searchRange; x <= animalPos.getX() + searchRange; x++) {
			for (int y = animalPos.getY() - searchRange; y <= animalPos.getY() + searchRange; y++) {
				for (int z = animalPos.getZ() - searchRange; z <= animalPos.getZ() + searchRange; z++) {
					cursor.set(x, y, z);
					BlockState state = level.getBlockState(cursor);
					if (!state.is(CBBlocks.FIXED_CARROT_FISHING_ROD.get()))
						continue;

					BlockEntity blockEntity = level.getBlockEntity(cursor);
					if (!(blockEntity instanceof FixedCarrotFishingRodBlockEntity rodEntity))
						continue;

					ItemStack baitItem = rodEntity.getBaitItem();
					if (baitItem.isEmpty() || !animal.isFood(baitItem))
						continue;

					Vec3 baitPosition = getBaitPosition(cursor.immutable(), state);
					double distance = animal.distanceToSqr(baitPosition);
					if (distance > searchRangeSqr || distance >= nearestDistance)
						continue;

					nearestDistance = distance;
					nearest = cursor.immutable();
					nearestBait = baitItem.copy();
				}
			}
		}

		cachedBait = nearestBait;
		return nearest;
	}

	@Nullable
	private Vec3 getBaitPosition(@Nullable BlockPos pos) {
		if (pos == null)
			return null;

		BlockState state = animal.level().getBlockState(pos);
		if (!state.is(CBBlocks.FIXED_CARROT_FISHING_ROD.get()))
			return null;

		BlockEntity blockEntity = animal.level().getBlockEntity(pos);
		if (!(blockEntity instanceof FixedCarrotFishingRodBlockEntity rodEntity))
			return null;

		if (rodEntity.getBaitItem().isEmpty())
			return null;

		Vec3 baitPosition = getBaitPosition(pos, state);
		return animal.distanceToSqr(baitPosition) <= getSearchRangeSqr() ? baitPosition : null;
	}

	private static double getSpeedModifier() {
		return CBConfigs.COMMON.fixedCarrotFishingRod.speedModifier.get();
	}

	private static double getSearchRange() {
		return CBConfigs.COMMON.fixedCarrotFishingRod.searchRange.get();
	}

	private static int getSearchBlockRange() {
		return Mth.ceil(getSearchRange());
	}

	private static double getSearchRangeSqr() {
		double searchRange = getSearchRange();
		return searchRange * searchRange;
	}

	private static double getStopDistanceSqr() {
		double stopDistance = CBConfigs.COMMON.fixedCarrotFishingRod.stopDistance.get();
		return stopDistance * stopDistance;
	}

	private static int getSearchCooldown() {
		return CBConfigs.COMMON.fixedCarrotFishingRod.searchCooldown.get();
	}

	private static int getStopCooldown() {
		return CBConfigs.COMMON.fixedCarrotFishingRod.stopCooldown.get();
	}

	private static Vec3 getBaitPosition(BlockPos pos, BlockState state) {
		Direction facing = state.getValue(FixedCarrotFishingRodBlock.FACING);
		return new Vec3(
			pos.getX() + 0.5 + facing.getStepX() * ITEM_XZ_OFFSET,
			pos.getY(),
			pos.getZ() + 0.5 + facing.getStepZ() * ITEM_XZ_OFFSET);
	}

	private void awardIfAnimalReachedPowerBelt() {
		if (!(animal.level() instanceof ServerLevel serverLevel) || rodPos == null)
			return;
		if (!isOnPowerBelt(animal.blockPosition()) && !isOnPowerBelt(animal.blockPosition().below()))
			return;
		if (!(serverLevel.getBlockEntity(rodPos) instanceof FixedCarrotFishingRodBlockEntity rodEntity))
			return;

		UUID owner = rodEntity.getAdvancementOwner();
		if (owner != null)
			CBAdvancements.awardPlayer(serverLevel, owner, CBAdvancements.VOLUNTARY_OVERTIME);
	}

	private boolean isOnPowerBelt(BlockPos pos) {
		return animal.level().getBlockState(pos)
			.is(CBBlocks.POWER_BELT.get());
	}
}
