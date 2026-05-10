package com.nobodiiiii.createbiotech.content.creeperblastchamber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

public class BlastProofChainDriveRenderer extends KineticBlockEntityRenderer<BlastProofChainDriveBlockEntity> {

	public BlastProofChainDriveRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void renderSafe(BlastProofChainDriveBlockEntity be, float partialTicks, PoseStack ms,
		MultiBufferSource buffer, int light, int overlay) {
		BlockState state = getRenderedBlockState(be);
		RenderType type = getRenderType(be, state);
		renderRotatingBuffer(be, getRotatedModel(be, state), ms, buffer.getBuffer(type), light);
	}

	@Override
	protected BlockState getRenderedBlockState(BlastProofChainDriveBlockEntity be) {
		return shaft(getRotationAxisOf(be));
	}
}
