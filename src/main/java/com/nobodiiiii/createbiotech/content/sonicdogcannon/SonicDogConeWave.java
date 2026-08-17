package com.nobodiiiii.createbiotech.content.sonicdogcannon;

import com.nobodiiiii.createbiotech.network.CBPackets;
import com.nobodiiiii.createbiotech.registry.CBMobEffects;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SonicDogConeWave {

	private static final double HALF_ANGLE_RADIANS = Math.toRadians(60.0d);
	private static final double MIN_DIRECTION_DOT = Math.cos(HALF_ANGLE_RADIANS);
	private static final int MAX_STUN_DURATION_TICKS = 5 * 20;
	private static final int MIN_STUN_DURATION_TICKS = 2 * 20;

	private SonicDogConeWave() {}

	public static void fire(ServerLevel level, Player owner, Vec3 damageOrigin, Vec3 direction, double range,
		int punchLevel) {
		Vec3 normalizedDirection = direction.normalize();
		SonicDogCannonFirePacket packet = new SonicDogCannonFirePacket(
			owner.getId(), owner.getUsedItemHand(), normalizedDirection, (float) range);
		CBPackets.sendToTrackingEntity(packet, owner);
		if (owner instanceof ServerPlayer serverPlayer)
			CBPackets.sendToPlayer(packet, serverPlayer);

		AABB bounds = new AABB(damageOrigin,
			damageOrigin.add(normalizedDirection.scale(range)))
			.inflate(range * Math.sin(HALF_ANGLE_RADIANS));
		MobEffect stun = CBMobEffects.sonicDogCannonStun();
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, bounds,
			candidate -> candidate != owner && candidate.isAlive() && !candidate.isSpectator())) {
			Vec3 offset = target.getBoundingBox().getCenter().subtract(damageOrigin);
			double distance = offset.length();
			if (distance < 1.0e-5d || distance > range + target.getBbWidth() * 0.5d)
				continue;
			if (offset.dot(normalizedDirection) / distance < MIN_DIRECTION_DOT)
				continue;

			int stunDuration = stunDurationTicks(distance, range);
			target.addEffect(new MobEffectInstance(stun, stunDuration), owner);
			SonicDogCannonKnockback.applyPunch(target, offset, punchLevel + 1);
		}
	}

	private static int stunDurationTicks(double distance, double range) {
		double distanceFactor = range > 0.0d ? Mth.clamp(distance / range, 0.0d, 1.0d) : 1.0d;
		return (int) Math.round(MAX_STUN_DURATION_TICKS
			+ (MIN_STUN_DURATION_TICKS - MAX_STUN_DURATION_TICKS) * distanceFactor);
	}
}
