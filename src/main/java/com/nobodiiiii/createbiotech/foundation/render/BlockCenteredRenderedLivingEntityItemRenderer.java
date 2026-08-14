package com.nobodiiiii.createbiotech.foundation.render;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.foundation.item.RenderedLivingEntityItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Renders an entity in block-model coordinates. The center of the entity's actual
 * rendered vertices is mapped to the center of a unit cube, so vanilla block-item
 * transforms rotate and offset every display context around the same point.
 */
public class BlockCenteredRenderedLivingEntityItemRenderer<T extends LivingEntity>
	extends BlockEntityWithoutLevelRenderer {

	private static final Vector3f BLOCK_CENTER = new Vector3f(0.5f, 0.5f, 0.5f);
	private static final float BASE_AUTO_RENDER_SCALE = 1.5f;
	private static final float MAX_AUTO_RENDER_SCALE = 1.5f;
	private static final float DEFAULT_ENTITY_Y_ROTATION = 90.0f;
	private static final float FIXED_ENTITY_Y_ROTATION = 180.0f;

	private final RenderedLivingEntityItem<T> item;

	@Nullable
	private T cachedEntity;
	@Nullable
	private Level cachedLevel;

	public static <T extends LivingEntity> IClientItemExtensions create(RenderedLivingEntityItem<T> item) {
		return new IClientItemExtensions() {
			private final BlockEntityWithoutLevelRenderer renderer =
				new BlockCenteredRenderedLivingEntityItemRenderer<>(item);

			@Override
			public BlockEntityWithoutLevelRenderer getCustomRenderer() {
				return renderer;
			}
		};
	}

	private BlockCenteredRenderedLivingEntityItemRenderer(RenderedLivingEntityItem<T> item) {
		super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
		this.item = item;
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int overlay) {
		Level level = Minecraft.getInstance().level;
		if (level == null)
			return;

		T entity = getOrCreateEntity(level);
		if (entity == null)
			return;

		item.configureRenderedEntityForGeometryMeasurement(entity, stack, transformType);
		Vector3f geometryCenter = measureGeometryCenter(entity);
		item.configureRenderedEntity(entity, stack, transformType);
		float scaleMultiplier = item.getRenderedEntityScaleMultiplier();
		float yRotation = getBaseYRotation(transformType);
		yRotation += item.getRenderedEntityYRotation(stack, transformType);

		poseStack.pushPose();
		renderBlockCenteredEntity(entity, geometryCenter, scaleMultiplier, yRotation, poseStack, buffer, packedLight);
		poseStack.popPose();
	}

	/**
	 * Renders arbitrary living-entity geometry through the same block-centered path
	 * used by block-centered entity items.
	 */
	public static void renderBlockCenteredEntity(LivingEntity entity, float scaleMultiplier, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight) {
		Vector3f geometryCenter = measureGeometryCenter(entity);
		poseStack.pushPose();
		renderBlockCenteredEntity(entity, geometryCenter, scaleMultiplier, DEFAULT_ENTITY_Y_ROTATION, poseStack, buffer,
			packedLight);
		poseStack.popPose();
	}

	/**
	 * Renders an arbitrary entity in a GUI slot after applying the template item's
	 * actual GUI display transform.
	 */
	public static void renderAutoScaledGuiEntityItem(GuiGraphics graphics, ItemStack transformStack,
		LivingEntity entity, float scaleMultiplier, int x, int y) {
		if (transformStack.isEmpty())
			return;

		Minecraft minecraft = Minecraft.getInstance();
		BakedModel model = minecraft.getItemRenderer()
			.getModel(transformStack, minecraft.level, minecraft.player, 0);
		PoseStack poseStack = graphics.pose();
		poseStack.pushPose();
		poseStack.translate(x + 8.0f, y + 8.0f, 150.0f);
		poseStack.mulPoseMatrix(new Matrix4f().scaling(1.0f, -1.0f, 1.0f));
		poseStack.scale(16.0f, 16.0f, 16.0f);
		ForgeHooksClient.handleCameraTransforms(poseStack, model, ItemDisplayContext.GUI, false);
		poseStack.translate(-0.5f, -0.5f, -0.5f);
		float renderScale = getGuiProjectedAutoScale(entity, scaleMultiplier, poseStack);
		renderBlockCenteredEntity(entity, renderScale, poseStack, graphics.bufferSource(), LightTexture.FULL_BRIGHT);
		graphics.flush();
		poseStack.popPose();
	}

	private static float getGuiProjectedAutoScale(LivingEntity entity, float scaleMultiplier, PoseStack poseStack) {
		GeometryBounds bounds = new GeometryBounds();
		MultiBufferSource measuringBuffer = renderType -> new GeometryBoundsVertexConsumer(bounds);
		renderBlockCenteredEntity(entity, 1.0f, poseStack, measuringBuffer, LightTexture.FULL_BRIGHT);
		if (!bounds.hasVertices())
			return scaleMultiplier;

		float poseScale = poseStack.last()
			.pose()
			.transformDirection(1.0f, 0.0f, 0.0f, new Vector3f())
			.length();
		if (poseScale <= 1.0e-6f)
			return scaleMultiplier;

		float projectedDimension = Math.max(bounds.sizeX(), bounds.sizeY()) / poseScale;
		if (projectedDimension <= 1.0e-6f)
			return scaleMultiplier;
		return Math.min(BASE_AUTO_RENDER_SCALE / projectedDimension, MAX_AUTO_RENDER_SCALE) * scaleMultiplier;
	}

	private static void renderBlockCenteredEntity(LivingEntity entity, Vector3f geometryCenter, float scaleMultiplier,
		float yRotation, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		poseStack.translate(BLOCK_CENTER.x, BLOCK_CENTER.y, BLOCK_CENTER.z);
		poseStack.mulPose(Axis.YP.rotationDegrees(yRotation));
		poseStack.scale(scaleMultiplier, scaleMultiplier, scaleMultiplier);
		poseStack.translate(-geometryCenter.x, -geometryCenter.y, -geometryCenter.z);
		renderRawEntity(entity, poseStack, buffer, packedLight);
	}

	private static float getBaseYRotation(ItemDisplayContext displayContext) {
		if (displayContext == ItemDisplayContext.FIXED)
			return FIXED_ENTITY_Y_ROTATION;
		if (displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
			|| displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
			return -DEFAULT_ENTITY_Y_ROTATION;
		return DEFAULT_ENTITY_Y_ROTATION;
	}

	private static Vector3f measureGeometryCenter(LivingEntity entity) {
		GeometryBounds bounds = measureGeometryBounds(entity);
		if (bounds.hasVertices())
			return bounds.center();

		EntityDimensions dimensions = entity.getDimensions(entity.getPose());
		return new Vector3f(0, dimensions.height / 2.0f, 0);
	}

	private static GeometryBounds measureGeometryBounds(LivingEntity entity) {
		GeometryBounds bounds = new GeometryBounds();
		MultiBufferSource measuringBuffer = renderType -> new GeometryBoundsVertexConsumer(bounds);
		renderRawEntity(entity, new PoseStack(), measuringBuffer, LightTexture.FULL_BRIGHT);
		return bounds;
	}

	private static void renderRawEntity(LivingEntity entity, PoseStack poseStack, MultiBufferSource buffer,
		int packedLight) {
		EntityRenderHelper.render(EntityRenderHelper.settings(entity)
			.packedLight(packedLight)
			.partialTicks(1.0f)
			.dispatcherYaw(0.0f)
			.yaw(0.0f)
			.bodyYaw(0.0f)
			.headYaw(0.0f)
			.pitch(0.0f)
			.flushBuffers(false), poseStack, buffer);
	}

	@Nullable
	private T getOrCreateEntity(Level level) {
		if (cachedEntity != null && cachedLevel == level)
			return cachedEntity;

		T entity = item.getRenderedEntityType().create(level);
		if (entity == null)
			return null;

		item.configureRenderedEntity(entity);
		if (entity instanceof Mob mob)
			mob.setNoAi(true);
		entity.setSilent(true);
		entity.setOnGround(true);
		entity.tickCount = 0;
		entity.hurtTime = 0;
		entity.deathTime = 0;
		entity.setYRot(0.0f);
		entity.yRotO = 0.0f;
		entity.setXRot(0.0f);
		entity.xRotO = 0.0f;
		entity.setYBodyRot(0.0f);
		entity.yBodyRotO = 0.0f;
		entity.yHeadRot = 0.0f;
		entity.yHeadRotO = 0.0f;

		cachedLevel = level;
		cachedEntity = entity;
		return entity;
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

		private Vector3f center() {
			return new Vector3f((minX + maxX) / 2.0f, (minY + maxY) / 2.0f, (minZ + maxZ) / 2.0f);
		}

		private float sizeX() {
			return maxX - minX;
		}

		private float sizeY() {
			return maxY - minY;
		}
	}

	private static class GeometryBoundsVertexConsumer implements VertexConsumer {
		private final GeometryBounds bounds;

		private GeometryBoundsVertexConsumer(GeometryBounds bounds) {
			this.bounds = bounds;
		}

		@Override
		public VertexConsumer vertex(double x, double y, double z) {
			bounds.include(new Vector3f((float) x, (float) y, (float) z));
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
}
