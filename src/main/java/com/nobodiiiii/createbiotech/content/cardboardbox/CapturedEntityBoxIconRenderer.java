package com.nobodiiiii.createbiotech.content.cardboardbox;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.foundation.render.BlockEntityModelElement;
import com.nobodiiiii.createbiotech.foundation.render.EntityGeometry;
import com.nobodiiiii.createbiotech.foundation.render.GuiEntityItemElement;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.nobodiiiii.createbiotech.registry.CBItems;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;

public final class CapturedEntityBoxIconRenderer {
	private static final Direction ICON_FACE = Direction.EAST;
	private static final float SMALL_BOX_MIN = 2.0f / 16.0f;
	private static final float SMALL_BOX_MAX = 14.0f / 16.0f;
	private static final float SMALL_BOX_HEIGHT = 12.0f / 16.0f;
	private static final float LARGE_BOX_MIN = 0.0f;
	private static final float LARGE_BOX_MAX = 1.0f;
	private static final float LARGE_BOX_HEIGHT = 1.0f;
	private static final float FACE_OFFSET = 1.0f / 128.0f;
	private static final float MAX_FLATTENED_DEPTH_OFFSET = 1.0f / 512.0f;
	private static final float ICON_FRAME_FILL = 0.6f;
	private static final float MAX_AUTO_RENDER_SCALE = 1.5f;
	private static final float MIN_VISIBLE_FACE_DOT = 1.0e-4f;
	private static final int MAX_CAPTURED_VERTICES = 262_144;
	private static final float ITEM_PLANE_TO_FACE_Y_ROT = itemPlaneToFaceYRot(ICON_FACE);
	private static final ItemStack ENTITY_ITEM_TRANSFORM = new ItemStack(CBItems.CAPTURED_SMALL_SLIME.get());
	private static final EntityGeometry.Collector GEOMETRY_COLLECTOR =
		EntityGeometry.Collector.caching(MAX_CAPTURED_VERTICES);
	private static final Vector3f FACE_CENTER_SCRATCH = new Vector3f();
	private static final Vector3f FACE_NORMAL_SCRATCH = new Vector3f();

	private CapturedEntityBoxIconRenderer() {}

	public static void renderOnEntity(ItemStack stack, float yaw, PoseStack poseStack, MultiBufferSource buffer,
		int light) {
		renderOnEntity(stack, CapturedEntityBoxHelper.hasCapturedEntity(stack), yaw, poseStack, buffer, light);
	}

	static void renderOnEntity(ItemStack stack, boolean captured, float yaw,
		PoseStack poseStack, MultiBufferSource buffer, int light) {
		render(stack, captured, stack.is(CBItems.LARGE_CARDBOARD_BOX.get()), poseStack, buffer, light, yaw,
			CapturedEntityRenderManager.RequestPriority.WORLD, false);
	}

