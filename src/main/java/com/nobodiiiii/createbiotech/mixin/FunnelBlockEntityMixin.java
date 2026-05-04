package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltBlockEntity;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock.Shape;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(FunnelBlockEntity.class)
public abstract class FunnelBlockEntityMixin {

	private static final String FUNNEL_MODE_CLASS = "com.simibubi.create.content.logistics.funnel.FunnelBlockEntity$Mode";

	@Inject(method = "determineCurrentMode()Lcom/simibubi/create/content/logistics/funnel/FunnelBlockEntity$Mode;",
		at = @At("HEAD"), cancellable = true, remap = false)
	private void createBiotech$determineCurrentMode(CallbackInfoReturnable<Object> cir) {
		FunnelBlockEntity funnel = (FunnelBlockEntity) (Object) this;
		BlockState blockState = funnel.getBlockState();
		if (!(blockState.getBlock() instanceof BeltFunnelBlock))
			return;

		Shape shape = blockState.getValue(BeltFunnelBlock.SHAPE);
		if (shape == Shape.PULLING || shape == Shape.PUSHING || funnel.getLevel() == null)
			return;

		SlimeBeltBlockEntity belt = SlimeBeltHelper.getSegmentBE(funnel.getLevel(), funnel.getBlockPos()
			.below());
		if (belt == null)
			return;

		Direction facing = blockState.getValue(BeltFunnelBlock.HORIZONTAL_FACING);
		cir.setReturnValue(getMode(belt.getMovementFacing() == facing ? "PUSHING_TO_BELT" : "TAKING_FROM_BELT"));
	}

	@Inject(method = "supportsAmountOnFilter()Z", at = @At("HEAD"), cancellable = true, remap = false)
	private void createBiotech$supportsAmountOnFilter(CallbackInfoReturnable<Boolean> cir) {
		FunnelBlockEntity funnel = (FunnelBlockEntity) (Object) this;
		BlockState blockState = funnel.getBlockState();
		if (!(blockState.getBlock() instanceof BeltFunnelBlock) || funnel.getLevel() == null)
			return;

		Shape shape = blockState.getValue(BeltFunnelBlock.SHAPE);
		if (shape == Shape.PUSHING)
			return;

		if (SlimeBeltHelper.getSegmentBE(funnel.getLevel(), funnel.getBlockPos()
			.below()) != null)
			cir.setReturnValue(true);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Object getMode(String name) {
		try {
			Class enumClass = Class.forName(FUNNEL_MODE_CLASS);
			return Enum.valueOf(enumClass, name);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Failed to resolve FunnelBlockEntity mode " + name, exception);
		}
	}

}
