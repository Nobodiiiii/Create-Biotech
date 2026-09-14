package com.nobodiiiii.createbiotech.compat.jei;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.foundation.render.GuiEntityItemElement;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureRenderer;
import com.nobodiiiii.createbiotech.registry.CBItems;

import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;

/**
 * Draws a slime the way a captured slime item looks in an inventory slot, so the
 * recipe artwork and the item it produces read as the same object.
 *
 * <p>Projects the known resting cube to fit the preview, without obtaining or
 * measuring a renderer from the entity dispatcher.
 */
public class SlimeEntityDrawable implements IDrawable {

	private static final ItemStack ENTITY_ITEM_TRANSFORM = new ItemStack(CBItems.CAPTURED_SMALL_SLIME.get());

	private final int width;
	private final int height;
	private final int slimeSize;
	private final boolean magmaCube;

	public SlimeEntityDrawable(int width, int height, int slimeSize, EntityType<? extends Slime> entityType) {
		this.width = width;
		this.height = height;
		this.slimeSize = Math.max(1, slimeSize);
		this.magmaCube = entityType == EntityType.MAGMA_CUBE;
	}

	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public int getHeight() {
		return height;
	}

	@Override
	public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset) {
		Minecraft minecraft = Minecraft.getInstance();
		BakedModel itemModel = minecraft.getItemRenderer()
			.getModel(ENTITY_ITEM_TRANSFORM, minecraft.level, minecraft.player, 0);
		guiGraphics.flush();
		RenderSystem.setShaderColor(1, 1, 1, 1);
		RenderSystem.enableDepthTest();
		if (itemModel.usesBlockLight())
			Lighting.setupFor3DItems();
		else
			Lighting.setupForFlatItems();
		PoseStack poseStack = guiGraphics.pose();
		poseStack.pushPose();
		try {
			poseStack.translate(xOffset + width / 2f, yOffset + height / 2f, 150);
			poseStack.mulPose(new Matrix4f().scaling(1, -1, 1));
			float boxScale = Math.min(width, height);
			poseStack.scale(boxScale, boxScale, boxScale);
			ClientHooks.handleCameraTransforms(poseStack, itemModel, ItemDisplayContext.GUI, false);
			poseStack.translate(-.5f, -.5f, -.5f);
			MachineCreatureRenderer.renderCenteredSlime(poseStack, guiGraphics.bufferSource(), LightTexture.FULL_BRIGHT,
				GuiEntityItemElement.baseYRotation(ItemDisplayContext.GUI), slimeSize, projectedScale(poseStack), magmaCube);
			guiGraphics.flush();
		} finally {
			poseStack.popPose();
			Lighting.setupFor3DItems();
		}
	}

	private float projectedScale(PoseStack poseStack) {
		Matrix4f pose = poseStack.last().pose();
		float side = MachineCreatureRenderer.restingSlimeSide(slimeSize, magmaCube);
		// A quarter-turn around Y leaves this cube's bounds unchanged.
		float projectedWidth = side * (Math.abs(pose.m00()) + Math.abs(pose.m10()) + Math.abs(pose.m20()));
		float projectedHeight = side * (Math.abs(pose.m01()) + Math.abs(pose.m11()) + Math.abs(pose.m21()));
		float poseScale = pose.transformDirection(1, 0, 0, new Vector3f()).length();
		float projectedSize = Math.max(projectedWidth, projectedHeight);
		return poseScale <= 1.0e-6f || projectedSize <= 1.0e-6f
			? 1 : Math.min(1.5f * poseScale / projectedSize, 1.5f);
	}
}
