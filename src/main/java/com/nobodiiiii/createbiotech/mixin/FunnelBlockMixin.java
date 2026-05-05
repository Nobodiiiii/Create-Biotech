package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper.FunnelSupport;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(FunnelBlock.class)
public abstract class FunnelBlockMixin {

	@Inject(method = "getStateForPlacement(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;",
		at = @At("RETURN"), cancellable = true)
	private void createBiotech$getStateForPlacement(BlockPlaceContext context,
		CallbackInfoReturnable<BlockState> cir) {
		BlockState state = cir.getReturnValue();
		if (state == null)
			return;

		Direction funnelFacing = AbstractFunnelBlock.getFunnelFacing(state);
		if (funnelFacing == null)
			return;

		FunnelSupport support = SlimeBeltHelper.getFunnelSupport(context.getLevel(), context.getClickedPos());
		if (support == null)
			return;
		if (!SlimeBeltHelper.isValidFunnelFacing(support, funnelFacing))
			return;
		Direction localFacing = SlimeBeltHelper.getLocalFunnelFacing(support, funnelFacing);
		BlockState localState = state.setValue(FunnelBlock.FACING, localFacing);

		BlockState equivalentFunnel = ProperWaterloggedBlock.withWater(context.getLevel(),
			((FunnelBlock) (Object) this).getEquivalentBeltFunnel(context.getLevel(), context.getClickedPos(), localState),
			context.getClickedPos());
		cir.setReturnValue(equivalentFunnel.setValue(BeltFunnelBlock.HORIZONTAL_FACING, localFacing)
			.setValue(BeltFunnelBlock.SHAPE, BeltFunnelBlock.getShapeForPosition(context.getLevel(),
				context.getClickedPos(), localFacing, state.getValue(FunnelBlock.EXTRACTING))));
	}

	@Inject(method = "updateShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
		at = @At("HEAD"), cancellable = true)
	private void createBiotech$updateShape(BlockState state, Direction direction, BlockState neighbour,
		LevelAccessor world, BlockPos pos, BlockPos neighbourPos, CallbackInfoReturnable<BlockState> cir) {
		Direction funnelFacing = AbstractFunnelBlock.getFunnelFacing(state);
		if (funnelFacing == null)
			return;

		FunnelSupport support = SlimeBeltHelper.getFunnelSupport(world, pos);
		if (support == null || direction != support.side()
			.getOpposite())
			return;
		if (!SlimeBeltHelper.isValidFunnelFacing(support, funnelFacing))
			return;
		Direction localFacing = SlimeBeltHelper.getLocalFunnelFacing(support, funnelFacing);
		BlockState localState = state.setValue(FunnelBlock.FACING, localFacing);

		BlockState equivalentFunnel = ProperWaterloggedBlock.withWater(world,
			((FunnelBlock) (Object) this).getEquivalentBeltFunnel(world, pos, localState), pos);
		cir.setReturnValue(equivalentFunnel.setValue(BeltFunnelBlock.HORIZONTAL_FACING, localFacing)
			.setValue(BeltFunnelBlock.SHAPE,
				BeltFunnelBlock.getShapeForPosition(world, pos, localFacing, state.getValue(FunnelBlock.EXTRACTING))));
	}

}
