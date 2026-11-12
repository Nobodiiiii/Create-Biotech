package com.nobodiiiii.createbiotech.entity.inventory;

import java.util.ArrayList;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class BionicCargoScreen extends AbstractContainerScreen<BionicCargoMenu> {
	private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/horse.png");
	private static final ResourceLocation CHEST_SLOTS = ResourceLocation.withDefaultNamespace("container/horse/chest_slots");
	private static final ResourceLocation SADDLE_SLOT = ResourceLocation.withDefaultNamespace("container/horse/saddle_slot");
	private Button previous, next;
	public BionicCargoScreen(BionicCargoMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}
	@Override protected void init() {
		super.init();
		previous = addRenderableWidget(new PageButton(leftPos + 80, topPos + 71, "<", button -> turnPage(0)));
		next = addRenderableWidget(new PageButton(leftPos + 158, topPos + 71, ">", button -> turnPage(1)));
	}
	private void turnPage(int button) {
		minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
	}
	@Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
		for (int slot = 0; slot < menu.visibleCargo(); slot++) {
			int x = slot % 5 * 18, y = slot / 5 * 18;
			graphics.blitSprite(CHEST_SLOTS, 90, 54, x, y, leftPos + 79 + x, topPos + 17 + y, 18, 18);
		}
		for (int slot = 0; slot < menu.visibleSaddles(); slot++)
			graphics.blitSprite(SADDLE_SLOT, leftPos + 7, topPos + 17 + slot * 18, 18, 18);
		if (menu.entity() != null)
			InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, leftPos + 26, topPos + 18,
				leftPos + 78, topPos + 70, 17, 0.25f, mouseX, mouseY, menu.entity());
	}
	@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		previous.visible = next.visible = menu.pageCount() > 1;
		previous.active = menu.page() > 0;
		next.active = menu.page() + 1 < menu.pageCount();
		super.render(graphics, mouseX, mouseY, partialTick);
		if (menu.pageCount() > 1) {
			String page = (menu.page() + 1) + "/" + menu.pageCount();
			graphics.drawString(font, page, leftPos + 125 - font.width(page) / 2, topPos + 73, 0x404040, false);
		}
		renderTooltip(graphics, mouseX, mouseY);
	}
	private static final class PageButton extends Button {
		private PageButton(int x, int y, String label, OnPress onPress) {
			super(x, y, 12, 12, Component.literal(label), onPress, DEFAULT_NARRATION);
		}
		@Override public void renderString(GuiGraphics graphics, Font font, int color) {
			graphics.drawString(font, getMessage(), getX() + (width - font.width(getMessage())) / 2 + 1,
				getY() + (height - font.lineHeight) / 2 + 1, color, false);
		}
	}
	@Override protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
		if (menu.getCarried().isEmpty() && hoveredSlot != null && hoveredSlot.isActive()
			&& hoveredSlot.index < BionicCargoMenu.SADDLES_PER_PAGE) {
			var stack = hoveredSlot.getItem();
			var tooltip = new ArrayList<Component>();
			tooltip.add(Component.translatable("create_biotech.bionic.saddle_slot", menu.saddleOffset() + hoveredSlot.index + 1));
			if (!stack.isEmpty()) tooltip.addAll(getTooltipFromContainerItem(stack));
			graphics.renderTooltip(font, tooltip, stack.getTooltipImage(), mouseX, mouseY);
		} else super.renderTooltip(graphics, mouseX, mouseY);
	}
}
