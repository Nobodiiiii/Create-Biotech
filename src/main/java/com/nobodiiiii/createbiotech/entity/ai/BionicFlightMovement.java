package com.nobodiiiii.createbiotech.entity.ai;

import net.minecraft.world.phys.Vec3;

/** Shared flight response: full input approaches the requested speed in blocks per tick. */
public final class BionicFlightMovement {
	private static final double INERTIA = 0.9d;

	private BionicFlightMovement() {}

	/** Input is a world-space direction; partial rider input preserves its strength. */
	public static Vec3 velocity(Vec3 current, Vec3 input, double speed) {
		Vec3 direction = input.lengthSqr() > 1.0d ? input.normalize() : input;
		double requestedSpeed = Double.isFinite(speed) ? Math.max(0.0d, speed) : 0.0d;
		return current.scale(INERTIA).add(direction.scale(requestedSpeed * (1.0d - INERTIA)));
	}
}
