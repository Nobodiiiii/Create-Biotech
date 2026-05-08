package com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod;

import java.util.EnumSet;
import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.registry.CBBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class FixedCarrotFishingRodTemptGoal extends Goal {

	private static final double SPEED_MODIFIER = 1.2;
	private static final int SEARCH_RANGE = 10;
	private static final double SEARCH_RANGE_SQR = SEARCH_RANGE * SEARCH_RANGE;
	private static final double STOP_DISTANCE_SQR = 2.5 * 2.5;
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

		searchCooldown = adjustedTickDelay(20);
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
		calmDown = adjustedTickDelay(100);
	}

	@Override
	public void tick() {
		Vec3 baitPosition = getBaitPosition(rodPos);
		if (baitPosition == null)
			return;

		animal.getLookControl()
			.setLookAt(baitPosition.x, baitPosition.y, baitPosition.z,
				(float) (animal.getMaxHeadYRot() + 20), (float) animal.getMaxHeadXRot());

		if (animal.distanceToSqr(baitPosition) < STOP_DISTANCE_SQR) {
			animal.getNavigation().stop();
			return;
		}

		animal.getNavigation().moveTo(baitPosition.x, baitPosition.y, baitPosition.z, SPEED_MODIFIER);
	}

	@Nullable
	private BlockPos findNearestRodWithMatchingBait() {
		Level level = animal.level();
		BlockPos animalPos = animal.blockPosition();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		BlockPos nearest = null;
		ItemStack nearestBait = null;
		double nearestDistance = Double.MAX_VALUE;

		for (int x = animalPos.getX() - SEARCH_RANGE; x <= animalPos.getX() + SEARCH_RANGE; x++) {
			for (int y = animalPos.getY() - SEARCH_RANGE; y <= animalPos.getY() + SEARCH_RANGE; y++) {
				for (int z = animalPos.getZ() - SEARCH_RANGE; z <= animalPos.getZ() + SEARCH_RANGE; z++) {
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
					if (distance > SEARCH_RANGE_SQR || distance >= nearestDistance)
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
		return animal.distanceToSqr(baitPosition) <= SEARCH_RANGE_SQR ? baitPosition : null;
	}

	private static Vec3 getBaitPosition(BlockPos pos, BlockState state) {
		Direction facing = state.getValue(FixedCarrotFishingRodBlock.FACING);
		return new Vec3(
			pos.getX() + 0.5 + facing.getStepX() * ITEM_XZ_OFFSET,
			pos.getY(),
			pos.getZ() + 0.5 + facing.getStepZ() * ITEM_XZ_OFFSET);
	}
}
