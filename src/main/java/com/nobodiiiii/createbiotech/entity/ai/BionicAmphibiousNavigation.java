package com.nobodiiiii.createbiotech.entity.ai;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.AmphibiousNodeEvaluator;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;

/** Retain strong-jump paths and wall-climbing pursuit for bodies that also have swimming tissue. */
public final class BionicAmphibiousNavigation extends AmphibiousPathNavigation {
	private final BionicWallClimbing wallClimbing;

	public BionicAmphibiousNavigation(SlimeBionicEntity mob, Level level) {
		super(mob, level);
		wallClimbing = new BionicWallClimbing(mob, this);
	}

	public boolean hasWallClimbingTarget() {
		return wallClimbing.hasTarget();
	}

	@Override
	public boolean moveTo(Entity target, double speed) {
		return wallClimbing.canUse() ? wallClimbing.moveTo(target, speed) : super.moveTo(target, speed);
	}

	@Override
	public boolean moveTo(double x, double y, double z, double speed) {
		return moveTo(x, y, z, 1, speed);
	}

	@Override
	public boolean moveTo(double x, double y, double z, int accuracy, double speed) {
		return wallClimbing.canUse()
			? wallClimbing.moveTo(BlockPos.containing(x, y, z), accuracy, speed)
			: super.moveTo(x, y, z, accuracy, speed);
	}

	@Override
	public boolean moveTo(@Nullable Path path, double speed) {
		wallClimbing.setPath(path);
		return super.moveTo(path, speed);
	}

	@Override
	public void tick() {
		boolean wasDone = isDone();
		super.tick();
		if (wasDone && isDone())
			wallClimbing.tick(speedModifier);
	}

	@Override
	public void stop() {
		super.stop();
		wallClimbing.stop();
	}

	@Override protected PathFinder createPathFinder(int visitedNodes) {
		nodeEvaluator = new AmphibiousNodeEvaluator(false) {
			@Override public int getNeighbors(Node[] neighbors, Node node) {
				SlimeBionicEntity bionic = (SlimeBionicEntity) mob;
				bionic.setEvaluatingJumpPath(true);
				try { return super.getNeighbors(neighbors, node); }
				finally { bionic.setEvaluatingJumpPath(false); }
			}
		};
		nodeEvaluator.setCanPassDoors(true);
		return new PathFinder(nodeEvaluator, visitedNodes);
	}
}
