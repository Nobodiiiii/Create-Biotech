package com.nobodiiiii.createbiotech.entity.ai;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;

/** WallClimberNavigation's direct pursuit, shared by ground and amphibious navigation. */
public final class BionicWallClimbing {
	private final Mob mob;
	private final PathNavigation navigation;
	@Nullable
	private BlockPos pathToPosition;
	private boolean hasMovementCommand;

	public BionicWallClimbing(Mob mob, PathNavigation navigation) {
		this.mob = mob;
		this.navigation = navigation;
	}

	public boolean canUse() {
		return mob instanceof SlimeBionicEntity bionic && bionic.canUseWallClimbing();
	}

	public static boolean isInProgress(PathNavigation navigation) {
		return navigation instanceof SlimeBionicGroundNavigation ground && ground.hasWallClimbingTarget()
			|| navigation instanceof BionicAmphibiousNavigation amphibious && amphibious.hasWallClimbingTarget();
	}

	public boolean hasTarget() {
		return pathToPosition != null && canUse();
	}

	public void setPath(@Nullable Path path) {
		// Path creation is also used to probe destinations. Only an actual move request
		// may replace the active climbing target.
		if (path != null && canUse()) {
			pathToPosition = path.getTarget();
			hasMovementCommand = true;
		} else
			stop();
	}

	public boolean moveTo(Entity target, double speed) {
		return moveTo(target.blockPosition(), navigation.createPath(target, 0), speed);
	}

	public boolean moveTo(BlockPos target, int accuracy, double speed) {
		return moveTo(target, navigation.createPath(target, accuracy), speed);
	}

	private boolean moveTo(BlockPos target, @Nullable Path path, double speed) {
		// WallClimberNavigation does not submit a null path: that would cancel
		// the airborne movement command before direct pursuit gets a chance to run.
		if (path != null)
			navigation.moveTo(path, speed);
		pathToPosition = target.immutable();
		hasMovementCommand = true;
		navigation.setSpeedModifier(speed);
		// Like WallClimberNavigation.moveTo(Entity), a missing ground path still
		// leaves a usable direct pursuit command.
		return true;
	}

	public void stop() {
		if (hasMovementCommand) {
			// MoveControl may stay in JUMPING until landing. Clear its speed as well
			// so cancelling a pursuit also stops an airborne climber's old command.
			mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.0d);
			mob.setSpeed(0.0f);
			mob.setXxa(0.0f);
			mob.setZza(0.0f);
		}
		pathToPosition = null;
		hasMovementCommand = false;
	}

	public void tick(double speed) {
		if (!canUse()) {
			stop();
			return;
		}
		if (pathToPosition == null)
			return;
		// Match vanilla's arrival checks, including targets below the climber.
		if (pathToPosition.closerToCenterThan(mob.position(), mob.getBbWidth())
			|| mob.getY() > pathToPosition.getY()
				&& BlockPos.containing(pathToPosition.getX(), mob.getY(), pathToPosition.getZ())
					.closerToCenterThan(mob.position(), mob.getBbWidth())) {
			// Vanilla only forgets the destination here. In particular, JUMPING
			// retains its speed until landing and can keep pressing into the wall.
			// Explicit cancellation still clears that command through stop().
			pathToPosition = null;
			return;
		}
		mob.getMoveControl().setWantedPosition(pathToPosition.getX(), pathToPosition.getY(),
			pathToPosition.getZ(), speed);
	}
}
