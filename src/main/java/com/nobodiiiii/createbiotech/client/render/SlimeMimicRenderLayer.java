package com.nobodiiiii.createbiotech.client.render;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.nobodiiiii.createbiotech.mixin.client.ModelPartAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class SlimeMimicRenderLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
	private static final ResourceLocation SLIME_TEXTURE = new ResourceLocation("textures/entity/slime/slime.png");
	private static final float SLIME_MODEL_WIDTH = 8.0f;
	private static final float SLIME_MODEL_CENTER_Y = 20.0f / 16.0f;
	private static final float OUTER_CUBE_INFLATE_PIXELS = 0.1f;
	private static final float INNER_RED = 1.0f;
	private static final float INNER_GREEN = 1.0f;
	private static final float INNER_BLUE = 1.0f;
	private static final float INNER_ALPHA = 1.0f;
	private static final float OUTER_RED = 1.0f;
	private static final float OUTER_GREEN = 1.0f;
	private static final float OUTER_BLUE = 1.0f;
	private static final float OUTER_ALPHA = 1.0f;
	private static final float OVERLAY_RED = 0.72f;
	private static final float OVERLAY_GREEN = 1.0f;
	private static final float OVERLAY_BLUE = 0.72f;
	private static final float OVERLAY_ALPHA = 0.28f;

	private static final ThreadLocal<Deque<RenderContext>> RENDER_CONTEXTS = ThreadLocal.withInitial(ArrayDeque::new);
	private static final ThreadLocal<Integer> INTERNAL_RENDER_DEPTH = ThreadLocal.withInitial(() -> 0);

	private static ModelPart innerCube;
	private static ModelPart outerCube;

	public SlimeMimicRenderLayer(RenderLayerParent<T, M> renderer) {
		super(renderer);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing,
		float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
		if (!SlimeMimicHandler.isSlimeMimic(entity) || entity.isInvisible())
			return;

		beginFallbackOverlay(buffer);
		try {
			renderFallbackOverlay(getParentModel(), entity, poseStack, buffer, packedLight, overlay(entity));
		} finally {
			endPartInterception();
		}
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

	public static void beginBodyPartReplacement(MultiBufferSource buffer) {
		pushContext(new RenderContext(RenderMode.SLIMEIFY_MODEL_PARTS, buffer));
	}

	public static void beginFallbackOverlay(MultiBufferSource buffer) {
		pushContext(new RenderContext(RenderMode.SKIP_MODEL_PARTS, buffer));
	}

	public static void endPartInterception() {
		Deque<RenderContext> contexts = RENDER_CONTEXTS.get();
		if (!contexts.isEmpty())
			contexts.pop();
		if (contexts.isEmpty())
			RENDER_CONTEXTS.remove();
	}

	public static boolean interceptModelPart(ModelPart part, PoseStack poseStack, int packedLight, int overlay) {
		if (INTERNAL_RENDER_DEPTH.get() > 0)
			return false;

		RenderContext context = currentContext();
		if (context == null)
			return false;

		if (context.mode == RenderMode.SKIP_MODEL_PARTS)
			return true;

		renderPartRecursive(part, poseStack, context.buffer, packedLight, overlay);
		return true;
	}

	private static void renderPartRecursive(ModelPart part, PoseStack poseStack, MultiBufferSource buffer,
		int packedLight, int overlay) {
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

	private static void renderCube(ModelPart.Cube cube, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
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
		VertexConsumer outerConsumer = buffer.getBuffer(RenderType.entityTranslucent(SLIME_TEXTURE));

		float outerWidth = width + 2.0f * OUTER_CUBE_INFLATE_PIXELS;
		float outerHeight = height + 2.0f * OUTER_CUBE_INFLATE_PIXELS;
		float outerDepth = depth + 2.0f * OUTER_CUBE_INFLATE_PIXELS;

		runWithoutPartInterception(() -> {
			poseStack.pushPose();
			poseStack.translate(centerX, centerY, centerZ);
			poseStack.scale(width / SLIME_MODEL_WIDTH, height / SLIME_MODEL_WIDTH, depth / SLIME_MODEL_WIDTH);
			poseStack.translate(0.0f, -SLIME_MODEL_CENTER_Y, 0.0f);
			innerCube().render(poseStack, innerConsumer, packedLight, overlay, INNER_RED, INNER_GREEN, INNER_BLUE,
				INNER_ALPHA);
			poseStack.popPose();

			poseStack.pushPose();
			poseStack.translate(centerX, centerY, centerZ);
			poseStack.scale(outerWidth / SLIME_MODEL_WIDTH, outerHeight / SLIME_MODEL_WIDTH,
				outerDepth / SLIME_MODEL_WIDTH);
			poseStack.translate(0.0f, -SLIME_MODEL_CENTER_Y, 0.0f);
			outerCube().render(poseStack, outerConsumer, packedLight, overlay, OUTER_RED, OUTER_GREEN, OUTER_BLUE,
				OUTER_ALPHA);
			poseStack.popPose();
		});
	}

	private static void renderFallbackOverlay(EntityModel<?> model, LivingEntity entity, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int overlay) {
		VertexConsumer overlayConsumer = buffer.getBuffer(RenderType.entityTranslucent(lookupTextureLocation(entity)));
		model.renderToBuffer(poseStack, overlayConsumer, packedLight, overlay, OVERLAY_RED, OVERLAY_GREEN, OVERLAY_BLUE,
			OVERLAY_ALPHA);
	}

	private static int overlay(LivingEntity entity) {
		return LivingEntityRenderer.getOverlayCoords(entity, 0.0f);
	}

	private static ResourceLocation lookupTextureLocation(LivingEntity entity) {
		EntityRenderer<? super LivingEntity> renderer = Minecraft.getInstance()
			.getEntityRenderDispatcher()
			.getRenderer(entity);
		return renderer.getTextureLocation(entity);
	}

	private static void pushContext(RenderContext context) {
		RENDER_CONTEXTS.get()
			.push(context);
	}

	private static RenderContext currentContext() {
		return RENDER_CONTEXTS.get()
			.peek();
	}

	private static ModelPart innerCube() {
		if (innerCube == null) {
			innerCube = Minecraft.getInstance()
				.getEntityModels()
				.bakeLayer(ModelLayers.SLIME)
				.getChild("cube");
		}
		return innerCube;
	}

	private static ModelPart outerCube() {
		if (outerCube == null) {
			outerCube = Minecraft.getInstance()
				.getEntityModels()
				.bakeLayer(ModelLayers.SLIME_OUTER)
				.getChild("cube");
		}
		return outerCube;
	}

	private static void runWithoutPartInterception(Runnable runnable) {
		INTERNAL_RENDER_DEPTH.set(INTERNAL_RENDER_DEPTH.get() + 1);
		try {
			runnable.run();
		} finally {
			int depth = INTERNAL_RENDER_DEPTH.get() - 1;
			if (depth <= 0) {
				INTERNAL_RENDER_DEPTH.remove();
				return;
			}
			INTERNAL_RENDER_DEPTH.set(depth);
		}
	}

	private enum RenderMode {
		SLIMEIFY_MODEL_PARTS,
		SKIP_MODEL_PARTS
	}

	private record RenderContext(RenderMode mode, MultiBufferSource buffer) {
	}
}
