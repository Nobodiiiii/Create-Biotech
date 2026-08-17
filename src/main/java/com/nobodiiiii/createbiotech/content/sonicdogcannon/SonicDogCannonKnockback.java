package com.nobodiiiii.createbiotech.content.sonicdogcannon;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

final class SonicDogCannonKnockback {

	private static final double WARDEN_HORIZONTAL_STRENGTH = 2.5d;
	private static final double WARDEN_VERTICAL_STRENGTH = 0.5d;
	private static final double PUNCH_HORIZONTAL_STRENGTH_PER_LEVEL = 0.6d;
	private static final double PUNCH_VERTICAL_STRENGTH = 0.1d;

	private SonicDogCannonKnockback() {}

	static void applyWarden(LivingEntity target, Vec3 direction) {
		Vec3 normalizedDirection = direction.normalize();
		double resistanceFactor = 1.0d - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
		target.push(
			normalizedDirection.x * WARDEN_HORIZONTAL_STRENGTH * resistanceFactor,
			normalizedDirection.y * WARDEN_VERTICAL_STRENGTH * resistanceFactor,
			normalizedDirection.z * WARDEN_HORIZONTAL_STRENGTH * resistanceFactor);
	}

	static void applyPunch(LivingEntity target, Vec3 direction, int punchLevel) {
		if (punchLevel <= 0)
			return;

		double resistanceFactor = Math.max(0.0d,
			1.0d - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
		Vec3 horizontal = direction.multiply(1.0d, 0.0d, 1.0d).normalize()
			.scale(PUNCH_HORIZONTAL_STRENGTH_PER_LEVEL * punchLevel * resistanceFactor);
		if (horizontal.lengthSqr() > 0.0d)
			target.push(horizontal.x, PUNCH_VERTICAL_STRENGTH, horizontal.z);
	}
}
