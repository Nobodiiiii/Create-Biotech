package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltFunnelShapeHelper;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock.Shape;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

@Mixin(BeltFunnelBlock.class)
public abstract class BeltFunnelShapeMixin {

	@Inject(method = "getShape", at = @At("RETURN"), cancellable = true)
	private void createBiotech$tiltShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context,
		CallbackInfoReturnable<VoxelShape> cir) {
		cir.setReturnValue(SlimeBeltFunnelShapeHelper.transformIfSupported(world, pos, cir.getReturnValue()));
	}

	@Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
	private void createBiotech$tiltItemCollisionShape(BlockState state, BlockGetter world, BlockPos pos,
		CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
		if (!(context instanceof EntityCollisionContext entityContext))
			return;
		if (!(entityContext.getEntity() instanceof ItemEntity))
			return;

		Shape shape = state.getValue(BeltFunnelBlock.SHAPE);
		if (shape != Shape.PULLING && shape != Shape.PUSHING)
			return;

		VoxelShape collision = AllShapes.FUNNEL_COLLISION.get(state.getValue(BeltFunnelBlock.HORIZONTAL_FACING));
		cir.setReturnValue(SlimeBeltFunnelShapeHelper.transformIfSupported(world, pos, collision));
	}
}
