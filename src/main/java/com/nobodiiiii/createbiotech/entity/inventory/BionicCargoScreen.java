package com.nobodiiiii.createbiotech.entity.inventory;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class BionicCargoScreen extends AbstractContainerScreen<BionicCargoMenu> {
	private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
	public BionicCargoScreen(BionicCargoMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}
	@Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, 71);
		graphics.blit(TEXTURE, leftPos, topPos + 71, 0, 126, imageWidth, 96);
		graphics.fill(leftPos + 7, topPos + 17, leftPos + 169, topPos + 71, 0xffc6c6c6);
		for (int row = 0; row < 3; row++) for (int col = 0; col < menu.capacity / 3; col++)
			graphics.blit(TEXTURE, leftPos + 43 + col * 18, topPos + 17 + row * 18, 7, 17, 18, 18);
	}
	@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}
}
