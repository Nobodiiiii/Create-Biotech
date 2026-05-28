package com.nobodiiiii.createbiotech.client.render;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.platform.NativeImage;
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
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

public class SlimeMimicRenderLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final ResourceLocation SLIME_TEXTURE = new ResourceLocation("textures/entity/slime/slime.png");
	private static final float SLIME_MODEL_WIDTH = 8.0f;
	private static final float SLIME_MODEL_CENTER_Y = 20.0f / 16.0f;
	private static final float OUTER_CUBE_INFLATE_PIXELS = 0.1f;
	private static final float FLAT_CUBE_THRESHOLD_PIXELS = 0.05f;
	private static final float FLAT_CUBE_FILTER_ALPHA = 0.55f;
	private static final float FLAT_CUBE_FILTER_NORMAL_OFFSET = 1.0f / 1024.0f;
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
	private static final Map<ResourceLocation, NativeImage> TEXTURE_IMAGE_CACHE = new HashMap<>();
	private static final Map<ResourceLocation, IdentityHashMap<ModelPart.Cube, Boolean>> CUBE_VISIBILITY_CACHE =
		new HashMap<>();
	private static volatile ReflectionAccess reflectionAccess;
	private static volatile boolean reflectionAccessResolved;
	private static volatile boolean reflectionAccessDisabled;

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

	public static void beginBodyPartReplacement(MultiBufferSource buffer, LivingEntity entity) {
		pushContext(new RenderContext(RenderMode.SLIMEIFY_MODEL_PARTS, buffer, lookupTextureLocation(entity)));
	}

	public static void beginFallbackOverlay(MultiBufferSource buffer) {
		pushContext(new RenderContext(RenderMode.SKIP_MODEL_PARTS, buffer, null));
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

		renderPartRecursive(part, poseStack, context, packedLight, overlay);
		return true;
	}

	private static void renderPartRecursive(ModelPart part, PoseStack poseStack, RenderContext context,
		int packedLight, int overlay) {
		if (!part.visible)
			return;

		poseStack.pushPose();
		part.translateAndRotate(poseStack);

		ModelPartAccessor accessor = (ModelPartAccessor) (Object) part;
		if (!part.skipDraw) {
			for (ModelPart.Cube cube : accessor.createBiotech$getCubes())
				renderCube(cube, poseStack, context, packedLight, overlay);
		}

		for (Map.Entry<String, ModelPart> child : accessor.createBiotech$getChildren().entrySet())
			renderPartRecursive(child.getValue(), poseStack, context, packedLight, overlay);

		poseStack.popPose();
	}

	private static void renderCube(ModelPart.Cube cube, PoseStack poseStack, RenderContext context, int packedLight,
		int overlay) {
		if (!cubeHasVisiblePixels(cube, context.texture()))
			return;

		float width = cube.maxX - cube.minX;
		float height = cube.maxY - cube.minY;
		float depth = cube.maxZ - cube.minZ;
		if (width < 0 || height < 0 || depth < 0)
			return;

		boolean flatCube = isFlatCube(width, height, depth);

		float centerX = (cube.minX + cube.maxX) * 0.5f / 16.0f;
		float centerY = (cube.minY + cube.maxY) * 0.5f / 16.0f;
		float centerZ = (cube.minZ + cube.maxZ) * 0.5f / 16.0f;

		if (flatCube) {
			renderFlatCubeFilter(cube, poseStack, context, packedLight, overlay);
			return;
		}

		VertexConsumer innerConsumer = context.buffer()
			.getBuffer(RenderType.entityCutoutNoCull(SLIME_TEXTURE));
		VertexConsumer outerConsumer = context.buffer()
			.getBuffer(RenderType.entityTranslucent(SLIME_TEXTURE));

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

	private static void renderFlatCubeFilter(ModelPart.Cube cube, PoseStack poseStack, RenderContext context,
		int packedLight, int overlay) {
		ResourceLocation texture = context.texture();
		if (texture == null)
			return;

		VertexConsumer baseConsumer = context.buffer()
			.getBuffer(RenderType.entityCutoutNoCull(texture));
		VertexConsumer filterConsumer = context.buffer()
			.getBuffer(RenderType.entityTranslucent(texture));

		runWithoutPartInterception(() -> {
			cube.compile(poseStack.last(), baseConsumer, packedLight, overlay, 1.0f, 1.0f, 1.0f, 1.0f);
			compileCubeWithNormalOffset(cube, poseStack.last(), filterConsumer, packedLight, overlay,
				OVERLAY_RED, OVERLAY_GREEN, OVERLAY_BLUE, FLAT_CUBE_FILTER_ALPHA, FLAT_CUBE_FILTER_NORMAL_OFFSET);
		});
	}

	private static boolean isFlatCube(float width, float height, float depth) {
		return width <= FLAT_CUBE_THRESHOLD_PIXELS || height <= FLAT_CUBE_THRESHOLD_PIXELS
			|| depth <= FLAT_CUBE_THRESHOLD_PIXELS;
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

	public static void clearCachedTextureData() {
		for (NativeImage image : TEXTURE_IMAGE_CACHE.values())
			image.close();
		TEXTURE_IMAGE_CACHE.clear();
		CUBE_VISIBILITY_CACHE.clear();
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

	private record RenderContext(RenderMode mode, MultiBufferSource buffer, ResourceLocation texture) {
	}

	private static boolean cubeHasVisiblePixels(ModelPart.Cube cube, ResourceLocation texture) {
		if (texture == null)
			return true;

		IdentityHashMap<ModelPart.Cube, Boolean> visibilityByCube =
			CUBE_VISIBILITY_CACHE.computeIfAbsent(texture, key -> new IdentityHashMap<>());
		Boolean cachedVisibility = visibilityByCube.get(cube);
		if (cachedVisibility != null)
			return cachedVisibility;

		NativeImage image = textureImage(texture);
		boolean visible = image == null || cubeHasVisiblePixels(cube, image);
		visibilityByCube.put(cube, visible);
		return visible;
	}

	private static boolean cubeHasVisiblePixels(ModelPart.Cube cube, NativeImage image) {
		ReflectionAccess access = reflectionAccess();
		if (access == null || !image.format().hasAlpha())
			return true;

		try {
			for (Object polygon : cubePolygons(access, cube)) {
				float minU = Float.POSITIVE_INFINITY;
				float minV = Float.POSITIVE_INFINITY;
				float maxU = Float.NEGATIVE_INFINITY;
				float maxV = Float.NEGATIVE_INFINITY;

				for (Object vertex : polygonVertices(access, polygon)) {
					float u = vertexU(access, vertex);
					float v = vertexV(access, vertex);
					minU = Math.min(minU, u);
					minV = Math.min(minV, v);
					maxU = Math.max(maxU, u);
					maxV = Math.max(maxV, v);
				}

				if (uvRangeHasVisiblePixels(image, minU, minV, maxU, maxV))
					return true;
			}
		} catch (RuntimeException e) {
			disableReflection("Failed to inspect slime mimic cube texture visibility; falling back to vanilla cube rendering.",
				e);
			return true;
		}

		return false;
	}

	private static boolean uvRangeHasVisiblePixels(NativeImage image, float minU, float minV, float maxU, float maxV) {
		int width = image.getWidth();
		int height = image.getHeight();
		int minX = clampTextureCoord((int) Math.floor(Math.min(minU, maxU) * width), width);
		int minY = clampTextureCoord((int) Math.floor(Math.min(minV, maxV) * height), height);
		int maxX = clampTextureCoord((int) Math.ceil(Math.max(minU, maxU) * width) - 1, width);
		int maxY = clampTextureCoord((int) Math.ceil(Math.max(minV, maxV) * height) - 1, height);
		if (maxX < minX || maxY < minY)
			return false;

		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				if (FastColor.ABGR32.alpha(image.getPixelRGBA(x, y)) > 0)
					return true;
			}
		}

		return false;
	}

	private static int clampTextureCoord(int value, int maxExclusive) {
		if (maxExclusive <= 0)
			return 0;
		return Math.max(0, Math.min(value, maxExclusive - 1));
	}

	private static NativeImage textureImage(ResourceLocation texture) {
		NativeImage cached = TEXTURE_IMAGE_CACHE.get(texture);
		if (cached != null)
			return cached;

		NativeImage loaded = loadTextureImage(texture);
		if (loaded != null)
			TEXTURE_IMAGE_CACHE.put(texture, loaded);
		return loaded;
	}

	private static NativeImage loadTextureImage(ResourceLocation texture) {
		try {
			Resource resource = Minecraft.getInstance()
				.getResourceManager()
				.getResource(texture)
				.orElse(null);
			if (resource == null)
				return null;
			try (InputStream stream = resource.open()) {
				return NativeImage.read(stream);
			}
		} catch (IOException e) {
			return null;
		}
	}

	private static void compileCubeWithNormalOffset(ModelPart.Cube cube, PoseStack.Pose pose, VertexConsumer consumer,
		int packedLight, int overlay, float red, float green, float blue, float alpha, float normalOffset) {
		ReflectionAccess access = reflectionAccess();
		if (access == null) {
			cube.compile(pose, consumer, packedLight, overlay, red, green, blue, alpha);
			return;
		}

		Matrix4f poseMatrix = pose.pose();
		Matrix3f normalMatrix = pose.normal();

		try {
			for (Object polygon : cubePolygons(access, cube)) {
				Vector3f transformedNormal = normalMatrix.transform(polygonNormal(access, polygon), new Vector3f());
				if (transformedNormal.lengthSquared() > 1.0e-7f)
					transformedNormal.normalize();
				else
					transformedNormal.zero();

				float normalX = transformedNormal.x();
				float normalY = transformedNormal.y();
				float normalZ = transformedNormal.z();

				for (Object vertex : polygonVertices(access, polygon)) {
					Vector3f localPos = vertexPos(access, vertex);
					Vector4f transformedPos = poseMatrix.transform(
						new Vector4f(localPos.x() / 16.0f, localPos.y() / 16.0f, localPos.z() / 16.0f, 1.0f));
					consumer.vertex(transformedPos.x() + normalX * normalOffset,
						transformedPos.y() + normalY * normalOffset,
						transformedPos.z() + normalZ * normalOffset,
						red, green, blue, alpha, vertexU(access, vertex), vertexV(access, vertex), overlay, packedLight,
						normalX, normalY, normalZ);
				}
			}
		} catch (RuntimeException e) {
			disableReflection("Failed to offset slime mimic flat-cube overlay; falling back to vanilla overlay rendering.",
				e);
			cube.compile(pose, consumer, packedLight, overlay, red, green, blue, alpha);
		}
	}

	private static ReflectionAccess reflectionAccess() {
		if (reflectionAccessDisabled)
			return null;
		if (reflectionAccessResolved)
			return reflectionAccess;

		synchronized (SlimeMimicRenderLayer.class) {
			if (reflectionAccessDisabled)
				return null;
			if (reflectionAccessResolved)
				return reflectionAccess;
			try {
				reflectionAccess = ReflectionAccess.create();
			} catch (RuntimeException e) {
				disableReflection("Failed to initialize slime mimic model reflection; flat-cube fixes will use vanilla rendering.",
					e);
				return null;
			}
			reflectionAccessResolved = true;
			return reflectionAccess;
		}
	}

	private static void disableReflection(String message, RuntimeException exception) {
		synchronized (SlimeMimicRenderLayer.class) {
			if (!reflectionAccessDisabled)
				LOGGER.warn(message, exception);
			reflectionAccess = null;
			reflectionAccessResolved = true;
			reflectionAccessDisabled = true;
		}
	}

	private static Object[] cubePolygons(ReflectionAccess access, ModelPart.Cube cube) {
		return (Object[]) readField(access.cubePolygonsField, cube);
	}

	private static Object[] polygonVertices(ReflectionAccess access, Object polygon) {
		return (Object[]) readField(access.polygonVerticesField, polygon);
	}

	private static Vector3f polygonNormal(ReflectionAccess access, Object polygon) {
		return new Vector3f((Vector3f) readField(access.polygonNormalField, polygon));
	}

	private static Vector3f vertexPos(ReflectionAccess access, Object vertex) {
		return (Vector3f) readField(access.vertexPosField, vertex);
	}

	private static float vertexU(ReflectionAccess access, Object vertex) {
		return readFloatField(access.vertexUField, vertex);
	}

	private static float vertexV(ReflectionAccess access, Object vertex) {
		return readFloatField(access.vertexVField, vertex);
	}

	private static Object readField(Field field, Object target) {
		try {
			return field.get(target);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Failed to read field " + field.getName(), e);
		}
	}

	private static float readFloatField(Field field, Object target) {
		try {
			return field.getFloat(target);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Failed to read field " + field.getName(), e);
		}
	}

	private static Class<?> classForName(String name) {
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("Failed to resolve class " + name, e);
		}
	}

	private static Field accessibleField(Field field) {
		field.setAccessible(true);
		return field;
	}

	private static Field findSingleField(Class<?> owner, Predicate<Field> predicate, String description) {
		List<Field> matches = findFields(owner, predicate);
		if (matches.size() != 1)
			throw new IllegalStateException("Expected exactly 1 " + description + " field on " + owner.getName()
				+ ", found " + matches.size());
		return accessibleField(matches.get(0));
	}

	private static List<Field> findFields(Class<?> owner, Predicate<Field> predicate) {
		List<Field> matches = new ArrayList<>();
		for (Field field : owner.getDeclaredFields()) {
			if (predicate.test(field))
				matches.add(field);
		}
		return matches;
	}

	private static boolean isArrayOf(Field field, Class<?> componentType) {
		return field.getType().isArray() && field.getType().getComponentType() == componentType;
	}

	private static final class ReflectionAccess {
		private final Field cubePolygonsField;
		private final Field polygonVerticesField;
		private final Field polygonNormalField;
		private final Field vertexPosField;
		private final Field vertexUField;
		private final Field vertexVField;

		private ReflectionAccess(Field cubePolygonsField, Field polygonVerticesField, Field polygonNormalField,
			Field vertexPosField, Field vertexUField, Field vertexVField) {
			this.cubePolygonsField = cubePolygonsField;
			this.polygonVerticesField = polygonVerticesField;
			this.polygonNormalField = polygonNormalField;
			this.vertexPosField = vertexPosField;
			this.vertexUField = vertexUField;
			this.vertexVField = vertexVField;
		}

		private static ReflectionAccess create() {
			Class<?> polygonClass = classForName("net.minecraft.client.model.geom.ModelPart$Polygon");
			Class<?> vertexClass = classForName("net.minecraft.client.model.geom.ModelPart$Vertex");
			Field cubePolygonsField =
				findSingleField(ModelPart.Cube.class, field -> isArrayOf(field, polygonClass), "cube polygon array");
			Field polygonVerticesField =
				findSingleField(polygonClass, field -> isArrayOf(field, vertexClass), "polygon vertex array");
			Field polygonNormalField =
				findSingleField(polygonClass, field -> field.getType() == Vector3f.class, "polygon normal");
			Field vertexPosField =
				findSingleField(vertexClass, field -> field.getType() == Vector3f.class, "vertex position");
			List<Field> vertexFloatFields = findFields(vertexClass, field -> field.getType() == float.class);
			if (vertexFloatFields.size() != 2)
				throw new IllegalStateException(
					"Expected exactly 2 float fields on " + vertexClass.getName() + ", found " + vertexFloatFields.size());
			return new ReflectionAccess(cubePolygonsField, polygonVerticesField, polygonNormalField, vertexPosField,
				accessibleField(vertexFloatFields.get(0)), accessibleField(vertexFloatFields.get(1)));
		}
	}

}
