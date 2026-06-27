package com.nobodiiiii.createbiotech.foundation.render;

import com.nobodiiiii.createbiotech.foundation.item.RenderedLivingEntityItem;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public class RenderedLivingEntityItemRenderer<T extends LivingEntity> extends CustomRenderedItemModelRenderer {
	private static final float CAMERA_X_ROTATION_DEGREES = 12.5f;
	private static final float CAMERA_Y_ROTATION_DEGREES = 167.5f;
	private static final float MIN_AUTO_SCALE_DIMENSION = 0.6f;
	private static final Vector3f GUI_LIGHT_0 = createGuiLight(12.5f, 45.0f);
	private static final Vector3f GUI_LIGHT_1 = createGuiLight(-20.0f, 50.0f);

	private final RenderedLivingEntityItem<T> item;

	@Nullable
	private T cachedEntity;
	@Nullable
	private Level cachedLevel;

	public static <T extends LivingEntity> IClientItemExtensions create(RenderedLivingEntityItem<T> item) {
		return SimpleCustomRenderer.create(item, new RenderedLivingEntityItemRenderer<>(item));
	}

	private RenderedLivingEntityItemRenderer(RenderedLivingEntityItem<T> item) {
		this.item = item;
	}

	@Override
	protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
		ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
		Level level = Minecraft.getInstance().level;
		if (level == null)
			return;

		T entity = getOrCreateEntity(level);
		if (entity == null)
			return;

		renderEntity(entity, transformType, ms, buffer, light);
	}

	private void renderEntity(T entity, ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer,
		int packedLight) {
		DisplayParameters parameters = DisplayParameters.forContext(transformType);
		boolean guiLighting = transformType == ItemDisplayContext.GUI;
		double scale = parameters.scale() * item.getRenderedEntityScaleMultiplier() / getLargestDimension(entity);
		int appliedLight = guiLighting ? LightTexture.FULL_BRIGHT : packedLight;
		Quaternionf camera = parameters.hasAngleCorrection()
			? new Quaternionf().rotateY(CAMERA_Y_ROTATION_DEGREES * Mth.DEG_TO_RAD)
				.rotateX(parameters.cameraXRotationDegrees() * Mth.DEG_TO_RAD)
			: null;
		Quaternionf pose = camera == null ? null : new Quaternionf().rotateZ((float) Math.PI)
			.mul(camera)
			.rotateZ((float) Math.PI);

		ms.pushPose();
		if (guiLighting)
			applyInvertedGuiLighting();
		try {
			ms.translate(parameters.x(), parameters.y(), parameters.z());
			ms.scale((float) scale, (float) scale, (float) -scale);
			if (pose != null)
				ms.mulPose(pose);

			EntityRenderHelper.render(EntityRenderHelper.settings(entity)
				.preserveOrientation()
				.packedLight(appliedLight)
				.partialTicks(1.0f)
				.cameraOrientation(camera)
				.dispatcherYaw(0.0f)
				.flushBuffers(false), ms, buffer);
			if (buffer instanceof MultiBufferSource.BufferSource bufferSource)
				bufferSource.endBatch();
		} finally {
			ms.popPose();
			if (guiLighting)
				Lighting.setupFor3DItems();
		}
	}

	private float getLargestDimension(T entity) {
		EntityDimensions dimensions = entity.getDimensions(entity.getPose());
		return Math.max(Math.max(dimensions.width, dimensions.height), MIN_AUTO_SCALE_DIMENSION);
	}

	private static void applyInvertedGuiLighting() {
		RenderSystem.setShaderLights(new Vector3f(GUI_LIGHT_0), new Vector3f(GUI_LIGHT_1));
	}

	private static Vector3f createGuiLight(float yRot, float xRot) {
		Vector3f light = new Vector3f(0.0f, 0.0f, 1.0f);
		light.rotate(Axis.YP.rotationDegrees(yRot));
		light.rotate(Axis.XN.rotationDegrees(xRot));
		return light;
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

		cachedLevel = level;
		cachedEntity = entity;
		return entity;
	}

	private record DisplayParameters(double x, double y, double z, double scale, boolean hasAngleCorrection,
		float cameraXRotationDegrees) {
		// CustomRenderedItemModelRenderer already centers the item at (0.5, 0.5, 0.5),
		// so these offsets are relative to that centered origin instead of absolute block-space.
		private static final DisplayParameters GUI = new DisplayParameters(0.0d, -0.3d, 0.0d, 1.0d, true,
			-CAMERA_X_ROTATION_DEGREES);
		private static final DisplayParameters GROUND = new DisplayParameters(0.0d, -0.05d, 0.0d, 1.0d, false,
			0.0f);
		private static final DisplayParameters HAND = new DisplayParameters(0.0d, -0.05d, 0.0d, 1.0d, true,
			CAMERA_X_ROTATION_DEGREES);
		private static final DisplayParameters DEFAULT = HAND;

		private static DisplayParameters forContext(ItemDisplayContext transformType) {
			if (transformType == ItemDisplayContext.GUI)
				return GUI;
			if (transformType == ItemDisplayContext.GROUND)
				return GROUND;
			if (transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
				|| transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
				|| transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
				|| transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
				return HAND;
			return DEFAULT;
		}
	}
}
