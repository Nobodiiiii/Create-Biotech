package com.nobodiiiii.createbiotech.compat.jei;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberBlock;
import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberBlockEntity;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBParticleTypes;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.platform.CatnipClientServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.joml.Quaternionf;

public class AnimatedEvokerEnchanting extends AnimatedKineticsWithEntities {

	private static final float RENDER_SCALE = 20f;
	private static final int RENDER_Z = 100;
	private static final double RENDER_Y_OFFSET_BLOCKS = 2.0d;
	private static final int PREVIEW_STORED_FLUID = 1;
	private static final int PREVIEW_FLUID_TOTAL = 1;
	private static final int DISPLAY_TRANSFORM_CYCLE = 80;
	private static final float OUTPUT_PHASE_START = 0.72f;

	private ItemStack inputCopy = ItemStack.EMPTY;
	private ItemStack outputBook = ItemStack.EMPTY;
	@Nullable
	private EvokerEnchantingChamberBlockEntity cachedBlockEntity;
	@Nullable
	private ClientLevel cachedLevel;
	private final List<Particle> activeParticles = new ArrayList<>();
	private long lastParticleTick = Long.MIN_VALUE;
	private final PreviewCamera jeiParticleCamera = new PreviewCamera();

	public AnimatedEvokerEnchanting withItems(ItemStack input, ItemStack output) {
		inputCopy = input.copy();
		outputBook = output.copy();
		return this;
	}

	@Override
	public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
		ClientLevel level = Minecraft.getInstance().level;
		EvokerEnchantingChamberBlockEntity blockEntity = getOrCreateBlockEntity(level);
		if (level == null || blockEntity == null)
			return;

		updatePreviewState(blockEntity);

		var poseStack = graphics.pose();
		poseStack.pushPose();
		poseStack.translate(xOffset, yOffset, RENDER_Z);
		poseStack.mulPose(Axis.XP.rotationDegrees(-15.5f));
		poseStack.mulPose(Axis.YP.rotationDegrees(22.5f));

		GuiGameElement.of(blockEntity)
			.lighting(DEFAULT_LIGHTING)
			.atLocal(0.0d, RENDER_Y_OFFSET_BLOCKS, 0.0d)
			.scale(RENDER_SCALE)
			.render(graphics);

