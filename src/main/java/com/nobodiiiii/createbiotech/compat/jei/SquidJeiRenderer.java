package com.nobodiiiii.createbiotech.compat.jei;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterSquidVisual;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;

import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.SquidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.animal.Squid;

public final class SquidJeiRenderer {

	private static final float GUI_SCALE = 20.0f;
	private static final float GUI_Y_OFFSET = -72.0f;
	private static final float GUI_RENDER_Z = 100.0f;

	@Nullable
	private static SquidModel<Squid> squidModel;

	private SquidJeiRenderer() {
	}

	public static void render(GuiGraphics graphics, int centerX, int centerY, float scale) {
		SquidModel<Squid> model = getSquidModel();
		if (model == null)
			return;

		PoseStack poseStack = graphics.pose();
		preparePose(poseStack);
		try {
			poseStack.translate(centerX, centerY + GUI_Y_OFFSET, GUI_RENDER_Z);
			poseStack.scale(GUI_SCALE * scale, GUI_SCALE * scale, GUI_SCALE * scale);
			UIRenderHelper.flipForGuiRender(poseStack);
			poseStack.scale(-SquidPrinterSquidVisual.RENDER_SCALE, -SquidPrinterSquidVisual.RENDER_SCALE,
				SquidPrinterSquidVisual.RENDER_SCALE);
			poseStack.mulPose(Axis.XP.rotationDegrees(-15.5f));
			poseStack.mulPose(Axis.YP.rotationDegrees(22.5f));
			renderOpenSquid(model, graphics, poseStack);
		} finally {
			cleanUpPose(poseStack);
		}
	}

	public static void renderOpenInScene(GuiGraphics graphics, double x, double y, double z, float sceneScale) {
		SquidModel<Squid> model = getSquidModel();
		if (model == null)
			return;

		PoseStack poseStack = graphics.pose();
		preparePose(poseStack);
		try {
			poseStack.scale(sceneScale, sceneScale, sceneScale);
			poseStack.translate(x, y, z);
			UIRenderHelper.flipForGuiRender(poseStack);
			poseStack.scale(-SquidPrinterSquidVisual.RENDER_SCALE, -SquidPrinterSquidVisual.RENDER_SCALE,
				SquidPrinterSquidVisual.RENDER_SCALE);
			renderOpenSquid(model, graphics, poseStack);
		} finally {
			cleanUpPose(poseStack);
		}
	}

	private static void renderOpenSquid(SquidModel<Squid> model, GuiGraphics graphics, PoseStack poseStack) {
		SquidPrinterSquidVisual.prepareOpenModel(model);
		SquidPrinterSquidVisual.renderModel(model, poseStack, graphics.bufferSource(), LightTexture.FULL_BRIGHT);
	}

	private static void preparePose(PoseStack poseStack) {
		poseStack.pushPose();
		RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
		RenderSystem.enableDepthTest();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		AnimatedKinetics.DEFAULT_LIGHTING.applyLighting();
	}

	private static void cleanUpPose(PoseStack poseStack) {
		poseStack.popPose();
		Lighting.setupFor3DItems();
	}

	private static @Nullable SquidModel<Squid> getSquidModel() {
		if (squidModel == null && Minecraft.getInstance().getEntityModels() != null)
			squidModel = new SquidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.SQUID));
		return squidModel;
	}
}
