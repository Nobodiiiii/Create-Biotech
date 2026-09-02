package com.nobodiiiii.createbiotech.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.mixin.client.CreativeModeInventoryScreenAccessor;
import com.nobodiiiii.createbiotech.registry.CBCreativeModeTabs;
import com.nobodiiiii.createbiotech.registry.CBCreativeTabSection;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

public final class CreativeTabSectionRenderer {

	private static final int BANNER_WIDTH = 162;
	private static final int BANNER_HEIGHT = 18;
	private static final int VISIBLE_ROWS = 5;
	private static int currentRow;

	private CreativeTabSectionRenderer() {}

	public static void setCurrentRow(int row) {
		currentRow = row;
	}

	public static void render(CreativeModeInventoryScreen screen, GuiGraphics graphics) {
		CreativeModeInventoryScreenAccessor accessor = (CreativeModeInventoryScreenAccessor) screen;
		int left = accessor.createBiotech$getLeftPos() + 8;
		int top = accessor.createBiotech$getTopPos() + 17;

		PoseStack poseStack = graphics.pose();
		poseStack.pushPose();
		poseStack.translate(left, top, 0);
		RenderSystem.enableDepthTest();
		RenderSystem.setShaderColor(1, 1, 1, 1);

		Font font = Minecraft.getInstance().font;
		for (CBCreativeTabSection section : CBCreativeTabSection.values()) {
			int sectionRow = CBCreativeModeTabs.getSectionRow(section) - currentRow;
			if (sectionRow < 0 || sectionRow >= VISIBLE_ROWS)
				continue;

			int y = sectionRow * BANNER_HEIGHT;
			graphics.blitSprite(section.sprite(), 0, y, BANNER_WIDTH, BANNER_HEIGHT);

			int textWidth = font.width(section.title());
			graphics.fill(2, y + 2, textWidth + 8, y + BANNER_HEIGHT - 2, 0xAA303030);
			graphics.drawString(font, section.title(), 5, y + 5, 0xFFFFFFFF, false);
		}

		poseStack.popPose();
		RenderSystem.disableDepthTest();
	}
}