		renderStraightEnchantParticles(graphics, level, blockEntity);
		poseStack.popPose();
	}

	private void updatePreviewState(EvokerEnchantingChamberBlockEntity blockEntity) {
		ItemStack displayedItem = getDisplayedItem(AnimationTickHolder.getRenderTime());
		boolean casting = !displayedItem.isEmpty() && ItemStack.isSameItemSameTags(displayedItem, inputCopy);
		ItemStack heldItem = casting ? displayedItem : ItemStack.EMPTY;
		ItemStack pendingOutput = casting ? ItemStack.EMPTY : displayedItem;
		int fluidRemaining = casting ? PREVIEW_FLUID_TOTAL : 0;
		blockEntity.setRenderPreviewState(heldItem, pendingOutput, PREVIEW_STORED_FLUID, fluidRemaining,
			PREVIEW_FLUID_TOTAL, false);
	}

	private ItemStack getDisplayedItem(float renderTime) {
		if (inputCopy.isEmpty())
			return outputBook;
		if (outputBook.isEmpty())
			return inputCopy;

		float cycle = (renderTime % DISPLAY_TRANSFORM_CYCLE) / DISPLAY_TRANSFORM_CYCLE;
		return cycle >= OUTPUT_PHASE_START ? outputBook : inputCopy;
	}

	private @Nullable EvokerEnchantingChamberBlockEntity getOrCreateBlockEntity(@Nullable Level level) {
		if (!(level instanceof ClientLevel clientLevel))
			return cachedBlockEntity;

		if (cachedBlockEntity == null || cachedLevel != clientLevel) {
			cachedLevel = clientLevel;
			cachedBlockEntity = new EvokerEnchantingChamberBlockEntity(BlockPos.ZERO, createRenderState());
		}

		cachedBlockEntity.setLevel(clientLevel);
		return cachedBlockEntity;
	}

	private void renderStraightEnchantParticles(GuiGraphics graphics, ClientLevel level,
		EvokerEnchantingChamberBlockEntity blockEntity) {
		syncStraightEnchantParticles(level, blockEntity);
		if (activeParticles.isEmpty())
			return;

		Camera camera = setupJeiParticleCamera(level);
		graphics.pose().pushPose();
		graphics.pose().scale(RENDER_SCALE, RENDER_SCALE, RENDER_SCALE);
		graphics.pose().translate(0.0d, RENDER_Y_OFFSET_BLOCKS, 0.0d);
		UIRenderHelper.flipForGuiRender(graphics.pose());

		ParticleRenderType renderType = ParticleRenderType.PARTICLE_SHEET_OPAQUE;
		LightTexture lightTexture = Minecraft.getInstance().gameRenderer.lightTexture();
		lightTexture.turnOnLightLayer();
		RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
		RenderSystem.disableCull();
		RenderSystem.enableDepthTest();
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
		RenderSystem.depthMask(true);

		try {
			BufferBuilder builder = Tesselator.getInstance().getBuilder();
			renderType.begin(builder, Minecraft.getInstance().textureManager);
			VertexConsumer transformed = new PoseStackVertexConsumer(builder, graphics.pose().last().pose());
			float partialTicks = AnimationTickHolder.getPartialTicks();
			for (Particle particle : activeParticles)
				particle.render(transformed, camera, partialTicks);
			renderType.end(Tesselator.getInstance());
		} finally {
			RenderSystem.enableCull();
			RenderSystem.disableBlend();
			lightTexture.turnOffLightLayer();
		}

		graphics.pose().popPose();
	}

	private Camera setupJeiParticleCamera(ClientLevel level) {
		Quaternionf inverseSceneRotation = new Quaternionf()
			.rotateY((float) Math.toRadians(-22.5f))
			.rotateX((float) Math.toRadians(15.5f));
		jeiParticleCamera.configure(0.0d, 0.0d, 0.0d, inverseSceneRotation);
		return jeiParticleCamera;
	}

	private void syncStraightEnchantParticles(ClientLevel level, EvokerEnchantingChamberBlockEntity blockEntity) {
		long currentTick = level.getGameTime();
		if (currentTick != lastParticleTick) {
			lastParticleTick = currentTick;
			Iterator<Particle> iterator = activeParticles.iterator();
			while (iterator.hasNext()) {
				Particle particle = iterator.next();
				particle.tick();
				if (!particle.isAlive())
					iterator.remove();
			}

			if (blockEntity.isCastingSpell()) {
				EvokerEnchantingChamberBlockEntity.forEachStraightEnchantParticle(level, blockEntity.getBlockPos(),
					blockEntity.getBlockState(), (x, y, z, dx, dy, dz) -> {
						Particle particle = CatnipClientServices.CLIENT_HOOKS.createParticleFromData(
							CBParticleTypes.STRAIGHT_ENCHANT.get(), level, x, y, z, dx, dy, dz);
						if (particle != null)
							activeParticles.add(particle);
					});
			}
		}
	}

	private static BlockState createRenderState() {
		return CBBlocks.EVOKER_ENCHANTING_CHAMBER.get()
			.defaultBlockState()
			.setValue(EvokerEnchantingChamberBlock.FACING, Direction.SOUTH)
			.setValue(EvokerEnchantingChamberBlock.HALF, DoubleBlockHalf.LOWER);
	}

	private static class PoseStackVertexConsumer implements VertexConsumer {
		private final VertexConsumer delegate;
		private final org.joml.Matrix4f pose;

		private PoseStackVertexConsumer(VertexConsumer delegate, org.joml.Matrix4f pose) {
			this.delegate = delegate;
			this.pose = new org.joml.Matrix4f(pose);
		}

		@Override
		public VertexConsumer vertex(double x, double y, double z) {
			float fx = (float) x;
			float fy = (float) y;
			float fz = (float) z;
			return delegate.vertex(pose, fx, fy, fz);
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int alpha) {
			return delegate.color(red, green, blue, alpha);
		}

		@Override
		public VertexConsumer uv(float u, float v) {
			return delegate.uv(u, v);
		}

		@Override
		public VertexConsumer overlayCoords(int u, int v) {
			return delegate.overlayCoords(u, v);
		}

		@Override
		public VertexConsumer uv2(int u, int v) {
			return delegate.uv2(u, v);
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			return delegate.normal(x, y, z);
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
