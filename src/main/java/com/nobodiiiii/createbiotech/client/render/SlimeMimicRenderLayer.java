package com.nobodiiiii.createbiotech.client.render;

import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.nobodiiiii.createbiotech.mixin.client.AgeableListModelFieldsAccessor;
import com.nobodiiiii.createbiotech.mixin.client.ModelPartAccessor;
import com.simibubi.create.foundation.mixin.accessor.AgeableListModelAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class SlimeMimicRenderLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
	private static final ResourceLocation SLIME_TEXTURE = new ResourceLocation("textures/entity/slime/slime.png");
	private static final float SLIME_MODEL_WIDTH = 8.0f;
	private static final float SLIME_MODEL_CENTER_Y = 20.0f / 16.0f;
	private static final float INNER_RED = 0.88f;
	private static final float INNER_GREEN = 1.0f;
	private static final float INNER_BLUE = 0.88f;
	private static final float INNER_ALPHA = 0.95f;
	private static final float OUTER_RED = 0.75f;
	private static final float OUTER_GREEN = 1.0f;
	private static final float OUTER_BLUE = 0.75f;
	private static final float OUTER_ALPHA = 0.55f;
	private static final float OVERLAY_RED = 0.72f;
	private static final float OVERLAY_GREEN = 1.0f;
	private static final float OVERLAY_BLUE = 0.72f;
	private static final float OVERLAY_ALPHA = 0.28f;

	private final ModelPart innerCube;
	private final ModelPart outerCube;

	public SlimeMimicRenderLayer(RenderLayerParent<T, M> renderer) {
		super(renderer);
		ModelPart innerRoot = Minecraft.getInstance()
			.getEntityModels()
			.bakeLayer(ModelLayers.SLIME);
		ModelPart outerRoot = Minecraft.getInstance()
			.getEntityModels()
			.bakeLayer(ModelLayers.SLIME_OUTER);
		this.innerCube = innerRoot.getChild("cube");
		this.outerCube = outerRoot.getChild("cube");
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing,
		float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
		if (!SlimeMimicHandler.isSlimeMimic(entity) || entity.isInvisible())
			return;

		M model = getParentModel();
		if (supportsSlimeMimicModel(model)) {
			renderSlimeBody(model, poseStack, buffer, packedLight, overlay(entity));
			return;
		}

		renderFallbackOverlay(entity, poseStack, buffer, packedLight, overlay(entity));
	}

	public static boolean supportsSlimeMimicModel(EntityModel<?> model) {
		return model instanceof HierarchicalModel<?> || model instanceof AgeableListModel<?>;
	}

	public static void registerOnAll(EntityRenderDispatcher dispatcher) {
		for (EntityRenderer<? extends Player> renderer : dispatcher.getSkinMap().values())
			registerOn(renderer);
		for (EntityRenderer<?> renderer : dispatcher.renderers.values())
			registerOn(renderer);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public static void registerOn(EntityRenderer<?> entityRenderer) {
		if (!(entityRenderer instanceof LivingEntityRenderer<?, ?> livingRenderer))
			return;
		livingRenderer.addLayer((RenderLayer) new SlimeMimicRenderLayer<>(livingRenderer));
	}

	@SuppressWarnings("unchecked")
	private void renderSlimeBody(M model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay) {
		if (model instanceof HierarchicalModel<?> hierarchicalModel) {
			renderPartRecursive(((HierarchicalModel<T>) hierarchicalModel).root(), poseStack, buffer, packedLight, overlay);
			return;
		}

		AgeableListModel<T> ageableModel = (AgeableListModel<T>) model;
		Iterable<ModelPart> headParts = ((AgeableListModelAccessor) ageableModel).create$callHeadParts();
		Iterable<ModelPart> bodyParts = ((AgeableListModelAccessor) ageableModel).create$callBodyParts();
		AgeableListModelFieldsAccessor fields = (AgeableListModelFieldsAccessor) ageableModel;

		if (ageableModel.young) {
			poseStack.pushPose();
			if (fields.createBiotech$scaleHead()) {
				float headScale = 1.5f / fields.createBiotech$getBabyHeadScale();
				poseStack.scale(headScale, headScale, headScale);
			}
			poseStack.translate(0.0f, fields.createBiotech$getBabyYHeadOffset() / 16.0f,
				fields.createBiotech$getBabyZHeadOffset() / 16.0f);
			renderParts(headParts, poseStack, buffer, packedLight, overlay);
			poseStack.popPose();

			poseStack.pushPose();
			float bodyScale = 1.0f / fields.createBiotech$getBabyBodyScale();
			poseStack.scale(bodyScale, bodyScale, bodyScale);
			poseStack.translate(0.0f, fields.createBiotech$getBodyYOffset() / 16.0f, 0.0f);
			renderParts(bodyParts, poseStack, buffer, packedLight, overlay);
			poseStack.popPose();
			return;
		}

		renderParts(headParts, poseStack, buffer, packedLight, overlay);
		renderParts(bodyParts, poseStack, buffer, packedLight, overlay);
	}

	private void renderParts(Iterable<ModelPart> parts, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		int overlay) {
		for (ModelPart part : parts)
			renderPartRecursive(part, poseStack, buffer, packedLight, overlay);
	}

	private void renderPartRecursive(ModelPart part, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		int overlay) {
		if (!part.visible)
			return;

		poseStack.pushPose();
		part.translateAndRotate(poseStack);

		ModelPartAccessor accessor = (ModelPartAccessor) (Object) part;
		if (!part.skipDraw) {
			for (ModelPart.Cube cube : accessor.createBiotech$getCubes())
				renderCube(cube, poseStack, buffer, packedLight, overlay);
		}

		for (Map.Entry<String, ModelPart> child : accessor.createBiotech$getChildren().entrySet())
			renderPartRecursive(child.getValue(), poseStack, buffer, packedLight, overlay);

		poseStack.popPose();
	}

	private void renderCube(ModelPart.Cube cube, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		int overlay) {
		float width = cube.maxX - cube.minX;
		float height = cube.maxY - cube.minY;
		float depth = cube.maxZ - cube.minZ;
		if (width <= 0 || height <= 0 || depth <= 0)
			return;

		float centerX = (cube.minX + cube.maxX) * 0.5f / 16.0f;
		float centerY = (cube.minY + cube.maxY) * 0.5f / 16.0f;
		float centerZ = (cube.minZ + cube.maxZ) * 0.5f / 16.0f;

		VertexConsumer innerConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(SLIME_TEXTURE));
		VertexConsumer outerConsumer = buffer.getBuffer(RenderType.entityTranslucentCull(SLIME_TEXTURE));

		poseStack.pushPose();
		poseStack.translate(centerX, centerY, centerZ);
		poseStack.scale(width / SLIME_MODEL_WIDTH, height / SLIME_MODEL_WIDTH, depth / SLIME_MODEL_WIDTH);
		poseStack.translate(0.0f, -SLIME_MODEL_CENTER_Y, 0.0f);
		innerCube.render(poseStack, innerConsumer, packedLight, overlay, INNER_RED, INNER_GREEN, INNER_BLUE,
			INNER_ALPHA);
		outerCube.render(poseStack, outerConsumer, packedLight, overlay, OUTER_RED, OUTER_GREEN, OUTER_BLUE,
			OUTER_ALPHA);
		poseStack.popPose();
	}

	private void renderFallbackOverlay(T entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		int overlay) {
		VertexConsumer overlayConsumer = buffer.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity)));
		getParentModel().renderToBuffer(poseStack, overlayConsumer, packedLight, overlay, OVERLAY_RED, OVERLAY_GREEN,
			OVERLAY_BLUE, OVERLAY_ALPHA);
	}

	private static int overlay(LivingEntity entity) {
		return LivingEntityRenderer.getOverlayCoords(entity, 0.0f);
	}
}
