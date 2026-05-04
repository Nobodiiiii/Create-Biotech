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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(FunnelBlock.class)
public abstract class FunnelBlockMixin {

	@Inject(method = "updateShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
		at = @At("HEAD"), cancellable = true)
	private void createBiotech$updateShape(BlockState state, Direction direction, BlockState neighbour,
		LevelAccessor world, BlockPos pos, BlockPos neighbourPos, CallbackInfoReturnable<BlockState> cir) {
		Direction funnelFacing = AbstractFunnelBlock.getFunnelFacing(state);
		if (funnelFacing == null || funnelFacing.getAxis()
			.isVertical())
			return;

		FunnelSupport support = SlimeBeltHelper.getFunnelSupport(world, pos);
		if (support == null || direction != support.side()
			.getOpposite())
			return;

		BlockState equivalentFunnel = ProperWaterloggedBlock.withWater(world,
			((FunnelBlock) (Object) this).getEquivalentBeltFunnel(world, pos, state), pos);
		cir.setReturnValue(equivalentFunnel.setValue(BeltFunnelBlock.SHAPE,
			BeltFunnelBlock.getShapeForPosition(world, pos, funnelFacing, state.getValue(FunnelBlock.EXTRACTING))));
	}

}
