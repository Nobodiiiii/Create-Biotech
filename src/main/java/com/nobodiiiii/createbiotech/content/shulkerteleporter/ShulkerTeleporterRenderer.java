package com.nobodiiiii.createbiotech.content.shulkerteleporter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.ShulkerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.monster.Shulker;

public class ShulkerTeleporterRenderer implements BlockEntityRenderer<ShulkerTeleporterBlockEntity> {

	private static final float TOP_OPEN_Y = 2.0f;
	private static final float TOP_CLOSED_Y = 0.0f;
	private static final float FULL_SPIN_DEGREES = 720.0f;

	private final ShulkerModel<Shulker> model;

	public ShulkerTeleporterRenderer(BlockEntityRendererProvider.Context context) {
		model = new ShulkerModel<>(context.bakeLayer(ModelLayers.SHULKER));
	}

	@Override
	public void render(ShulkerTeleporterBlockEntity be, float partialTick, PoseStack poseStack,
		MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		VertexConsumer vertexConsumer = Sheets.DEFAULT_SHULKER_TEXTURE_LOCATION.buffer(bufferSource,
			RenderType::entityCutoutNoCull);
		float progress = be.getClosingProgress(partialTick);
		float topY = TOP_OPEN_Y + (TOP_CLOSED_Y - TOP_OPEN_Y) * progress;
		float spin = progress * FULL_SPIN_DEGREES;

		renderBase(poseStack, vertexConsumer, packedLight, packedOverlay);
		renderLid(poseStack, vertexConsumer, packedLight, packedOverlay, topY, spin);
	}

	private void renderBase(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay) {
		ModelPart lid = model.getLid();
		resetModel();

		poseStack.pushPose();
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
