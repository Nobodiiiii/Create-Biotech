package com.nobodiiiii.createbiotech.content.experience;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public class ExperiencePumpRenderer extends KineticBlockEntityRenderer<ExperiencePumpBlockEntity> {

	public static final ResourceLocation COG_MODEL_LOCATION = CreateBiotech.asResource("block/experience_pump/cog");
	public static final PartialModel COG = PartialModel.of(COG_MODEL_LOCATION);

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
		return CachedBuffers.partialFacing(COG, state);
	}
}
