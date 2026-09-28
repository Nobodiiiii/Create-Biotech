package com.nobodiiiii.createbiotech.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SlimeBionicGroundNavigation extends GroundPathNavigation {
	private static final double COLLISION_STEP = 0.5d;
	private static final double COLLISION_EPSILON = 1.0e-7d;

	public SlimeBionicGroundNavigation(Mob mob, Level level) {
		super(mob, level);
	}

	@Override
	protected boolean hasValidPathType(PathType type) {
		// StriderPathNavigation permits lava and its surrounding fire nodes.
		if (mob.canStandOnFluid(Fluids.LAVA.defaultFluidState())
			&& (type == PathType.LAVA || type == PathType.DAMAGE_FIRE || type == PathType.DANGER_FIRE))
			return true;
		return super.hasValidPathType(type);
	}

	@Override
	public boolean isStableDestination(BlockPos pos) {
		return mob.canStandOnFluid(Fluids.LAVA.defaultFluidState()) && level.getBlockState(pos).is(Blocks.LAVA)
			|| super.isStableDestination(pos);
	}

	@Override
	protected void followThePath() {
		Path currentPath = path;
		if (currentPath == null)
			return;

		Vec3 mobPos = getTempMobPos();
		int shortcutLimit = firstNodeAfterCurrentElevation(currentPath, mobPos);
		if (!tryShortcutToLaterNode(currentPath, shortcutLimit)) {
			if (isAt(currentPath, 0.5f)
				|| atElevationChange(currentPath) && isAt(currentPath, mob.getBbWidth() * 0.5f)) {
				currentPath.advance();
			}
		}

		doStuckDetection(mobPos);
	}

	private int firstNodeAfterCurrentElevation(Path currentPath, Vec3 mobPos) {
		int currentY = Mth.floor(mobPos.y);
		for (int i = currentPath.getNextNodeIndex(); i < currentPath.getNodeCount(); i++) {
			if (currentPath.getNode(i).y != currentY)
				return i;
		}
		return currentPath.getNodeCount();
	}

	private boolean tryShortcutToLaterNode(Path currentPath, int limit) {
		int currentIndex = currentPath.getNextNodeIndex();
		Vec3 from = mob.position();
		for (int i = Math.min(limit, currentPath.getNodeCount()) - 1; i > currentIndex; i--) {
			if (!hasValidPathType(currentPath.getNode(i).type))
				continue;

			Vec3 nodePos = currentPath.getEntityPosAtNode(mob, i);
			Vec3 target = new Vec3(nodePos.x, from.y, nodePos.z);
			if (canMoveStraightTo(from, target)) {
				currentPath.setNextNodeIndex(i);
				return true;
			}
		}
		return false;
	}

	private boolean isAt(Path currentPath, float horizontalDistance) {
		Vec3 next = currentPath.getNextEntityPos(mob);
		return Math.abs(mob.getX() - next.x) < horizontalDistance
			&& Math.abs(mob.getZ() - next.z) < horizontalDistance
			&& Math.abs(mob.getY() - next.y) < 1.0d;
	}

	private boolean atElevationChange(Path currentPath) {
		int currentIndex = currentPath.getNextNodeIndex();
		if (currentIndex >= currentPath.getNodeCount())
			return false;

		int limit = Math.min(currentPath.getNodeCount(), currentIndex + Mth.ceil(mob.getBbWidth() * 0.5f) + 1);
		int currentY = currentPath.getNode(currentIndex).y;
		for (int i = currentIndex + 1; i < limit; i++) {
			if (currentPath.getNode(i).y != currentY)
				return true;
		}
		return false;
	}

	private boolean canMoveStraightTo(Vec3 from, Vec3 target) {
		double dx = target.x - from.x;
		double dz = target.z - from.z;
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance < 1.0e-8d)
			return true;

		AABB baseBox = mob.getBoundingBox();
		int steps = Math.max(1, Mth.ceil(distance / COLLISION_STEP));
		for (int step = 1; step <= steps; step++) {
			double t = (double) step / steps;
			double x = from.x + dx * t;
			double z = from.z + dz * t;
			if (!hasValidSamplePathType(x, z))
				return false;

			AABB movedBox = baseBox.move(x - mob.getX(), 0.0d, z - mob.getZ()).deflate(COLLISION_EPSILON);
			if (!level.noCollision(mob, movedBox))
				return false;
		}
		return true;
	}

	private boolean hasValidSamplePathType(double x, double z) {
		if (nodeEvaluator == null)
			return true;
		return hasValidPathType(nodeEvaluator.getPathType(mob, BlockPos.containing(x, mob.getY(), z)));
	}
}
