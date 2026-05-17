package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import org.joml.Matrix4f;

public class SpiderAssemblyTableScreen extends AbstractContainerScreen<SpiderAssemblyTableMenu> {

	private static final int PANEL_COLOR = 0xE01B1B1B;
	private static final int BORDER_COLOR = 0xFF5D5D5D;
	private static final int SLOT_COLOR = 0xFF2C2C2C;
	private static final int SLOT_BORDER_COLOR = 0xFF777777;

	public SpiderAssemblyTableScreen(SpiderAssemblyTableMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 176;
		imageHeight = 166;
		inventoryLabelY = 72;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);
		renderFluidOverlayTooltips(graphics, mouseX, mouseY);
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
		drawHybridFluidContents(graphics, left, top);
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

	private void drawHybridFluidContents(GuiGraphics graphics, int left, int top) {
		for (int i = 0; i < SpiderAssemblyTableBlockEntity.LEG_COUNT; i++) {
			FluidTank tank = menu.getBlockEntity().getFluidTank(i);
			FluidStack fluid = tank.getFluid();
			if (fluid.isEmpty())
				continue;

			int x = left + 17 + i * 18;
			int y = top + SpiderAssemblyTableMenu.HYBRID_SLOT_ROW_Y;
			drawFluidSprite(graphics, x, y, fluid, tank.getCapacity());
		}
	}

	private void renderFluidOverlayTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
		for (int i = 0; i < SpiderAssemblyTableBlockEntity.LEG_COUNT; i++) {
			FluidTank tank = menu.getBlockEntity().getFluidTank(i);
			FluidStack fluid = tank.getFluid();
			if (fluid.isEmpty())
				continue;

			int x = leftPos + 17 + i * 18;
			int y = topPos + SpiderAssemblyTableMenu.HYBRID_SLOT_ROW_Y;
			if (mouseX < x || mouseX >= x + 16 || mouseY < y || mouseY >= y + 16)
				continue;

			List<Component> lines = new ArrayList<>();
			lines.add(fluid.getDisplayName());
			lines.add(Component.literal(tank.getFluidAmount() + " / " + tank.getCapacity() + " mB"));
			graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
			return;
		}
	}

	private static void drawFluidSprite(GuiGraphics graphics, int x, int y, FluidStack fluid, int capacity) {
		IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluid());
		ResourceLocation stillTexture = ext.getStillTexture(fluid);
		if (stillTexture == null)
			return;
		TextureAtlasSprite sprite = Minecraft.getInstance()
			.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
			.apply(stillTexture);
		int color = ext.getTintColor(fluid);

		int height = 16;
		int filled = (int) Math.max(1L, ((long) fluid.getAmount() * height) / Math.max(1, capacity));
		if (filled > height)
			filled = height;
		int maskTop = height - filled;

		RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
		Matrix4f matrix = graphics.pose().last().pose();
		setShaderColorFromInt(color);

		float uMin = sprite.getU0();
		float uMax = sprite.getU1();
		float vMin = sprite.getV0();
		float vMax = sprite.getV1();
		float vMinAdjusted = vMin + (maskTop / 16f) * (vMax - vMin);

		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		Tesselator tessellator = Tesselator.getInstance();
		BufferBuilder buffer = tessellator.getBuilder();
		buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
		float zLevel = 100f;
		buffer.vertex(matrix, x, y + 16, zLevel).uv(uMin, vMax).endVertex();
		buffer.vertex(matrix, x + 16, y + 16, zLevel).uv(uMax, vMax).endVertex();
		buffer.vertex(matrix, x + 16, y + maskTop, zLevel).uv(uMax, vMinAdjusted).endVertex();
		buffer.vertex(matrix, x, y + maskTop, zLevel).uv(uMin, vMinAdjusted).endVertex();
		tessellator.end();

		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
	}

	private static void setShaderColorFromInt(int color) {
		float a = ((color >> 24) & 0xFF) / 255f;
		if (a <= 0f)
			a = 1f;
		float r = ((color >> 16) & 0xFF) / 255f;
		float g = ((color >> 8) & 0xFF) / 255f;
		float b = (color & 0xFF) / 255f;
		RenderSystem.setShaderColor(r, g, b, a);
	}

	private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
		graphics.fill(x, y, x + width, y + 1, color);
		graphics.fill(x, y + height - 1, x + width, y + height, color);
		graphics.fill(x, y, x + 1, y + height, color);
		graphics.fill(x + width - 1, y, x + width, y + height, color);
	}
}
