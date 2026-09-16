package com.nobodiiiii.createbiotech.foundation.render;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalCapturedRenderPlan;
import com.nobodiiiii.createbiotech.mixin.WalkAnimationStateAccessor;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;

public final class EntityRenderHelper {
	public static final Direction DEFAULT_FACING = Direction.SOUTH;

	/** Set while a {@link #batch} is open, so nested renders skip the redundant graphics-mode swap. */
	private static boolean inFancyBatch;

	private EntityRenderHelper() {
	}

	public static <T extends Entity> RenderSettings<T> settings(T entity) {
		return new RenderSettings<>(entity);
	}

	/**
	 * Runs several {@link #render} calls under a single fancy-graphics scope. Without this every
	 * entity pays its own graphics-mode swap and lambda.
	 */
	public static void batch(Runnable batch) {
		if (inFancyBatch) {
			batch.run();
			return;
		}
		inFancyBatch = true;
		try {
			runWithFancyGraphics(batch);
		} finally {
			inFancyBatch = false;
		}
	}

	private static void runWithFancyGraphics(Runnable render) {
		if (!Minecraft.useShaderTransparency()) {
			render.run();
			return;
		}
		var graphicsMode = Minecraft.getInstance().options.graphicsMode();
		GraphicsStatus previous = graphicsMode.get();
		try {
			graphicsMode.set(GraphicsStatus.FANCY);
			render.run();
		} finally {
			graphicsMode.set(previous);
		}
	}

	/**
	 * Renders an entity with every orientation input pinned to zero, so the result
	 * depends only on the pose supplied by the caller. Used by every off-world display path
	 * (item models, GUI slots, baked box-face icons) and by the geometry
	 * measurement passes that must observe exactly what those paths draw.
	 */
	public static void renderUnoriented(Entity entity, PoseStack poseStack, MultiBufferSource buffer,
		int packedLight) {
		render(settings(entity)
			.packedLight(packedLight)
			.partialTicks(1.0f)
			.dispatcherYaw(0.0f)
			.yaw(0.0f)
			.bodyYaw(0.0f)
			.headYaw(0.0f)
			.pitch(0.0f)
			.flushBuffers(false), poseStack, buffer);
	}

