package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.util.List;
import java.util.UUID;

import com.nobodiiiii.createbiotech.network.CBPackets;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class EndermanStockKeeperRequestScreen extends StockKeeperRequestScreen {

	private List<List<BigItemStack>> mergedSnapshot;
	private ItemStack selected = ItemStack.EMPTY;
	private Phase phase = Phase.NONE;
	private EditBox quantityBox;
	private Button nextButton;
	private Button confirmButton;
	private Button cancelJobButton;
	private Button backButton;
	private List<GaugeCraftPlan.Line> preview = List.of();
	private UUID previewToken = new UUID(0, 0);
	private String previewStatus = "";
	private int treeScroll;

	private enum Phase { NONE, SELECT, WAITING, PREVIEW }

	public EndermanStockKeeperRequestScreen(EndermanStockKeeperRequestMenu menu, Inventory inventory,
		Component title) {
		super(menu, inventory, title);
	}

	@Override
	protected void init() {
		String previous = quantityBox == null ? "1" : quantityBox.getValue();
		super.init();
		int left = (width - 252) / 2;
		int top = (height - 220) / 2;
		quantityBox = new EditBox(font, left + 82, top + 47, 88, 18,
			Component.translatable("create_biotech.gauge_craft.quantity"));
		quantityBox.setMaxLength(5);
		quantityBox.setFilter(value -> value.matches("[0-9]*"));
		quantityBox.setValue(previous);
		addRenderableWidget(quantityBox);
		nextButton = addRenderableWidget(Button.builder(
			Component.translatable("create_biotech.gauge_craft.view_tree"), button -> requestPreview())
			.bounds(left + 17, top + 181, 100, 20).build());
		confirmButton = addRenderableWidget(Button.builder(
			Component.translatable("create_biotech.gauge_craft.order"), button -> confirmOrder())
			.bounds(left + 17, top + 181, 100, 20).build());
		cancelJobButton = addRenderableWidget(Button.builder(
			Component.translatable("create_biotech.gauge_craft.cancel_job"), button -> {
				phase = Phase.WAITING;
				updateOverlayWidgets();
				CBPackets.sendToServer(new GaugeCraftCancelPacket());
			}).bounds(left + 17, top + 181, 100, 20).build());
		backButton = addRenderableWidget(Button.builder(
			Component.translatable("gui.cancel"), button -> closeCraftOverlay())
			.bounds(left + 135, top + 181, 100, 20).build());
		updateOverlayWidgets();
	}

	public boolean isCraftable(ItemStack stack) {
		return ((EndermanStockKeeperRequestMenu) getMenu()).getGaugeOutputs().stream()
			.anyMatch(output -> ItemStack.isSameItemSameComponents(output, stack));
	}

	public void beginCraft(ItemStack stack) {
		selected = stack.copyWithCount(1);
		phase = Phase.SELECT;
		preview = List.of();
		previewStatus = "";
		treeScroll = 0;
		quantityBox.setValue("1");
		quantityBox.setFocused(true);
		setFocused(quantityBox);
		updateOverlayWidgets();
	}

	private void requestPreview() {
		int quantity;
		try {
			quantity = Integer.parseInt(quantityBox.getValue());
		} catch (NumberFormatException exception) {
			quantity = 0;
		}
		if (quantity <= 0 || quantity > GaugeCraftPlan.MAX_REQUEST) {
			previewStatus = "input";
			return;
		}
		phase = Phase.WAITING;
		previewStatus = "";
		updateOverlayWidgets();
		CBPackets.sendToServer(new GaugeCraftRequestPacket(false, new UUID(0, 0), selected,
			quantity, addressBox.getValue()));
	}

	private void confirmOrder() {
		if (phase != Phase.PREVIEW || !"ready".equals(previewStatus))
			return;
		phase = Phase.WAITING;
		updateOverlayWidgets();
		CBPackets.sendToServer(new GaugeCraftRequestPacket(true, previewToken, ItemStack.EMPTY, 0, ""));
	}

	public void acceptPreview(GaugeCraftPreviewPacket packet) {
		if (phase != Phase.WAITING)
			return;
		previewToken = packet.token();
		preview = packet.lines();
		previewStatus = packet.status();
		treeScroll = 0;
		if ("accepted".equals(previewStatus) || "cancelled".equals(previewStatus)) {
			closeCraftOverlay();
			return;
		}
		phase = Phase.PREVIEW;
		updateOverlayWidgets();
	}

	private void closeCraftOverlay() {
		phase = Phase.NONE;
		quantityBox.setFocused(false);
		setFocused(null);
		updateOverlayWidgets();
	}

	private void updateOverlayWidgets() {
		if (quantityBox == null)
			return;
		quantityBox.visible = phase == Phase.SELECT;
		nextButton.visible = phase == Phase.SELECT;
		confirmButton.visible = phase == Phase.PREVIEW;
		confirmButton.active = "ready".equals(previewStatus);
		cancelJobButton.visible = phase == Phase.PREVIEW && ("processing".equals(previewStatus)
			|| "waiting_output".equals(previewStatus) || "paused_unloaded".equals(previewStatus)
			|| "paused_changed".equals(previewStatus) || "delivering".equals(previewStatus)
			|| "waiting_storage".equals(previewStatus));
		backButton.visible = phase != Phase.NONE;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		if (phase == Phase.NONE)
			return;
		int left = (width - 252) / 2;
		int top = (height - 220) / 2;
		graphics.fill(0, 0, width, height, 0xA0000000);
		graphics.fill(left, top, left + 252, top + 220, 0xFF1B1A25);
		graphics.fill(left + 2, top + 2, left + 250, top + 218, 0xFF323041);
		graphics.drawString(font, Component.translatable("create_biotech.gauge_craft.title"),
			left + 15, top + 13, 0xFFFFFF, false);
		graphics.renderItem(selected, left + 16, top + 39);
		graphics.drawString(font, selected.getHoverName(), left + 39, top + 44, 0xFFFFFF, false);
		if (phase == Phase.SELECT) {
			graphics.drawString(font, Component.translatable("create_biotech.gauge_craft.quantity"),
				left + 16, top + 52, 0xDDDDDD, false);
			graphics.drawString(font, Component.translatable("create_biotech.gauge_craft.address",
				addressBox.getValue()), left + 16, top + 89, 0xBBBBBB, false);
			graphics.drawString(font, Component.translatable("create_biotech.gauge_craft.address_hint"),
				left + 16, top + 104, 0xBBBBBB, false);
			if ("input".equals(previewStatus))
				graphics.drawString(font, Component.translatable("create_biotech.gauge_craft.invalid"),
					left + 16, top + 114, 0xFF7777, false);
		} else if (phase == Phase.WAITING) {
			graphics.drawString(font, Component.translatable("create_biotech.gauge_craft.planning"),
				left + 16, top + 92, 0xFFFFFF, false);
		} else {
			graphics.drawString(font, Component.translatable("create_biotech.gauge_craft.status." +
				(previewStatus.equals("ready") || previewStatus.equals("missing") || previewStatus.equals("busy")
					|| previewStatus.equals("processing") || previewStatus.equals("waiting_output")
					|| previewStatus.equals("paused_unloaded") || previewStatus.equals("paused_changed")
					|| previewStatus.equals("delivering") || previewStatus.equals("waiting_storage")
					? previewStatus : "invalid")), left + 16, top + 70,
				"ready".equals(previewStatus) ? 0x9FE99F : 0xFF9999, false);
			if (!previewStatus.equals("ready") && !previewStatus.equals("missing") && !previewStatus.equals("busy")
				&& !cancelJobButton.visible)
				graphics.drawString(font, font.plainSubstrByWidth(previewStatus, 220), left + 16,
					top + 82, 0xFF9999, false);
			int shown = Math.min(6, Math.max(0, preview.size() - treeScroll));
			for (int i = 0; i < shown; i++) {
				GaugeCraftPlan.Line line = preview.get(treeScroll + i);
				int y = top + 96 + i * 13;
				int indent = Math.min(line.depth(), 10) * 8;
				String prefix = line.stocked() ? "✓ " : line.missing() ? "✕ " : "↳ ";
				String label = prefix + line.stack().getHoverName().getString() + " ×" + line.count();
				graphics.drawString(font, font.plainSubstrByWidth(label, 220 - indent),
					left + 17 + indent, y, line.missing() ? 0xFF7777 : line.stocked() ? 0xA0D9A0 : 0xFFFFFF,
					false);
			}
		}
		if (quantityBox.visible)
			quantityBox.render(graphics, mouseX, mouseY, partialTick);
		if (nextButton.visible)
			nextButton.render(graphics, mouseX, mouseY, partialTick);
		if (confirmButton.visible)
			confirmButton.render(graphics, mouseX, mouseY, partialTick);
		if (cancelJobButton.visible)
			cancelJobButton.render(graphics, mouseX, mouseY, partialTick);
		if (backButton.visible)
			backButton.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (phase == Phase.NONE)
			return super.mouseClicked(mouseX, mouseY, button);
		if (quantityBox.visible && quantityBox.mouseClicked(mouseX, mouseY, button))
			return true;
		if (nextButton.visible && nextButton.mouseClicked(mouseX, mouseY, button))
			return true;
		if (confirmButton.visible && confirmButton.mouseClicked(mouseX, mouseY, button))
			return true;
		if (cancelJobButton.visible && cancelJobButton.mouseClicked(mouseX, mouseY, button))
			return true;
		backButton.mouseClicked(mouseX, mouseY, button);
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (phase == Phase.PREVIEW) {
			treeScroll = Math.max(0, Math.min(Math.max(0, preview.size() - 6),
				treeScroll - (int) Math.signum(scrollY)));
			return true;
		}
		return phase != Phase.NONE || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (phase == Phase.NONE)
			return super.keyPressed(keyCode, scanCode, modifiers);
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			closeCraftOverlay();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER) {
			if (phase == Phase.SELECT)
				requestPreview();
			else if (phase == Phase.PREVIEW)
				confirmOrder();
			return true;
		}
		return phase == Phase.SELECT && quantityBox.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		return phase == Phase.NONE ? super.charTyped(codePoint, modifiers)
			: phase == Phase.SELECT && quantityBox.charTyped(codePoint, modifiers);
	}

	@Override
	protected void containerTick() {
		super.containerTick();

		List<List<BigItemStack>> snapshot = getMenu().contentHolder.getClientStockSnapshot();
		if (snapshot == null || snapshot == mergedSnapshot || snapshot.isEmpty())
			return;

		EndermanStockKeeperRequestMenu endermanMenu = (EndermanStockKeeperRequestMenu) getMenu();
		List<BigItemStack> unsorted = snapshot.get(snapshot.size() - 1);
		for (ItemStack output : endermanMenu.getGaugeOutputs()) {
			boolean alreadyVisible = snapshot.stream()
				.flatMap(List::stream)
				.anyMatch(entry -> ItemStack.isSameItemSameComponents(entry.stack, output));
			if (!alreadyVisible)
				unsorted.add(new GaugeOutputBigItemStack(output));
		}

		mergedSnapshot = snapshot;
		refreshSearchNextTick = true;
		moveToTopNextTick = false;
	}
}
