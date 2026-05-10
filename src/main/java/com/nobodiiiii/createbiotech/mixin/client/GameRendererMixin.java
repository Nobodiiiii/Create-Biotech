package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nobodiiiii.createbiotech.registry.CBFluids;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "pick", at = @At("RETURN"))
	private void createBiotech$pickLiquidLivingSlime(float partialTicks, CallbackInfo ci) {
		Entity entity = minecraft.getCameraEntity();
		if (entity == null || minecraft.level == null || minecraft.player == null)
			return;

		double reach = minecraft.player.getBlockReach();
		Vec3 start = entity.getEyePosition(partialTicks);
		Vec3 direction = entity.getViewVector(1.0F);
		Vec3 end = start.add(direction.scale(reach));
		HitResult fluidHit =
			minecraft.level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, entity));
		if (!(fluidHit instanceof BlockHitResult blockHitResult))
			return;

		FluidState fluidState = minecraft.level.getFluidState(blockHitResult.getBlockPos());
		if (!createBiotech$isLiquidLivingSlime(fluidState))
			return;

		HitResult currentHit = minecraft.hitResult;
		if (currentHit == null || currentHit.getType() == HitResult.Type.MISS) {
			minecraft.hitResult = blockHitResult;
			return;
		}

		double fluidDistance = start.distanceToSqr(blockHitResult.getLocation());
		double currentDistance = start.distanceToSqr(currentHit.getLocation());
		if (fluidDistance <= currentDistance)
			minecraft.hitResult = blockHitResult;
	}

	private static boolean createBiotech$isLiquidLivingSlime(FluidState fluidState) {
		return !fluidState.isEmpty() && fluidState.getFluidType() == CBFluids.LIQUID_LIVING_SLIME_TYPE.get();
	}
}
