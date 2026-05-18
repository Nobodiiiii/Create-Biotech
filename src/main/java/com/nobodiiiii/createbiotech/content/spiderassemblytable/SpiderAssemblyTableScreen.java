package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;

import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import org.joml.Matrix4f;

public class SpiderAssemblyTableScreen extends AbstractSimiContainerScreen<SpiderAssemblyTableMenu> {

	private static final ResourceLocation BACKGROUND =
		new ResourceLocation(CreateBiotech.MOD_ID, "textures/gui/spider_assembly_table.png");
	private static final int BG_WIDTH = 216;
	private static final int BG_HEIGHT = 113;

	private List<Rect2i> extraAreas = Collections.emptyList();

	public SpiderAssemblyTableScreen(SpiderAssemblyTableMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
	}

	@Override
	protected void init() {
		setWindowSize(BG_WIDTH, BG_HEIGHT + 4 + AllGuiTextures.PLAYER_INVENTORY.getHeight());
		super.init();

		for (int i = 0; i < SpiderAssemblyTableBlockEntity.LEG_COUNT; i++) {
			final int slotIdx = i;
			int x = leftPos + SpiderAssemblyTableMenu.SLOT_X_START + i * SpiderAssemblyTableMenu.SLOT_X_PITCH - 1;
			int y = topPos + SpiderAssemblyTableMenu.LOCK_ROW_Y;
			LockIconButton button = new LockIconButton(x, y, slotIdx);
			button.withCallback(
				() -> Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, slotIdx));
			addRenderableWidget(button);
		}

		extraAreas = ImmutableList.of(new Rect2i(leftPos + BG_WIDTH, topPos + BG_HEIGHT - 56, 64, 56));
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		int invX = getLeftOfCentered(AllGuiTextures.PLAYER_INVENTORY.getWidth());
		int invY = topPos + BG_HEIGHT + 4;
		renderPlayerInventory(graphics, invX, invY);

		graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, BG_WIDTH, BG_HEIGHT, BG_WIDTH, BG_HEIGHT);

		graphics.drawString(font, title, leftPos + 15, topPos + 5, 0xFFE6E6E6, false);

		drawHybridContents(graphics, leftPos, topPos);

		renderTableModel(graphics);
	}

	private void renderTableModel(GuiGraphics graphics) {
		GuiGameElement.of(new ItemStack(CBItems.SPIDER_ASSEMBLY_TABLE.get()))
			.<GuiGameElement.GuiRenderBuilder>at(leftPos + BG_WIDTH + 12, topPos + BG_HEIGHT - 40, -200)
			.scale(4)
			.render(graphics);
	}

	@Override
	protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
		renderFluidOverlayTooltips(graphics, mouseX, mouseY);
		super.renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	public List<Rect2i> getExtraAreas() {
		return extraAreas;
	}

	private void drawHybridContents(GuiGraphics graphics, int left, int top) {
		for (int i = 0; i < SpiderAssemblyTableBlockEntity.LEG_COUNT; i++) {
			int x = left + SpiderAssemblyTableMenu.SLOT_X_START + i * SpiderAssemblyTableMenu.SLOT_X_PITCH;
			int y = top + SpiderAssemblyTableMenu.HYBRID_SLOT_ROW_Y;

			FluidTank tank = menu.getBlockEntity().getFluidTank(i);
			FluidStack fluid = tank.getFluid();
			ItemStack slotItem = menu.getBlockEntity().getInventory()
				.getStackInSlot(SpiderAssemblyTableBlockEntity.HYBRID_SLOT_START + i);

			if (!fluid.isEmpty()) {
				drawFluidSprite(graphics, x, y, fluid, tank.getCapacity(), 1f);
				continue;
			}

			if (!slotItem.isEmpty())
				continue;

			if (menu.getBlockEntity().isHybridSlotBlocked(i)) {
				drawBlockedMark(graphics, x, y);
				continue;
			}
			FluidStack fluidLock = menu.getBlockEntity().getFluidLock(i);
			ItemStack itemLock = menu.getBlockEntity().getItemLock(i);
			if (!fluidLock.isEmpty()) {
				drawFluidSprite(graphics, x, y, fluidLock, Math.max(1, fluidLock.getAmount()), 0.4f);
			} else if (!itemLock.isEmpty()) {
				drawGhostItem(graphics, itemLock, x, y);
			}
		}
	}

	private void drawGhostItem(GuiGraphics graphics, ItemStack stack, int x, int y) {
		RenderSystem.enableBlend();
		RenderSystem.setShaderColor(1f, 1f, 1f, 0.4f);
		graphics.renderItem(stack, x, y);
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		graphics.fill(RenderType.guiOverlay(), x, y, x + 16, y + 16, 0x88202020);
		RenderSystem.disableBlend();
	}

	private void drawBlockedMark(GuiGraphics graphics, int x, int y) {
		int color = 0xFFB04040;
		for (int i = 0; i < 14; i++) {
			graphics.fill(x + 1 + i, y + 1 + i, x + 2 + i, y + 2 + i, color);
			graphics.fill(x + 1 + i, y + 14 - i, x + 2 + i, y + 15 - i, color);
		}
	}

	private void renderFluidOverlayTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
		for (int i = 0; i < SpiderAssemblyTableBlockEntity.LEG_COUNT; i++) {
			FluidTank tank = menu.getBlockEntity().getFluidTank(i);
			FluidStack fluid = tank.getFluid();
			if (fluid.isEmpty())
				continue;

			int x = leftPos + SpiderAssemblyTableMenu.SLOT_X_START + i * SpiderAssemblyTableMenu.SLOT_X_PITCH;
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

	private static void drawFluidSprite(GuiGraphics graphics, int x, int y, FluidStack fluid, int capacity,
		float alphaMul) {
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

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
		setShaderColorFromInt(color, alphaMul);

		Matrix4f matrix = graphics.pose().last().pose();
		float uMin = sprite.getU0();
		float uMax = sprite.getU1();
		float vMin = sprite.getV0();
		float vMax = sprite.getV1();
		float vMinAdjusted = vMin + (maskTop / 16f) * (vMax - vMin);

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

	private static void setShaderColorFromInt(int color, float alphaMul) {
		float a = ((color >> 24) & 0xFF) / 255f;
		if (a <= 0f)
			a = 1f;
		a *= alphaMul;
		float r = ((color >> 16) & 0xFF) / 255f;
		float g = ((color >> 8) & 0xFF) / 255f;
		float b = (color & 0xFF) / 255f;
		RenderSystem.setShaderColor(r, g, b, a);
	}

	private class LockIconButton extends IconButton {
		private static final ScreenElement LOCKED_ICON_YELLOW =
			new TintedIcon(AllIcons.I_CONFIG_LOCKED, 0xFFE6B33A);

		private final int hybridIndex;

		LockIconButton(int x, int y, int hybridIndex) {
			super(x, y, AllIcons.I_CONFIG_UNLOCKED);
			this.hybridIndex = hybridIndex;
		}

		@Override
		public void doRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
			boolean locked = menu.getBlockEntity().isHybridSlotLocked(hybridIndex);
			setIcon(locked ? LOCKED_ICON_YELLOW : AllIcons.I_CONFIG_UNLOCKED);
			super.doRender(graphics, mouseX, mouseY, partialTicks);
		}
	}

	private static class TintedIcon implements ScreenElement {
		private final ScreenElement delegate;
		private final float r;
		private final float g;
		private final float b;

		TintedIcon(ScreenElement delegate, int color) {
			this.delegate = delegate;
			this.r = ((color >> 16) & 0xFF) / 255f;
			this.g = ((color >> 8) & 0xFF) / 255f;
			this.b = (color & 0xFF) / 255f;
		}

		@Override
		public void render(GuiGraphics graphics, int x, int y) {
			RenderSystem.setShaderColor(r, g, b, 1f);
			delegate.render(graphics, x, y);
			RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		}
	}
}
