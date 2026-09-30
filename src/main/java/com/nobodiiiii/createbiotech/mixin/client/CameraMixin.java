package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nobodiiiii.createbiotech.content.bouncing.BouncingAnimation;
import com.nobodiiiii.createbiotech.content.bouncing.BouncingAnimation.VisualDeformation;
import com.nobodiiiii.createbiotech.content.shulkerteleporter.ShulkerTeleporterClientEvents;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.nobodiiiii.createbiotech.registry.CBMobEffects;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

@Mixin(Camera.class)
public abstract class CameraMixin {
	private static final float BOUNCING_VIEW_ROTATION_SCALE = 0.18F;

	@Shadow
	private float eyeHeightOld;

	@Shadow
	private float eyeHeight;

	@Shadow
	public abstract Vec3 getPosition();

	@Shadow
	protected abstract void setPosition(Vec3 pPos);

	@Shadow
	public abstract float getXRot();

	@Shadow
	public abstract float getYRot();

	@Shadow
	public abstract float getRoll();

	@Shadow
	protected abstract void setRotation(float yRot, float xRot, float roll);

	@Shadow
	protected abstract void move(float distance, float vertical, float horizontal);

	@Shadow
	private float getMaxZoom(float distance) { throw new AssertionError(); }

	@Inject(method = "setup", at = @At("RETURN"))
	private void createBiotech$followBionicSeat(BlockGetter level, Entity entity,
		boolean detached, boolean reverse, float partialTick, CallbackInfo ci) {
		var pose = com.nobodiiiii.createbiotech.entity.BionicRiding.pose(entity, partialTick);
		if (pose == null) return;
		if (!detached) {
			var rotation = com.nobodiiiii.createbiotech.entity.BionicRiding.quaternion(pose.rotation())
				.mul(new org.joml.Quaternionf().rotationYXZ(Mth.PI - getYRot() * Mth.DEG_TO_RAD,
					-getXRot() * Mth.DEG_TO_RAD, -getRoll() * Mth.DEG_TO_RAD));
			var angles = rotation.getEulerAnglesYXZ(new org.joml.Vector3f());
			setRotation(180 - angles.y * Mth.RAD_TO_DEG, -angles.x * Mth.RAD_TO_DEG,
				-angles.z * Mth.RAD_TO_DEG);
		}
		setPosition(pose.eyes(entity));
		if (detached) {
			float scale = ((Player) entity).getScale();
			float distance = net.neoforged.neoforge.client.ClientHooks.getDetachedCameraDistance(
				(Camera) (Object) this, reverse, scale, 4);
			move(-getMaxZoom(distance * scale), 0, 0);
		}
	}

	@Inject(method = "setup", at = @At("RETURN"), require = 0)
	private void createBiotech$lowerFirstPersonViewInShulkerTeleporter(BlockGetter level, Entity entity,
		boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
		if (!CBConfigs.CLIENT.enableShulkerTeleporterCameraOffset.get())
			return;
		if (detached)
			return;
		if (!(entity instanceof Player player))
			return;
		if (player != Minecraft.getInstance().player)
			return;

		Vec3 offset = ShulkerTeleporterClientEvents.getFirstPersonCameraOffset(player, partialTick);
		if (offset.equals(Vec3.ZERO))
			return;

		setPosition(getPosition().add(offset));
	}

	@Inject(method = "setup", at = @At("RETURN"), require = 1)
	private void createBiotech$applyBouncingFirstPersonMotion(BlockGetter level, Entity entity,
		boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;
		if (player == null)
			return;

		if (entity != player || detached || minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
			BouncingAnimation.resetCamera(player);
			return;
		}
		if (!player.isAlive() || player.isSleeping() || !player.hasEffect(CBMobEffects.BOUNCING)) {
			BouncingAnimation.resetCamera(player);
			return;
		}

		VisualDeformation deformation = BouncingAnimation.getCameraDeformation(player, partialTick);
		float standingEyeHeight = player.getDimensions(Pose.STANDING).eyeHeight();
		float logicalEyeHeight = Mth.lerp(partialTick, eyeHeightOld, eyeHeight);
		double offsetX = standingEyeHeight * deformation.shearX();
		double offsetY = standingEyeHeight * deformation.verticalScale() - logicalEyeHeight;
		double offsetZ = standingEyeHeight * deformation.shearZ();
		if (offsetX != 0.0D || offsetY != 0.0D || offsetZ != 0.0D)
			setPosition(getPosition().add(offsetX, offsetY, offsetZ));

		float yawRadians = getYRot() * Mth.DEG_TO_RAD;
		float sideShear = deformation.shearX() * Mth.cos(yawRadians)
			+ deformation.shearZ() * Mth.sin(yawRadians);
		float forwardShear = -deformation.shearX() * Mth.sin(yawRadians)
			+ deformation.shearZ() * Mth.cos(yawRadians);
		float pitchOffset = (float) (-Math.atan(forwardShear) * Mth.RAD_TO_DEG)
			* BOUNCING_VIEW_ROTATION_SCALE;
		float rollOffset = (float) (Math.atan(sideShear) * Mth.RAD_TO_DEG)
			* BOUNCING_VIEW_ROTATION_SCALE;
		if (pitchOffset != 0.0F || rollOffset != 0.0F)
			setRotation(getYRot(), getXRot() + pitchOffset, getRoll() + rollOffset);
	}
}
