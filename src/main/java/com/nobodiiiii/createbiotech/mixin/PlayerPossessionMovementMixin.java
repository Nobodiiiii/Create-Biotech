package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.nobodiiiii.createbiotech.content.possession.EchoShardPossession;
import com.nobodiiiii.createbiotech.content.possession.PossessionAccess;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Preserves the bionic body's hop-only ground locomotion while a player possesses it. */
@Mixin(value = Player.class, priority = 1500)
public abstract class PlayerPossessionMovementMixin extends LivingEntity {
	@Unique
	private static final double CREATE_BIOTECH$MIN_INPUT_SQR = 1.0e-7d;
	@Unique
	private static final int CREATE_BIOTECH$MIN_JUMP_DELAY = 10;
	@Unique
	private static final int CREATE_BIOTECH$RANDOM_JUMP_DELAY = 20;
	@Unique
	private int createBiotech$possessionJumpDelay;

	protected PlayerPossessionMovementMixin(EntityType<? extends LivingEntity> type, Level level) {
		super(type, level);
	}

	@ModifyArg(
		method = "travel(Lnet/minecraft/world/phys/Vec3;)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;travel(Lnet/minecraft/world/phys/Vec3;)V"),
		index = 0)
	private Vec3 createBiotech$replaceGroundMovementWithHops(Vec3 input) {
		if (!createBiotech$usesGroundHops()) {
			if (!createBiotech$usesHopLocomotion())
				createBiotech$possessionJumpDelay = 0;
			return input;
		}
		// A normal jump has already supplied the hop for this tick. Preserve its input instead of
		// replacing it with the automatic cadence below.
		if (getDeltaMovement().y > 0.0d)
			return input;

		double horizontalInputSqr = input.x * input.x + input.z * input.z;
		if (horizontalInputSqr < CREATE_BIOTECH$MIN_INPUT_SQR)
			return new Vec3(0.0d, input.y, 0.0d);

		if (createBiotech$possessionJumpDelay-- <= 0) {
			createBiotech$possessionJumpDelay = CREATE_BIOTECH$MIN_JUMP_DELAY
				+ getRandom().nextInt(CREATE_BIOTECH$RANDOM_JUMP_DELAY);
			jumpFromGround();
			return input;
		}

		return new Vec3(0.0d, input.y, 0.0d);
	}

	@Unique
	private boolean createBiotech$usesGroundHops() {
		Player player = (Player) (Object) this;
		return createBiotech$usesHopLocomotion() && onGround()
			&& !isInWater() && !isInLava() && !isFallFlying()
			&& !player.getAbilities().flying;
	}

	@Unique
	private boolean createBiotech$usesHopLocomotion() {
		if (!((Object) this instanceof PossessionAccess access))
			return false;
		CompoundTag state = access.createBiotech$getPossessionState();
		return EchoShardPossession.usesHopLocomotion(state);
	}
}
