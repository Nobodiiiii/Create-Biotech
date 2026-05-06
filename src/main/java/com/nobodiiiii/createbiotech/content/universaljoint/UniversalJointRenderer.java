package com.nobodiiiii.createbiotech.content.universaljoint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

public class UniversalJointRenderer extends KineticBlockEntityRenderer<UniversalJointBlockEntity> {

	public UniversalJointRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void renderSafe(UniversalJointBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
		int light, int overlay) {
		BlockState state = getRenderedBlockState(be);
		renderRotatingBuffer(be, getRotatedModel(be, state), ms, buffer.getBuffer(RenderType.solid()), light);
	}
}
