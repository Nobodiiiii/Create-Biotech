package com.nobodiiiii.createbiotech.content.cardboardbox;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.foundation.render.BlockEntityModelElement;
import com.nobodiiiii.createbiotech.foundation.render.RenderedLivingEntityItemRenderer;
import com.nobodiiiii.createbiotech.foundation.render.RenderedLivingEntityItemRenderer.EntityRenderTuning;
import com.nobodiiiii.createbiotech.registry.CBItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.ForgeHooksClient;

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
	private static final float ICON_SCALE = 0.6f;
	private static final float GUI_CENTERING_X = -2.0f / 16.0f;
	private static final float GUI_CENTERING_Y = 4.0f / 16.0f;
	private static final float SQUID_ENTITY_FOOT_Y_OFFSET = 1.1f;
	private static final float ITEM_PLANE_TO_FACE_Y_ROT = itemPlaneToFaceYRot(ICON_FACE);
	private static final ItemStack ENTITY_ITEM_TRANSFORM = new ItemStack(CBItems.CAPTURED_SMALL_SLIME.get());

	@Nullable
	private static ItemStack cachedStack;
	@Nullable
	private static Level cachedLevel;
	@Nullable
	private static LivingEntity cachedCapturedEntity;

	private CapturedEntityBoxIconRenderer() {}

	public static void renderOnEntity(ItemStack stack, float yaw, PoseStack poseStack, MultiBufferSource buffer,
		int light) {
		render(stack, poseStack, buffer, light, yaw);
	}

	public static void renderOnItem(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer, int light) {
		poseStack.pushPose();
		poseStack.translate(0.0f, -0.5f, 0.0f);
		render(stack, poseStack, buffer, light, -90.0f);
		poseStack.popPose();
	}

	private static void render(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer, int light, float yaw) {
		if (!CapturedEntityBoxHelper.hasCapturedEntity(stack))
			return;

		LivingEntity capturedEntity = getOrCreateCapturedEntity(stack);
		if (capturedEntity == null)
			return;

		FaceBounds face = FaceBounds.of(stack);

		BlockEntityModelElement.builder()
			.atLocal(-0.5f, 0.0f, -0.5f)
			.rotateCentered(0.0f, -yaw - 90.0f, 0.0f)
			.packedLight(light)
			.render(poseStack, buffer, (iconPoseStack, iconBuffer, packedLight) -> {
				Matrix4f boxToRender = new Matrix4f(iconPoseStack.last()
					.pose());

				applyEntityItemTransform(iconPoseStack, face);
				EntityRenderTuning tuning = getEntityRenderTuning(capturedEntity);
				FaceAlignment alignment =
					measureGeometryAlignment(capturedEntity, tuning, iconPoseStack, boxToRender, face, packedLight);
				MultiBufferSource clippedBuffer =
					renderType -> new FaceClippingVertexConsumer(iconBuffer.getBuffer(renderType), boxToRender, face,
						alignment);
				RenderedLivingEntityItemRenderer.renderEntity(capturedEntity, tuning.scaleMultiplier(),
					tuning.footYOffset(), iconPoseStack, clippedBuffer, packedLight);
			});
	}

	private static FaceAlignment measureGeometryAlignment(LivingEntity entity, EntityRenderTuning tuning,
		PoseStack poseStack, Matrix4f boxToRender, FaceBounds face, int packedLight) {
		GeometryBounds bounds = new GeometryBounds();
		Matrix4f renderToBox = new Matrix4f(boxToRender).invert();
		EntityDimensions dimensions = entity.getDimensions(entity.getPose());
		float renderScale = RenderedLivingEntityItemRenderer.getEntityRenderScale(entity, tuning.scaleMultiplier());
		float collisionHalfWidth = dimensions.width * renderScale / 2.0f;
		GeometryBounds horizontalCenterBounds =
			measureCollisionHorizontalBounds(poseStack, renderToBox, collisionHalfWidth);
		MultiBufferSource measuringBuffer = renderType -> new GeometryBoundsVertexConsumer(renderToBox, bounds);

		RenderedLivingEntityItemRenderer.renderEntity(entity, tuning.scaleMultiplier(), tuning.footYOffset(), poseStack,
			measuringBuffer, packedLight);
		if (!bounds.hasVertices())
			return FaceAlignment.none();

		float xAlignment = face.x() - horizontalCenterBounds.centerX();
		return new FaceAlignment(xAlignment, face.centerY() - bounds.centerY(),
			face.centerZ() - horizontalCenterBounds.centerZ(),
			bounds.minX() + xAlignment, bounds.maxX() + xAlignment);
	}

	private static GeometryBounds measureCollisionHorizontalBounds(PoseStack poseStack, Matrix4f renderToBox,
		float collisionHalfWidth) {
		GeometryBounds bounds = new GeometryBounds();
		Matrix4f entityBaseToBox = new Matrix4f(renderToBox).mul(poseStack.last()
			.pose());

		includeCollisionCorner(bounds, entityBaseToBox, -collisionHalfWidth, -collisionHalfWidth);
		includeCollisionCorner(bounds, entityBaseToBox, -collisionHalfWidth, collisionHalfWidth);
		includeCollisionCorner(bounds, entityBaseToBox, collisionHalfWidth, -collisionHalfWidth);
		includeCollisionCorner(bounds, entityBaseToBox, collisionHalfWidth, collisionHalfWidth);
		return bounds;
	}

	private static void includeCollisionCorner(GeometryBounds bounds, Matrix4f entityBaseToBox, float x, float z) {
		bounds.include(entityBaseToBox.transformPosition(x, 0.0f, z, new Vector3f()));
	}

	private static EntityRenderTuning getEntityRenderTuning(LivingEntity entity) {
		EntityType<?> type = entity.getType();
		if (type == EntityType.SQUID || type == EntityType.GLOW_SQUID)
			return new EntityRenderTuning(1.0f, SQUID_ENTITY_FOOT_Y_OFFSET);
		return new EntityRenderTuning(1.0f, 0.0f);
	}

	private static void applyEntityItemTransform(PoseStack poseStack, FaceBounds face) {
		Minecraft minecraft = Minecraft.getInstance();
		BakedModel model = minecraft.getItemRenderer()
			.getModel(ENTITY_ITEM_TRANSFORM, minecraft.level, minecraft.player, 0);

		poseStack.translate(face.x + FACE_OFFSET, face.centerY(), face.centerZ());
		poseStack.mulPose(Axis.YP.rotationDegrees(ITEM_PLANE_TO_FACE_Y_ROT));
		float scale = face.itemScale() * ICON_SCALE;
		poseStack.scale(scale, scale, scale);
		poseStack.translate(GUI_CENTERING_X, GUI_CENTERING_Y, 0.0f);
		ForgeHooksClient.handleCameraTransforms(poseStack, model, ItemDisplayContext.GUI, false);
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

	@Nullable
	private static LivingEntity getOrCreateCapturedEntity(ItemStack stack) {
		Level level = Minecraft.getInstance().level;
		if (level == null)
			return null;
		if (cachedCapturedEntity != null && cachedLevel == level && cachedStack != null
			&& ItemStack.isSameItemSameTags(cachedStack, stack))
			return cachedCapturedEntity;

		Entity entity = CapturedEntityBoxHelper.createCapturedEntity(stack, level);
		if (!(entity instanceof LivingEntity livingEntity))
			return null;

		if (livingEntity instanceof Mob mob)
			mob.setNoAi(true);
		livingEntity.setSilent(true);
		livingEntity.setOnGround(true);
		livingEntity.tickCount = 0;
		livingEntity.hurtTime = 0;
		livingEntity.deathTime = 0;
		livingEntity.hurtMarked = false;

		cachedLevel = level;
		cachedStack = stack.copy();
		cachedCapturedEntity = livingEntity;
		return livingEntity;
	}

	private record FaceBounds(float x, float minY, float maxY, float minZ, float maxZ) {
		private static FaceBounds of(ItemStack stack) {
			if (stack.is(CBItems.LARGE_CARDBOARD_BOX.get()))
				return new FaceBounds(LARGE_BOX_MAX, 0.0f, LARGE_BOX_HEIGHT, LARGE_BOX_MIN, LARGE_BOX_MAX);
			return new FaceBounds(SMALL_BOX_MAX, 0.0f, SMALL_BOX_HEIGHT, SMALL_BOX_MIN, SMALL_BOX_MAX);
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

	private static class GeometryBounds {
		private float minX = Float.POSITIVE_INFINITY;
		private float minY = Float.POSITIVE_INFINITY;
		private float minZ = Float.POSITIVE_INFINITY;
		private float maxX = Float.NEGATIVE_INFINITY;
		private float maxY = Float.NEGATIVE_INFINITY;
		private float maxZ = Float.NEGATIVE_INFINITY;

		private void include(Vector3f vertex) {
			minX = Math.min(minX, vertex.x());
			minY = Math.min(minY, vertex.y());
			minZ = Math.min(minZ, vertex.z());
			maxX = Math.max(maxX, vertex.x());
			maxY = Math.max(maxY, vertex.y());
			maxZ = Math.max(maxZ, vertex.z());
		}

		private boolean hasVertices() {
			return minX != Float.POSITIVE_INFINITY;
		}

		private float centerX() {
			return (minX + maxX) / 2.0f;
		}

		private float minX() {
			return minX;
		}

		private float maxX() {
			return maxX;
		}

		private float centerY() {
			return (minY + maxY) / 2.0f;
		}

		private float centerZ() {
			return (minZ + maxZ) / 2.0f;
		}
	}

	private static class GeometryBoundsVertexConsumer implements VertexConsumer {
		private final Matrix4f renderToBox;
		private final GeometryBounds bounds;

		private GeometryBoundsVertexConsumer(Matrix4f renderToBox, GeometryBounds bounds) {
			this.renderToBox = renderToBox;
			this.bounds = bounds;
		}

		@Override
		public VertexConsumer vertex(double x, double y, double z) {
			Vector3f boxLocal = renderToBox.transformPosition((float) x, (float) y, (float) z, new Vector3f());
			bounds.include(boxLocal);
			return this;
		}

		@Override
		public VertexConsumer vertex(Matrix4f matrix, float x, float y, float z) {
			return vertex(matrix.transformPosition(x, y, z, new Vector3f()));
		}

		private VertexConsumer vertex(Vector3f vec) {
			return vertex(vec.x(), vec.y(), vec.z());
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int alpha) {
			return this;
		}

		@Override
		public VertexConsumer uv(float u, float v) {
			return this;
		}

		@Override
		public VertexConsumer overlayCoords(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer uv2(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			return this;
		}

		@Override
		public void endVertex() {
		}

		@Override
		public void defaultColor(int red, int green, int blue, int alpha) {
		}

		@Override
		public void unsetDefaultColor() {
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
		public VertexConsumer vertex(double x, double y, double z) {
			current = new ClippedVertex();
			current.x = x;
			current.y = y;
			current.z = z;
			Vector3f local = renderToBox.transformPosition((float) x, (float) y, (float) z, new Vector3f());
			current.localX = local.x() + alignment.x();
			current.localY = local.y() + alignment.y();
			current.localZ = local.z() + alignment.z();
			return this;
		}

		@Override
		public VertexConsumer vertex(Matrix4f matrix, float x, float y, float z) {
			return vertex(matrix.transformPosition(x, y, z, new Vector3f()));
		}

		private VertexConsumer vertex(Vector3f vec) {
			return vertex(vec.x(), vec.y(), vec.z());
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int alpha) {
			current.red = red;
			current.green = green;
			current.blue = blue;
			current.alpha = alpha;
			return this;
		}

		@Override
		public VertexConsumer uv(float u, float v) {
			current.u = u;
			current.v = v;
			return this;
		}

		@Override
		public VertexConsumer overlayCoords(int u, int v) {
			current.overlay = u | v << 16;
			return this;
		}

		@Override
		public VertexConsumer uv2(int u, int v) {
			current.light = u | v << 16;
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			current.normalX = x;
			current.normalY = y;
			current.normalZ = z;
			return this;
		}

		@Override
		public void endVertex() {
			quad.add(current.copy());
			if (quad.size() < 4)
				return;
			emitClippedQuad(quad);
			quad.clear();
		}

		@Override
		public void defaultColor(int red, int green, int blue, int alpha) {
			wrapped.defaultColor(red, green, blue, alpha);
		}

		@Override
		public void unsetDefaultColor() {
			wrapped.unsetDefaultColor();
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
			wrapped.vertex(projected.x(), projected.y(), projected.z())
				.color(vertex.red, vertex.green, vertex.blue, vertex.alpha)
				.uv(vertex.u, vertex.v)
				.overlayCoords(vertex.overlay)
				.uv2(vertex.light)
				.normal(vertex.normalX, vertex.normalY, vertex.normalZ)
				.endVertex();
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
