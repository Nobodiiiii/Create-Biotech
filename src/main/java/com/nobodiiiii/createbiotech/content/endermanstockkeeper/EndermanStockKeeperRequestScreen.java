package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.nobodiiiii.createbiotech.network.CBPackets;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;

import net.createmod.catnip.data.Pair;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
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
	private Button jobsButton;
	private List<GaugeCraftJobs.Summary> jobs = List.of();
	private UUID selectedJob;
	private int jobsScroll;
	private int jobsRefreshTicks;
	private boolean jobsLoaded;
	private static final int VISIBLE_JOBS = 4;
	private List<GaugeCraftPlan.Line> preview = List.of();
	private UUID previewToken = new UUID(0, 0);
	private String previewStatus = "";
	private int treeScroll;

	private enum Phase { NONE, SELECT, WAITING, PREVIEW, JOBS }

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
		jobsButton = addRenderableWidget(Button.builder(
			Component.translatable("create_biotech.gauge_craft.jobs"), button -> openJobs())
			.bounds(Math.min(width - 48, getGuiLeft() + imageWidth + 4), getGuiTop() + 38, 44, 20).build());
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
				if (selectedJob != null) {
					CBPackets.sendToServer(new GaugeCraftCancelPacket(getMenu().containerId, selectedJob));
					selectedJob = null;
					updateOverlayWidgets();
				}
			}).bounds(left + 17, top + 181, 100, 20).build());
		backButton = addRenderableWidget(Button.builder(
			Component.translatable("gui.cancel"), button -> closeCraftOverlay())
			.bounds(left + 135, top + 181, 100, 20).build());
		updateOverlayWidgets();
	}

	private void openJobs() {
		phase = Phase.JOBS;
		jobs = List.of();
		selectedJob = null;
		jobsScroll = 0;
		jobsLoaded = false;
		quantityBox.setFocused(false);
		setFocused(null);
		updateOverlayWidgets();
		refreshJobs();
	}

	private void refreshJobs() {
		jobsRefreshTicks = 20;
		CBPackets.sendToServer(new GaugeCraftJobsRequestPacket(getMenu().containerId));
	}

	public void acceptJobs(GaugeCraftJobsPacket packet) {
		if (phase != Phase.JOBS)
			return;
		jobs = packet.jobs();
		jobsLoaded = true;
		jobsScroll = Math.min(jobsScroll, Math.max(0, jobs.size() - VISIBLE_JOBS));
		if (jobs.stream().noneMatch(job -> job.id().equals(selectedJob)))
			selectedJob = null;
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
		if ("accepted".equals(previewStatus)) {
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
		jobsButton.visible = phase == Phase.NONE;
		quantityBox.visible = phase == Phase.SELECT;
		nextButton.visible = phase == Phase.SELECT;
		confirmButton.visible = phase == Phase.PREVIEW;
		confirmButton.active = "ready".equals(previewStatus);
		cancelJobButton.visible = phase == Phase.JOBS;
		cancelJobButton.active = jobs.stream().anyMatch(job -> job.id().equals(selectedJob) && job.canCancel());
		backButton.visible = phase != Phase.NONE;
		backButton.setMessage(Component.translatable(phase == Phase.JOBS ? "gui.back" : "gui.cancel"));
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
		graphics.drawString(font, Component.translatable(phase == Phase.JOBS
			? "create_biotech.gauge_craft.jobs_title" : "create_biotech.gauge_craft.title"),
			left + 15, top + 13, 0xFFFFFF, false);
		if (phase != Phase.JOBS) {
			graphics.renderItem(selected, left + 16, top + 32);
			graphics.drawString(font, font.plainSubstrByWidth(selected.getHoverName().getString(), 192),
				left + 39, top + 35, 0xFFFFFF, false);
		}
		if (phase == Phase.JOBS) {
			renderJobs(graphics, left, top);
		} else if (phase == Phase.SELECT) {
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
			boolean knownStatus = previewStatus.equals("ready") || previewStatus.equals("missing")
				|| previewStatus.equals("busy");
			graphics.drawString(font, Component.translatable("create_biotech.gauge_craft.status." +
				(knownStatus ? previewStatus : "invalid")), left + 16, top + 70,
				"ready".equals(previewStatus) ? 0x9FE99F : 0xFF9999, false);
			if (!knownStatus)
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
		if (phase == Phase.JOBS) {
			int row = jobRow(mouseX, mouseY);
			if (row >= 0) {
				GaugeCraftJobs.Summary job = jobs.get(row);
				graphics.renderComponentTooltip(font, List.of(job.output().getHoverName(),
					Component.translatable("create_biotech.gauge_craft.status." + job.status()),
					Component.translatable("create_biotech.gauge_craft.progress", job.completed(), job.total()),
					Component.translatable(job.address().isBlank() ? "create_biotech.gauge_craft.address_hint"
						: "create_biotech.gauge_craft.address", job.address())), mouseX, mouseY);
			}
		}
	}

	private void renderJobs(GuiGraphics graphics, int left, int top) {
		graphics.drawString(font, Component.translatable("create_biotech.gauge_craft.jobs_count",
			jobs.size(), GaugeCraftJobs.MAX_ACTIVE_JOBS), left + 16, top + 30,
			jobs.size() >= GaugeCraftJobs.MAX_ACTIVE_JOBS ? 0xFF9999 : 0xBBBBBB, false);
		if (!jobsLoaded || jobs.isEmpty())
			graphics.drawString(font, Component.translatable(jobsLoaded
				? "create_biotech.gauge_craft.no_jobs" : "create_biotech.gauge_craft.loading_jobs"),
				left + 16, top + 64, 0xDDDDDD, false);
		for (int row = 0; row < Math.min(VISIBLE_JOBS, jobs.size() - jobsScroll); row++) {
			GaugeCraftJobs.Summary job = jobs.get(jobsScroll + row);
			int y = top + 46 + row * 30;
			graphics.fill(left + 12, y, left + 240, y + 28,
				job.id().equals(selectedJob) ? 0xFF64577D : 0xFF242330);
			graphics.renderItem(job.output(), left + 16, y + 6);
			String label = job.output().getHoverName().getString() + " ×" + job.count();
			graphics.drawString(font, font.plainSubstrByWidth(label, 197), left + 37, y + 4, 0xFFFFFF, false);
			String status = Component.translatable("create_biotech.gauge_craft.status." + job.status()).getString();
			graphics.drawString(font, font.plainSubstrByWidth(status, 197), left + 37, y + 16, 0xBBBBBB, false);
		}
		if (jobs.size() > VISIBLE_JOBS)
			graphics.drawString(font, (jobsScroll + 1) + "–" + Math.min(jobs.size(), jobsScroll + VISIBLE_JOBS)
				+ " / " + jobs.size(), left + 175, top + 169, 0xBBBBBB, false);
	}

	private int jobRow(double mouseX, double mouseY) {
		int left = (width - 252) / 2;
		int y = (height - 220) / 2 + 46;
		if (mouseX < left + 12 || mouseX >= left + 240 || mouseY < y || mouseY >= y + VISIBLE_JOBS * 30)
			return -1;
		int index = jobsScroll + (int) (mouseY - y) / 30;
		return index < jobs.size() ? index : -1;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (phase == Phase.NONE) {
			if (jobsButton.mouseClicked(mouseX, mouseY, button))
				return true;
			if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
				ItemStack hovered = getHoveredIngredient((int) mouseX, (int) mouseY)
					.map(Pair::getFirst).orElse(ItemStack.EMPTY);
				if (!hovered.isEmpty() && isCraftable(hovered)) {
					beginCraft(hovered);
					return true;
				}
			}
			return super.mouseClicked(mouseX, mouseY, button);
		}
		if (phase == Phase.JOBS && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			int row = jobRow(mouseX, mouseY);
			if (row >= 0) {
				selectedJob = jobs.get(row).id();
				updateOverlayWidgets();
				return true;
			}
		}
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
		if (phase == Phase.JOBS) {
			jobsScroll = Math.max(0, Math.min(Math.max(0, jobs.size() - VISIBLE_JOBS),
				jobsScroll - (int) Math.signum(scrollY)));
			return true;
		}
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
		if (phase == Phase.JOBS && --jobsRefreshTicks <= 0)
			refreshJobs();

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

	@Override
	public Optional<Pair<ItemStack, Rect2i>> getHoveredIngredient(int mouseX, int mouseY) {
		return phase == Phase.NONE ? super.getHoveredIngredient(mouseX, mouseY) : Optional.empty();
	}

	@Override
	public List<Rect2i> getExtraAreas() {
		List<Rect2i> areas = new ArrayList<>(super.getExtraAreas());
		if (jobsButton != null)
			areas.add(new Rect2i(jobsButton.getX(), jobsButton.getY(), jobsButton.getWidth(), jobsButton.getHeight()));
		if (phase != Phase.NONE)
			areas.add(new Rect2i((width - 252) / 2, (height - 220) / 2, 252, 220));
		return areas;
	}
}
