package com.nobodiiiii.createbiotech.compat.jei;

import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public class ItemIconDrawable implements IDrawable {

	private final ItemStack stack;
	private final int width;
	private final int height;

	public ItemIconDrawable(ItemStack stack) {
		this(stack, 16, 16);
	}

	public ItemIconDrawable(ItemStack stack, int width, int height) {
		this.stack = stack;
		this.width = width;
		this.height = height;
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
	public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
		graphics.renderItem(stack, xOffset, yOffset);
	}

	@SuppressWarnings("unused")
	private void suppressMinecraftWarn() {
		Minecraft.getInstance();
	}
}
