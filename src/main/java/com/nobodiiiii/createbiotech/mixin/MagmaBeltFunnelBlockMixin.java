package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock.Shape;
import com.simibubi.create.foundation.advancement.AllAdvancements;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(BeltFunnelBlock.class)
public abstract class MagmaBeltFunnelBlockMixin {

	@Inject(method = "getShapeForPosition(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Z)Lcom/simibubi/create/content/logistics/funnel/BeltFunnelBlock$Shape;",
		at = @At("HEAD"), cancellable = true, remap = false)
		private static void createBiotech$magmaGetShapeForPosition(BlockGetter world, BlockPos pos, Direction facing,
		boolean extracting, CallbackInfoReturnable<Shape> cir) {
		BlockState stateBelow = world.getBlockState(pos.below());
		if (!MagmaBeltBlock.isMagmaBelt(stateBelow))
			return;
		Shape perpendicularState = extracting ? Shape.PUSHING : Shape.PULLING;
		if (!MagmaBeltBlock.canTransportObjects(stateBelow)) {
			cir.setReturnValue(perpendicularState);
			return;
		}
		Direction movementFacing = stateBelow.getValue(MagmaBeltBlock.HORIZONTAL_FACING);
		cir.setReturnValue(movementFacing.getAxis() != facing.getAxis() ? perpendicularState : Shape.RETRACTED);
	}

	@Inject(method = "onWrenched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
		at = @At("HEAD"), cancellable = true, remap = false)
	private void createBiotech$magmaOnWrenched(BlockState state, UseOnContext context,
		CallbackInfoReturnable<InteractionResult> cir) {
		Level world = context.getLevel();
		BlockState below = world.getBlockState(context.getClickedPos()
			.below());
		if (!MagmaBeltBlock.isMagmaBelt(below))
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
			if (below.getValue(MagmaBeltBlock.SLOPE) != BeltSlope.HORIZONTAL)
				newShape = Shape.RETRACTED;
			else
				newShape = Shape.EXTENDED;
		}

		if (newShape == shape) {
			cir.setReturnValue(InteractionResult.SUCCESS);
			return;
		}

		world.setBlockAndUpdate(context.getClickedPos(), state.setValue(BeltFunnelBlock.SHAPE, newShape));

		if (newShape == Shape.EXTENDED) {
			Direction facing = state.getValue(BeltFunnelBlock.HORIZONTAL_FACING);
			BlockState opposite = world.getBlockState(context.getClickedPos()
				.relative(facing));
			if (opposite.getBlock() instanceof BeltFunnelBlock
				&& opposite.getValue(BeltFunnelBlock.SHAPE) == Shape.EXTENDED
				&& opposite.getValue(BeltFunnelBlock.HORIZONTAL_FACING) == facing.getOpposite())
				AllAdvancements.FUNNEL_KISS.awardTo(context.getPlayer());
		}

		cir.setReturnValue(InteractionResult.SUCCESS);
	}

}
