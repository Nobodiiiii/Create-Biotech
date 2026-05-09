package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberBlockEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;

@Mixin(LivingEntityRenderer.class)
public abstract class CreeperRendererMixin {

	@Unique
	private static final float CREATE_BIOTECH_CREEPER_PRESS_SINK = 0.08f;
	@Unique
	private static final float CREATE_BIOTECH_CREEPER_MAX_SQUASH = 0.45f;
	@Unique
	private static final float CREATE_BIOTECH_CREEPER_MAX_SPREAD = 0.2f;
	@Unique
	private static final ThreadLocal<Integer> CREATE_BIOTECH_TRANSFORM_DEPTH = ThreadLocal.withInitial(() -> 0);

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
		float verticalScale = 1f - CREATE_BIOTECH_CREEPER_MAX_SQUASH * compression;
		float yOffset = -CREATE_BIOTECH_CREEPER_PRESS_SINK * compression;

		poseStack.pushPose();
		poseStack.translate(0, yOffset, 0);
		poseStack.scale(horizontalScale, verticalScale, horizontalScale);
		CREATE_BIOTECH_TRANSFORM_DEPTH.set(CREATE_BIOTECH_TRANSFORM_DEPTH.get() + 1);
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
			return;
		}
		CREATE_BIOTECH_TRANSFORM_DEPTH.set(depth - 1);
	}
}
