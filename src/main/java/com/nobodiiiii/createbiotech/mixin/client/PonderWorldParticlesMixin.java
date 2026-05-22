package com.nobodiiiii.createbiotech.mixin.client;

import java.util.Map;
import java.util.Queue;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;

import net.createmod.ponder.foundation.PonderWorldParticles;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/**
 * In ponder the scene is rendered through a PoseStack that rotates the world to the
 * isometric view angle, but the {@code Camera} handed to particles only has its rotation
 * tweaked by {@code SceneCamera.set(xRot+90, yRot+180)} — and that tweak is applied to a
 * camera whose position is the player's real MC position. The two transforms compose into
 * a fixed (and usually wrong) billboard orientation, so {@code minecraft:explosion} ends
 * up looking like a horizontal disc.
 *
 * <p>This mixin replaces the camera passed to {@code Particle#render} with one whose
 * rotation is the inverse of the rotation portion of the modelview matrix — i.e. it
 * pre-cancels the upcoming scene rotation so the final quad ends up facing the viewer
 * regardless of how the user spins the ponder view.
 */
@Mixin(value = PonderWorldParticles.class, remap = false)
public abstract class PonderWorldParticlesMixin {

	@Shadow
	private Map<ParticleRenderType, Queue<Particle>> byType;

	@Inject(method = "renderParticles", at = @At("HEAD"), cancellable = true)
	private void createBiotech$cameraFacingBillboards(PoseStack ms, MultiBufferSource buffer, Camera renderInfo,
		float pt, CallbackInfo ci) {
		ci.cancel();

		Minecraft mc = Minecraft.getInstance();
		LightTexture lightTexture = mc.gameRenderer.lightTexture();

		lightTexture.turnOnLightLayer();
		RenderSystem.enableDepthTest();
		PoseStack modelViewStack = RenderSystem.getModelViewStack();
		modelViewStack.pushPose();
		modelViewStack.mulPoseMatrix(ms.last().pose());
		RenderSystem.applyModelViewMatrix();

		Camera billboardCamera = createBiotech$buildBillboardCamera(ms.last().pose(), renderInfo);

		for (ParticleRenderType type : byType.keySet()) {
			if (type == ParticleRenderType.NO_RENDER)
				continue;
			Iterable<Particle> iterable = byType.get(type);
			if (iterable == null)
				continue;
			RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
			RenderSystem.setShader(GameRenderer::getParticleShader);
			Tesselator tessellator = Tesselator.getInstance();
			BufferBuilder bufferbuilder = tessellator.getBuilder();
			type.begin(bufferbuilder, mc.getTextureManager());
			for (Particle particle : iterable)
				particle.render(bufferbuilder, billboardCamera, pt);
			type.end(tessellator);
		}

		modelViewStack.popPose();
		RenderSystem.applyModelViewMatrix();
		RenderSystem.depthMask(true);
		RenderSystem.disableBlend();
		lightTexture.turnOffLightLayer();
	}

	private static Camera createBiotech$buildBillboardCamera(Matrix4f sceneMatrix, Camera fallback) {
		// Extract the rotation portion of the scene matrix and invert it.
		// The scene matrix maps "scene world" → "GUI screen" with whatever pitch/yaw the
		// user has set. Applying its inverse rotation to the particle quad means the
		// scene's rotation will undo it during render, leaving a screen-facing quad.
		Matrix3f rot3 = new Matrix3f(sceneMatrix);
		Quaternionf sceneRotation = rot3.normal().getNormalizedRotation(new Quaternionf());
		Quaternionf inverse = sceneRotation.invert(new Quaternionf());

		BillboardCamera cam = new BillboardCamera();
		cam.createBiotech$setBillboardRotation(inverse);
		// Position stays (0,0,0) like SceneCamera's default so particle vertex offsets
		// (particle_pos - camera_pos) are just particle positions in scene coords.
		Vec3 fallbackPos = fallback == null ? Vec3.ZERO : fallback.getPosition();
		cam.createBiotech$setPosition(fallbackPos);
		return cam;
	}

	/**
	 * Public stand-in Camera whose rotation we can set directly. Lives in the same file
	 * as the mixin (top-level non-public class) so the mixin processor doesn't choke.
	 */
	private static final class BillboardCamera extends Camera {
		private final Quaternionf billboardRotation = new Quaternionf();
		private Vec3 billboardPosition = Vec3.ZERO;

		void createBiotech$setBillboardRotation(Quaternionf q) {
			this.billboardRotation.set(q);
		}

		void createBiotech$setPosition(Vec3 pos) {
			this.billboardPosition = pos;
		}

		@Override
		public Quaternionf rotation() {
			return this.billboardRotation;
		}

		@Override
		public Vec3 getPosition() {
			return this.billboardPosition;
		}
	}
}
