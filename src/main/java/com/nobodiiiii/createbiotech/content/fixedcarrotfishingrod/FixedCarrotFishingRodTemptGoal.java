package com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod;

import java.util.EnumSet;
import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.registry.CBBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class FixedCarrotFishingRodTemptGoal extends Goal {

	private static final ItemStack CARROT = new ItemStack(Items.CARROT);
	private static final ItemStack CARROT_ON_A_STICK = new ItemStack(Items.CARROT_ON_A_STICK);
	private static final double SPEED_MODIFIER = 1.2;
	private static final int SEARCH_RANGE = 10;
	private static final double SEARCH_RANGE_SQR = SEARCH_RANGE * SEARCH_RANGE;
	private static final double STOP_DISTANCE_SQR = 2.5 * 2.5;
	private static final double CARROT_XZ_OFFSET = 6.5 / 16.0;

	private final Animal animal;
	private int calmDown;
	private int searchCooldown;
	@Nullable
	private BlockPos rodPos;

	public FixedCarrotFishingRodTemptGoal(Animal animal) {
		this.animal = animal;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	public static boolean isTemptedByCarrot(Animal animal) {
		return animal.isFood(CARROT) || animal.isFood(CARROT_ON_A_STICK) || animal instanceof Pig;
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
		if (!canTemptNow())
			return false;

		rodPos = findNearestRod();
		return rodPos != null;
	}

	@Override
	public boolean canContinueToUse() {
		return canTemptNow() && getCarrotPosition(rodPos) != null;
	}

	@Override
	public void stop() {
		rodPos = null;
		animal.getNavigation().stop();
		calmDown = adjustedTickDelay(100);
	}

	@Override
	public void tick() {
		Vec3 carrotPosition = getCarrotPosition(rodPos);
		if (carrotPosition == null)
			return;

		animal.getLookControl()
			.setLookAt(carrotPosition.x, carrotPosition.y, carrotPosition.z,
				(float) (animal.getMaxHeadYRot() + 20), (float) animal.getMaxHeadXRot());

		if (animal.distanceToSqr(carrotPosition) < STOP_DISTANCE_SQR) {
			animal.getNavigation().stop();
			return;
		}

		animal.getNavigation().moveTo(carrotPosition.x, carrotPosition.y, carrotPosition.z, SPEED_MODIFIER);
	}

	private boolean canTemptNow() {
		return animal.isAlive()
			&& !animal.isNoAi()
			&& !animal.isVehicle()
			&& isTemptedByCarrot(animal);
	}

	@Nullable
	private BlockPos findNearestRod() {
		Level level = animal.level();
		BlockPos animalPos = animal.blockPosition();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		BlockPos nearest = null;
		double nearestDistance = Double.MAX_VALUE;

		for (int x = animalPos.getX() - SEARCH_RANGE; x <= animalPos.getX() + SEARCH_RANGE; x++) {
			for (int y = animalPos.getY() - SEARCH_RANGE; y <= animalPos.getY() + SEARCH_RANGE; y++) {
				for (int z = animalPos.getZ() - SEARCH_RANGE; z <= animalPos.getZ() + SEARCH_RANGE; z++) {
					cursor.set(x, y, z);
					BlockState state = level.getBlockState(cursor);
					if (!state.is(CBBlocks.FIXED_CARROT_FISHING_ROD.get()))
						continue;

					Vec3 carrotPosition = getCarrotPosition(cursor.immutable(), state);
					double distance = animal.distanceToSqr(carrotPosition);
					if (distance > SEARCH_RANGE_SQR || distance >= nearestDistance)
						continue;

					nearestDistance = distance;
					nearest = cursor.immutable();
				}
			}
		}

		return nearest;
	}

	@Nullable
	private Vec3 getCarrotPosition(@Nullable BlockPos pos) {
		if (pos == null)
			return null;

		BlockState state = animal.level().getBlockState(pos);
		if (!state.is(CBBlocks.FIXED_CARROT_FISHING_ROD.get()))
			return null;

		Vec3 carrotPosition = getCarrotPosition(pos, state);
		return animal.distanceToSqr(carrotPosition) <= SEARCH_RANGE_SQR ? carrotPosition : null;
	}

	private static Vec3 getCarrotPosition(BlockPos pos, BlockState state) {
		Direction facing = state.getValue(FixedCarrotFishingRodBlock.FACING);
		return new Vec3(
			pos.getX() + 0.5 + facing.getStepX() * CARROT_XZ_OFFSET,
			pos.getY(),
			pos.getZ() + 0.5 + facing.getStepZ() * CARROT_XZ_OFFSET);
	}
}