	public static void renderOnItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
		MultiBufferSource buffer, int light) {
		renderOnItem(stack, CapturedEntityBoxHelper.hasCapturedEntity(stack),
			stack.is(CBItems.LARGE_CARDBOARD_BOX.get()), displayContext, poseStack, buffer, light);
	}

	static void renderOnItem(ItemStack stack, boolean captured,
		ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource buffer, int light) {
		renderOnItem(stack, captured, stack.is(CBItems.LARGE_CARDBOARD_BOX.get()), displayContext,
			poseStack, buffer, light);
	}

	/** Renders captured contents on an item that uses the large-box model but is not the box item itself. */
	public static void renderOnLargeItem(ItemStack stack, ItemDisplayContext displayContext,
		PoseStack poseStack, MultiBufferSource buffer, int light) {
		renderOnItem(stack, CapturedEntityBoxHelper.hasCapturedEntity(stack), true, displayContext,
			poseStack, buffer, light);
	}

	private static void renderOnItem(ItemStack stack, boolean captured, boolean largeBox,
		ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource buffer, int light) {
		poseStack.pushPose();
		poseStack.translate(0.0f, -0.5f, 0.0f);
		render(stack, captured, largeBox, poseStack, buffer, light, -90.0f,
			CapturedEntityRenderManager.RequestPriority.forDisplayContext(displayContext),
			displayContext == ItemDisplayContext.GUI);
		poseStack.popPose();
	}

	private static void render(ItemStack stack, boolean captured, boolean largeBox,
		PoseStack poseStack, MultiBufferSource buffer, int light, float yaw,
		CapturedEntityRenderManager.RequestPriority priority, boolean orthographicView) {
		if (!captured || !CBConfigs.CLIENT.renderCapturedEntitiesOnBoxes.get())
			return;

		FaceBounds face = FaceBounds.forFace(largeBox);

		BlockEntityModelElement.builder()
			.atLocal(-0.5f, 0.0f, -0.5f)
			.rotateCentered(0.0f, -yaw - 90.0f, 0.0f)
			.packedLight(light)
			.render(poseStack, buffer, (iconPoseStack, iconBuffer, packedLight) -> {
				if (!isFrontFaceVisible(iconPoseStack, face, orthographicView))
					return;

				CapturedEntityBoxHelper.CapturedEntityRenderData renderData =
					CapturedEntityBoxHelper.getCapturedEntityRenderData(stack);
				if (renderData == null)
					return;

				BakedCapturedEntityIcon icon =
					CapturedEntityRenderManager.getOrSchedule(renderData, face.large(), priority);
				if (icon != null)
					icon.render(iconPoseStack, iconBuffer, packedLight);
			});
	}

	private static boolean isFrontFaceVisible(PoseStack poseStack, FaceBounds face, boolean orthographicView) {
		PoseStack.Pose pose = poseStack.last();
		pose.normal()
			.transform(ICON_FACE.getStepX(), ICON_FACE.getStepY(), ICON_FACE.getStepZ(), FACE_NORMAL_SCRATCH);
		float normalLengthSquared = FACE_NORMAL_SCRATCH.lengthSquared();
		if (normalLengthSquared <= 1.0e-12f)
			return false;

		if (orthographicView)
			return FACE_NORMAL_SCRATCH.z() / Mth.sqrt(normalLengthSquared) > MIN_VISIBLE_FACE_DOT;

		pose.pose()
			.transformPosition(face.x(), face.centerY(), face.centerZ(), FACE_CENTER_SCRATCH);
		float viewLengthSquared = FACE_CENTER_SCRATCH.lengthSquared();
		if (viewLengthSquared <= 1.0e-12f)
			return true;

		float facing = -FACE_NORMAL_SCRATCH.dot(FACE_CENTER_SCRATCH)
			/ Mth.sqrt(normalLengthSquared * viewLengthSquared);
		return facing > MIN_VISIBLE_FACE_DOT;
	}

	static GeometryProfile prepareGeometry(LivingEntity entity) {
		GEOMETRY_COLLECTOR.reset();
		try (CapturedEntityRenderTime.Scope ignored = CapturedEntityRenderTime.open()) {
			EntityGeometry.measureInto(entity, GEOMETRY_COLLECTOR);
		}

		if (!GEOMETRY_COLLECTOR.hasVertices())
			GEOMETRY_COLLECTOR.includeEntityDimensions(entity.getDimensions(entity.getPose()));

		Vector3f geometryCenter = GEOMETRY_COLLECTOR.bounds()
			.center();
		FaceProjection small = projectGeometry(geometryCenter, FaceBounds.SMALL);
		FaceProjection large = projectGeometry(geometryCenter, FaceBounds.LARGE);
		return new GeometryProfile(geometryCenter, small, large);
	}

	/**
	 * Runs the live entity-render-and-clip pipeline once in box-local space and
	 * captures the emitted stream as a replayable mesh. The identity
	 * box-to-render matrix makes the clipper's output coordinates box-local, so
	 * replaying them under any later pose reproduces exactly what the live path
	 * would have drawn that frame.
	 */
	static BakedCapturedEntityIcon bakeIcon(LivingEntity entity, GeometryProfile geometry, boolean largeBox) {
		FaceBounds face = FaceBounds.forFace(largeBox);
		FaceProjection projection = geometry.forFace(largeBox);
		BakedCapturedEntityIcon.Builder builder = BakedCapturedEntityIcon.builder();
		Matrix4f identityBoxToRender = new Matrix4f();
		MultiBufferSource clippingSource = renderType -> new FaceClippingVertexConsumer(builder.target(renderType),
			identityBoxToRender, face, projection.alignment());

		PoseStack poseStack = new PoseStack();
		applyEntityItemTransform(poseStack, face);
		try (CapturedEntityRenderTime.Scope ignored = CapturedEntityRenderTime.open()) {
			GuiEntityItemElement.of(entity)
				.blockCentered()
				.geometryCenter(geometry.geometryCenter())
				.fixedScale(projection.renderScale())
				.packedLight(BakedCapturedEntityIcon.LIGHT_SENTINEL)
				.render(poseStack, clippingSource);
		}
		return builder.build(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())
			.toString());
	}

	private static FaceProjection projectGeometry(Vector3f geometryCenter, FaceBounds face) {
		EntityGeometry.Bounds unitBounds = projectBounds(geometryCenter, face, 1.0f);
		if (!unitBounds.hasVertices())
			return new FaceProjection(1.0f, FaceAlignment.none());

		float projectedWidth = unitBounds.sizeZ();
		float projectedHeight = unitBounds.sizeY();
		float renderScale = 1.0f;
		if (projectedWidth > 1.0e-6f && projectedHeight > 1.0e-6f) {
			float widthScale = face.width() * ICON_FRAME_FILL / projectedWidth;
			float heightScale = face.height() * ICON_FRAME_FILL / projectedHeight;
			renderScale = Math.min(Math.min(widthScale, heightScale), MAX_AUTO_RENDER_SCALE);
		}

		EntityGeometry.Bounds finalBounds = projectBounds(geometryCenter, face, renderScale);
		if (!finalBounds.hasVertices())
			return new FaceProjection(renderScale, FaceAlignment.none());

		float xAlignment = face.x() - finalBounds.centerX();
		FaceAlignment alignment = new FaceAlignment(xAlignment, face.centerY() - finalBounds.centerY(),
			face.centerZ() - finalBounds.centerZ(), finalBounds.minX() + xAlignment,
			finalBounds.maxX() + xAlignment);
		return new FaceProjection(renderScale, alignment);
	}

	private static EntityGeometry.Bounds projectBounds(Vector3f geometryCenter, FaceBounds face, float renderScale) {
		PoseStack poseStack = new PoseStack();
		applyEntityItemTransform(poseStack, face);
		GuiEntityItemElement.applyBlockCenteredTransform(poseStack, geometryCenter, renderScale);
		return GEOMETRY_COLLECTOR.transformBounds(poseStack.last()
			.pose());
	}

	private static void applyEntityItemTransform(PoseStack poseStack, FaceBounds face) {
		Minecraft minecraft = Minecraft.getInstance();
		BakedModel model = minecraft.getItemRenderer()
			.getModel(ENTITY_ITEM_TRANSFORM, minecraft.level, minecraft.player, 0);

		poseStack.translate(face.x + FACE_OFFSET, face.centerY(), face.centerZ());
		poseStack.mulPose(Axis.YP.rotationDegrees(ITEM_PLANE_TO_FACE_Y_ROT));
		float scale = face.itemScale();
		poseStack.scale(scale, scale, scale);
		ClientHooks.handleCameraTransforms(poseStack, model, ItemDisplayContext.GUI, false);
		poseStack.translate(-0.5f, -0.5f, -0.5f);
	}

	private static float itemPlaneToFaceYRot(Direction face) {
		return switch (face) {
		case NORTH -> 180.0f;
		case SOUTH -> 0.0f;
		case WEST -> -90.0f;
		case EAST -> 90.0f;
		default -> 90.0f;
		};
	}

	private record FaceBounds(float x, float minY, float maxY, float minZ, float maxZ, boolean large) {
		private static final FaceBounds SMALL =
			new FaceBounds(SMALL_BOX_MAX, 0.0f, SMALL_BOX_HEIGHT, SMALL_BOX_MIN, SMALL_BOX_MAX, false);
		private static final FaceBounds LARGE =
			new FaceBounds(LARGE_BOX_MAX, 0.0f, LARGE_BOX_HEIGHT, LARGE_BOX_MIN, LARGE_BOX_MAX, true);

		private static FaceBounds of(ItemStack stack) {
			return stack.is(CBItems.LARGE_CARDBOARD_BOX.get()) ? LARGE : SMALL;
		}

		private static FaceBounds forFace(boolean large) {
			return large ? LARGE : SMALL;
		}

		private float width() {
			return maxZ - minZ;
		}

		private float height() {
			return maxY - minY;
		}

		private float centerY() {
			return (minY + maxY) / 2.0f;
		}

		private float centerZ() {
			return (minZ + maxZ) / 2.0f;
		}

		private float itemScale() {
			return Math.min(width(), height());
		}
	}

	static record GeometryProfile(Vector3f geometryCenter, FaceProjection small, FaceProjection large) {
		private FaceProjection forFace(boolean largeBox) {
			return largeBox ? large : small;
		}
	}

	private record FaceProjection(float renderScale, FaceAlignment alignment) {
	}

	private record FaceAlignment(float x, float y, float z, float minX, float maxX) {
		private static FaceAlignment none() {
			return new FaceAlignment(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		}

		private float depthOffset(double localX) {
			float depth = maxX - minX;
			if (depth <= 1.0e-6f)
				return 0.0f;
			float normalizedDepth = Mth.clamp(((float) localX - minX) / depth, 0.0f, 1.0f);
			return normalizedDepth * MAX_FLATTENED_DEPTH_OFFSET;
		}
	}

	private static class FaceClippingVertexConsumer implements VertexConsumer {
		private final VertexConsumer wrapped;
		private final Matrix4f boxToRender;
		private final Matrix4f renderToBox;
		private final FaceBounds face;
		private final FaceAlignment alignment;
		private final List<ClippedVertex> quad = new ArrayList<>(4);
		private ClippedVertex current = new ClippedVertex();

		private FaceClippingVertexConsumer(VertexConsumer wrapped, Matrix4f boxToRender, FaceBounds face,
			FaceAlignment alignment) {
			this.wrapped = wrapped;
			this.boxToRender = new Matrix4f(boxToRender);
			this.renderToBox = new Matrix4f(boxToRender).invert();
			this.face = face;
			this.alignment = alignment;
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			current = new ClippedVertex();
			current.x = x;
			current.y = y;
			current.z = z;
			Vector3f local = renderToBox.transformPosition(x, y, z, new Vector3f());
			current.localX = local.x() + alignment.x();
			current.localY = local.y() + alignment.y();
			current.localZ = local.z() + alignment.z();
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			current.red = red;
			current.green = green;
			current.blue = blue;
			current.alpha = alpha;
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			current.u = u;
			current.v = v;
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			current.overlay = u | v << 16;
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			current.light = u | v << 16;
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			current.normalX = x;
			current.normalY = y;
			current.normalZ = z;
			quad.add(current.copy());
			if (quad.size() == 4) {
				emitClippedQuad(quad);
				quad.clear();
			}
			return this;
		}

		private void emitClippedQuad(List<ClippedVertex> source) {
			List<ClippedVertex> clipped = clip(source);
			if (clipped.size() < 3)
				return;
			for (int i = 1; i < clipped.size() - 1; i++)
				emitDegenerateQuad(clipped.get(0), clipped.get(i), clipped.get(i + 1));
		}

		private List<ClippedVertex> clip(List<ClippedVertex> source) {
			List<ClippedVertex> result = source;
			result = clipAgainst(result, vertex -> face.minZ - vertex.localZ);
			result = clipAgainst(result, vertex -> vertex.localZ - face.maxZ);
			result = clipAgainst(result, vertex -> face.minY - vertex.localY);
			result = clipAgainst(result, vertex -> vertex.localY - face.maxY);
			return result;
		}

		private List<ClippedVertex> clipAgainst(List<ClippedVertex> source, ClipDistance distance) {
			if (source.isEmpty())
				return source;

			List<ClippedVertex> result = new ArrayList<>();
			for (int i = 0; i < source.size(); i++) {
				ClippedVertex current = source.get(i);
				ClippedVertex previous = source.get((i + source.size() - 1) % source.size());
				double currentDistance = distance.get(current);
				double previousDistance = distance.get(previous);
				boolean currentInside = currentDistance <= 0.0d;
				boolean previousInside = previousDistance <= 0.0d;

				if (currentInside != previousInside)
					result.add(ClippedVertex.lerp(previous, current,
						-previousDistance / (currentDistance - previousDistance)));
				if (currentInside)
					result.add(current);
			}
			return result;
		}

		private void emitDegenerateQuad(ClippedVertex a, ClippedVertex b, ClippedVertex c) {
			emit(a);
			emit(b);
			emit(c);
			emit(c);
		}

		private void emit(ClippedVertex vertex) {
			float depthOffset = alignment.depthOffset(vertex.localX);
			Vector3f projected = boxToRender.transformPosition(face.x + FACE_OFFSET + depthOffset,
				(float) vertex.localY, (float) vertex.localZ, new Vector3f());
			wrapped.addVertex(projected.x(), projected.y(), projected.z())
				.setColor(vertex.red, vertex.green, vertex.blue, vertex.alpha)
				.setUv(vertex.u, vertex.v)
				.setOverlay(vertex.overlay)
				.setLight(vertex.light)
				.setNormal(vertex.normalX, vertex.normalY, vertex.normalZ);
		}

		@FunctionalInterface
		private interface ClipDistance {
			double get(ClippedVertex vertex);
		}
	}

	private static class ClippedVertex {
		private double x;
		private double y;
		private double z;
		private double localX;
		private double localY;
		private double localZ;
		private int red = 255;
		private int green = 255;
		private int blue = 255;
		private int alpha = 255;
		private float u;
		private float v;
		private int overlay;
		private int light;
		private float normalX;
		private float normalY = 1.0f;
		private float normalZ;

		private ClippedVertex copy() {
			ClippedVertex copy = new ClippedVertex();
			copy.x = x;
			copy.y = y;
			copy.z = z;
			copy.localX = localX;
			copy.localY = localY;
			copy.localZ = localZ;
			copy.red = red;
			copy.green = green;
			copy.blue = blue;
			copy.alpha = alpha;
			copy.u = u;
			copy.v = v;
			copy.overlay = overlay;
			copy.light = light;
			copy.normalX = normalX;
			copy.normalY = normalY;
			copy.normalZ = normalZ;
			return copy;
		}

		private static ClippedVertex lerp(ClippedVertex from, ClippedVertex to, double t) {
			ClippedVertex result = new ClippedVertex();
			result.x = Mth.lerp(t, from.x, to.x);
			result.y = Mth.lerp(t, from.y, to.y);
			result.z = Mth.lerp(t, from.z, to.z);
			result.localX = Mth.lerp(t, from.localX, to.localX);
			result.localY = Mth.lerp(t, from.localY, to.localY);
			result.localZ = Mth.lerp(t, from.localZ, to.localZ);
			result.red = Mth.floor(Mth.lerp(t, from.red, to.red));
			result.green = Mth.floor(Mth.lerp(t, from.green, to.green));
			result.blue = Mth.floor(Mth.lerp(t, from.blue, to.blue));
			result.alpha = Mth.floor(Mth.lerp(t, from.alpha, to.alpha));
			result.u = (float) Mth.lerp(t, from.u, to.u);
			result.v = (float) Mth.lerp(t, from.v, to.v);
			result.overlay = from.overlay;
			result.light = from.light;
			result.normalX = (float) Mth.lerp(t, from.normalX, to.normalX);
			result.normalY = (float) Mth.lerp(t, from.normalY, to.normalY);
			result.normalZ = (float) Mth.lerp(t, from.normalZ, to.normalZ);
			return result;
		}
	}
}
