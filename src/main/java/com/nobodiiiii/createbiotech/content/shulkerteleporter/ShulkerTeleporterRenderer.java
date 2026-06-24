package com.nobodiiiii.createbiotech.content.shulkerteleporter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ShulkerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.level.block.state.BlockState;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;

public class ShulkerTeleporterRenderer extends KineticBlockEntityRenderer<ShulkerTeleporterBlockEntity> {

	private static final float LOWER_SHELL_Y = -2.0f;
	private static final float MIXER_IDLE_HEAD_OFFSET = 7 / 16f;
	private static final float FULL_SPIN_DEGREES = 720.0f;

	private final ShulkerModel<Shulker> model;

	public ShulkerTeleporterRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
		model = new ShulkerModel<>(context.bakeLayer(ModelLayers.SHULKER));
	}

	@Override
	protected void renderSafe(ShulkerTeleporterBlockEntity be, float partialTick, PoseStack poseStack,
		MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		VertexConsumer vertexConsumer = Sheets.DEFAULT_SHULKER_TEXTURE_LOCATION.buffer(bufferSource,
			RenderType::entityCutoutNoCull);
		float progress = be.getClosingProgress(partialTick);
		float topY = be.getTopShellYOffset(partialTick);
		float spin = progress * FULL_SPIN_DEGREES;

		renderMixerBody(be, poseStack, bufferSource, packedLight, packedOverlay);
		renderDriveCog(be, poseStack, bufferSource, packedLight);
		renderMixerPole(be, poseStack, bufferSource, packedLight, topY);
		renderBase(poseStack, vertexConsumer, packedLight, packedOverlay);
		renderLid(poseStack, vertexConsumer, packedLight, packedOverlay, topY, spin);
	}

	private void renderMixerBody(ShulkerTeleporterBlockEntity be, PoseStack poseStack, MultiBufferSource bufferSource,
		int packedLight,
		int packedOverlay) {
		poseStack.pushPose();
		Minecraft.getInstance()
			.getBlockRenderer()
			.renderSingleBlock(AllBlocks.MECHANICAL_MIXER.getDefaultState(), poseStack, bufferSource, packedLight,
				packedOverlay);
		poseStack.popPose();
	}

	private void renderDriveCog(ShulkerTeleporterBlockEntity be, PoseStack poseStack, MultiBufferSource bufferSource,
		int packedLight) {
		BlockState blockState = be.getBlockState();
		SuperByteBuffer cog = CachedBuffers.partial(AllPartialModels.SHAFTLESS_COGWHEEL, blockState);
		standardKineticRotationTransform(cog, be, packedLight)
			.renderInto(poseStack, bufferSource.getBuffer(RenderType.solid()));
	}

	private void renderMixerPole(ShulkerTeleporterBlockEntity be, PoseStack poseStack, MultiBufferSource bufferSource,
		int packedLight, float topShellY) {
		BlockState blockState = be.getBlockState();
		CachedBuffers.partial(AllPartialModels.MECHANICAL_MIXER_POLE, blockState)
			.translate(0, topShellY - ShulkerTeleporterBlockEntity.TOP_SHELL_OPEN_Y - MIXER_IDLE_HEAD_OFFSET, 0)
			.light(packedLight)
			.renderInto(poseStack, bufferSource.getBuffer(RenderType.solid()));
	}

	private void renderBase(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay) {
		ModelPart lid = model.getLid();
		resetModel();

		poseStack.pushPose();
		poseStack.translate(0.0d, LOWER_SHELL_Y, 0.0d);
		applyShulkerBoxPose(poseStack);
		for (ModelPart part : model.parts()) {
			if (part != lid)
				part.render(poseStack, vertexConsumer, packedLight, packedOverlay);
		}
		poseStack.popPose();
	}

	private void renderLid(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
		float yOffset, float spinDegrees) {
		ModelPart lid = model.getLid();
		resetModel();
		lid.setPos(0.0f, 24.0f, 0.0f);
		lid.yRot = (float) Math.toRadians(spinDegrees);

		poseStack.pushPose();
		poseStack.translate(0.0d, yOffset, 0.0d);
		applyShulkerBoxPose(poseStack);
		lid.render(poseStack, vertexConsumer, packedLight, packedOverlay);
		poseStack.popPose();
	}

	private void resetModel() {
		for (ModelPart part : model.parts())
			part.resetPose();
		model.getHead().resetPose();
	}

	private static void applyShulkerBoxPose(PoseStack poseStack) {
		poseStack.translate(0.5f, 0.5f, 0.5f);
		poseStack.scale(0.9995f, 0.9995f, 0.9995f);
		poseStack.mulPose(Direction.UP.getRotation());
		poseStack.scale(1.0f, -1.0f, -1.0f);
		poseStack.translate(0.0f, -1.0f, 0.0f);
	}
}
