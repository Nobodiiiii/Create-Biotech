package com.nobodiiiii.createbiotech.content.dingdongchicken;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.CreateBiotech;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;

public class DingDongChickenRenderer
	extends MobRenderer<DingDongChickenEntity, DingDongChickenModel> {

	private static final ResourceLocation CHICKEN_TEXTURE =
		ResourceLocation.withDefaultNamespace("textures/entity/chicken.png");
	public static final PartialModel BELL_BASE_MODEL = bellModel("base");
	public static final PartialModel BELL_MODEL = bellModel("bell");
	public static final PartialModel BELL_PLUNGER_MODEL = bellModel("plunger");

	public DingDongChickenRenderer(EntityRendererProvider.Context context) {
		super(context, new DingDongChickenModel(context.bakeLayer(ModelLayers.CHICKEN)), 0.3F);
		addLayer(new BackBellLayer(this));
	}

	@Override
	public ResourceLocation getTextureLocation(DingDongChickenEntity entity) {
		return CHICKEN_TEXTURE;
	}

	@Override
	protected float getBob(DingDongChickenEntity entity, float partialTick) {
		float flap = Mth.lerp(partialTick, entity.oFlap, entity.flap);
		float flapSpeed = Mth.lerp(partialTick, entity.oFlapSpeed, entity.flapSpeed);
		return (Mth.sin(flap) + 1.0F) * flapSpeed;
	}

	private static PartialModel bellModel(String part) {
		return PartialModel.of(CreateBiotech.asResource("block/ding_dong_chicken/bell_" + part));
	}

	private static final class BackBellLayer
		extends RenderLayer<DingDongChickenEntity, DingDongChickenModel> {

		private BackBellLayer(DingDongChickenRenderer renderer) {
			super(renderer);
		}

		@Override
		public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			DingDongChickenEntity entity, float limbSwing, float limbSwingAmount, float partialTick,
			float ageInTicks, float netHeadYaw, float headPitch) {
			if (entity.isInvisible())
				return;

			poseStack.pushPose();
			if (entity.isBaby()) {
				// Match AgeableListModel's vanilla body transform for baby chickens.
				poseStack.scale(0.5F, 0.5F, 0.5F);
				poseStack.translate(0, 24.0F / 16.0F, 0);
			}

			// Follow the chicken body, then stand the bell on the body's upper face.
			getParentModel().translateToBody(poseStack);
			poseStack.translate(0, 0, 3.0F / 16.0F);
			poseStack.mulPose(Axis.XP.rotationDegrees(90));
			poseStack.translate(-0.5F, 0, -0.5F);

			float animation = entity.getBellAnimation(partialTick);
			int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0);

			CachedBuffers.partial(BELL_BASE_MODEL, Blocks.AIR.defaultBlockState())
				.light(packedLight)
				.overlay(overlay)
				.renderInto(poseStack, buffer.getBuffer(RenderType.solid()));
			renderAnimatedBell(poseStack, buffer, packedLight, overlay, animation,
				entity.getId() * 0.7548777F);
			poseStack.popPose();
		}

		private static void renderAnimatedBell(PoseStack poseStack, MultiBufferSource buffer,
			int packedLight, int overlay, float animation, float animationOffset) {
			float plungerOffset = (float) (1 - 4
				* Math.pow(Math.max(animation - 0.5F, 0) - 0.5F, 2));
			float swingStrength = (float) Math.pow(animation, 1.25F);

			CachedBuffers.partial(BELL_PLUNGER_MODEL, Blocks.AIR.defaultBlockState())
				.translate(0, plungerOffset * -0.75F / 16F, 0)
				.light(packedLight)
				.overlay(overlay)
				.renderInto(poseStack, buffer.getBuffer(RenderType.solid()));

			CachedBuffers.partial(BELL_MODEL, Blocks.AIR.defaultBlockState())
				.center()
				.translate(0, -1F / 16F, 0)
				.rotateXDegrees(swingStrength * 8
					* Mth.sin(animation * Mth.PI * 4 + animationOffset))
				.rotateZDegrees(swingStrength * 8
					* Mth.cos(animation * Mth.PI * 4 + animationOffset))
				.translate(0, 1F / 16F, 0)
				.scale(0.995F)
				.uncenter()
				.light(packedLight)
				.overlay(overlay)
				.renderInto(poseStack, buffer.getBuffer(RenderType.solid()));
		}
	}
}
