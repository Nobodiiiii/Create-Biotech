package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterBlock;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterBlockEntity;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterInkParticleOption;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.AllBlocks;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.platform.ForgeCatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraftforge.fluids.FluidStack;

public class AnimatedSquidSpout extends AnimatedKineticsWithEntities {
	private static final int SCENE_SCALE = 20;
	/**
	 * The scene camera looks at the block's south side, so the preview is posed
	 * facing south to put the printer's — and the squid's — front toward the viewer.
	 */
	private static final Direction PREVIEW_FACING = Direction.SOUTH;
	private static final BlockPos PARTICLE_ORIGIN = BlockPos.ZERO;
	private static final int BURST_PHASE_TICKS = 3;
	private static final int CYCLE_LENGTH_TICKS = 30;

	private List<FluidStack> fluids;
	private final JeiSceneParticles inkParticles = new JeiSceneParticles();

	public AnimatedSquidSpout withFluids(List<FluidStack> fluids) {
		this.fluids = fluids;
		return this;
	}

	@Override
	public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
		PoseStack matrixStack = graphics.pose();
		scene(graphics, xOffset, yOffset, () -> {
			int scale = SCENE_SCALE;

			blockElement(CBBlocks.SQUID_PRINTER.get()
				.defaultBlockState()
				.setValue(SquidPrinterBlock.FACING, PREVIEW_FACING))
				.scale(scale)
				.render(graphics);

			SquidJeiRenderer.renderOpenInScene(graphics, PREVIEW_FACING, scale);

			blockElement(AllBlocks.DEPOT.getDefaultState())
				.atLocal(0, 2, 0)
				.scale(scale)
				.render(graphics);

			DEFAULT_LIGHTING.applyLighting();
			matrixStack.pushPose();
			UIRenderHelper.flipForGuiRender(matrixStack);
			matrixStack.scale(16, 16, 16);
			float from = 3f / 16f;
			float to = 17f / 16f;
			FluidStack fluidStack = fluids.get(0);
			ForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluidStack, from, from, from, to, to, to,
				graphics.bufferSource(), matrixStack, LightTexture.FULL_BRIGHT, false, true);
			matrixStack.popPose();

			// The ink particles below draw in immediate mode, so the batched fluid box
			// has to land first or it would paint over them.
			graphics.flush();
			renderInkParticles(graphics);
			Lighting.setupFor3DItems();
		});
	}

	private void renderInkParticles(GuiGraphics graphics) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null)
			return;

		syncInkParticles(level);
		inkParticles.render(graphics, SCENE_SCALE, 0.0d, 0.0d, 0.0d);
	}

	/**
	 * Emits exactly what {@link SquidPrinterBlockEntity} emits in the world, on the
	 * same cadence: a burst every tick once the cycle is under way, and a slower
	 * ambient drip every third tick.
	 */
	private void syncInkParticles(ClientLevel level) {
		if (!inkParticles.advanceOnce(level))
			return;

		int cycleTick = (int) ((AnimationTickHolder.getTicks() - offset * 8) % CYCLE_LENGTH_TICKS);
		if (cycleTick < 0)
			cycleTick += CYCLE_LENGTH_TICKS;
		if (cycleTick < BURST_PHASE_TICKS)
			return;

		SquidPrinterBlockEntity.forEachBurstInkParticle(level, PARTICLE_ORIGIN, this::spawnInkParticle);
		if (level.getGameTime() % 3 == 0)
			SquidPrinterBlockEntity.forEachAmbientInkParticle(level, PARTICLE_ORIGIN, this::spawnInkParticle);
	}

	private void spawnInkParticle(SquidPrinterInkParticleOption options, double x, double y, double z, double dx,
		double dy, double dz) {
		inkParticles.add(Minecraft.getInstance().particleEngine.createParticle(options, x, y, z, dx, dy, dz));
	}
}
