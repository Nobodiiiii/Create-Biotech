package com.nobodiiiii.createbiotech.content.experience;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

public class ExperiencePumpRenderer extends KineticBlockEntityRenderer<ExperiencePumpBlockEntity> {

	public ExperiencePumpRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void renderSafe(ExperiencePumpBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
		int light, int overlay) {
		BlockState state = getRenderedBlockState(be);
		RenderType type = getRenderType(be, state);
		renderRotatingBuffer(be, getRotatedModel(be, state), ms, buffer.getBuffer(type), light);
	}

	@Override
	protected SuperByteBuffer getRotatedModel(ExperiencePumpBlockEntity be, BlockState state) {
		return CachedBuffers.partialFacing(AllPartialModels.MECHANICAL_PUMP_COG, state);
	}
}
