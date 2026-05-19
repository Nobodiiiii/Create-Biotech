package com.nobodiiiii.createbiotech.foundation.gui;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.foundation.render.EntityRenderHelper;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.ILightingSettings;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.AbstractRenderElement;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class GuiEntityElement {

	public static final ILightingSettings DEFAULT_LIGHTING = Lighting::setupForEntityInInventory;

	private GuiEntityElement() {
	}

	public static <T extends Entity> GuiEntityRenderBuilder<T> of(T entity) {
		return new GuiEntityRenderBuilder<>(entity);
	}

	@FunctionalInterface
	public interface EntityStateModifier<T extends Entity> {
		@Nullable
		StateRestorer apply(T entity, float partialTicks);
	}

	@FunctionalInterface
	public interface StateRestorer {
		void restore();
	}

	public static class GuiEntityRenderBuilder<T extends Entity> extends AbstractRenderElement {
		private final T entity;
		private final EntityRenderHelper.RenderSettings<T> renderSettings;

		private double xLocal;
		private double yLocal;
		private double zLocal;
		private double xRot;
		private double yRot;
		private double zRot;
		private double sceneScale = 1;
		private double entityScaleX = 1;
		private double entityScaleY = 1;
		private double entityScaleZ = 1;
		private Vec3 rotationOffset = Vec3.ZERO;
		@Nullable
		private ILightingSettings customLighting = DEFAULT_LIGHTING;
		private float partialTicks = AnimationTickHolder.getPartialTicks();
		@Nullable
		private EntityStateModifier<T> stateModifier;
		@Nullable
		private Quaternionf poseOrientation;
		@Nullable
		private Quaternionf cameraOrientationOverride;

		private GuiEntityRenderBuilder(T entity) {
			this.entity = entity;
			this.renderSettings = EntityRenderHelper.settings(entity);
		}

		@Override
		public GuiEntityRenderBuilder<T> at(float x, float y) {
			super.at(x, y);
			return this;
		}

		@Override
		public GuiEntityRenderBuilder<T> at(float x, float y, float z) {
			super.at(x, y, z);
			return this;
		}

		@Override
		public GuiEntityRenderBuilder<T> withBounds(int width, int height) {
			super.withBounds(width, height);
			return this;
		}

		@Override
		public GuiEntityRenderBuilder<T> withAlpha(float alpha) {
			super.withAlpha(alpha);
			return this;
		}

		public GuiEntityRenderBuilder<T> atLocal(double x, double y, double z) {
			this.xLocal = x;
			this.yLocal = y;
			this.zLocal = z;
			return this;
		}

		public GuiEntityRenderBuilder<T> rotate(double xRot, double yRot, double zRot) {
			this.xRot = xRot;
			this.yRot = yRot;
			this.zRot = zRot;
			return this;
		}

		public GuiEntityRenderBuilder<T> rotateCentered(double xRot, double yRot, double zRot) {
			return this.rotate(xRot, yRot, zRot)
				.withRotationOffset(VecHelper.getCenterOf(BlockPos.ZERO));
		}

		public GuiEntityRenderBuilder<T> scale(double scale) {
			this.sceneScale = scale;
			return this;
		}

		public GuiEntityRenderBuilder<T> scaleEntity(double scale) {
			return scaleEntity(scale, scale, scale);
		}

		public GuiEntityRenderBuilder<T> scaleEntity(double xScale, double yScale, double zScale) {
			this.entityScaleX = xScale;
			this.entityScaleY = yScale;
			this.entityScaleZ = zScale;
			return this;
		}

		public GuiEntityRenderBuilder<T> withRotationOffset(Vec3 offset) {
			this.rotationOffset = offset;
			return this;
		}

		public GuiEntityRenderBuilder<T> lighting(ILightingSettings lighting) {
			this.customLighting = lighting;
			return this;
		}

		public GuiEntityRenderBuilder<T> packedLight(int packedLight) {
			renderSettings.packedLight(packedLight);
			return this;
		}

		public GuiEntityRenderBuilder<T> renderShadow(boolean renderShadow) {
			renderSettings.renderShadow(renderShadow);
			return this;
		}

		public GuiEntityRenderBuilder<T> partialTicks(float partialTicks) {
			this.partialTicks = partialTicks;
			renderSettings.partialTicks(partialTicks);
			return this;
		}

		public GuiEntityRenderBuilder<T> ticks(int tickCount) {
			renderSettings.ticks(tickCount);
			return this;
		}

		public GuiEntityRenderBuilder<T> dispatcherYaw(float dispatcherYaw) {
			renderSettings.dispatcherYaw(dispatcherYaw);
			return this;
		}

		public GuiEntityRenderBuilder<T> yaw(float yaw) {
			renderSettings.yaw(yaw);
			return this;
		}

		public GuiEntityRenderBuilder<T> bodyYaw(float bodyYaw) {
			renderSettings.bodyYaw(bodyYaw);
			return this;
		}

		public GuiEntityRenderBuilder<T> headYaw(float headYaw) {
			renderSettings.headYaw(headYaw);
			return this;
		}

		public GuiEntityRenderBuilder<T> pitch(float pitch) {
			renderSettings.pitch(pitch);
			return this;
		}

		public GuiEntityRenderBuilder<T> stateModifier(EntityStateModifier<T> stateModifier) {
			this.stateModifier = stateModifier;
			return this;
		}

		public GuiEntityRenderBuilder<T> poseOrientation(Quaternionf poseOrientation) {
			this.poseOrientation = new Quaternionf(poseOrientation);
			return this;
		}

		public GuiEntityRenderBuilder<T> cameraOrientation(Quaternionf cameraOrientation) {
			this.cameraOrientationOverride = new Quaternionf(cameraOrientation);
			return this;
		}

		public GuiEntityRenderBuilder<T> inventoryLike(float angleXComponent, float angleYComponent) {
			Quaternionf camera = new Quaternionf().rotateX((float) Math.toRadians(angleYComponent * 20.0F));
			Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
			pose.mul(camera);
			float yaw = 180.0F + angleXComponent * 40.0F;

			return scaleEntity(1.0d, -1.0d, -1.0d)
				.poseOrientation(pose)
				.cameraOrientation(camera)
				.bodyYaw(180.0F + angleXComponent * 20.0F)
				.yaw(yaw)
				.pitch(-angleYComponent * 20.0F)
				.headYaw(yaw)
				.dispatcherYaw(0.0F);
		}

		public GuiEntityRenderBuilder<T> face(Direction direction) {
			renderSettings.face(direction);
			return this;
		}

		@Override
		public void render(GuiGraphics graphics) {
			PoseStack poseStack = graphics.pose();
			prepareMatrix(poseStack);

			poseStack.translate(x, y, z);
			poseStack.scale((float) sceneScale, (float) sceneScale, (float) sceneScale);
			poseStack.translate(xLocal, yLocal, zLocal);
			UIRenderHelper.flipForGuiRender(poseStack);
			poseStack.scale((float) entityScaleX, (float) entityScaleY, (float) entityScaleZ);
			poseStack.translate(rotationOffset.x, rotationOffset.y, rotationOffset.z);
			poseStack.mulPose(Axis.ZP.rotationDegrees((float) zRot));
			poseStack.mulPose(Axis.XP.rotationDegrees((float) xRot));
			poseStack.mulPose(Axis.YP.rotationDegrees((float) yRot));
			poseStack.translate(-rotationOffset.x, -rotationOffset.y, -rotationOffset.z);
			if (poseOrientation != null)
				poseStack.mulPose(new Quaternionf(poseOrientation));

			Quaternionf sceneCameraOrientation = new Quaternionf()
				.rotateZ((float) Math.toRadians(-zRot))
				.rotateX((float) Math.toRadians(-xRot))
				.rotateY((float) Math.toRadians(yRot));

			StateRestorer customStateRestorer = null;
			try {
				if (stateModifier != null)
					customStateRestorer = stateModifier.apply(entity, partialTicks);
				renderSettings.cameraOrientation(cameraOrientationOverride != null ? cameraOrientationOverride : sceneCameraOrientation)
					.flushBuffers(true);
				EntityRenderHelper.render(renderSettings, poseStack, graphics.bufferSource());
			} finally {
				if (customStateRestorer != null)
					customStateRestorer.restore();
			}

			cleanUpMatrix(poseStack);
		}

		private void prepareMatrix(PoseStack poseStack) {
			poseStack.pushPose();
			RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
			RenderSystem.enableDepthTest();
			RenderSystem.enableBlend();
			RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA);
			prepareLighting();
		}

		private void cleanUpMatrix(PoseStack poseStack) {
			poseStack.popPose();
			cleanUpLighting();
		}

		private void prepareLighting() {
			if (customLighting != null) {
				customLighting.applyLighting();
			} else {
				Lighting.setupFor3DItems();
			}
		}

		private void cleanUpLighting() {
			if (customLighting != null)
				Lighting.setupFor3DItems();
		}
	}
}
