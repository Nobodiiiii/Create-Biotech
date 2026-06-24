package com.nobodiiiii.createbiotech.content.shulkerteleporter;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nobodiiiii.createbiotech.network.CBPackets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ShulkerTeleporterScreen extends AbstractContainerScreen<ShulkerTeleporterMenu> {

	private static final Component OWN_LABEL =
		Component.translatable("create_biotech.shulker_teleporter.own_address");
	private static final Component TARGET_LABEL =
		Component.translatable("create_biotech.shulker_teleporter.target_address");
	private static final Component SAVE_LABEL =
		Component.translatable("create_biotech.shulker_teleporter.save");

	private EditBox ownAddress;
	private EditBox targetAddress;

	public ShulkerTeleporterScreen(ShulkerTeleporterMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = 210;
		imageHeight = 118;
	}

	@Override
	protected void init() {
		super.init();
		int x = leftPos + 14;
		int y = topPos + 30;

		ownAddress = new EditBox(font, x, y, 182, 20, OWN_LABEL);
		ownAddress.setMaxLength(32);
		ownAddress.setValue(menu.getOwnAddress());
		addRenderableWidget(ownAddress);

		targetAddress = new EditBox(font, x, y + 42, 182, 20, TARGET_LABEL);
		targetAddress.setMaxLength(32);
		targetAddress.setValue(menu.getTargetAddress());
		addRenderableWidget(targetAddress);

		addRenderableWidget(Button.builder(SAVE_LABEL, button -> save())
			.bounds(leftPos + 65, topPos + 91, 80, 20)
			.build());
		setInitialFocus(ownAddress);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 257 || keyCode == 335) {
			save();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		RenderSystem.disableDepthTest();
		graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0101010);
		graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFF2B1D35);
		graphics.fill(leftPos + 7, topPos + 7, leftPos + imageWidth - 7, topPos + imageHeight - 7, 0xFF3A2847);
		RenderSystem.enableDepthTest();
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, 8, 8, 0xE9D6FF, false);
		graphics.drawString(font, OWN_LABEL, 14, 20, 0xCDB8E6, false);
		graphics.drawString(font, TARGET_LABEL, 14, 62, 0xCDB8E6, false);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}

	private void save() {
		CBPackets.sendToServer(new ShulkerTeleporterConfigPacket(menu.getBlockPos(), ownAddress.getValue(),
			targetAddress.getValue()));
		if (minecraft != null && minecraft.player != null)
			minecraft.player.closeContainer();
	}
}
