package com.nobodiiiii.createbiotech.entity;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Ghast-style wandering for flying bodies without ground-reaching legs. */
final class BionicLowFlightGoal extends Goal {
	private static final double HOVER_HEIGHT = 1.5d;
	private static final double SURFACE_PROBE_OFFSET = 0.125d;
	private static final double SURFACE_SEARCH_DEPTH = 12.0d;
	private static final double MAX_HOVER_SPEED = 0.2d;
	private static final double WANDER_RANGE = 16.0d;
	private static final double MAX_CRUISE_CLEARANCE = 4.0d;
	private final SlimeBionicEntity bionic;
	@Nullable private Vec3 destination;
	private int retryDelay;

	BionicLowFlightGoal(SlimeBionicEntity bionic) {
		this.bionic = bionic;
		setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override public boolean canUse() {
		return !bionic.isOrderedToSit() && !bionic.isPassenger() && !bionic.hasControllingPassenger()
			&& !bionic.getBlockAttachment().locksMovement() && BionicLocomotionPolicy.prefersLowFlight(bionic);
	}

	@Override public boolean requiresUpdateEveryTick() { return true; }
	@Override public void stop() {
		destination = null;
		retryDelay = 0;
	}

	@Override
	public void tick() {
		if (!bionic.getNavigation().isDone()) {
			destination = null;
			return;
		}
		Vec3 origin = bionic.position();
		if (destination != null) {
			double distance = origin.distanceToSqr(destination);
			if (distance < 1.0d || distance > 4.0d * WANDER_RANGE * WANDER_RANGE
				|| bionic.horizontalCollision || bionic.verticalCollision)
				destination = null;
		}
		if (retryDelay > 0)
			retryDelay--;
		if (destination == null && retryDelay == 0) {
			destination = findDestination(origin);
			retryDelay = destination == null ? 6 : 0;
		}
		if (destination == null) {
			holdHoverHeight();
			return;
		}
		double speedModifier = Math.min(1.0d, origin.distanceTo(destination) / 4.0d);
		bionic.getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, speedModifier);
	}

	private void holdHoverHeight() {
		BlockHitResult surface = surfaceBelow(bionic.position().add(0, SURFACE_PROBE_OFFSET, 0), SURFACE_SEARCH_DEPTH);
		double floor = surface.getLocation().y;
		if (surface.getType() == HitResult.Type.MISS) {
			// Return from high above the terrain. Underground, use only the local probe.
			floor = bionic.level().getHeight(Heightmap.Types.MOTION_BLOCKING, bionic.getBlockX(), bionic.getBlockZ());
			if (floor <= bionic.level().getMinBuildHeight() || bionic.getY() <= floor)
				return;
		}
		double speed = Math.min(bionic.getAttributeValue(Attributes.FLYING_SPEED), MAX_HOVER_SPEED);
		double verticalSpeed = Mth.clamp((floor + HOVER_HEIGHT - bionic.getY()) * 0.25d, -speed, speed);
		Vec3 velocity = bionic.getDeltaMovement();
		bionic.setDeltaMovement(velocity.x, Mth.lerp(0.25d, velocity.y, verticalSpeed), velocity.z);
	}

	@Nullable
	private Vec3 findDestination(Vec3 origin) {
		var level = bionic.level();
		var random = bionic.getRandom();
		for (int attempt = 0; attempt < 6; attempt++) {
			double range = attempt < 3 ? WANDER_RANGE : 5.0d;
			double dx = (random.nextDouble() * 2.0d - 1.0d) * range;
			double dz = (random.nextDouble() * 2.0d - 1.0d) * range;
			Vec3 probe = origin.add(dx, random.nextDouble() * 3.0d + SURFACE_PROBE_OFFSET, dz);
			if (dx * dx + dz * dz < 4.0d || !isLoaded(probe))
				continue;
			BlockHitResult surface = surfaceBelow(probe, SURFACE_SEARCH_DEPTH);
			if (surface.getType() == HitResult.Type.MISS || surface.isInside())
				continue;
			double height = HOVER_HEIGHT + random.nextDouble() - 0.5d;
			Vec3 target = new Vec3(probe.x, surface.getLocation().y + height, probe.z);
			BlockPos pos = BlockPos.containing(target);
			if (!level.isOutsideBuildHeight(pos) && bionic.isWithinRestriction(pos)
				&& level.getFluidState(pos).isEmpty() && canFlyDirectly(origin, target))
				return target;
		}
		return null;
	}

	private boolean canFlyDirectly(Vec3 origin, Vec3 target) {
		Vec3 offset = target.subtract(origin);
		int steps = Mth.ceil(offset.length());
		Vec3 step = offset.scale(1.0d / steps);
		AABB bounds = bionic.getBoundingBox();
		for (int i = 1; i <= steps; i++) {
			// Sweep the assembled body's bounds along the course, including between samples.
			if (!bionic.level().noCollision(bionic, bounds.expandTowards(step)))
				return false;
			bounds = bounds.move(step);
			if (i % 4 == 0 && i < steps) {
				Vec3 probe = origin.add(step.scale(i)).add(0, SURFACE_PROBE_OFFSET, 0);
				if (!isLoaded(probe) || surfaceBelow(probe, MAX_CRUISE_CLEARANCE + SURFACE_PROBE_OFFSET)
					.getType() == HitResult.Type.MISS)
					return false;
			}
		}
		return true;
	}

	private boolean isLoaded(Vec3 point) {
		return bionic.level().hasChunk(SectionPos.blockToSectionCoord(point.x), SectionPos.blockToSectionCoord(point.z));
	}

	private BlockHitResult surfaceBelow(Vec3 start, double depth) {
		return bionic.level().clip(new ClipContext(start, start.add(0, -depth, 0),
			ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, bionic));
	}
}