	public static <T extends Entity> void render(RenderSettings<T> settings, PoseStack poseStack,
		MultiBufferSource buffer) {
		EntityRenderDispatcher dispatcher = Minecraft.getInstance()
			.getEntityRenderDispatcher();
		boolean overridesCamera = settings.cameraOrientation != null;
		Quaternionf previousCamera = overridesCamera && dispatcher.cameraOrientation() != null
			? new Quaternionf(dispatcher.cameraOrientation()) : null;
		EntityRenderState state = EntityRenderState.capture(settings.entity);

		try {
			settings.applyEntityState();
			if (overridesCamera)
				dispatcher.overrideCameraOrientation(new Quaternionf(settings.cameraOrientation).conjugate());
			if (inFancyBatch)
				renderWithAssignedRenderer(dispatcher, settings, poseStack, buffer);
			else
				runWithFancyGraphics(
					() -> renderWithAssignedRenderer(dispatcher, settings, poseStack, buffer));
			if (settings.flushBuffers && buffer instanceof MultiBufferSource.BufferSource bufferSource)
				bufferSource.endBatch();
		} finally {
			if (previousCamera != null)
				dispatcher.overrideCameraOrientation(previousCamera);
			state.restore(settings.entity);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static <T extends Entity> void renderWithAssignedRenderer(EntityRenderDispatcher dispatcher,
		RenderSettings<T> settings, PoseStack poseStack, MultiBufferSource buffer) {
		EntityRenderer renderer = dispatcher.getRenderer(settings.entity);
		if (settings.entity instanceof LivingEntity living
			&& SurgicalCapturedRenderPlan.tryRenderSlimeMimic(renderer, living, settings.dispatcherYaw,
				settings.partialTicks, poseStack, buffer, settings.packedLight))
			return;
		renderer.render(settings.entity, settings.dispatcherYaw, settings.partialTicks, poseStack, buffer,
			settings.packedLight);
	}

	public static class RenderSettings<T extends Entity> {
		private final T entity;
		private int packedLight = LightTexture.FULL_BRIGHT;
		private float partialTicks = AnimationTickHolder.getPartialTicks();
		@Nullable
		private Integer tickCountOverride;
		private float dispatcherYaw;
		private boolean flushBuffers;
		@Nullable
		private Quaternionf cameraOrientation;
		@Nullable
		private Float renderYaw;
		@Nullable
		private Float bodyYaw;
		@Nullable
		private Float headYaw;
		@Nullable
		private Float pitch;
		@Nullable
		private Float walkPosition;
		@Nullable
		private Float walkSpeed;

		private RenderSettings(T entity) {
			this.entity = entity;
			face(DEFAULT_FACING);
		}

		public RenderSettings<T> packedLight(int packedLight) {
			this.packedLight = packedLight;
			return this;
		}

		public RenderSettings<T> partialTicks(float partialTicks) {
			this.partialTicks = partialTicks;
			return this;
		}

		public RenderSettings<T> ticks(int tickCount) {
			this.tickCountOverride = tickCount;
			return this;
		}

		public RenderSettings<T> dispatcherYaw(float dispatcherYaw) {
			this.dispatcherYaw = dispatcherYaw;
			return this;
		}

		public RenderSettings<T> flushBuffers(boolean flushBuffers) {
			this.flushBuffers = flushBuffers;
			return this;
		}

		public RenderSettings<T> cameraOrientation(@Nullable Quaternionf cameraOrientation) {
			this.cameraOrientation = cameraOrientation == null ? null : new Quaternionf(cameraOrientation);
			return this;
		}

		public RenderSettings<T> yaw(float yaw) {
			this.renderYaw = yaw;
			return this;
		}

		public RenderSettings<T> bodyYaw(float bodyYaw) {
			this.bodyYaw = bodyYaw;
			return this;
		}

		public RenderSettings<T> headYaw(float headYaw) {
			this.headYaw = headYaw;
			return this;
		}

		public RenderSettings<T> pitch(float pitch) {
			this.pitch = pitch;
			return this;
		}

		/**
		 * Drives the limb swing of a proxy that is never ticked. {@code position} advances the swing
		 * cycle, {@code speed} scales its amplitude; both the previous and the current value are
		 * pinned so the result stays independent of {@link #partialTicks}.
		 */
		public RenderSettings<T> walkAnimation(float position, float speed) {
			this.walkPosition = position;
			this.walkSpeed = speed;
			return this;
		}

		public RenderSettings<T> preserveOrientation() {
			this.renderYaw = null;
			this.bodyYaw = null;
			this.headYaw = null;
			this.pitch = null;
			return this;
		}

		public RenderSettings<T> face(Direction direction) {
			float yaw = direction.toYRot();
			return yaw(yaw).bodyYaw(yaw).headYaw(yaw);
		}

		private void applyEntityState() {
			if (tickCountOverride != null)
				entity.tickCount = tickCountOverride;

			Float appliedYaw = renderYaw;
			if (appliedYaw != null) {
				entity.setYRot(appliedYaw);
				entity.yRotO = appliedYaw;
			}

			if (pitch != null) {
				entity.setXRot(pitch);
				entity.xRotO = pitch;
			}

			if (entity instanceof LivingEntity livingEntity) {
				float appliedBodyYaw = bodyYaw != null ? bodyYaw : appliedYaw != null ? appliedYaw : livingEntity.yBodyRot;
				float appliedHeadYaw = headYaw != null ? headYaw : appliedYaw != null ? appliedYaw : livingEntity.yHeadRot;
				livingEntity.setYBodyRot(appliedBodyYaw);
				livingEntity.yBodyRotO = appliedBodyYaw;
				livingEntity.yHeadRot = appliedHeadYaw;
				livingEntity.yHeadRotO = appliedHeadYaw;

				if (walkPosition != null && walkSpeed != null) {
					WalkAnimationStateAccessor walk = (WalkAnimationStateAccessor) (Object) livingEntity.walkAnimation;
					walk.createBiotech$setPosition(walkPosition);
					walk.createBiotech$setSpeedOld(walkSpeed);
					walk.createBiotech$setSpeed(walkSpeed);
				}
			}
		}
	}

	private record EntityRenderState(float yRot, float yRotO, float xRot, float xRotO, int tickCount,
		float bodyYaw, float bodyYawO, float headYaw, float headYawO, int hurtTime, int deathTime,
		float walkPosition, float walkSpeedOld, float walkSpeed, boolean living) {

		private static EntityRenderState capture(Entity entity) {
			if (entity instanceof LivingEntity livingEntity) {
				WalkAnimationState walkAnimation = livingEntity.walkAnimation;
				WalkAnimationStateAccessor walk = (WalkAnimationStateAccessor) (Object) walkAnimation;
				return new EntityRenderState(entity.getYRot(), entity.yRotO, entity.getXRot(), entity.xRotO,
					entity.tickCount, livingEntity.yBodyRot, livingEntity.yBodyRotO, livingEntity.yHeadRot,
					livingEntity.yHeadRotO, livingEntity.hurtTime, livingEntity.deathTime,
					walk.createBiotech$getPosition(), walk.createBiotech$getSpeedOld(),
					walk.createBiotech$getSpeed(), true);
			}
			return new EntityRenderState(entity.getYRot(), entity.yRotO, entity.getXRot(), entity.xRotO,
				entity.tickCount, 0, 0, 0, 0, 0, 0, 0, 0, 0, false);
		}

		private void restore(Entity entity) {
			entity.setYRot(yRot);
			entity.yRotO = yRotO;
			entity.setXRot(xRot);
			entity.xRotO = xRotO;
			entity.tickCount = tickCount;
			if (living && entity instanceof LivingEntity livingEntity) {
				livingEntity.setYBodyRot(bodyYaw);
				livingEntity.yBodyRotO = bodyYawO;
				livingEntity.yHeadRot = headYaw;
				livingEntity.yHeadRotO = headYawO;
				livingEntity.hurtTime = hurtTime;
				livingEntity.deathTime = deathTime;
				WalkAnimationStateAccessor walk = (WalkAnimationStateAccessor) (Object) livingEntity.walkAnimation;
				walk.createBiotech$setPosition(walkPosition);
				walk.createBiotech$setSpeedOld(walkSpeedOld);
				walk.createBiotech$setSpeed(walkSpeed);
			}
		}
	}
}
