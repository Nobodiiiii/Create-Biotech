package com.nobodiiiii.createbiotech.compat.jei;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureManager;

/**
 * Runs a detached particle simulation inside a JEI scene.
 *
 * <p>Particles in a recipe preview cannot go through {@link net.minecraft.client.particle.ParticleEngine}:
 * they are not in the world, they must survive being drawn many times per frame
 * into different scenes, and they have to be posed by the scene's matrix rather
 * than the player's camera. This owns that simulation — ticking at most once per
 * game tick, and drawing through the same call sequence
 * {@code ParticleEngine#render} uses so a previewed particle looks exactly like
 * the one the block emits in the world.
 */
public final class JeiSceneParticles {

	/**
	 * Vanilla's draw order. Each type's {@code begin} sets its own blend and depth
	 * state, so drawing out of order changes how particles composite.
	 */
	private static final List<ParticleRenderType> RENDER_ORDER = List.of(
		ParticleRenderType.TERRAIN_SHEET,
		ParticleRenderType.PARTICLE_SHEET_OPAQUE,
		ParticleRenderType.PARTICLE_SHEET_LIT,
		ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT,
		ParticleRenderType.CUSTOM);

	private final List<Particle> active = new ArrayList<>();
	private final SceneCamera camera = new SceneCamera();
	private long lastTick = Long.MIN_VALUE;

	/**
	 * Advances the simulation by one tick, at most once per game tick however many
	 * times a frame calls it. Returns whether this call was the one that advanced,
	 * so callers can gate emission on it.
	 */
	public boolean advanceOnce(ClientLevel level) {
		long now = level.getGameTime();
		if (now == lastTick)
			return false;

		lastTick = now;
		Iterator<Particle> iterator = active.iterator();
		while (iterator.hasNext()) {
			Particle particle = iterator.next();
			particle.tick();
			if (!particle.isAlive())
				iterator.remove();
		}
		return true;
	}

	public void add(@Nullable Particle particle) {
		if (particle != null)
			active.add(particle);
	}

	public boolean isEmpty() {
		return active.isEmpty();
	}

	public void clear() {
		active.clear();
	}

	/**
	 * Draws every live particle into the current scene, offset by
	 * {@code (localX, localY, localZ)} blocks from the scene origin.
	 */
	public void render(GuiGraphics graphics, double sceneScale, double localX, double localY, double localZ) {
		if (active.isEmpty())
			return;

		Minecraft minecraft = Minecraft.getInstance();
		PoseStack poseStack = graphics.pose();
		poseStack.pushPose();
		poseStack.scale((float) sceneScale, (float) sceneScale, (float) sceneScale);
		poseStack.translate(localX, localY, localZ);
		UIRenderHelper.flipForGuiRender(poseStack);
		Matrix4f pose = new Matrix4f(poseStack.last()
			.pose());

		LightTexture lightTexture = minecraft.gameRenderer.lightTexture();
		TextureManager textureManager = minecraft.getTextureManager();
		Tesselator tesselator = Tesselator.getInstance();
		float partialTicks = AnimationTickHolder.getPartialTicks();

		lightTexture.turnOnLightLayer();
		RenderSystem.enableDepthTest();
		// flipForGuiRender mirrors the pose, reversing the winding of the quads that
		// Particle#render emits. The world has no such mirror and so never needs this.
		RenderSystem.disableCull();

		try {
			for (ParticleRenderType renderType : RENDER_ORDER) {
				if (!hasAnyOfType(renderType))
					continue;

				RenderSystem.setShader(GameRenderer::getParticleShader);
				BufferBuilder builder = tesselator.getBuilder();
				renderType.begin(builder, textureManager);

				VertexConsumer posed = new PosedVertexConsumer(builder, pose);
				for (Particle particle : active)
					if (particle.getRenderType() == renderType)
						particle.render(posed, camera, partialTicks);

				renderType.end(tesselator);
			}
		} finally {
			RenderSystem.depthMask(true);
			RenderSystem.disableBlend();
			RenderSystem.enableCull();
			lightTexture.turnOffLightLayer();
			poseStack.popPose();
		}
	}

	private boolean hasAnyOfType(ParticleRenderType renderType) {
		for (Particle particle : active)
			if (particle.getRenderType() == renderType)
				return true;
		return false;
	}

	/**
	 * Feeds the scene matrix into vertices that {@link Particle#render} emits in
	 * plain coordinates.
	 */
	private static class PosedVertexConsumer implements VertexConsumer {
		private final VertexConsumer delegate;
		private final Matrix4f pose;

		private PosedVertexConsumer(VertexConsumer delegate, Matrix4f pose) {
			this.delegate = delegate;
			this.pose = pose;
		}

		@Override
		public VertexConsumer vertex(double x, double y, double z) {
			delegate.vertex(pose, (float) x, (float) y, (float) z);
			return this;
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int alpha) {
			delegate.color(red, green, blue, alpha);
			return this;
		}

		@Override
		public VertexConsumer uv(float u, float v) {
			delegate.uv(u, v);
			return this;
		}

		@Override
		public VertexConsumer overlayCoords(int u, int v) {
			delegate.overlayCoords(u, v);
			return this;
		}

		@Override
		public VertexConsumer uv2(int u, int v) {
			delegate.uv2(u, v);
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			delegate.normal(x, y, z);
			return this;
		}

		@Override
		public void endVertex() {
			delegate.endVertex();
		}

		@Override
		public void defaultColor(int red, int green, int blue, int alpha) {
			delegate.defaultColor(red, green, blue, alpha);
		}

		@Override
		public void unsetDefaultColor() {
			delegate.unsetDefaultColor();
		}
	}

	/**
	 * Stands in for the player camera so billboarded particles face the scene's
	 * viewer instead of wherever the player happens to be looking.
	 */
	private static class SceneCamera extends Camera {

		/**
		 * Undoes the scene rotation, which is what leaves particle quads square-on to
		 * the JEI viewer. Derived from the scene constants so the two cannot drift
		 * apart.
		 */
		private static final Quaternionf SCENE_VIEW = new Quaternionf()
			.rotateY((float) Math.toRadians(-AnimatedKineticsWithEntities.SCENE_Y_ROTATION))
			.rotateX((float) Math.toRadians(-AnimatedKineticsWithEntities.SCENE_X_ROTATION))
			.normalize();

		private SceneCamera() {
			setPosition(0.0d, 0.0d, 0.0d);
		}

		/**
		 * Returned normalized and as a fresh instance: Embeddium scales particle quads
		 * by the quaternion's magnitude, so a drifting or shared value renders them at
		 * wildly wrong sizes.
		 */
		@Override
		public Quaternionf rotation() {
			return new Quaternionf(SCENE_VIEW);
		}
	}
}
