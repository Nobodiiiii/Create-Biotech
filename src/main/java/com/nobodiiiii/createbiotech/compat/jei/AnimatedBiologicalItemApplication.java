package com.nobodiiiii.createbiotech.compat.jei;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModel;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModels;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;

import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;

/** Draws the shared JSON chicken parts in a Create-style JEI scene. */
public class AnimatedBiologicalItemApplication extends AnimatedKineticsWithEntities {

	private static final int ENTITY_SCALE = 20;
	private static final double ENTITY_LOCAL_X = 0.5d;
	private static final double ENTITY_LOCAL_Y = 0.0d;
	private static final double ENTITY_LOCAL_Z = 0.5d;

	private final MachineCreatureModel model = MachineCreatureModels.chicken();
	private boolean hasBell;

	public AnimatedBiologicalItemApplication withEntityType(EntityType<? extends Chicken> entityType) {
		hasBell = entityType == CBEntityTypes.DING_DONG_CHICKEN.get();
		return this;
	}

	@Override
	public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
		scene(graphics, xOffset, yOffset, () -> renderChicken(graphics));
	}

	private void renderChicken(GuiGraphics graphics) {
		graphics.flush();
		RenderSystem.setShaderColor(1, 1, 1, 1);
		RenderSystem.enableDepthTest();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		DEFAULT_LIGHTING.applyLighting();
		PoseStack poseStack = graphics.pose();
		poseStack.pushPose();
		try {
			poseStack.scale(ENTITY_SCALE, ENTITY_SCALE, ENTITY_SCALE);
			poseStack.translate(ENTITY_LOCAL_X, ENTITY_LOCAL_Y, ENTITY_LOCAL_Z);
			UIRenderHelper.flipForGuiRender(poseStack);
			// Preserve the south-facing living renderer's model-space transform.
			poseStack.mulPose(Axis.YP.rotationDegrees(180));
			poseStack.scale(-1, -1, 1);
			poseStack.translate(0, -1.501f, 0);

			model.resetPose();
			model.part("body/bell").visible = hasBell;
			model.render(poseStack, graphics.bufferSource(), LightTexture.FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, -1);
			graphics.bufferSource().endBatch();
		} finally {
			poseStack.popPose();
			Lighting.setupFor3DItems();
		}
	}
}
