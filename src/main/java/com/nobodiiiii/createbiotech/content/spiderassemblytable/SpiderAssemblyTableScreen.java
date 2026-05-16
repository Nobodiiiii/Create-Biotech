package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;

public class SpiderAssemblyTableScreen extends AbstractContainerScreen<SpiderAssemblyTableMenu> {

	private static final int PANEL_COLOR = 0xE01B1B1B;
	private static final int BORDER_COLOR = 0xFF5D5D5D;
	private static final int SLOT_COLOR = 0xFF2C2C2C;
	private static final int SLOT_BORDER_COLOR = 0xFF777777;
	private static final int GAUGE_EMPTY_COLOR = 0xFF202020;

	public SpiderAssemblyTableScreen(SpiderAssemblyTableMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 176;
		imageHeight = 196;
		inventoryLabelY = 98;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);
		renderFluidTooltip(graphics, mouseX, mouseY);
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		RenderSystem.enableBlend();
		int left = leftPos;
		int top = topPos;
		graphics.fill(left, top, left + imageWidth, top + imageHeight, PANEL_COLOR);
		drawBorder(graphics, left, top, imageWidth, imageHeight, BORDER_COLOR);

		menu.slots.forEach(slot -> drawSlotBackground(graphics, left + slot.x - 1, top + slot.y - 1));
		drawFluidGauges(graphics, left, top);
		RenderSystem.disableBlend();
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, titleLabelX, titleLabelY, 0xE6E6E6, false);
		graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xBDBDBD, false);
	}

	private void drawSlotBackground(GuiGraphics graphics, int x, int y) {
		graphics.fill(x, y, x + 18, y + 18, SLOT_BORDER_COLOR);
		graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_COLOR);
	}

	private void drawFluidGauges(GuiGraphics graphics, int left, int top) {
		for (int i = 0; i < SpiderAssemblyTableBlockEntity.LEG_COUNT; i++) {
			int x = left + 36 + i * 18;
			int y = top + 91;
			drawBorder(graphics, x - 1, y - 1, 14, 8, SLOT_BORDER_COLOR);
			graphics.fill(x, y, x + 12, y + 6, GAUGE_EMPTY_COLOR);

			FluidTank tank = menu.getBlockEntity().getFluidTank(i);
			FluidStack fluid = tank.getFluid();
			if (fluid.isEmpty())
				continue;

			int width = Math.max(1, Math.round(12 * (fluid.getAmount() / (float) tank.getCapacity())));
			int color = IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor(fluid);
			graphics.fill(x, y, x + width, y + 6, 0xFF000000 | color);
		}
	}

	private void renderFluidTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
		for (int i = 0; i < SpiderAssemblyTableBlockEntity.LEG_COUNT; i++) {
			int x = leftPos + 36 + i * 18;
			int y = topPos + 91;
			if (mouseX < x || mouseX >= x + 12 || mouseY < y || mouseY >= y + 6)
				continue;

			FluidTank tank = menu.getBlockEntity().getFluidTank(i);
			FluidStack fluid = tank.getFluid();
			Component fluidText = fluid.isEmpty()
				? Component.translatable("create_biotech.gui.spider_assembly_table.empty_fluid")
				: fluid.getDisplayName();
			Component amountText = Component.literal(tank.getFluidAmount() + " / " + tank.getCapacity() + " mB");
			graphics.renderComponentTooltip(font, List.of(fluidText, amountText), mouseX, mouseY);
			return;
		}
	}

	private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
		graphics.fill(x, y, x + width, y + 1, color);
		graphics.fill(x, y + height - 1, x + width, y + height, color);
		graphics.fill(x, y, x + 1, y + height, color);
		graphics.fill(x + width - 1, y, x + width, y + height, color);
	}
}
