package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterBlockEntity;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterSquidVisual;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.AllBlocks;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.platform.ForgeCatnipServices;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.fluids.FluidStack;
import org.joml.Quaternionf;

public class AnimatedSquidSpout extends AnimatedKineticsWithEntities {
	private static final double SQUID_ATTACHMENT_Y = SquidPrinterSquidVisual.HEAD_TOP_Y;
	private static final int SCENE_SCALE = 20;
	private static final BlockPos PARTICLE_ORIGIN = BlockPos.ZERO;
	private static final double JEI_INK_RANGE_SCALE = 0.5d;
	private static final float JEI_INK_SIZE_SCALE = 0.5f;
	private static final double BURST_CENTER_X = 0.5d;
	private static final double BURST_CENTER_Y = -0.5d;
	private static final double BURST_CENTER_Z = 0.5d;
	private static final double AMBIENT_CENTER_X = 0.5d;
	private static final double AMBIENT_CENTER_Y = 0.05d;
	private static final double AMBIENT_CENTER_Z = 0.5d;
	private static final int BURST_PHASE_TICKS = 3;
	private static final int CYCLE_LENGTH_TICKS = 30;

	private List<FluidStack> fluids;
	private final List<Particle> activeParticles = new ArrayList<>();
	private long lastParticleTick = Long.MIN_VALUE;
	private final PreviewCamera jeiParticleCamera = new PreviewCamera();

	public AnimatedSquidSpout withFluids(List<FluidStack> fluids) {
		this.fluids = fluids;
		return this;
	}

	@Override
	public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
		PoseStack matrixStack = graphics.pose();
		matrixStack.pushPose();
		matrixStack.translate(xOffset, yOffset, 100);

		matrixStack.mulPose(Axis.XP.rotationDegrees(-15.5f));
		matrixStack.mulPose(Axis.YP.rotationDegrees(22.5f));
		int scale = SCENE_SCALE;

		blockElement(CBBlocks.SQUID_PRINTER.get()
			.defaultBlockState())
			.scale(scale)
			.render(graphics);

		SquidJeiRenderer.renderOpenInScene(graphics, 0.5d, SQUID_ATTACHMENT_Y, 0.5d, scale);

		blockElement(AllBlocks.DEPOT.getDefaultState())
			.atLocal(0, 2, 0)
			.scale(scale)
			.render(graphics);

