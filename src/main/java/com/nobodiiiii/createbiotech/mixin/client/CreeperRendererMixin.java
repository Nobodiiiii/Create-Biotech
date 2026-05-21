package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberBlockEntity;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;

@Mixin(LivingEntityRenderer.class)
public abstract class CreeperRendererMixin {

	@Unique
	private static final float CREATE_BIOTECH_CREEPER_FINAL_HEIGHT_SCALE = 1f / 1.8f;
	@Unique
	private static final float CREATE_BIOTECH_CREEPER_MAX_SPREAD = 0.2f;
	@Unique
	private static final ThreadLocal<Integer> CREATE_BIOTECH_TRANSFORM_DEPTH = ThreadLocal.withInitial(() -> 0);
	@Unique
	private static final ThreadLocal<int[]> CREATE_BIOTECH_SAVED_SWELL = new ThreadLocal<>();

	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
		at = @At("HEAD"))
	private void createBiotech$applyChamberCompression(LivingEntity entity, float entityYaw, float partialTicks,
		PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		if (!(entity instanceof Creeper creeper))
			return;

		float compression = CreeperBlastChamberBlockEntity.getClientWorkingCreeperCompression(creeper, partialTicks);
		if (compression <= 0f)
			return;

		float horizontalScale = 1f + CREATE_BIOTECH_CREEPER_MAX_SPREAD * compression;
		float verticalScale = 1f + (CREATE_BIOTECH_CREEPER_FINAL_HEIGHT_SCALE - 1f) * compression;

		poseStack.pushPose();
		poseStack.scale(horizontalScale, verticalScale, horizontalScale);
		CREATE_BIOTECH_TRANSFORM_DEPTH.set(CREATE_BIOTECH_TRANSFORM_DEPTH.get() + 1);

		if (CreeperBlastChamberBlockEntity.isPonderCompressionActive(creeper)) {
			com.nobodiiiii.createbiotech.mixin.client.CreeperAccessor accessor =
				(com.nobodiiiii.createbiotech.mixin.client.CreeperAccessor) creeper;
			int origOld = accessor.createBiotech$getOldSwell();
			int origNew = accessor.createBiotech$getSwell();
			float pulse = 0.5f + 0.5f * Mth.sin(AnimationTickHolder.getRenderTime(creeper.level()) * 0.9f);
			int renderSwell = Mth.floor(Mth.clamp(compression * Mth.lerp(pulse, 0.55f, 1f), 0f, 1f) * 24f);
			accessor.createBiotech$setOldSwell(renderSwell);
			accessor.createBiotech$setSwell(renderSwell);
			CREATE_BIOTECH_SAVED_SWELL.set(new int[]{origOld, origNew});
		}
	}

	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
		at = @At("RETURN"))
	private void createBiotech$restoreChamberCompression(LivingEntity entity, float entityYaw, float partialTicks,
		PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		int depth = CREATE_BIOTECH_TRANSFORM_DEPTH.get();
		if (depth <= 0)
			return;
		poseStack.popPose();
		if (depth == 1) {
			CREATE_BIOTECH_TRANSFORM_DEPTH.remove();
		} else {
			CREATE_BIOTECH_TRANSFORM_DEPTH.set(depth - 1);
		}
		int[] saved = CREATE_BIOTECH_SAVED_SWELL.get();
		if (saved != null && entity instanceof Creeper creeper) {
			com.nobodiiiii.createbiotech.mixin.client.CreeperAccessor accessor =
				(com.nobodiiiii.createbiotech.mixin.client.CreeperAccessor) creeper;
			accessor.createBiotech$setOldSwell(saved[0]);
			accessor.createBiotech$setSwell(saved[1]);
			CREATE_BIOTECH_SAVED_SWELL.remove();
		}
	}
}
