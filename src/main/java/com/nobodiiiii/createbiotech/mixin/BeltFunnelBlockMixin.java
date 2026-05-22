package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurface;
import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurfaceResolver;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock.Shape;
import com.simibubi.create.foundation.advancement.AllAdvancements;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(BeltFunnelBlock.class)
public abstract class BeltFunnelBlockMixin {

	@Inject(method = "getShapeForPosition(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Z)Lcom/simibubi/create/content/logistics/funnel/BeltFunnelBlock$Shape;",
		at = @At("HEAD"), cancellable = true, remap = false)
	private static void createBiotech$getShapeForPosition(BlockGetter world, BlockPos pos, Direction localFacing,
		boolean extracting, CallbackInfoReturnable<Shape> cir) {
		BeltSurface surface = BeltSurfaceResolver.resolve(world, pos);
		if (surface == null)
			return;
		// localFacing is in surface-local frame; surface forward (= belt motion) is local NORTH.
		// Same axis as forward → RETRACTED; perpendicular → PUSHING/PULLING.
		Shape perpendicular = extracting ? Shape.PUSHING : Shape.PULLING;
		cir.setReturnValue(localFacing.getAxis() == Direction.NORTH.getAxis() ? Shape.RETRACTED : perpendicular);
	}

	@Inject(method = "isOnValidBelt(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z",
		at = @At("HEAD"), cancellable = true, remap = false)
	private static void createBiotech$isOnValidBelt(BlockState state, LevelReader world, BlockPos pos,
		CallbackInfoReturnable<Boolean> cir) {
		if (BeltSurfaceResolver.resolve(world, pos) != null)
			cir.setReturnValue(true);
	}

	@Inject(method = "onWrenched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
		at = @At("HEAD"), cancellable = true, remap = false)
	private void createBiotech$onWrenched(BlockState state, UseOnContext context,
		CallbackInfoReturnable<InteractionResult> cir) {
		Level world = context.getLevel();
		BeltSurface surface = BeltSurfaceResolver.resolve(world, context.getClickedPos());
		if (surface == null)
			return;
		if (world.isClientSide) {
			cir.setReturnValue(InteractionResult.SUCCESS);
			return;
		}

		Shape shape = state.getValue(BeltFunnelBlock.SHAPE);
		Shape newShape = shape;
		if (shape == Shape.PULLING)
			newShape = Shape.PUSHING;
		else if (shape == Shape.PUSHING)
			newShape = Shape.PULLING;
		else if (shape == Shape.EXTENDED)
			newShape = Shape.RETRACTED;
		else if (shape == Shape.RETRACTED) {
			// EXTENDED is only meaningful on the canonical "horizontal belt, top track" surface:
			// outwardNormal = UP and belt motion is horizontal. Otherwise stay RETRACTED.
			boolean canExtend = surface.outwardNormal() == Direction.UP
				&& surface.movementFacing().getAxis().isHorizontal();
			if (canExtend)
				newShape = Shape.EXTENDED;
		}

		if (newShape == shape) {
			cir.setReturnValue(InteractionResult.SUCCESS);
			return;
		}

		world.setBlockAndUpdate(context.getClickedPos(), state.setValue(BeltFunnelBlock.SHAPE, newShape));

		if (newShape == Shape.EXTENDED) {
			Direction localFacing = state.getValue(BeltFunnelBlock.HORIZONTAL_FACING);
			BlockState opposite = world.getBlockState(context.getClickedPos().relative(surface.worldize(localFacing)));
			if (opposite.getBlock() instanceof BeltFunnelBlock
				&& opposite.getValue(BeltFunnelBlock.SHAPE) == Shape.EXTENDED
				&& opposite.getValue(BeltFunnelBlock.HORIZONTAL_FACING) == localFacing.getOpposite())
				AllAdvancements.FUNNEL_KISS.awardTo(context.getPlayer());
		}

		cir.setReturnValue(InteractionResult.SUCCESS);
	}
}
