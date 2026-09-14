package com.nobodiiiii.createbiotech.compat.jei;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterSquidVisual;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModel;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModels;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;

import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Direction;

/**
 * Draws the printer's squid into a JEI scene.
 *
 * <p>The squid is a machine-owned model rather than an entity, so it cannot
 * go through {@link com.nobodiiiii.createbiotech.foundation.gui.GuiEntityElement}.
 * The placement is kept in step with
 * {@code SquidPrinterRenderer#renderSquid} instead: same local attachment point,
 * same model-space correction, so the preview and the real block agree.
 */
public final class SquidJeiRenderer {

	private static final double SQUID_LOCAL_X = 0.5d;
	private static final double SQUID_LOCAL_Z = 0.5d;

	private static final MachineCreatureModel SQUID_MODEL = MachineCreatureModels.squid();

	private SquidJeiRenderer() {
	}

	/**
	 * Places the open squid on the printer already drawn at the origin of the
	 * current JEI scene, turned to the given facing exactly as the world renderer
	 * turns it. The squid's texture has one distinctive side; pointing it the same
	 * way the block points means the preview shows what a placed printer shows.
	 */
	public static void renderOpenInScene(GuiGraphics graphics, Direction facing, float sceneScale) {
		MachineCreatureModel model = SQUID_MODEL;

		PoseStack poseStack = graphics.pose();
		poseStack.pushPose();
		RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
		RenderSystem.enableDepthTest();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		AnimatedKinetics.DEFAULT_LIGHTING.applyLighting();

		try {
			poseStack.scale(sceneScale, sceneScale, sceneScale);
			// Scene-local offsets are applied before the GUI flip, where +Y points
			// down, so the world renderer's attachment height is negated here.
			poseStack.translate(SQUID_LOCAL_X, -SquidPrinterSquidVisual.HEAD_TOP_Y, SQUID_LOCAL_Z);
			UIRenderHelper.flipForGuiRender(poseStack);
			poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - facing.toYRot()));
			float scale = SquidPrinterSquidVisual.RENDER_SCALE;
			poseStack.scale(-scale, -scale, scale);

			SquidPrinterSquidVisual.prepareOpenModel(model);
			SquidPrinterSquidVisual.renderModel(model, poseStack, graphics.bufferSource(), LightTexture.FULL_BRIGHT);
		} finally {
			poseStack.popPose();
			Lighting.setupFor3DItems();
		}
	}
}
