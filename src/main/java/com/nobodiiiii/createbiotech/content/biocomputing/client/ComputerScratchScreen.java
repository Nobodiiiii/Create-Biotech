package com.nobodiiiii.createbiotech.content.biocomputing.client;

import static com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchCanvas.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerIO;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerItemReference;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerLibrary;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Block;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Category;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.DataType;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Input;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Op;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Socket;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgramJson;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgramMenu;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgramPacket;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgramPacket.Action;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgramPacket.EditResult;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgramPacket.ProgramState;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgramPacket.ProgramUpdate;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerRuntime.ChannelState;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerRuntime.Diagnostic;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerRuntime.Snapshot;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerRuntime.Status;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchCanvas.Drop;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchCanvas.ListControl;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchCanvas.Placed;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchCanvas.Preview;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchCanvas.Rect;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.Datum;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.Element;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.InputPoint;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.Location;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.LoosePoint;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.Tile;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchPanelArt.Grain;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchPanelArt.PanelButton;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchPanelArt.Symbol;
import com.nobodiiiii.createbiotech.foundation.gui.CanvasViewport;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.nobodiiiii.createbiotech.registry.CBBlocks;

import net.minecraft.client.gui.GuiGraphics;
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
	private static final int PANEL_PADDING = 8;
	private static final int PANE_GAP = 6;
	private static final int TITLE_HEIGHT = 17;
	private static final int TOOLBAR_HEIGHT = 25;
	private static final int CONSOLE_TAB_HEIGHT = 17;
	private static final int CONSOLE_BODY_OFFSET = 20;
	private static final int INVENTORY_COLUMNS = 9;
	private static final List<Category> CATEGORIES = List.of(Category.values());

	private enum ConsoleMode { OUTPUT, VARIABLES, IO, INVENTORY }

	private final ScratchDraft draft;
	private final CanvasViewport viewport = new CanvasViewport();
	private final ItemStack computerItem;
	private Category category = Category.CONTROL;
	private ComputerLibrary library;
	private List<PaletteEntry> paletteEntries = List.of();
	private int paletteContentHeight;
	private ComputerLibrary validatedLibrary;
	private long validatedRevision = -1;
	private Op unavailable;
	private ScratchCanvas canvas;
	private Rect paletteArea, workspaceArea, canvasArea, consoleArea, consoleBody, toolbarArea;
	@Nullable
	private Rect libraryArea;
	private final PanelButton[] tabs = new PanelButton[CATEGORIES.size()];
	private final PanelButton[] consoleTabs = new PanelButton[ConsoleMode.values().length];
	private PanelButton run, save, stop, reload;
	private int paletteScroll, consoleScroll;
	private ConsoleMode consoleMode = ConsoleMode.OUTPUT;
	private Drag drag;
	private InventoryPick inventoryPick;
	private int panButton = -1;
	private EditBox input;
	private Placed editingSpot;
	private ComputerProgram executionProgram;
	private long executionCheckRevision = -1;
	private boolean matchesExecution;
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
		computerItem = new ItemStack((library == ComputerLibrary.ALL ? CBBlocks.CREATIVE_COMPUTER : CBBlocks.COMPUTER).get());
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

		initToolbar();
		int sideWidth = paletteArea.width() + 4;
		int halfTab = (sideWidth - 2) / 2;
		int tabHeight = categoryTabHeight();
		for (int i = 0; i < CATEGORIES.size(); i++) {
			Category option = CATEGORIES.get(i);
			boolean fullRow = i == CATEGORIES.size() - 1 && CATEGORIES.size() % 2 == 1;
			int tabX = leftPos + PANEL_PADDING + (i % 2) * (halfTab + 2);
			int tabY = workspaceArea.y() + (i / 2) * (tabHeight + 2);
			int tabW = fullRow ? sideWidth : i % 2 == 0 ? halfTab : sideWidth - halfTab - 2;
			tabs[i] = addRenderableWidget(new PanelButton(new Rect(tabX, tabY, tabW, tabHeight),
				categoryName(option), ScratchBlockArt.tint(option), () -> select(option)));
			tabs[i].selected = option == category;
			tabs[i].setTooltip(Tooltip.create(categoryName(option)));
		}

		int cx = consoleArea.x() + 3;
		int[] modeWidths = consoleArea.width() < 260 ? new int[] {35, 39, 24, 45} : new int[] {46, 46, 42, 53};
		ConsoleMode[] modes = ConsoleMode.values();
		for (int i = 0; i < modes.length; i++) {
			ConsoleMode mode = modes[i];
			Rect area = new Rect(cx, consoleArea.y() + 1, modeWidths[i], CONSOLE_TAB_HEIGHT);
			consoleTabs[i] = addRenderableWidget(new PanelButton(area, consoleName(mode), 0, () -> selectConsole(mode)));
			consoleTabs[i].selected = mode == consoleMode;
			consoleTabs[i].setTooltip(Tooltip.create(mode == ConsoleMode.INVENTORY ? text("inventory_help") : consoleName(mode)));
			cx += modeWidths[i] + 2;
		}
		reload = iconButton(new Rect(consoleArea.right() - 21, consoleArea.y() + 1, 18, CONSOLE_TAB_HEIGHT),
			"reload_program", Symbol.RELOAD, true, this::reloadProgram);
		reload.setTooltip(Tooltip.create(text("reload_program_tooltip")));
		reload.visible = canReload();
	}

	private void initToolbar() {
		boolean compact = imageWidth < 320;
		int buttonWidth = compact ? 18 : 50;
		int y = toolbarArea.y() + 3;
		int x = leftPos + PANEL_PADDING;
		PanelButton copy = iconButton(new Rect(x, y, buttonWidth, 18), "copy", Symbol.COPY, compact, this::copyProgram);
		copy.setTooltip(Tooltip.create(text("copy_tooltip")));
		x += buttonWidth + 3;
		PanelButton paste = iconButton(new Rect(x, y, buttonWidth, 18), "paste", Symbol.PASTE, compact, this::pasteProgram);
		paste.setTooltip(Tooltip.create(text("paste_tooltip")));
		PanelButton help = iconButton(new Rect(x + buttonWidth + 3, y, 18, 18), "help_title", Symbol.HELP, true, () -> {});
		help.setTooltip(Tooltip.create(text("help")));

		x = toolbarArea.right() - PANEL_PADDING - buttonWidth;
		save = iconButton(new Rect(x, y, buttonWidth, 18), "save", Symbol.SAVE, compact, this::saveProgram);
		x -= buttonWidth + 3;
		stop = iconButton(new Rect(x, y, buttonWidth, 18), "stop", Symbol.STOP, compact, this::stopProgram);
		x -= buttonWidth + 3;
		run = iconButton(new Rect(x, y, buttonWidth, 18), "run", Symbol.RUN, compact, this::runProgram);
		iconButton(new Rect(workspaceArea.right() - 22, workspaceArea.y() + 2, 19, workspaceHeaderHeight() - 2),
			"reset_view", Symbol.CENTER, true, this::resetView);
	}

	private PanelButton iconButton(Rect area, String key, Symbol symbol, boolean iconOnly, Runnable action) {
		PanelButton button = addRenderableWidget(new PanelButton(area, text(key), symbol, iconOnly, action));
		button.setTooltip(Tooltip.create(text(key)));
		return button;
	}

	private void layoutAreas() {
		int left = leftPos + PANEL_PADDING;
		int right = leftPos + imageWidth - PANEL_PADDING;
		int bodyY = topPos + (imageHeight < 255 ? 21 : 24);
		int sideWidth = Math.clamp(imageWidth * 26 / 100, 70, 141);
		int workspaceX = left + sideWidth + PANE_GAP;
		boolean dockedConsole = right - workspaceX >= 224;
		toolbarArea = new Rect(leftPos, topPos + imageHeight - TOOLBAR_HEIGHT, imageWidth, TOOLBAR_HEIGHT);
		int bottom = toolbarArea.y() - PANE_GAP;
		int normalConsoleHeight = Math.clamp(43 + (imageHeight - 302) / 6, 36, 64);
		int consoleHeight = consoleMode == ConsoleMode.INVENTORY ? 30 + 4 * inventorySlotSize() : normalConsoleHeight;
		int consoleX = dockedConsole ? workspaceX : left;
		consoleArea = new Rect(consoleX, bottom - consoleHeight, right - consoleX, consoleHeight);
		consoleBody = new Rect(consoleX + 3, consoleArea.y() + CONSOLE_BODY_OFFSET, consoleArea.width() - 6,
			consoleHeight - CONSOLE_BODY_OFFSET - 3);
		libraryArea = dockedConsole ? new Rect(left, bottom - normalConsoleHeight, sideWidth, normalConsoleHeight) : null;
		int workspaceBottom = consoleArea.y() - PANE_GAP;
		workspaceArea = new Rect(workspaceX, bodyY, right - workspaceX, Math.max(0, workspaceBottom - bodyY));
		int headerHeight = workspaceHeaderHeight();
		canvasArea = new Rect(workspaceX + 2, bodyY + 2 + headerHeight, workspaceArea.width() - 4,
			Math.max(0, workspaceArea.height() - headerHeight - 4));
		int tabRows = (tabs.length + 1) / 2;
		int paletteY = bodyY + tabRows * (categoryTabHeight() + 2) + 3;
		int paletteBottom = libraryArea == null ? workspaceBottom : libraryArea.y() - PANE_GAP;
		paletteArea = new Rect(left + 2, paletteY + 2, sideWidth - 4, Math.max(0, paletteBottom - paletteY - 4));
		paletteScroll = Math.min(paletteScroll, paletteLimit());
	}

	private int categoryTabHeight() { return imageHeight < 255 ? 12 : imageHeight < 270 ? 14 : 18; }
	private int workspaceHeaderHeight() { return imageHeight < 255 ? 16 : 19; }
	private int inventorySlotSize() { return imageHeight < 255 ? 16 : 18; }

	private void resetView() {
		viewport.reset();
		if (canvasArea.width() < 250)
			viewport.zoomAt(0, 0, canvasArea.width() < 160 ? -6 : -3);
	}

	private void select(Category option) {
		category = option;
		paletteScroll = 0;
		rebuildPalette();
		for (int i = 0; i < tabs.length; i++)
			tabs[i].selected = CATEGORIES.get(i) == option;
	}

	private void selectConsole(ConsoleMode mode) {
		consoleMode = mode;
		consoleScroll = 0;
		layoutAreas();
		ConsoleMode[] modes = ConsoleMode.values();
		for (int i = 0; i < consoleTabs.length; i++) {
			consoleTabs[i].selected = modes[i] == mode;
			consoleTabs[i].setY(consoleArea.y() + 1);
		}
		reload.setY(consoleArea.y() + 1);
		if (input != null)
			input.setY(editBoxY(editingSpot.bounds()));
	}

	private static Component categoryName(Category option) {
		return text("category." + option.name().toLowerCase(Locale.ROOT));
	}

	private static Component consoleName(ConsoleMode mode) {
		return text(mode.name().toLowerCase(Locale.ROOT));
	}

	private void rebuildPalette() {
		List<PaletteEntry> entries = new ArrayList<>();
		int y = PALETTE_PADDING;
		if (category == Category.DATA) {
			for (DataType type : DataType.values()) {
				PaletteEntry entry = paletteEntry(new Datum(type, type.initial()), text("data." + type.key()), true, y);
				entries.add(entry);
				y += entry.height();
			}
		}
		for (Op op : Op.values()) {
			boolean inCategory = op.category() == category || (category == Category.DATA && op == Op.VARIABLE);
			if (!inCategory || !library.allows(op) || op.isLegacyFrequencyBinding())
				continue;
			PaletteEntry entry = paletteEntry(Tile.create(op), text("palette." + op.key()), false, y);
			entries.add(entry);
			y += entry.height();
		}
		paletteEntries = List.copyOf(entries);
		paletteContentHeight = y + PALETTE_PADDING;
		paletteScroll = Math.min(paletteScroll, paletteLimit());
	}

	private PaletteEntry paletteEntry(Element element, Component name, boolean showName, int y) {
		Preview preview = canvas.preview(element);
		int width = paletteArea.width() - PALETTE_PADDING - PALETTE_SCROLLBAR_GUTTER - 2;
		float scale = Math.min(1, width / (float) preview.bounds().width());
		int previewHeight = (int) Math.ceil(preview.bounds().height() * scale);
		String caption = showName || font.lineHeight * scale < 6 ? name.getString() : "";
		int contentHeight = previewHeight + (caption.isEmpty() ? 0 : font.lineHeight + 2);
		int rowHeight = Math.max(24, contentHeight + 6);
		return new PaletteEntry(element.snapshot(), preview, scale, y, rowHeight, previewHeight, contentHeight, name, caption);
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
		if (action == Action.RUN && program.hasEmptyInputs()) {
			flashStatus(text("fill_inputs"), 0xffffd17a);
			return;
		}
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
			if (drag != null && drag.source == null && drag.element instanceof Tile tile && !library.allows(tile.op))
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
		if (response.result() == EditResult.INCOMPLETE) {
			flashStatus(text("fill_inputs"), 0xffffd17a);
			return;
		}
		if (response.result() != EditResult.ACCEPTED)
			return;
		ComputerProgram saved = response.state().saved();
		if (draft.revision() == submittedDraftRevision && !draft.snapshot().equals(saved)) {
			if (input != null)
				finishInput();
			if (draft.revision() == submittedDraftRevision) {
				drag = null;
				inventoryPick = null;
				draft.replaceWith(saved);
			}
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

	private boolean draftMatchesExecution() {
		ComputerProgram execution = menu.programState().execution();
		if (executionProgram != execution || executionCheckRevision != draft.revision()) {
			executionProgram = execution;
			executionCheckRevision = draft.revision();
			matchesExecution = execution != null && draft.snapshot().equals(execution);
		}
		return matchesExecution;
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
		if (drag != null && (drag.source == null || drag.moved)) {
			graphics.pose().pushPose();
			graphics.pose().translate(0, 0, 250);
			drag.preview.render(graphics, mouseX + 7, mouseY + 7, 1);
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
		ScratchPanelArt.panel(graphics, new Rect(leftPos, topPos, imageWidth, imageHeight), TITLE_HEIGHT, toolbarArea);
		String heading = canvas.shorten(title.getString() + (draft.isDirty() ? " *" : ""), imageWidth - 2 * PANEL_PADDING);
		graphics.drawString(font, heading, leftPos + (imageWidth - font.width(heading)) / 2,
			topPos + (TITLE_HEIGHT - font.lineHeight) / 2, 0xff4b2d18, false);
		int dividerX = run.getX() - 8;
		graphics.fill(dividerX, toolbarArea.y() + 4, dividerX + 1, toolbarArea.bottom() - 4, 0xff92938e);
		graphics.fill(dividerX + 1, toolbarArea.y() + 4, dividerX + 2, toolbarArea.bottom() - 4, 0xffe2e2e1);

		drawPalette(graphics, mouseX, mouseY);
		drawWorkspaceHeader(graphics);
		drawCanvas(graphics, mouseX, mouseY);
		drawLibrary(graphics, mouseX, mouseY);
		drawConsole(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

	private void drawWorkspaceHeader(GuiGraphics graphics) {
		ScratchPanelArt.rim(graphics, workspaceArea);
		Rect header = new Rect(workspaceArea.x() + 2, workspaceArea.y() + 2, workspaceArea.width() - 4, workspaceHeaderHeight());
		ScratchPanelArt.rail(graphics, header);
		int blocks = draft.blockCount();
		String count = blocks + "/" + ComputerProgram.MAX_BLOCKS;
		int countColor = blocks >= ComputerProgram.MAX_BLOCKS ? 0xffff8983
			: blocks >= ComputerProgram.MAX_BLOCKS * 4 / 5 ? 0xffffd17a : 0xffb7b0a3;
		int countX = header.right() - 26 - font.width(count);
		int labelX = header.x() + 6;
		int y = header.y() + (header.height() - font.lineHeight) / 2;
		String label = canvas.shorten(text("program_root").getString(), countX - labelX - 6);
		graphics.drawString(font, label, labelX, y, ScratchPanelArt.LABEL_COLOR, true);
		graphics.drawString(font, count, countX, y, countColor, false);
	}

	private void drawLibrary(GuiGraphics graphics, int mouseX, int mouseY) {
		if (libraryArea == null)
			return;
		Rect area = libraryArea;
		ScratchPanelArt.rim(graphics, area);
		ScratchPanelArt.fill(graphics, area.inflate(-2), 0xff454441);
		int size = Math.min(36, area.height() - 6);
		Rect slot = new Rect(area.x() + 4, area.y() + (area.height() - size) / 2, size, size);
		ScratchPanelArt.recess(graphics, slot, 0xff312f2c, 0xff666059);
		graphics.enableScissor(slot.x() + 1, slot.y() + 1, slot.right() - 1, slot.bottom() - 1);
		graphics.pose().pushPose();
		graphics.pose().translate(slot.x() + 1, slot.y() + 1, 0);
		float scale = (size - 2) / 16f;
		graphics.pose().scale(scale, scale, 1);
		graphics.renderItem(computerItem, 0, 0);
		graphics.pose().popPose();
		graphics.disableScissor();
		int x = slot.right() + 7;
		int y = area.y() + (area.height() - 2 * font.lineHeight - 4) / 2;
		int available = area.right() - x - 5;
		graphics.drawString(font, canvas.shorten(text("library_title").getString(), available), x, y, 0xffbbb3a1, false);
		Component label = libraryLabel(library);
		String shown = canvas.shorten(label.getString(), available);
		graphics.drawString(font, shown, x, y + font.lineHeight + 4, 0xffebdeba, true);
		if (!shown.equals(label.getString()) && area.contains(mouseX, mouseY))
			setTooltipForNextRenderPass(label);
	}

	private void drawPalette(GuiGraphics graphics, int mouseX, int mouseY) {
		if (paletteArea.height() == 0)
			return;
		ScratchPanelArt.rim(graphics, paletteArea.inflate(2));
		ScratchPanelArt.grain(graphics, paletteArea, Grain.PALETTE);
		graphics.enableScissor(paletteArea.x(), paletteArea.y(), paletteArea.right(), paletteArea.bottom());
		for (PaletteEntry entry : paletteEntries) {
			Rect row = paletteRow(entry);
			if (row.intersects(paletteArea)) {
				boolean hovered = drag == null && row.contains(mouseX, mouseY) && paletteArea.contains(mouseX, mouseY);
				if (hovered)
					ScratchPanelArt.fill(graphics, row, 0xff4b3c2d);
				int y = row.y() + (row.height() - entry.contentHeight()) / 2;
				entry.preview().render(graphics, row.x() + 1, y, entry.scale());
				if (!entry.caption().isEmpty()) {
					String label = canvas.shorten(entry.caption(), row.width() - 2);
					graphics.drawString(font, label, row.x() + 1, y + entry.previewHeight() + 2, 0xffded0b9, false);
				}
				if (hovered) {
					MutableComponent tooltip = entry.name().copy();
					if (entry.template() instanceof Block block && block.op().category() != Category.DATA) {
						Op op = block.op();
						tooltip.append(" · ").append(libraryLabel(op.library()));
						if (op.hasFrequencyInput())
							tooltip.append("\n").append(text("frequency_help"));
						if (op == Op.STOCK_COUNT || op == Op.HAS_STOCK)
							tooltip.append("\n").append(text("stock_list_help"));
						if (op == Op.ORDER_ITEM || op == Op.ORDER_BATCH)
							tooltip.append("\n").append(text("order_help"));
					}
					setTooltipForNextRenderPass(tooltip);
				}
			}
		}
		graphics.disableScissor();
		ScratchPanelArt.scrollBar(graphics, paletteArea.right() - 5, paletteArea.y() + 2, paletteArea.height() - 4,
			paletteContentHeight - 4, paletteScroll);
	}

	private void drawCanvas(GuiGraphics graphics, int mouseX, int mouseY) {
		ScratchPanelArt.grain(graphics, canvasArea, Grain.CANVAS);
		Snapshot snapshot = menu.snapshot();
		boolean sameProgram = draftMatchesExecution();
		int activeBlock = sameProgram && snapshot.status() == Status.RUNNING ? snapshot.activeBlockIndex() : -1;
		int problemBlock = sameProgram && snapshot.diagnostic() != null ? snapshot.diagnostic().blockIndex() : -1;
		ScratchCanvas.Layout layout = canvas.layout(draft, activeBlock, problemBlock);
		graphics.enableScissor(canvasArea.x(), canvasArea.y(), canvasArea.right(), canvasArea.bottom());
		graphics.pose().pushPose();
		graphics.pose().translate(canvasArea.x() + viewport.screenX(0), canvasArea.y() + viewport.screenY(0), 0);
		float scale = (float) viewport.scale();
		graphics.pose().scale(scale, scale, 1);
		int visibleLeft = (int) Math.floor(worldX(canvasArea.x()));
		int visibleTop = (int) Math.floor(worldY(canvasArea.y()));
		layout.render(graphics, new Rect(visibleLeft, visibleTop, (int) Math.ceil(canvasArea.width() / scale) + 2,
			(int) Math.ceil(canvasArea.height() / scale) + 2));
		Drop target = dropAt(layout, mouseX, mouseY);
		if (target != null)
			target.highlight(graphics);
		if (inventoryPick != null) {
			for (Placed spot : layout.items()) {
				Rect bounds = spot.bounds();
				graphics.renderOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 0xffdfc895);
			}
			for (Drop name : layout.nameFields())
				name.highlight(graphics);
		}
		graphics.pose().popPose();
		graphics.disableScissor();
		if (drag == null && inventoryPick == null && panButton == -1 && canvasArea.contains(mouseX, mouseY)) {
			Placed itemSpot = itemSpotAt(layout, mouseX, mouseY);
			if (itemSpot != null) {
				Component help = text("item_input_help");
				String item = itemSpot.item().text;
				if (item.isEmpty()) {
					setTooltipForNextRenderPass(help);
				} else {
					MutableComponent tooltip = ComputerItemReference.displayName(item).copy();
					if (!item.equals(tooltip.getString()))
						tooltip.append("\n").append(item);
					setTooltipForNextRenderPass(tooltip.append("\n").append(help));
				}
			} else if (input == null) {
				double x = worldX(mouseX);
				double y = worldY(mouseY);
				Placed placed = layout.blockAt(x, y);
				ListControl control = layout.listControlAt(placed, x, y);
				Drop empty = layout.emptyAt(placed, x, y);
				if (control != null) {
					setTooltipForNextRenderPass(text(control.row() < 0 ? "add_list_entry" : "remove_list_entry"));
				} else if (empty != null) {
					setTooltipForNextRenderPass(text(empty.nameSlot() == null ? "empty_input" : "fixed_name_help"));
				} else if (placed != null && placed.element() instanceof Datum datum) {
					setTooltipForNextRenderPass(text("data." + datum.type.key()).append("\n").append(text("edit_data_help")));
				} else if (placed != null && placed.element().isData()) {
					MutableComponent help = text("drag_data_help");
					if (placed.location() instanceof InputPoint point) {
						Socket socket = point.field().slot.socket();
						if (socket == Socket.LIST_TARGET || socket == Socket.MAP_TARGET)
							help.append("\n").append(text("inline_collection_help"));
					}
					setTooltipForNextRenderPass(help);
				}
			}
		}
	}

	private void drawConsole(GuiGraphics graphics, int mouseX, int mouseY) {
		ScratchPanelArt.rim(graphics, consoleArea);
		ScratchPanelArt.fill(graphics, consoleArea.inflate(-2), 0xff4c4940);
		ScratchPanelArt.rail(graphics, new Rect(consoleArea.x() + 2, consoleArea.y() + 2,
			consoleArea.width() - 4, CONSOLE_BODY_OFFSET - 2));
		ScratchPanelArt.recess(graphics, consoleBody, consoleMode == ConsoleMode.INVENTORY ? 0xff373630 : 0xff263229,
			0xff6e7865);
		Snapshot snapshot = menu.snapshot();

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
		} else if (snapshot.diagnostic() != null) {
			Diagnostic diagnostic = snapshot.diagnostic();
			statusMessage = text("problem." + diagnostic.problem().name().toLowerCase(Locale.ROOT));
			if (diagnostic.blockIndex() >= 0)
				statusMessage = text("problem_at", diagnostic.blockIndex() + 1, statusMessage);
			statusMessage = text("status." + snapshot.status().name().toLowerCase(Locale.ROOT)).append(" · ").append(statusMessage);
			statusColor = 0xffff8983;
		} else {
			Status status = snapshot.status();
			statusMessage = text("status." + status.name().toLowerCase(Locale.ROOT));
			statusColor = switch (status) {
				case RUNNING -> 0xffc6da9f;
				case EMPTY_VARIABLE, VARIABLE_LIMIT, LIBRARY_LOCKED, PROCEDURE_LIMIT, COLLECTION_LIMIT -> 0xffff8983;
				default -> 0xffc1c0b4;
			};
		}
		drawConsoleStatus(graphics, statusMessage, statusColor, mouseX, mouseY);

		if (consoleMode == ConsoleMode.INVENTORY) {
			drawInventory(graphics, mouseX, mouseY);
			return;
		}
		int rowHeight = font.lineHeight + 2;
		int startY = consoleBody.y() + 3;
		List<Component> lines = consoleLines();
		if (lines.isEmpty()) {
			String emptyKey = consoleMode == ConsoleMode.VARIABLES ? "no_variables" : "no_output";
			graphics.drawString(font, text(emptyKey), consoleBody.x() + 7, startY, 0xffa1ae98, false);
			return;
		}
		int maxScroll = Math.max(0, lines.size() - visibleLines());
		consoleScroll = Math.min(maxScroll, consoleScroll);
		int from = consoleMode == ConsoleMode.OUTPUT ? maxScroll - consoleScroll : consoleScroll;
		for (int i = from; i < Math.min(lines.size(), from + visibleLines()); i++) {
			Component line = lines.get(i);
			String fullLine = line.getString();
			int x = consoleBody.x() + 7;
			int y = startY + (i - from) * rowHeight;
			if (consoleMode == ConsoleMode.OUTPUT) {
				graphics.drawString(font, Integer.toString(i + 1), x, y, 0xff9ea793, false);
				graphics.fill(x + 13, y, x + 14, y + font.lineHeight, 0xff465446);
				x += 21;
			}
			Rect row = new Rect(x, y, consoleBody.right() - x - 8, rowHeight);
			String shown = canvas.shorten(fullLine, row.width());
			graphics.drawString(font, shown, row.x(), row.y(), 0xffd3e0c2, false);
			if (drag == null && !shown.equals(fullLine) && row.contains(mouseX, mouseY))
				setTooltipForNextRenderPass(line);
		}
		ScratchPanelArt.scrollBar(graphics, consoleBody.right() - 6, startY, visibleLines() * rowHeight, lines.size() * rowHeight,
			from * rowHeight);
	}

	private void drawConsoleStatus(GuiGraphics graphics, Component message, int color, int mouseX, int mouseY) {
		PanelButton last = consoleTabs[consoleTabs.length - 1];
		int left = last.getX() + last.getWidth() + 5;
		int right = reload.visible ? reload.getX() - 4 : consoleArea.right() - 6;
		if (right - left < 10)
			return;
		String status = canvas.shorten(message.getString(), right - left - 16);
		int width = status.isEmpty() ? 10 : font.width(status) + 16;
		Rect badge = new Rect(right - width, consoleArea.y() + 3, width, 13);
		ScratchPanelArt.recess(graphics, badge, 0xff343a2d, 0xff727263);
		graphics.fill(badge.x() + 3, badge.y() + 5, badge.x() + 7, badge.y() + 9, 0xff23271f);
		graphics.fill(badge.x() + 4, badge.y() + 6, badge.x() + 7, badge.y() + 9, color);
		if (!status.isEmpty())
			graphics.drawString(font, status, badge.x() + 11, badge.y() + (badge.height() - font.lineHeight) / 2, color, false);
		if (!status.equals(message.getString()) && drag == null && inventoryPick == null && badge.contains(mouseX, mouseY))
			setTooltipForNextRenderPass(message);
	}

	private void drawInventory(GuiGraphics graphics, int mouseX, int mouseY) {
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			Rect slot = inventorySlot(i);
			ScratchPanelArt.recess(graphics, slot, slot.contains(mouseX, mouseY) ? 0xff74684f : 0xff4d4b42, 0xff8a8575);
			ItemStack stack = menu.getSlot(i).getItem();
			if (!stack.isEmpty()) {
				int inset = (slot.width() - 16) / 2;
				graphics.renderItem(stack, slot.x() + inset, slot.y() + inset);
				graphics.renderItemDecorations(font, stack, slot.x() + inset, slot.y() + inset);
			}
		}
	}

	private Rect inventorySlot(int index) {
		boolean hotbar = index < INVENTORY_COLUMNS;
		int row = hotbar ? 3 : index / INVENTORY_COLUMNS - 1;
		int size = inventorySlotSize();
		int x = consoleBody.x() + (consoleBody.width() - INVENTORY_COLUMNS * size) / 2;
		int y = consoleBody.y() + 3 + row * size + (hotbar ? 3 : 0);
		return new Rect(x + (index % INVENTORY_COLUMNS) * size, y, size, size);
	}

	private int inventoryIndexAt(double x, double y) {
		if (consoleMode == ConsoleMode.INVENTORY && consoleArea.contains(x, y)) {
			for (int i = 0; i < Inventory.INVENTORY_SIZE; i++)
				if (inventorySlot(i).contains(x, y))
					return i;
		}
		return -1;
	}

	private List<Component> consoleLines() {
		Snapshot snapshot = menu.snapshot();
		return switch (consoleMode) {
			case OUTPUT -> snapshot.output();
			case VARIABLES -> snapshot.variables().stream()
				.<Component>map(variable -> Component.literal(variable.name() + " = ").append(variable.value())).toList();
			case IO -> formatIoLines(snapshot);
			case INVENTORY -> List.of();
		};
	}

	private List<Component> formatIoLines(Snapshot snapshot) {
		List<Component> lines = new ArrayList<>(2 + snapshot.channels().size() + ComputerIO.INPUT_SLOTS);
		lines.add(text("io.library", libraryLabel(library)));
		if (snapshot.channels().isEmpty())
			lines.add(text("io.channel_unbound"));
		for (ChannelState state : snapshot.channels())
			lines.add(text("io.channel", frequencyName(state.first()), frequencyName(state.second()), state.input(), state.output()));
		if (library.includesLogistics() || snapshot.stockLinkBound())
			lines.add(text(snapshot.stockLinkBound() ? "io.stock_bound" : "io.stock_unbound"));
		List<String> slots = snapshot.inputSlots();
		for (int i = 0; i < ComputerIO.INPUT_SLOTS; i++) {
			lines.add(text("io.input_slot", i + 1, emptyPlaceholder(slots.get(i))));
		}
		return lines;
	}

	private static String emptyPlaceholder(String value) {
		return value.isEmpty() ? "—" : value;
	}

	private static Component frequencyName(String reference) {
		return reference.isEmpty() ? Component.literal("—") : ComputerItemReference.displayName(reference);
	}

	private int visibleLines() { return Math.max(1, (consoleBody.height() - 5) / (font.lineHeight + 2)); }

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
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && (editingSpot.item() != null || editingSpot.nameSlot() != null)
				&& consoleTabs[ConsoleMode.INVENTORY.ordinal()].isMouseOver(mouseX, mouseY)) {
				selectConsole(ConsoleMode.INVENTORY);
				return true;
			}
			finishInput();
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && paletteArea.contains(mouseX, mouseY)) {
			for (PaletteEntry entry : paletteEntries) {
				if (paletteRow(entry).contains(mouseX, mouseY)) {
					beginDrag(ScratchDraft.element(entry.template()), null, mouseX, mouseY);
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
		Placed picked = layout.blockAt(x, y);
		ListControl control = layout.listControlAt(picked, x, y);
		if (control != null) {
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
				draft.editCollection(control.tile(), control.row(), control.depth());
			return true;
		}
		Drop empty = layout.emptyAt(picked, x, y);
		if (empty != null) {
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && empty.location() instanceof InputPoint point) {
				Datum datum = new Datum(DataType.TEXT, "");
				edit(new Placed(empty.area(), datum, point, false), datum);
			}
			return true;
		}
		if (picked != null) {
			if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
				draft.remove(picked.location());
			else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
				beginDrag(picked.element(), picked, mouseX, mouseY);
		} else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			panButton = button;
		}
		return true;
	}

	private void beginDrag(Element element, Placed source, double x, double y) {
		drag = new Drag(element, source, canvas.preview(element), x, y);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
		if (panButton == button) {
			viewport.pan(dx, dy);
			return true;
		}
		if (drag != null)
			drag.track(mouseX, mouseY);
		return drag != null || inventoryPick != null || super.mouseDragged(mouseX, mouseY, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (inventoryPick != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			InventoryPick pick = inventoryPick;
			inventoryPick = null;
			Placed spot = null;
			InputPoint name = null;
			if (editingSpot != null && (inventoryIndexAt(mouseX, mouseY) == pick.index() || input.isMouseOver(mouseX, mouseY))) {
				if (editingSpot.item() != null)
					spot = editingSpot;
				else
					name = editingSpot.nameSlot();
			} else if (canvasArea.contains(mouseX, mouseY)) {
				ScratchCanvas.Layout layout = canvas.layout(draft);
				Drop target = layout.nameAt(worldX(mouseX), worldY(mouseY));
				if (target != null)
					name = target.nameSlot();
				else
					spot = itemSpotAt(layout, mouseX, mouseY);
			}
			setItem(spot, name, pick.stack());
			return true;
		}
		if (panButton == button) {
			panButton = -1;
			return true;
		}
		if (drag == null || button != GLFW.GLFW_MOUSE_BUTTON_LEFT)
			return super.mouseReleased(mouseX, mouseY, button);
		Drag released = drag;
		released.track(mouseX, mouseY);
		if (released.moved) {
			ScratchCanvas.Layout layout = canvas.layout(draft);
			Drop target = dropAt(layout, mouseX, mouseY);
			if (target != null) {
				draft.drop(released.element, released.from(), target.location());
			} else if (released.element.isData()) {
				if (paletteArea.contains(mouseX, mouseY) && released.source != null) {
					draft.remove(released.from());
				} else if (canvasArea.contains(mouseX, mouseY)) {
					double x = worldX(mouseX);
					double y = worldY(mouseY);
					Placed hit = layout.blockAt(x, y);
					if (hit == null || (released.from() instanceof LoosePoint && hit.element() == released.element))
						draft.placeLoose(released.element, released.from(), (int) Math.floor(x), (int) Math.floor(y));
				}
			}
		} else if (released.source != null && released.element instanceof Datum datum) {
			edit(released.source, datum);
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
		return drag == null || !drag.moved || !canvasArea.contains(x, y) ? null
			: layout.dropAt(draft, drag.element, drag.from(), worldX(x), worldY(y));
	}

	private Placed itemSpotAt(ScratchCanvas.Layout layout, double x, double y) {
		Placed spot = input != null && input.isMouseOver(x, y) ? editingSpot : layout.blockAt(worldX(x), worldY(y));
		return spot != null && spot.item() != null ? spot : null;
	}

	public List<ItemDropTarget> itemDropTargets() {
		return visibleItemTargets().stream().map(target -> {
			Rect area = target.area();
			return new ItemDropTarget(new Rect2i(area.x(), area.y(), area.width(), area.height()), stack -> {
				// JEI keeps the original targets for the duration of a drag.
				if (visibleItemTargets().contains(target))
					setItem(target.spot(), target.name(), stack);
			});
		}).toList();
	}

	private List<ItemTarget> visibleItemTargets() {
		if (canvas == null || drag != null || inventoryPick != null || panButton != -1)
			return List.of();
		List<ItemTarget> targets = new ArrayList<>();
		Rect editArea = input == null ? null : new Rect(input.getX(), input.getY(), input.getWidth(), input.getHeight());
		InputPoint editingName = editingSpot == null ? null : editingSpot.nameSlot();
		if (editArea != null && editingSpot.item() != null)
			targets.add(new ItemTarget(editArea, editingSpot, null));
		else if (editArea != null && editingName != null)
			targets.add(new ItemTarget(editArea, null, editingName));
		ScratchCanvas.Layout layout = canvas.layout(draft);
		for (Placed spot : layout.items()) {
			if (editingSpot != null && spot.element() == editingSpot.element())
				continue;
			Rect area = visibleScreenArea(spot.bounds());
			if (area != null && (editArea == null || !editArea.intersects(area)))
				targets.add(new ItemTarget(area, spot, null));
		}
		for (Drop name : layout.nameFields()) {
			if (name.location().equals(editingName))
				continue;
			Rect area = visibleScreenArea(name.area());
			if (area != null && (editArea == null || !editArea.intersects(area)))
				targets.add(new ItemTarget(area, null, name.nameSlot()));
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

	private void setItem(@Nullable Placed spot, @Nullable InputPoint name, ItemStack stack) {
		if (stack.isEmpty() || (spot == null && name == null))
			return;
		String value = ComputerItemReference.write(stack, spot != null && spot.includeItemColor());
		if (value.length() > DataType.ITEM.maxLength()) {
			flashStatus(text("item_input_too_long"), 0xffff8983);
			return;
		}
		if (name != null) {
			Datum item = new Datum(DataType.ITEM, value);
			if (draft.canDrop(item, null, name)) {
				if (input != null)
					finishInput();
				draft.drop(item, null, name);
			}
		} else if (editingSpot != null && editingSpot.element() == spot.element()) {
			input.setValue(value);
			finishInput();
		} else {
			if (input != null)
				finishInput();
			draft.setData(spot.item(), value);
		}
	}

	private void edit(Placed spot, Datum datum) {
		if (datum.type == DataType.BOOLEAN) {
			draft.setData(datum, Boolean.toString(!Boolean.parseBoolean(datum.text)));
			return;
		}
		editingSpot = spot;

		Rect bounds = spot.bounds();
		int boxWidth = Math.min(canvasArea.width(), Math.max(74, (int) (bounds.width() * viewport.scale())));
		int x = Math.clamp(canvasArea.x() + (int) viewport.screenX(bounds.x()), canvasArea.x(), canvasArea.right() - boxWidth);
		input = new EditBox(font, x, editBoxY(bounds), boxWidth, 18, text("edit_literal"));
		input.setMaxLength(datum.type.maxLength());
		input.setValue(datum.text);
		addRenderableWidget(input);
		setFocused(input);
		input.setHighlightPos(0);
	}

	private int editBoxY(Rect bounds) {
		return Math.clamp(canvasArea.y() + (int) viewport.screenY(bounds.y()), canvasArea.y(), Math.max(canvasArea.y(), canvasArea.bottom() - 18));
	}

	private void finishInput() {
		Datum datum = (Datum) editingSpot.element();
		String value = input.getValue();
		if (editingSpot.location() instanceof InputPoint point && point.field().value == null) {
			if (!value.isEmpty())
				draft.drop(new Datum(datum.type, value), null, point);
		} else {
			draft.setData(datum, value);
		}
		editingSpot = null;
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

	private record PaletteEntry(Input template, Preview preview, float scale, int y, int height, int previewHeight,
		int contentHeight, Component name, String caption) {}

	private static final class Drag {
		final Element element;
		final Placed source;
		final Preview preview;
		final double startX, startY;
		boolean moved;

		Drag(Element element, Placed source, Preview preview, double startX, double startY) {
			this.element = element;
			this.source = source;
			this.preview = preview;
			this.startX = startX;
			this.startY = startY;
		}

		Location from() { return source == null ? null : source.location(); }
		void track(double x, double y) { moved |= Math.abs(x - startX) >= 4 || Math.abs(y - startY) >= 4; }
	}
	private record InventoryPick(int index, ItemStack stack) {}
	private record ItemTarget(Rect area, @Nullable Placed spot, @Nullable InputPoint name) {}
	public record ItemDropTarget(Rect2i area, Consumer<ItemStack> accept) {}
}
