package com.nobodiiiii.createbiotech.content.biocomputing.client;

import static com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchCanvas.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerIO;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerIO.Channel;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerItemReference;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerLibrary;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Block;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Category;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.ItemInput;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Op;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgramJson;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgramMenu;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgramPacket;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgramPacket.Action;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgramPacket.EditResult;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgramPacket.ProgramState;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgramPacket.ProgramUpdate;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerRuntime.ChannelState;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerRuntime.Snapshot;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerRuntime.Status;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchCanvas.Drop;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchCanvas.ListControl;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchCanvas.LiteralSpot;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchCanvas.Preview;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchCanvas.Rect;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.Field;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.Location;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.ReporterPoint;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.Tile;
import com.nobodiiiii.createbiotech.foundation.gui.CanvasViewport;
import com.nobodiiiii.createbiotech.network.CBPackets;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class ComputerScratchScreen extends AbstractContainerScreen<ComputerProgramMenu> {
	private static final int PALETTE_SCROLL_STEP = 27;
	private static final int PALETTE_PADDING = 4;
	private static final int PALETTE_SCROLLBAR_GUTTER = 8;
	private static final int MARGIN = 6;
	private static final int MIN_EDITOR_WIDTH = 224;
	private static final int INVENTORY_COLUMNS = 9;
	private static final int INVENTORY_SLOT_SIZE = 18;
	private static final List<Category> CATEGORIES = List.of(
		Category.EVENTS, Category.CONTROL, Category.OPERATORS, Category.VARIABLES, Category.SENSING, Category.OUTPUT
	);

	private enum ConsoleMode { OUTPUT, VARIABLES, IO, INVENTORY }

	private final ScratchDraft draft;
	private final CanvasViewport viewport = new CanvasViewport();
	private Category category = Category.CONTROL;
	private ComputerLibrary library;
	private List<PaletteEntry> paletteEntries = List.of();
	private int paletteContentHeight;
	private ComputerLibrary validatedLibrary;
	private long validatedRevision = -1;
	private Op unavailable;
	private ScratchCanvas canvas;
	private Rect paletteArea, canvasArea, consoleArea;
	private final Button[] tabs = new Button[CATEGORIES.size()];
	private final Button[] consoleTabs = new Button[ConsoleMode.values().length];
	private Button run, save, stop, reload;
	private int paletteScroll, consoleScroll;
	private ConsoleMode consoleMode = ConsoleMode.OUTPUT;
	private Drag drag;
	private InventoryPick inventoryPick;
	private int panButton = -1;
	private EditBox input;
	private LiteralSpot editingSpot;
	private String editingInitial;
	private ComputerProgram editingBefore;
	private ComputerProgram runningProgram;
	private long runningCheckRevision = -1;
	private boolean matchesRunning = true;
	private long draftBaseRevision;
	private Action pendingAction;
	private long submittedDraftRevision;
	private boolean closeRequested;
	private Component statusFlash;
	private int statusFlashColor;
	private int statusFlashTicks;

	public ComputerScratchScreen(ComputerProgramMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		ProgramState programs = menu.programState();
		draft = new ScratchDraft(programs.saved());
		draftBaseRevision = programs.revision();
		library = programs.library();
	}

	@Override
	protected void init() {
		if (input != null)
			finishInput();
		int rightMargin = MARGIN;
		if (ModList.get().isLoaded("jei"))
			rightMargin = Math.max(MARGIN, Math.min(Math.clamp(width / 4, 90, 170), width - MIN_EDITOR_WIDTH - MARGIN));
		imageWidth = Math.min(560, width - MARGIN - rightMargin);
		imageHeight = Math.min(420, height - 2 * MARGIN);
		super.init();
		leftPos = (width + MARGIN - rightMargin - imageWidth) / 2;
		drag = null;
		inventoryPick = null;
		panButton = -1;
		layoutAreas();
		if (canvas == null)
			resetView();
		canvas = new ScratchCanvas(font);
		rebuildPalette();

		boolean compact = imageWidth < 336;
		int actionWidth = compact ? 18 : 38;
		int clipboardWidth = compact ? 18 : 36;
		int bx = leftPos + imageWidth - 7;
		bx -= 18;
		Button help = button(bx, topPos + 6, 18, Component.literal("?"), () -> {});
		help.setTooltip(Tooltip.create(text("help")));

		bx -= 25;
		Button center = button(bx, topPos + 6, 22, Component.literal("↺"), this::resetView);
		center.setTooltip(Tooltip.create(text("reset_view")));

		bx -= clipboardWidth + 3;
		Button paste = button(bx, topPos + 6, clipboardWidth, toolbarLabel("paste", "↓", compact), this::pasteProgram);
		paste.setTooltip(Tooltip.create(text("paste_tooltip")));

		bx -= clipboardWidth + 3;
		Button copy = button(bx, topPos + 6, clipboardWidth, toolbarLabel("copy", "↑", compact), this::copyProgram);
		copy.setTooltip(Tooltip.create(text("copy_tooltip")));

		bx -= actionWidth + 3;
		stop = button(bx, topPos + 6, actionWidth, toolbarLabel("stop", "■", compact).withStyle(ChatFormatting.RED), this::stopProgram);
		stop.setTooltip(Tooltip.create(text("stop")));

		bx -= actionWidth + 3;
		save = button(bx, topPos + 6, actionWidth, toolbarLabel("save", "✓", compact).withStyle(ChatFormatting.AQUA), this::saveProgram);
		save.setTooltip(Tooltip.create(text("save")));

		bx -= actionWidth + 3;
		run = button(bx, topPos + 6, actionWidth, toolbarLabel("run", "▶", compact).withStyle(ChatFormatting.GREEN), this::runProgram);
		run.setTooltip(Tooltip.create(text("run")));

		int sideWidth = paletteArea.width();
		int halfTab = (sideWidth - 3) / 2;
		for (int i = 0; i < CATEGORIES.size(); i++) {
			Category option = CATEGORIES.get(i);
			boolean fullRow = i == CATEGORIES.size() - 1 && CATEGORIES.size() % 2 == 1;
			int tabX = paletteArea.x() + (fullRow ? 0 : (i % 2) * (halfTab + 3));
			int tabY = canvasArea.y() + (i / 2) * 20;
			int tabW = fullRow ? sideWidth : halfTab;
			tabs[i] = button(tabX, tabY, tabW, categoryLabel(option, tabW), () -> select(option));
			tabs[i].setTooltip(Tooltip.create(categoryName(option)));
		}

		int cx = consoleArea.x() + 4;
		int[] modeWidths = compact ? new int[] {32, 34, 23, 42} : new int[] {46, 46, 35, 58};
		ConsoleMode[] modes = ConsoleMode.values();
		for (int i = 0; i < modes.length; i++) {
			ConsoleMode mode = modes[i];
			consoleTabs[i] = button(cx, consoleArea.y() + 4, modeWidths[i], consoleTabLabel(mode, modeWidths[i]),
				() -> selectConsole(mode));
			consoleTabs[i].setTooltip(Tooltip.create(mode == ConsoleMode.INVENTORY ? text("inventory_help") : consoleName(mode)));
			cx += modeWidths[i] + 2;
		}
		int reloadWidth = compact ? 22 : 64;
		reload = button(consoleArea.right() - reloadWidth - 4, consoleArea.y() + 4, reloadWidth,
			toolbarLabel("reload_program", "↻", compact), this::reloadProgram);
		reload.setTooltip(Tooltip.create(text("reload_program_tooltip")));
		reload.visible = hasConflict();
	}

	private void layoutAreas() {
		int sideWidth = Math.clamp(imageWidth * 28 / 100, 70, 141);
		int consoleHeight = consoleMode == ConsoleMode.INVENTORY ? 102 : Math.clamp(imageHeight / 4, 62, 94);
		int bottom = topPos + imageHeight - 7;
		int right = leftPos + imageWidth - 7;
		consoleArea = new Rect(leftPos + 7, bottom - consoleHeight, imageWidth - 14, consoleHeight);
		int canvasX = leftPos + sideWidth + 13;
		canvasArea = new Rect(canvasX, topPos + 31, right - canvasX, consoleArea.y() - topPos - 36);
		paletteArea = new Rect(leftPos + 7, canvasArea.y() + 61, sideWidth, Math.max(0, canvasArea.height() - 61));
		paletteScroll = Math.min(paletteScroll, paletteLimit());
	}

	private void resetView() {
		viewport.reset();
		if (canvasArea.width() < 250)
			viewport.zoomAt(0, 0, canvasArea.width() < 160 ? -6 : -3);
	}

	private static MutableComponent toolbarLabel(String key, String icon, boolean compact) {
		return compact ? Component.literal(icon) : text(key);
	}

	private Button button(int x, int y, int width, Component label, Runnable action) {
		return addRenderableWidget(Button.builder(label, ignored -> action.run()).bounds(x, y, width, 18).build());
	}

	private void select(Category option) {
		category = option;
		paletteScroll = 0;
		rebuildPalette();
		for (int i = 0; i < tabs.length; i++)
			tabs[i].setMessage(categoryLabel(CATEGORIES.get(i), tabs[i].getWidth()));
	}

	private void selectConsole(ConsoleMode mode) {
		consoleMode = mode;
		consoleScroll = 0;
		layoutAreas();
		ConsoleMode[] modes = ConsoleMode.values();
		for (int i = 0; i < consoleTabs.length; i++) {
			consoleTabs[i].setMessage(consoleTabLabel(modes[i], consoleTabs[i].getWidth()));
			consoleTabs[i].setY(consoleArea.y() + 4);
		}
		reload.setY(consoleArea.y() + 4);
		if (input != null)
			input.setY(editBoxY(editingSpot.bounds()));
	}

	private Component categoryLabel(Category option, int width) {
		String label = categoryName(option).getString();
		return Component.literal(canvas.shorten(label, width - 7))
			.withStyle(option == category ? ChatFormatting.GOLD : ChatFormatting.WHITE);
	}

	private static Component categoryName(Category option) {
		return text("category." + option.name().toLowerCase(Locale.ROOT));
	}

	private Component consoleTabLabel(ConsoleMode mode, int width) {
		return Component.literal(canvas.shorten(consoleName(mode).getString(), width - 6))
			.withStyle(mode == consoleMode ? ChatFormatting.GOLD : ChatFormatting.WHITE);
	}

	private static Component consoleName(ConsoleMode mode) {
		return text(mode.name().toLowerCase(Locale.ROOT));
	}

	private void rebuildPalette() {
		List<PaletteEntry> entries = new ArrayList<>();
		int y = PALETTE_PADDING;
		for (Op op : Op.values()) {
			if (op == Op.ITEM_ID || op.category() != category || !library.allows(op))
				continue;
			boolean paired = op.slots().stream().anyMatch(ComputerProgram.Slot::itemAlternative);
			PaletteEntry plain = paletteEntry(op, false, paired, y);
			entries.add(plain);
			y += plain.height();
			if (paired) {
				PaletteEntry item = paletteEntry(op, true, true, y);
				entries.add(item);
				y += item.height();
			}
		}
		paletteEntries = List.copyOf(entries);
		paletteContentHeight = y + PALETTE_PADDING;
		paletteScroll = Math.min(paletteScroll, paletteLimit());
	}

	private PaletteEntry paletteEntry(Op op, boolean withItemId, boolean paired, int y) {
		Tile tile = Tile.create(op, withItemId);
		Preview preview = canvas.preview(tile);
		int width = paletteArea.width() - PALETTE_PADDING - PALETTE_SCROLLBAR_GUTTER - 2;
		float scale = Math.min(1, width / (float) preview.bounds().width());
		int previewHeight = (int) Math.ceil(preview.bounds().height() * scale);
		String form = paired ? text(withItemId ? "item_form" : "text_form").getString() : "";
		String caption = form;
		if (font.lineHeight * scale < 6) {
			String name = text("palette." + op.key()).getString();
			caption = form.isEmpty() ? name : form + " · " + name;
		}
		int contentHeight = previewHeight + (caption.isEmpty() ? 0 : font.lineHeight + 2);
		int rowHeight = Math.max(24, contentHeight + 6);
		return new PaletteEntry(tile.snapshot(), preview, scale, y, rowHeight, previewHeight, contentHeight, form, caption);
	}

	private Op unavailableBlock() {
		if (validatedLibrary != library || validatedRevision != draft.revision()) {
			validatedLibrary = library;
			validatedRevision = draft.revision();
			unavailable = draft.snapshot().firstUnavailable(library);
		}
		return unavailable;
	}

	private static Component libraryLabel(ComputerLibrary library) {
		return text("library." + library.key());
	}

	private static Component libraryRequired(Op op) {
		return op == null ? text("status.library_locked")
			: text("library_required", text("palette." + op.key()), libraryLabel(op.library()));
	}

	private void saveProgram() {
		submitProgram(Action.SAVE);
	}

	private void runProgram() {
		submitProgram(Action.RUN);
	}

	private void submitProgram(Action action) {
		if (pendingAction != null || hasConflict() || drag != null)
			return;
		if (input != null)
			finishInput();
		Op blocked = unavailableBlock();
		if (blocked != null) {
			closeRequested = false;
			flashStatus(libraryRequired(blocked), 0xffff8983);
			return;
		}
		ComputerProgram program = draft.snapshot();
		pendingAction = action;
		submittedDraftRevision = draft.revision();
		CBPackets.sendToServer(new ComputerProgramPacket(menu.containerId, action, draftBaseRevision, Optional.of(program)));
	}

	private void stopProgram() {
		CBPackets.sendToServer(new ComputerProgramPacket(menu.containerId, Action.STOP, draftBaseRevision, Optional.empty()));
	}

	private boolean hasConflict() {
		return draftBaseRevision != menu.programState().revision();
	}

	private boolean canReload() {
		return hasConflict() || (unavailableBlock() != null && draft.isDirty());
	}

	private void reloadProgram() {
		if (pendingAction != null || !canReload())
			return;
		if (input != null)
			finishInput();
		ProgramState programs = menu.programState();
		draft.replaceWith(programs.saved());
		draft.markSaved(programs.saved());
		draftBaseRevision = programs.revision();
		statusFlash = null;
		resetView();
	}

	private void copyProgram() {
		if (input != null)
			finishInput();
		if (minecraft == null)
			return;
		minecraft.keyboardHandler.setClipboard(ComputerProgramJson.toJson(draft.snapshot()));
		flashStatus(text("copied"), 0xff9bdb9a);
	}

	private void pasteProgram() {
		if (input != null)
			finishInput();
		if (minecraft == null)
			return;
		Optional<ComputerProgram> parsed = ComputerProgramJson.fromJson(minecraft.keyboardHandler.getClipboard());
		if (parsed.isPresent()) {
			Op blocked = parsed.get().firstUnavailable(library);
			if (blocked != null) {
				flashStatus(libraryRequired(blocked), 0xffff8983);
				return;
			}
			draft.replaceWith(parsed.get());
			resetView();
			flashStatus(text("pasted"), 0xff9bdb9a);
		} else {
			flashStatus(text("invalid_json"), 0xffff8983);
		}
	}

	private void flashStatus(Component message, int color) {
		statusFlash = message;
		statusFlashColor = color;
		statusFlashTicks = 50;
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (library != menu.programState().library()) {
			library = menu.programState().library();
			paletteScroll = 0;
			rebuildPalette();
			if (drag != null && drag.from == null && !library.allows(drag.tile.op))
				drag = null;
		}
		if (statusFlashTicks > 0 && --statusFlashTicks == 0)
			statusFlash = null;
		ProgramUpdate response = menu.takeEditResponse();
		if (response != null && pendingAction != null)
			onEditResponse(response);
	}

	private void onEditResponse(ProgramUpdate response) {
		Action action = pendingAction;
		pendingAction = null;
		boolean close = closeRequested;
		closeRequested = false;
		if (response.result() == EditResult.REJECTED) {
			flashStatus(text("edit_rejected"), 0xffff8983);
			return;
		}
		if (response.result() == EditResult.LIBRARY_LOCKED) {
			flashStatus(libraryRequired(unavailableBlock()), 0xffff8983);
			return;
		}
		if (response.result() != EditResult.ACCEPTED)
			return;
		ComputerProgram saved = response.state().saved();
		if (draft.revision() == submittedDraftRevision && !draft.snapshot().equals(saved)) {
			if (input != null)
				finishInput();
			drag = null;
			inventoryPick = null;
			draft.replaceWith(saved);
		}
		draft.markSaved(saved);
		draftBaseRevision = response.state().revision();
		if (close)
			onClose();
		else if (action == Action.SAVE)
			flashStatus(text("saved"), 0xff7cd4ff);
	}

	@Override
	public void onClose() {
		if (input != null)
			finishInput();
		drag = null;
		inventoryPick = null;
		if (pendingAction != null) {
			closeRequested = true;
		} else if (!draft.isDirty()) {
			super.onClose();
		} else if (!hasConflict()) {
			closeRequested = true;
			submitProgram(Action.SAVE);
		}
	}

	private boolean draftMatchesRunning() {
		ComputerProgram running = menu.programState().running();
		if (runningProgram != running || runningCheckRevision != draft.revision()) {
			runningProgram = running;
			runningCheckRevision = draft.revision();
			matchesRunning = runningProgram != null && draft.snapshot().equals(runningProgram);
		}
		return matchesRunning;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		boolean canSubmit = drag == null && pendingAction == null && !hasConflict() && unavailableBlock() == null;
		run.active = canSubmit;
		save.active = canSubmit;
		stop.active = menu.snapshot().status() == Status.RUNNING
			|| menu.snapshot().channels().stream().anyMatch(channel -> channel.output() > 0);
		reload.visible = canReload();
		reload.active = pendingAction == null;
		super.render(graphics, mouseX, mouseY, partialTick);
		if (drag != null) {
			graphics.pose().pushPose();
			graphics.pose().translate(0, 0, 250);
			drag.preview().render(graphics, mouseX + 7, mouseY + 7, 1);
			graphics.pose().popPose();
		}
		if (inventoryPick != null) {
			if (inventoryIndexAt(mouseX, mouseY) != inventoryPick.index()) {
				graphics.pose().pushPose();
				graphics.pose().translate(0, 0, 250);
				graphics.renderItem(inventoryPick.stack(), mouseX - 8, mouseY - 8);
				graphics.pose().popPose();
			}
		} else {
			int index = inventoryIndexAt(mouseX, mouseY);
			if (index >= 0 && drag == null) {
				ItemStack stack = menu.getSlot(index).getItem();
				if (!stack.isEmpty())
					graphics.renderTooltip(font, stack, mouseX, mouseY);
			}
		}
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff222c33);
		graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xff536475);

		int blocks = draft.blockCount();
		String countText = blocks + "/" + ComputerProgram.MAX_BLOCKS;
		int countColor = blocks >= ComputerProgram.MAX_BLOCKS ? 0xffff8983
			: blocks >= ComputerProgram.MAX_BLOCKS * 4 / 5 ? 0xffffd17a : 0xff8ea4b5;
		int toolbarLeft = run.getX() - 8;
		int countWidth = font.width(countText);
		int maxTitleWidth = Math.max(0, toolbarLeft - (leftPos + 8) - countWidth - 8);
		String heading = title.getString() + (draft.isDirty() ? " *" : "");
		if (maxTitleWidth >= font.width("…"))
			graphics.drawString(font, canvas.shorten(heading, maxTitleWidth), leftPos + 8, topPos + 11, 0xfff1f5f8, false);
		if (toolbarLeft - countWidth >= leftPos + 8)
			graphics.drawString(font, countText, toolbarLeft - countWidth, topPos + 11, countColor, false);

		drawPalette(graphics, mouseX, mouseY);
		drawCanvas(graphics, mouseX, mouseY);
		drawConsole(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

	private void drawPalette(GuiGraphics graphics, int mouseX, int mouseY) {
		fill(graphics, paletteArea, 0xff151c23);
		if (paletteArea.height() == 0)
			return;
		graphics.enableScissor(paletteArea.x(), paletteArea.y(), paletteArea.right(), paletteArea.bottom());
		for (PaletteEntry entry : paletteEntries) {
			Rect row = paletteRow(entry);
			if (row.intersects(paletteArea)) {
				Op op = entry.template().op();
				boolean hovered = drag == null && row.contains(mouseX, mouseY) && paletteArea.contains(mouseX, mouseY);
				if (hovered)
					fill(graphics, row, 0xff25313c);
				int y = row.y() + (row.height() - entry.contentHeight()) / 2;
				entry.preview().render(graphics, row.x() + 1, y, entry.scale());
				if (!entry.caption().isEmpty()) {
					String label = canvas.shorten(entry.caption(), row.width() - 2);
					graphics.drawString(font, label, row.x() + 1, y + entry.previewHeight() + 2, 0xffd2e9ff, false);
				}
				if (hovered) {
					MutableComponent tooltip = text("palette." + op.key());
					if (!entry.form().isEmpty())
						tooltip.append(" · ").append(entry.form());
					tooltip.append(" · ").append(libraryLabel(op.library()));
					if (op == Op.BIND_FREQUENCY || op == Op.TUNE_FREQUENCY)
						tooltip.append("\n").append(text("frequency_help"));
					if (op == Op.STOCK_COUNT || op == Op.HAS_STOCK)
						tooltip.append("\n").append(text("stock_list_help"));
					if (op == Op.ORDER_ITEM || op == Op.ORDER_BATCH)
						tooltip.append("\n").append(text("order_help"));
					setTooltipForNextRenderPass(tooltip);
				}
			}
		}
		graphics.disableScissor();
		drawScrollbar(graphics, paletteArea.right() - 4, paletteArea.y(), paletteArea.height(),
			paletteContentHeight, paletteScroll);
	}

	private void drawCanvas(GuiGraphics graphics, int mouseX, int mouseY) {
		fill(graphics, canvasArea, 0xff18232b);
		Snapshot snapshot = menu.snapshot();
		int activeBlock = snapshot.status() == Status.RUNNING && draftMatchesRunning()
			? snapshot.activeBlockIndex() : -1;
		ScratchCanvas.Layout layout = canvas.layout(draft, activeBlock);
		graphics.enableScissor(canvasArea.x(), canvasArea.y(), canvasArea.right(), canvasArea.bottom());
		graphics.pose().pushPose();
		graphics.pose().translate(canvasArea.x() + viewport.screenX(0), canvasArea.y() + viewport.screenY(0), 0);
		float scale = (float) viewport.scale();
		graphics.pose().scale(scale, scale, 1);
		int x = (int) Math.floor(worldX(canvasArea.x()));
		int y = (int) Math.floor(worldY(canvasArea.y()));
		layout.render(graphics, new Rect(x, y, (int) Math.ceil(canvasArea.width() / scale) + 2,
			(int) Math.ceil(canvasArea.height() / scale) + 2));
		Drop target = dropAt(layout, mouseX, mouseY);
		if (target != null)
			target.highlight(graphics);
		if (inventoryPick != null) {
			for (LiteralSpot spot : layout.literals()) {
				if (takesItem(spot.field())) {
					Rect bounds = spot.bounds();
					graphics.renderOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 0xffa8dcff);
				}
			}
		}
		graphics.pose().popPose();
		graphics.disableScissor();
		if (drag == null && inventoryPick == null && panButton == -1 && canvasArea.contains(mouseX, mouseY)) {
			Field field = itemFieldAt(layout, mouseX, mouseY);
			if (field != null) {
				Component help = text("item_input_help");
				setTooltipForNextRenderPass(field.literal.isEmpty() ? help : Component.literal(field.literal).append("\n").append(help));
			} else if (input == null) {
				double worldX = worldX(mouseX);
				double worldY = worldY(mouseY);
				ListControl control = layout.listControlAt(worldX, worldY);
				if (control != null) {
					setTooltipForNextRenderPass(text(control.row() < 0 ? "add_list_entry" : "remove_list_entry"));
				} else {
					ScratchCanvas.Placed placed = layout.blockAt(worldX, worldY);
					if (placed != null && placed.tile().op == Op.ITEM_ID)
						setTooltipForNextRenderPass(text("detach_item_id"));
				}
			}
		}
	}

	private void drawConsole(GuiGraphics graphics, int mouseX, int mouseY) {
		fill(graphics, consoleArea, 0xff151c23);
		Snapshot snapshot = menu.snapshot();
		int statusLeft = consoleTabs[consoleTabs.length - 1].getX() + consoleTabs[consoleTabs.length - 1].getWidth() + 8;
		int statusRight = reload.visible ? reload.getX() - 5 : consoleArea.right() - 7;
		int maxStatusWidth = Math.max(0, statusRight - statusLeft);

		Component statusMessage;
		int statusColor;
		if (pendingAction != null) {
			statusMessage = text("submitting");
			statusColor = 0xff7cd4ff;
		} else if (hasConflict()) {
			statusMessage = text("edit_conflict");
			statusColor = 0xffff8983;
		} else if (statusFlash != null) {
			statusMessage = statusFlash;
			statusColor = statusFlashColor;
		} else if (unavailableBlock() != null) {
			statusMessage = libraryRequired(unavailableBlock());
			statusColor = 0xffff8983;
		} else {
			Status status = snapshot.status();
			statusMessage = text("status." + status.name().toLowerCase(Locale.ROOT));
			statusColor = switch (status) {
				case RUNNING -> 0xff9bdb9a;
				case EMPTY_VARIABLE, VARIABLE_LIMIT, LIBRARY_LOCKED, PROCEDURE_LIMIT -> 0xffff8983;
				default -> 0xffa9bdcc;
			};
		}
		String statusText = statusMessage.getString();
		String statusLabel = canvas.shorten(statusText, maxStatusWidth);
		if (maxStatusWidth >= font.width("…"))
			graphics.drawString(font, statusLabel, statusRight - font.width(statusLabel), consoleArea.y() + 9,
				statusColor, false);
		if (drag == null && inventoryPick == null && !statusLabel.equals(statusText)
			&& new Rect(statusLeft, consoleArea.y() + 4, maxStatusWidth, 17).contains(mouseX, mouseY))
			setTooltipForNextRenderPass(statusMessage);

		if (consoleMode == ConsoleMode.INVENTORY) {
			drawInventory(graphics, mouseX, mouseY);
			return;
		}
		int rowHeight = font.lineHeight + 2;
		int startY = consoleArea.y() + 25;
		List<String> lines = consoleLines();
		if (lines.isEmpty()) {
			String emptyKey = consoleMode == ConsoleMode.VARIABLES ? "no_variables" : "no_output";
			graphics.drawString(font, text(emptyKey), consoleArea.x() + 7, startY, 0xff879ba9, false);
			return;
		}
		int maxScroll = Math.max(0, lines.size() - visibleLines());
		consoleScroll = Math.min(maxScroll, consoleScroll);
		int from = consoleMode == ConsoleMode.OUTPUT ? maxScroll - consoleScroll : consoleScroll;
		for (int i = from; i < Math.min(lines.size(), from + visibleLines()); i++) {
			String fullLine = lines.get(i);
			Rect row = new Rect(consoleArea.x() + 7, startY + (i - from) * rowHeight, consoleArea.width() - 17, rowHeight);
			String shown = canvas.shorten(fullLine, row.width());
			graphics.drawString(font, shown, row.x(), row.y(), 0xfff1f5f8, false);
			if (drag == null && !shown.equals(fullLine) && row.contains(mouseX, mouseY))
				setTooltipForNextRenderPass(Component.literal(fullLine));
		}
		drawScrollbar(graphics, consoleArea.right() - 4, startY, visibleLines() * rowHeight, lines.size() * rowHeight,
			from * rowHeight);
	}

	private void drawInventory(GuiGraphics graphics, int mouseX, int mouseY) {
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			Rect slot = inventorySlot(i);
			graphics.fill(slot.x(), slot.y(), slot.right() - 1, slot.bottom() - 1,
				slot.contains(mouseX, mouseY) ? 0xff425965 : 0xff283641);
			ItemStack stack = menu.getSlot(i).getItem();
			if (!stack.isEmpty()) {
				graphics.renderItem(stack, slot.x(), slot.y());
				graphics.renderItemDecorations(font, stack, slot.x(), slot.y());
			}
		}
	}

	private Rect inventorySlot(int index) {
		boolean hotbar = index < INVENTORY_COLUMNS;
		int row = hotbar ? 3 : index / INVENTORY_COLUMNS - 1;
		int x = consoleArea.x() + (consoleArea.width() - INVENTORY_COLUMNS * INVENTORY_SLOT_SIZE) / 2;
		int y = consoleArea.y() + 24 + row * INVENTORY_SLOT_SIZE + (hotbar ? 3 : 0);
		return new Rect(x + (index % INVENTORY_COLUMNS) * INVENTORY_SLOT_SIZE, y, INVENTORY_SLOT_SIZE, INVENTORY_SLOT_SIZE);
	}

	private int inventoryIndexAt(double x, double y) {
		if (consoleMode == ConsoleMode.INVENTORY && consoleArea.contains(x, y)) {
			for (int i = 0; i < Inventory.INVENTORY_SIZE; i++)
				if (inventorySlot(i).contains(x, y))
					return i;
		}
		return -1;
	}

	private List<String> consoleLines() {
		Snapshot snapshot = menu.snapshot();
		return switch (consoleMode) {
			case OUTPUT -> {
				List<String> raw = snapshot.output();
				List<String> numbered = new ArrayList<>(raw.size());
				for (int i = 0; i < raw.size(); i++)
					numbered.add((i + 1) + ": " + raw.get(i));
				yield numbered;
			}
			case VARIABLES -> snapshot.variables().stream()
				.map(variable -> variable.name() + " = " + variable.value()).toList();
			case IO -> formatIoLines(snapshot);
			case INVENTORY -> List.of();
		};
	}

	private List<String> formatIoLines(Snapshot snapshot) {
		List<String> lines = new ArrayList<>(2 + Channel.VALUES.length + ComputerIO.INPUT_SLOTS);
		lines.add(text("io.library", libraryLabel(library)).getString());
		for (Channel channel : Channel.VALUES) {
			ChannelState state = snapshot.channels().get(channel.ordinal());
			lines.add(state.isBound()
				? text("io.channel", channel.key(), emptyPlaceholder(state.first()), emptyPlaceholder(state.second()),
					state.input(), state.output()).getString()
				: text("io.channel_unbound", channel.key()).getString());
		}
		if (library.includesLogistics() || snapshot.stockLinkBound())
			lines.add(text(snapshot.stockLinkBound() ? "io.stock_bound" : "io.stock_unbound").getString());
		List<String> slots = snapshot.inputSlots();
		for (int i = 0; i < ComputerIO.INPUT_SLOTS; i++) {
			lines.add(text("io.input_slot", i + 1, emptyPlaceholder(slots.get(i))).getString());
		}
		return lines;
	}

	private static String emptyPlaceholder(String value) {
		return value.isEmpty() ? "—" : value;
	}

	private int visibleLines() { return Math.max(1, (consoleArea.height() - 26) / (font.lineHeight + 2)); }

	private static void drawScrollbar(GuiGraphics graphics, int x, int y, int visible, int total, int scroll) {
		if (visible <= 0 || total <= visible)
			return;
		int thumb = Math.max(6, visible * visible / total);
		int offset = (visible - thumb) * scroll / (total - visible);
		graphics.fill(x, y + offset, x + 2, y + offset + thumb, 0xff7492a8);
	}

	private static void fill(GuiGraphics graphics, Rect rect, int color) {
		graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), color);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (drag != null || inventoryPick != null || panButton != -1)
			return true;
		if (mouseX < leftPos || mouseX >= leftPos + imageWidth || mouseY < topPos || mouseY >= topPos + imageHeight)
			return false;
		int inventoryIndex = inventoryIndexAt(mouseX, mouseY);
		if (inventoryIndex >= 0) {
			ItemStack stack = menu.getSlot(inventoryIndex).getItem();
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !stack.isEmpty())
				inventoryPick = new InventoryPick(inventoryIndex, stack.copyWithCount(1));
			return true;
		}
		if (input != null) {
			if (input.isMouseOver(mouseX, mouseY))
				return super.mouseClicked(mouseX, mouseY, button);
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && takesItem(editingSpot.field())
				&& consoleTabs[ConsoleMode.INVENTORY.ordinal()].isMouseOver(mouseX, mouseY)) {
				selectConsole(ConsoleMode.INVENTORY);
				return true;
			}
			finishInput();
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && paletteArea.contains(mouseX, mouseY)) {
			for (PaletteEntry entry : paletteEntries) {
				if (paletteRow(entry).contains(mouseX, mouseY)) {
					beginDrag(new Tile(entry.template()), null, mouseX, mouseY);
					break;
				}
			}
			return true;
		}
		if (!canvasArea.contains(mouseX, mouseY))
			return super.mouseClicked(mouseX, mouseY, button);
		if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
			panButton = button;
			return true;
		}
		ScratchCanvas.Layout layout = canvas.layout(draft);
		double x = worldX(mouseX);
		double y = worldY(mouseY);
		ListControl control = layout.listControlAt(x, y);
		if (control != null) {
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
				draft.editCollection(control.tile(), control.row());
			return true;
		}
		LiteralSpot literal = layout.literalAt(x, y);
		if (literal != null) {
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
				edit(literal);
			return true;
		}
		ScratchCanvas.Placed picked = layout.blockAt(x, y);
		if (picked != null) {
			if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
				draft.remove(picked.location());
			else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
				beginDrag(picked.tile(), picked.location(), mouseX, mouseY);
		} else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			panButton = button;
		}
		return true;
	}

	private void beginDrag(Tile tile, Location from, double x, double y) {
		drag = new Drag(tile, from, canvas.preview(tile), x, y);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
		if (panButton == button) {
			viewport.pan(dx, dy);
			return true;
		}
		return drag != null || inventoryPick != null || super.mouseDragged(mouseX, mouseY, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (inventoryPick != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			InventoryPick pick = inventoryPick;
			inventoryPick = null;
			Field field = null;
			if (inventoryIndexAt(mouseX, mouseY) == pick.index() && editingSpot != null && takesItem(editingSpot.field()))
				field = editingSpot.field();
			else if (canvasArea.contains(mouseX, mouseY))
				field = itemFieldAt(canvas.layout(draft), mouseX, mouseY);
			if (field != null)
				setItem(field, pick.stack());
			return true;
		}
		if (panButton == button) {
			panButton = -1;
			return true;
		}
		if (drag == null || button != GLFW.GLFW_MOUSE_BUTTON_LEFT)
			return super.mouseReleased(mouseX, mouseY, button);
		if (drag.movedTo(mouseX, mouseY)) {
			ScratchCanvas.Layout layout = canvas.layout(draft);
			Drop target = dropAt(layout, mouseX, mouseY);
			if (target != null) {
				draft.drop(drag.tile, drag.from, target.location());
			} else if (drag.tile.op == Op.ITEM_ID && drag.from instanceof ReporterPoint
				&& (paletteArea.contains(mouseX, mouseY) || (canvasArea.contains(mouseX, mouseY)
					&& layout.blockAt(worldX(mouseX), worldY(mouseY)) == null))) {
				draft.remove(drag.from);
			}
		}
		drag = null;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		int direction = (int) Math.signum(vertical);
		if (direction == 0)
			return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
		if (inventoryPick != null || (consoleMode == ConsoleMode.INVENTORY && consoleArea.contains(mouseX, mouseY)))
			return true;
		if (!paletteArea.contains(mouseX, mouseY) && !consoleArea.contains(mouseX, mouseY) && !canvasArea.contains(mouseX, mouseY))
			return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
		if (input != null)
			finishInput();
		if (paletteArea.contains(mouseX, mouseY)) {
			paletteScroll = Math.clamp(paletteScroll - direction * PALETTE_SCROLL_STEP, 0, paletteLimit());
		} else if (consoleArea.contains(mouseX, mouseY)) {
			int delta = consoleMode == ConsoleMode.OUTPUT ? direction : -direction;
			consoleScroll = Math.clamp(consoleScroll + delta, 0, Math.max(0, consoleLines().size() - visibleLines()));
		} else {
			viewport.zoomAt(mouseX - canvasArea.x(), mouseY - canvasArea.y(), direction);
		}
		return true;
	}

	private Rect paletteRow(PaletteEntry entry) {
		return new Rect(paletteArea.x() + PALETTE_PADDING, paletteArea.y() + entry.y() - paletteScroll,
			paletteArea.width() - PALETTE_PADDING - PALETTE_SCROLLBAR_GUTTER, entry.height());
	}

	private int paletteLimit() { return Math.max(0, paletteContentHeight - paletteArea.height()); }
	private double worldX(double x) { return viewport.worldX(x - canvasArea.x()); }
	private double worldY(double y) { return viewport.worldY(y - canvasArea.y()); }

	private Drop dropAt(ScratchCanvas.Layout layout, double x, double y) {
		return drag == null || !canvasArea.contains(x, y) ? null : layout.dropAt(draft, drag.tile, drag.from, worldX(x), worldY(y));
	}

	private static boolean takesItem(Field field) {
		return field.slot.itemInput() != ItemInput.NONE;
	}

	private Field itemFieldAt(ScratchCanvas.Layout layout, double x, double y) {
		LiteralSpot spot = input != null && input.isMouseOver(x, y) ? editingSpot : layout.literalAt(worldX(x), worldY(y));
		return spot != null && takesItem(spot.field()) ? spot.field() : null;
	}

	public List<ItemDropTarget> itemDropTargets() {
		return visibleItemTargets().stream().map(target -> {
			Rect area = target.area();
			return new ItemDropTarget(new Rect2i(area.x(), area.y(), area.width(), area.height()), stack -> {
				// JEI keeps the original targets for the duration of a drag.
				if (visibleItemTargets().contains(target))
					setItem(target.field(), stack);
			});
		}).toList();
	}

	private List<FieldTarget> visibleItemTargets() {
		if (canvas == null || drag != null || inventoryPick != null || panButton != -1)
			return List.of();
		List<FieldTarget> targets = new ArrayList<>();
		Rect editArea = input == null ? null : new Rect(input.getX(), input.getY(), input.getWidth(), input.getHeight());
		if (editArea != null && takesItem(editingSpot.field()))
			targets.add(new FieldTarget(editArea, editingSpot.field()));
		for (LiteralSpot spot : canvas.layout(draft).literals()) {
			if (!takesItem(spot.field()) || (editingSpot != null && spot.field() == editingSpot.field()))
				continue;
			Rect area = visibleScreenArea(spot.bounds());
			if (area != null && (editArea == null || !editArea.intersects(area)))
				targets.add(new FieldTarget(area, spot.field()));
		}
		return targets;
	}

	private Rect visibleScreenArea(Rect bounds) {
		int x = Math.max(canvasArea.x(), (int) Math.floor(canvasArea.x() + viewport.screenX(bounds.x())));
		int y = Math.max(canvasArea.y(), (int) Math.floor(canvasArea.y() + viewport.screenY(bounds.y())));
		int right = Math.min(canvasArea.right(), (int) Math.ceil(canvasArea.x() + viewport.screenX(bounds.right())));
		int bottom = Math.min(canvasArea.bottom(), (int) Math.ceil(canvasArea.y() + viewport.screenY(bounds.bottom())));
		return right > x && bottom > y ? new Rect(x, y, right - x, bottom - y) : null;
	}

	private void setItem(Field field, ItemStack stack) {
		if (stack.isEmpty())
			return;
		ItemInput kind = field.slot.itemInput();
		String value = ComputerItemReference.write(stack, field.includeItemColor);
		if (value.length() > field.slot.maxLiteralLength()) {
			flashStatus(text("item_input_too_long"), 0xffff8983);
			return;
		}
		if (editingSpot != null && editingSpot.field() == field) {
			if (!field.literal.equals(value))
				input.setValue(value);
			finishInput();
		} else if (!field.literal.equals(value)) {
			if (input != null)
				finishInput();
			draft.recordUndo();
			field.literal = value;
			draft.markMutated();
		}
	}

	private void edit(LiteralSpot spot) {
		Field field = spot.field();
		if (field.slot.socket().isSelector()) {
			draft.recordUndo();
			switch (field.slot.socket()) {
				case CONDITION -> field.literal = Boolean.toString(!Boolean.parseBoolean(field.literal));
				case CHANNEL -> field.literal = Channel.next(field.literal);
				case SLOT -> field.literal = ComputerProgram.nextSlot(field.literal);
				default -> {}
			}
			draft.markMutated();
			return;
		}
		editingSpot = spot;
		editingInitial = field.literal;
		editingBefore = draft.snapshot();

		Rect bounds = spot.bounds();
		int boxWidth = Math.min(canvasArea.width(), Math.max(74, (int) (bounds.width() * viewport.scale())));
		int x = Math.clamp(canvasArea.x() + (int) viewport.screenX(bounds.x()), canvasArea.x(), canvasArea.right() - boxWidth);
		input = new EditBox(font, x, editBoxY(bounds), boxWidth, 18, text("edit_literal"));
		input.setMaxLength(field.slot.maxLiteralLength());
		input.setValue(field.literal);
		input.setResponder(value -> {
			field.literal = value;
			draft.markMutated();
		});
		addRenderableWidget(input);
		setFocused(input);
		input.setHighlightPos(0);
	}

	private int editBoxY(Rect bounds) {
		return Math.clamp(canvasArea.y() + (int) viewport.screenY(bounds.y()), canvasArea.y(), Math.max(canvasArea.y(), canvasArea.bottom() - 18));
	}

	private void finishInput() {
		if (!editingSpot.field().literal.equals(editingInitial))
			draft.pushUndoSnapshot(editingBefore);
		editingSpot = null;
		editingInitial = null;
		editingBefore = null;
		setFocused(null);
		removeWidget(input);
		input = null;
	}

	@Override
	protected boolean hasClickedOutside(double x, double y, int left, int top, int button) { return false; }

	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		if (inventoryPick != null) {
			if (key == GLFW.GLFW_KEY_ESCAPE)
				inventoryPick = null;
			return true;
		}
		if (input != null) {
			if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE)
				finishInput();
			else
				input.keyPressed(key, scanCode, modifiers);
			return true;
		}
		if (drag != null && key == GLFW.GLFW_KEY_ESCAPE) {
			drag = null;
			return true;
		}
		if (drag == null && hasControlDown()) {
			if (key == GLFW.GLFW_KEY_Z) {
				if (hasShiftDown())
					draft.redo();
				else
					draft.undo();
				return true;
			}
			if (key == GLFW.GLFW_KEY_Y) {
				draft.redo();
				return true;
			}
			if (key == GLFW.GLFW_KEY_S) {
				saveProgram();
				return true;
			}
		}
		return super.keyPressed(key, scanCode, modifiers);
	}

	private record PaletteEntry(Block template, Preview preview, float scale, int y, int height, int previewHeight,
		int contentHeight, String form, String caption) {}
	private record Drag(Tile tile, Location from, Preview preview, double startX, double startY) {
		boolean movedTo(double x, double y) { return Math.abs(x - startX) >= 4 || Math.abs(y - startY) >= 4; }
	}
	private record InventoryPick(int index, ItemStack stack) {}
	private record FieldTarget(Rect area, Field field) {}
	public record ItemDropTarget(Rect2i area, Consumer<ItemStack> accept) {}
}
