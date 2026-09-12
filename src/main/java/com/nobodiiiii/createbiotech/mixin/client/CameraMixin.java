package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nobodiiiii.createbiotech.content.bouncing.BouncingCameraAnimation;
import com.nobodiiiii.createbiotech.content.bouncing.BouncingCameraAnimation.CameraMotion;
import com.nobodiiiii.createbiotech.content.shulkerteleporter.ShulkerTeleporterClientEvents;
import com.nobodiiiii.createbiotech.registry.CBConfigs;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

@Mixin(Camera.class)
public abstract class CameraMixin {

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
			BouncingCameraAnimation.reset(player);
			return;
		}

		CameraMotion motion = BouncingCameraAnimation.update(player, partialTick);
		if (motion.verticalOffset() != 0.0F)
			setPosition(getPosition().add(0.0D, motion.verticalOffset(), 0.0D));
		if (motion.pitch() != 0.0F || motion.roll() != 0.0F)
			setRotation(getYRot(), getXRot() + motion.pitch(), getRoll() + motion.roll());
	}
}
