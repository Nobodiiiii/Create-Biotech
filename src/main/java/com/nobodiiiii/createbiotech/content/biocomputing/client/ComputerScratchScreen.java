package com.nobodiiiii.createbiotech.content.biocomputing.client;

import static com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchCanvas.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.lwjgl.glfw.GLFW;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerIO;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerIO.Channel;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerLibrary;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Category;
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
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchCanvas.LiteralSpot;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchCanvas.Rect;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.Field;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.Location;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.Tile;
import com.nobodiiiii.createbiotech.foundation.gui.CanvasViewport;
import com.nobodiiiii.createbiotech.network.CBPackets;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;

public final class ComputerScratchScreen extends AbstractContainerScreen<ComputerProgramMenu> {
	private static final int PALETTE_ROW = 27;
	private static final List<Category> CATEGORIES = List.of(
		Category.EVENTS, Category.CONTROL, Category.OPERATORS, Category.VARIABLES, Category.SENSING, Category.OUTPUT
	);

	private enum ConsoleMode { OUTPUT, VARIABLES, IO }

	private final ScratchDraft draft;
	private final CanvasViewport viewport = new CanvasViewport();
	private Category category = Category.CONTROL;
	private ComputerLibrary library;
	private List<Op> prototypes;
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
	private int panButton = -1;
	private EditBox input;
	private Field editingField;
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
		prototypes = prototypes(category);
	}

	@Override
	protected void init() {
		imageWidth = Math.min(720, width - 12);
		imageHeight = Math.min(468, height - 12);
		super.init();
		input = null;
		editingField = null;
		editingInitial = null;
		editingBefore = null;
		drag = null;
		panButton = -1;
		canvas = new ScratchCanvas(font);

		int sideWidth = Math.clamp(imageWidth * 31 / 100, 114, 158);
		int consoleHeight = Math.clamp(imageHeight / 4, 62, 94);
		int bottom = topPos + imageHeight - 7;
		int right = leftPos + imageWidth - 7;
		consoleArea = new Rect(leftPos + 7, bottom - consoleHeight, imageWidth - 14, consoleHeight);
		canvasArea = new Rect(leftPos + sideWidth + 13, topPos + 31,
			right - (leftPos + sideWidth + 13), consoleArea.y() - topPos - 36);
		paletteArea = new Rect(leftPos + 7, canvasArea.y() + 61, sideWidth, Math.max(0, canvasArea.height() - 61));
		paletteScroll = Math.min(paletteScroll, paletteLimit());

		int bx = right;
		bx -= 18;
		Button help = button(bx, topPos + 6, 18, Component.literal("?"), () -> {});
		help.setTooltip(Tooltip.create(text("help")));

		bx -= 25;
		Button center = button(bx, topPos + 6, 22, Component.literal("↺"), viewport::reset);
		center.setTooltip(Tooltip.create(text("reset_view")));

		bx -= 39;
		Button paste = button(bx, topPos + 6, 36, text("paste"), this::pasteProgram);
		paste.setTooltip(Tooltip.create(text("paste_tooltip")));

		bx -= 39;
		Button copy = button(bx, topPos + 6, 36, text("copy"), this::copyProgram);
		copy.setTooltip(Tooltip.create(text("copy_tooltip")));

		bx -= 41;
		stop = button(bx, topPos + 6, 38, text("stop").withStyle(ChatFormatting.RED), this::stopProgram);

		bx -= 41;
		save = button(bx, topPos + 6, 38, text("save").withStyle(ChatFormatting.AQUA), this::saveProgram);

		bx -= 41;
		run = button(bx, topPos + 6, 38, text("run").withStyle(ChatFormatting.GREEN), this::runProgram);

		int halfTab = (sideWidth - 3) / 2;
		for (int i = 0; i < CATEGORIES.size(); i++) {
			Category option = CATEGORIES.get(i);
			boolean fullRow = i == CATEGORIES.size() - 1 && CATEGORIES.size() % 2 == 1;
			int tabX = paletteArea.x() + (fullRow ? 0 : (i % 2) * (halfTab + 3));
			int tabY = canvasArea.y() + (i / 2) * 20;
			int tabW = fullRow ? sideWidth : halfTab;
			tabs[i] = button(tabX, tabY, tabW, categoryLabel(option, tabW), () -> select(option));
		}

		int cx = consoleArea.x() + 4;
		int[] modeWidths = {46, 46, 42};
		ConsoleMode[] modes = ConsoleMode.values();
		for (int i = 0; i < modes.length; i++) {
			ConsoleMode mode = modes[i];
			consoleTabs[i] = button(cx, consoleArea.y() + 4, modeWidths[i], consoleTabLabel(mode, modeWidths[i]),
				() -> selectConsole(mode));
			cx += modeWidths[i] + 2;
		}
		reload = button(consoleArea.right() - 68, consoleArea.y() + 4, 64, text("reload_program"), this::reloadProgram);
		reload.setTooltip(Tooltip.create(text("reload_program_tooltip")));
		reload.visible = hasConflict();
	}

	private Button button(int x, int y, int width, Component label, Runnable action) {
		return addRenderableWidget(Button.builder(label, ignored -> action.run()).bounds(x, y, width, 18).build());
	}

	private void select(Category option) {
		category = option;
		prototypes = prototypes(option);
		paletteScroll = 0;
		for (int i = 0; i < tabs.length; i++)
			tabs[i].setMessage(categoryLabel(CATEGORIES.get(i), tabs[i].getWidth()));
	}

	private void selectConsole(ConsoleMode mode) {
		consoleMode = mode;
		consoleScroll = 0;
		ConsoleMode[] modes = ConsoleMode.values();
		for (int i = 0; i < consoleTabs.length; i++)
			consoleTabs[i].setMessage(consoleTabLabel(modes[i], consoleTabs[i].getWidth()));
	}

	private Component categoryLabel(Category option, int width) {
		String label = text("category." + option.name().toLowerCase(Locale.ROOT)).getString();
		return Component.literal(canvas.shorten(label, width - 7))
			.withStyle(option == category ? ChatFormatting.GOLD : ChatFormatting.WHITE);
	}

	private Component consoleTabLabel(ConsoleMode mode, int width) {
		String key = switch (mode) {
			case OUTPUT -> "output";
			case VARIABLES -> "variables";
			case IO -> "io";
		};
		return Component.literal(canvas.shorten(text(key).getString(), width - 6))
			.withStyle(mode == consoleMode ? ChatFormatting.GOLD : ChatFormatting.WHITE);
	}

	private List<Op> prototypes(Category category) {
		return Arrays.stream(Op.values()).filter(op -> op.category() == category && library.allows(op)).toList();
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
		viewport.reset();
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
			viewport.reset();
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
			prototypes = prototypes(category);
			paletteScroll = 0;
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
			graphics.pose().translate(mouseX + 7, mouseY + 7, 250);
			canvas.preview(graphics, drag.tile, 0, 0);
			graphics.pose().popPose();
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
		int maxTitleWidth = Math.max(24, toolbarLeft - (leftPos + 8) - countWidth - 8);
		String heading = title.getString() + (draft.isDirty() ? " *" : "");
		String shortened = canvas.shorten(heading, maxTitleWidth);
		graphics.drawString(font, shortened, leftPos + 8, topPos + 11, 0xfff1f5f8, false);
		graphics.drawString(font, countText, leftPos + 8 + font.width(shortened) + 8, topPos + 11, countColor, false);

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
		for (int i = 0; i < prototypes.size(); i++) {
			Rect row = paletteRow(i);
			if (row.intersects(paletteArea)) {
				Op op = prototypes.get(i);
				boolean hovered = drag == null && row.contains(mouseX, mouseY) && paletteArea.contains(mouseX, mouseY);
				canvas.paletteBlock(graphics, op, row, hovered);
				if (hovered) {
					MutableComponent tooltip = text("palette." + op.key()).append(" · ").append(libraryLabel(op.library()));
					if (op == Op.BIND_FREQUENCY || op == Op.TUNE_FREQUENCY)
						tooltip.append("\n").append(text("frequency_help"));
					if (op == Op.ORDER_ITEM || op == Op.ORDER_BATCH)
						tooltip.append("\n").append(text("order_help"));
					setTooltipForNextRenderPass(tooltip);
				}
			}
		}
		graphics.disableScissor();
		drawScrollbar(graphics, paletteArea.right() - 4, paletteArea.y(), paletteArea.height(),
			paletteArea.height() + paletteLimit(), paletteScroll);
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
		graphics.pose().popPose();
		graphics.disableScissor();
	}

	private void drawConsole(GuiGraphics graphics, int mouseX, int mouseY) {
		fill(graphics, consoleArea, 0xff151c23);
		Snapshot snapshot = menu.snapshot();
		int statusLeft = consoleTabs[consoleTabs.length - 1].getX() + consoleTabs[consoleTabs.length - 1].getWidth() + 8;
		int statusRight = reload.visible ? reload.getX() - 5 : consoleArea.right() - 7;
		int maxStatusWidth = Math.max(20, statusRight - statusLeft);

		String statusLabel;
		int statusColor;
		if (pendingAction != null) {
			statusLabel = canvas.shorten(text("submitting").getString(), maxStatusWidth);
			statusColor = 0xff7cd4ff;
		} else if (hasConflict()) {
			statusLabel = canvas.shorten(text("edit_conflict").getString(), maxStatusWidth);
			statusColor = 0xffff8983;
		} else if (statusFlash != null) {
			statusLabel = canvas.shorten(statusFlash.getString(), maxStatusWidth);
			statusColor = statusFlashColor;
		} else if (unavailableBlock() != null) {
			statusLabel = canvas.shorten(libraryRequired(unavailableBlock()).getString(), maxStatusWidth);
			statusColor = 0xffff8983;
		} else {
			Status status = snapshot.status();
			statusLabel = canvas.shorten(text("status." + status.name().toLowerCase(Locale.ROOT)).getString(), maxStatusWidth);
			statusColor = switch (status) {
				case RUNNING -> 0xff9bdb9a;
				case EMPTY_VARIABLE, VARIABLE_LIMIT, LIBRARY_LOCKED, PROCEDURE_LIMIT -> 0xffff8983;
				default -> 0xffa9bdcc;
			};
		}
		graphics.drawString(font, statusLabel, statusRight - font.width(statusLabel), consoleArea.y() + 9,
			statusColor, false);
		if (unavailableBlock() != null && drag == null
			&& new Rect(statusLeft, consoleArea.y() + 4, maxStatusWidth, 17).contains(mouseX, mouseY))
			setTooltipForNextRenderPass(libraryRequired(unavailableBlock()));

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
		if (drag != null || panButton != -1)
			return true;
		if (input != null) {
			if (input.isMouseOver(mouseX, mouseY))
				return super.mouseClicked(mouseX, mouseY, button);
			finishInput();
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && paletteArea.contains(mouseX, mouseY)) {
			for (int i = 0; i < prototypes.size(); i++) {
				if (paletteRow(i).contains(mouseX, mouseY)) {
					drag = new Drag(new Tile(prototypes.get(i)), null);
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
				drag = new Drag(picked.tile(), picked.location());
		} else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			panButton = button;
		}
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
		if (panButton == button) {
			viewport.pan(dx, dy);
			return true;
		}
		return drag != null || super.mouseDragged(mouseX, mouseY, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (panButton == button) {
			panButton = -1;
			return true;
		}
		if (drag == null || button != GLFW.GLFW_MOUSE_BUTTON_LEFT)
			return super.mouseReleased(mouseX, mouseY, button);
		Drop target = dropAt(canvas.layout(draft), mouseX, mouseY);
		if (target != null)
			draft.drop(drag.tile, drag.from, target.location());
		drag = null;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		int direction = (int) Math.signum(vertical);
		if (direction == 0)
			return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
		if (input != null)
			finishInput();
		if (paletteArea.contains(mouseX, mouseY)) {
			paletteScroll = Math.clamp(paletteScroll - direction * PALETTE_ROW, 0, paletteLimit());
		} else if (consoleArea.contains(mouseX, mouseY)) {
			int delta = consoleMode == ConsoleMode.OUTPUT ? direction : -direction;
			consoleScroll = Math.clamp(consoleScroll + delta, 0, Math.max(0, consoleLines().size() - visibleLines()));
		} else if (canvasArea.contains(mouseX, mouseY)) {
			viewport.zoomAt(mouseX - canvasArea.x(), mouseY - canvasArea.y(), direction);
		} else {
			return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
		}
		return true;
	}

	private Rect paletteRow(int index) {
		return new Rect(paletteArea.x() + 5, paletteArea.y() + 4 + index * PALETTE_ROW - paletteScroll, paletteArea.width() - 14, 21);
	}

	private int paletteLimit() { return Math.max(0, prototypes.size() * PALETTE_ROW + 4 - paletteArea.height()); }
	private double worldX(double x) { return viewport.worldX(x - canvasArea.x()); }
	private double worldY(double y) { return viewport.worldY(y - canvasArea.y()); }

	private Drop dropAt(ScratchCanvas.Layout layout, double x, double y) {
		return drag == null || !canvasArea.contains(x, y) ? null : layout.dropAt(draft, drag.tile, drag.from, worldX(x), worldY(y));
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
		editingField = field;
		editingInitial = field.literal;
		editingBefore = draft.snapshot();

		Rect bounds = spot.bounds();
		int boxWidth = Math.min(canvasArea.width(), Math.max(74, (int) (bounds.width() * viewport.scale())));
		int x = Math.clamp(canvasArea.x() + (int) viewport.screenX(bounds.x()), canvasArea.x(), canvasArea.right() - boxWidth);
		int y = Math.clamp(canvasArea.y() + (int) viewport.screenY(bounds.y()), canvasArea.y(), canvasArea.bottom() - 18);
		input = new EditBox(font, x, y, boxWidth, 18, text("edit_literal"));
		input.setMaxLength(ComputerProgram.MAX_LITERAL_LENGTH);
		input.setValue(field.literal);
		input.setResponder(value -> {
			field.literal = value;
			draft.markMutated();
		});
		addRenderableWidget(input);
		setFocused(input);
		input.setHighlightPos(0);
	}

	private void finishInput() {
		if (editingField != null && editingBefore != null && !editingField.literal.equals(editingInitial))
			draft.pushUndoSnapshot(editingBefore);
		editingField = null;
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

	private record Drag(Tile tile, Location from) {}
}