		DEFAULT_LIGHTING.applyLighting();
		BufferSource buffer = MultiBufferSource.immediate(Tesselator.getInstance()
			.getBuilder());
		matrixStack.pushPose();
		UIRenderHelper.flipForGuiRender(matrixStack);
		matrixStack.scale(16, 16, 16);
		float from = 3f / 16f;
		float to = 17f / 16f;
		FluidStack fluidStack = fluids.get(0);
		ForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluidStack, from, from, from, to, to, to,
			graphics.bufferSource(), matrixStack, LightTexture.FULL_BRIGHT, false, true);
		matrixStack.popPose();

		renderInkParticles(graphics);
		buffer.endBatch();
		Lighting.setupFor3DItems();

		matrixStack.popPose();
	}

	private void renderInkParticles(GuiGraphics graphics) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null)
			return;

		syncInkParticles(level);
		if (activeParticles.isEmpty())
			return;

		Camera camera = setupJeiParticleCamera();
		PoseStack poseStack = graphics.pose();
		poseStack.pushPose();
		poseStack.scale(SCENE_SCALE, SCENE_SCALE, SCENE_SCALE);
		UIRenderHelper.flipForGuiRender(poseStack);

		ParticleRenderType renderType = ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
		LightTexture lightTexture = Minecraft.getInstance().gameRenderer.lightTexture();
		lightTexture.turnOnLightLayer();
		PoseStack modelView = RenderSystem.getModelViewStack();
		modelView.pushPose();
		modelView.mulPoseMatrix(poseStack.last().pose());
		RenderSystem.applyModelViewMatrix();
		RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
		RenderSystem.disableCull();
		RenderSystem.enableDepthTest();
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
		RenderSystem.depthMask(true);

		try {
			BufferBuilder builder = Tesselator.getInstance().getBuilder();
			RenderSystem.setShader(GameRenderer::getParticleShader);
			renderType.begin(builder, Minecraft.getInstance().textureManager);
			float partialTicks = AnimationTickHolder.getPartialTicks();
			for (Particle particle : activeParticles)
				particle.render(builder, camera, partialTicks);
			renderType.end(Tesselator.getInstance());
		} finally {
			modelView.popPose();
			RenderSystem.applyModelViewMatrix();
			RenderSystem.enableCull();
			RenderSystem.disableBlend();
			lightTexture.turnOffLightLayer();
		}

		poseStack.popPose();
	}

	private void syncInkParticles(ClientLevel level) {
		long currentTick = level.getGameTime();
		if (currentTick == lastParticleTick)
			return;

		lastParticleTick = currentTick;
		Iterator<Particle> iterator = activeParticles.iterator();
		while (iterator.hasNext()) {
			Particle particle = iterator.next();
			particle.tick();
			if (!particle.isAlive())
				iterator.remove();
		}

		int cycleTick = (int) ((AnimationTickHolder.getTicks() - offset * 8) % CYCLE_LENGTH_TICKS);
		if (cycleTick < 0)
			cycleTick += CYCLE_LENGTH_TICKS;

		if (cycleTick >= BURST_PHASE_TICKS)
			SquidPrinterBlockEntity.forEachBurstInkParticle(level, PARTICLE_ORIGIN,
				(x, y, z, dx, dy, dz) -> spawnScaledInkParticle(x, y, z, dx, dy, dz, BURST_CENTER_X, BURST_CENTER_Y,
					BURST_CENTER_Z));
		if (currentTick % 3 == 0 && cycleTick >= BURST_PHASE_TICKS)
			SquidPrinterBlockEntity.forEachAmbientInkParticle(level, PARTICLE_ORIGIN,
				(x, y, z, dx, dy, dz) -> spawnScaledInkParticle(x, y, z, dx, dy, dz, AMBIENT_CENTER_X,
					AMBIENT_CENTER_Y, AMBIENT_CENTER_Z));
	}

	private void spawnScaledInkParticle(double x, double y, double z, double dx, double dy, double dz,
		double anchorX, double anchorY, double anchorZ) {
		double scaledX = anchorX + (x - anchorX) * JEI_INK_RANGE_SCALE;
		double scaledY = anchorY + (y - anchorY) * JEI_INK_RANGE_SCALE;
		double scaledZ = anchorZ + (z - anchorZ) * JEI_INK_RANGE_SCALE;
		double scaledDx = dx * JEI_INK_RANGE_SCALE;
		double scaledDy = dy * JEI_INK_RANGE_SCALE;
		double scaledDz = dz * JEI_INK_RANGE_SCALE;

		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null)
			return;
		Particle particle = minecraft.particleEngine.createParticle(ParticleTypes.SQUID_INK, scaledX, scaledY,
			scaledZ, scaledDx, scaledDy, scaledDz);
		if (particle != null) {
			particle.scale(JEI_INK_SIZE_SCALE);
			activeParticles.add(particle);
		}
	}

	private Camera setupJeiParticleCamera() {
		Quaternionf inverseSceneRotation = new Quaternionf()
			.rotateY((float) Math.toRadians(-22.5f))
			.rotateX((float) Math.toRadians(15.5f));
		jeiParticleCamera.configure(0.0d, 0.0d, 0.0d, inverseSceneRotation);
		return jeiParticleCamera;
	}

	private static class PreviewCamera extends Camera {
		private Quaternionf rotation = new Quaternionf();

		private void configure(double x, double y, double z, Quaternionf rotation) {
			super.setPosition(x, y, z);
			this.rotation = new Quaternionf(rotation);
		}

		@Override
		public Quaternionf rotation() {
			return new Quaternionf(rotation);
		}
	}
}
