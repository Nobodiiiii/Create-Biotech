package com.nobodiiiii.createbiotech.content.allay.client.render;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.mixin.client.ModelPartAccessor;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.trains.schedule.hat.TrainHatInfo;
import com.nobodiiiii.createbiotech.content.allay.entity.courier.AllayCourierEntity;

import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.model.AllayModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import dev.engine_room.flywheel.lib.transform.TransformStack;

/**
 * Logistics-hat layer specialized for Allay models. Its transforms intentionally mirror Create's
 * {@code CreateHatArmorLayer}; only Create's contextual hat selection is replaced.
 */
final class AllayLogisticsHatRenderer extends RenderLayer<Allay, AllayModel> {

	private static final TrainHatInfo ALLAY_HAT_INFO =
		new TrainHatInfo("", 0, Vec3.ZERO, 1.0f);

	AllayLogisticsHatRenderer(RenderLayerParent<Allay, AllayModel> renderer) {
		super(renderer);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, Allay allay,
		float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
		float netHeadYaw, float headPitch) {
		if (allay instanceof AllayCourierEntity courier && courier.shouldRenderLogisticsHat()) {
			render(getParentModel(), poseStack, buffer, packedLight);
		}
	}

	static void render(AllayModel allayModel, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight) {
		render(allayModel.root(), poseStack, buffer, packedLight);
	}

	static void render(ModelPart root, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight) {
		List<ModelPart> partsToHead =
			TrainHatInfo.getAdjustedPart(ALLAY_HAT_INFO, root, "head");
		if (partsToHead.isEmpty()) {
			return;
		}

		poseStack.pushPose();
		partsToHead.forEach(part -> part.translateAndRotate(poseStack));

		ModelPart lastChild = partsToHead.get(partsToHead.size() - 1);
		if (!lastChild.isEmpty()) {
			List<ModelPart.Cube> cubes =
				((ModelPartAccessor) (Object) lastChild).createBiotech$getCubes();
			ModelPart.Cube cube = cubes.get(Mth.clamp(
				ALLAY_HAT_INFO.cubeIndex(), 0, cubes.size() - 1));
			poseStack.translate(
				ALLAY_HAT_INFO.offset().x() / 16.0f,
				(cube.minY - cube.maxY + ALLAY_HAT_INFO.offset().y()) / 16.0f,
				ALLAY_HAT_INFO.offset().z() / 16.0f);
			float scale = Math.max(cube.maxX - cube.minX, cube.maxZ - cube.minZ)
				/ 8.0f * ALLAY_HAT_INFO.scale();
			poseStack.scale(scale, scale, scale);
		}

		poseStack.scale(1.0f, -1.0f, -1.0f);
		poseStack.translate(0.0f, -2.25f / 16.0f, 0.0f);
		TransformStack.of(poseStack).rotateXDegrees(-8.5f);
		CachedBuffers.partial(AllPartialModels.LOGISTICS_HAT, Blocks.AIR.defaultBlockState())
			.disableDiffuse()
			.light(packedLight)
			.renderInto(poseStack, buffer.getBuffer(Sheets.cutoutBlockSheet()));
		poseStack.popPose();
	}
}
