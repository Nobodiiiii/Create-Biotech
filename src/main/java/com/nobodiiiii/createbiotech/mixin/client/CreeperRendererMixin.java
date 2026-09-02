package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberBlockEntity;
import com.nobodiiiii.createbiotech.foundation.render.RenderProxyEntities;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;

/**
 * Squashes a creeper that a ponder scene has marked as being compressed by a blast chamber.
 * <p>
 * In a live world the chamber draws its creepers as render proxies and applies compression itself,
 * so this only ever fires inside a ponder scene. Wrapping the whole method rather than injecting at
 * HEAD/RETURN keeps the pose push and the swell override paired through nesting and exceptions
 * alike, and the guard order makes every other entity in the world leave before anything is read
 * from persistent data.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class CreeperRendererMixin {

	@Unique
	private static final float CREATE_BIOTECH_CREEPER_FINAL_HEIGHT_SCALE = 16f / 26f;
	@Unique
	private static final float CREATE_BIOTECH_CREEPER_MAX_SPREAD = 0.2f;
	@Unique
	private static final int CREATE_BIOTECH_MAX_RENDER_SWELL = 24;

	@WrapMethod(
		method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
	private void createBiotech$applyChamberCompression(LivingEntity entity, float entityYaw, float partialTicks,
		PoseStack poseStack, MultiBufferSource buffer, int packedLight, Operation<Void> original) {
		float compression = createBiotech$chamberCompression(entity, partialTicks);
		if (compression <= 0f) {
			original.call(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
			return;
		}

		CreeperAccessor accessor = (CreeperAccessor) entity;
		int oldSwell = accessor.createBiotech$getOldSwell();
		int swell = accessor.createBiotech$getSwell();
		float pulse = 0.5f + 0.5f * Mth.sin(AnimationTickHolder.getRenderTime(entity.level()) * 0.9f);
		int renderSwell = Mth.floor(Mth.clamp(compression * Mth.lerp(pulse, 0.55f, 1f), 0f, 1f)
			* CREATE_BIOTECH_MAX_RENDER_SWELL);
		accessor.createBiotech$setOldSwell(renderSwell);
		accessor.createBiotech$setSwell(renderSwell);

		float horizontalScale = 1f + CREATE_BIOTECH_CREEPER_MAX_SPREAD * compression;
		float verticalScale = 1f + (CREATE_BIOTECH_CREEPER_FINAL_HEIGHT_SCALE - 1f) * compression;
		poseStack.pushPose();
		poseStack.scale(horizontalScale, verticalScale, horizontalScale);
		try {
			original.call(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
		} finally {
			poseStack.popPose();
			accessor.createBiotech$setOldSwell(oldSwell);
			accessor.createBiotech$setSwell(swell);
		}
	}

	/**
	 * Ordered cheapest-rejection-first. Reading persistent data is last because NeoForge allocates a
	 * CompoundTag on the first probe of any entity, and this method runs for every living entity the
	 * game draws.
	 */
	@Unique
	private static float createBiotech$chamberCompression(LivingEntity entity, float partialTicks) {
		if (!(entity instanceof Creeper creeper))
			return 0f;
		if (!(creeper.level() instanceof PonderLevel))
			return 0f;
		if (RenderProxyEntities.isProxy(creeper))
			return 0f;
		return CreeperBlastChamberBlockEntity.getClientWorkingCreeperCompression(creeper, partialTicks);
	}
}
