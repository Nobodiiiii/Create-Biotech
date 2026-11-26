package com.nobodiiiii.createbiotech.content.biocomputing.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerItemReference;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Category;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.DataType;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Op;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Shape;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchBlockArt.Outline;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchBlockArt.Region;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.Datum;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.Element;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.Field;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.InputPoint;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.Location;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.LooseData;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.LoosePoint;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.StackPoint;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.Tile;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchPanelArt.Symbol;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

final class ScratchCanvas {
	private static final int DATA_TEXT_INSET_X = 6;
	// The final glyph's advance already supplies one pixel of the right inset.
	private static final int LITERAL_TEXT_PADDING = 2 * DATA_TEXT_INSET_X - 1;
	private static final int SLOT_MARGIN = 2;
	private static final int SLOT_PADDING = 2 * SLOT_MARGIN;
	private static final int NON_DATA_TEXT_INSET_X = DATA_TEXT_INSET_X + SLOT_MARGIN;
	private static final int DATUM_HEIGHT = 16;
	private static final int BLOCK_HEIGHT = DATUM_HEIGHT + SLOT_PADDING;
	private static final int STACK_SPACING = SLOT_PADDING;
	private static final int BRANCH_EDGE_HEIGHT = DATUM_HEIGHT - SLOT_PADDING;
	private static final int BRANCH_SLOT_HEIGHT = BLOCK_HEIGHT + STACK_SPACING - BRANCH_EDGE_HEIGHT - SLOT_PADDING;
	private static final int INDENT = 18;
	private static final int MIN_STACK_WIDTH = 98;
	private static final int LIST_ITEM_WIDTH = 68;
	private static final int LIST_QUANTITY_WIDTH = 32;
	private static final int LIST_CONTROL_SIZE = 14;
	private static final int ITEM_ICON_SIZE = 12;
	private static final int ITEM_ICON_GAP = 4;
	private static final int BLOCK_INK = 0xfff7ecd9;
	private static final int VALUE_INK = 0xff2b261e;
	private static final int LITERAL_TINT = 0xfffff4de;
	private static final int GUIDE = 0xffdfc895;
	private static final int SOCKET_FILL = 0xff2a2c29;
	private static final int SOCKET_EDGE = 0xff7c7465;
	private final Font font;

	ScratchCanvas(Font font) {
		this.font = font;
	}

	Layout layout(ScratchDraft draft) {
		return layout(draft, -1, -1);
	}

	Layout layout(ScratchDraft draft, int activeCommandIndex, int problemCommandIndex) {
		Layout layout = new Layout(activeCommandIndex, problemCommandIndex);
		boolean hasDeclarations = draft.commands.stream().anyMatch(tile -> tile.op.isDeclaration());
		String label = text(hasDeclarations ? "program_root" : "startup").getString();
		int flagSize = ScratchPanelArt.SYMBOL_SIZE;
		int width = Math.max(102, font.width(label) + 2 * NON_DATA_TEXT_INSET_X + flagSize + SLOT_MARGIN);
		Rect hat = new Rect(14, 14, width, BLOCK_HEIGHT);
		layout.art(hat, Category.EVENTS, Region.HAT);
		layout.symbol(Symbol.FLAG, hat.right() - NON_DATA_TEXT_INSET_X - flagSize, centeredTextY(hat), 0xfff4d78d);
		layout.label(label, hat.x + NON_DATA_TEXT_INSET_X, hat, BLOCK_INK);
		layout.stack(draft.commands, null, 1, hat.x, hat.bottom() + STACK_SPACING, hat.width);
		for (LooseData data : draft.looseData)
			layout.element(data.element(), new LoosePoint(draft.looseData, data), data.x(), data.y());
		return layout;
	}

	Preview preview(Element element) {
		Layout layout = new Layout(-1, -1);
		Rect bounds = layout.element(element, null, 0, 0);
		if (element instanceof Tile tile && tile.op.shape() == Shape.COMMAND) {
			int end = layout.bodies(tile, null, bounds.x, bounds.bottom(), bounds.width);
			bounds = new Rect(bounds.x, bounds.y, bounds.width, end - bounds.y);
			bounds = ScratchBlockArt.paintedBounds(bounds, commandRegion(tile.op));
		}
		return new Preview(layout, bounds);
	}

	private static Region commandRegion(Op op) {
		return op.bodyCount() > 0 ? Region.MOUTH : op == Op.STOP ? Region.CAP : Region.STACK;
	}

	String shorten(String label, int maxWidth) {
		return shorten(font, label, maxWidth);
	}

	static String shorten(Font font, String label, int maxWidth) {
		if (font.width(label) <= maxWidth)
			return label;
		int suffixWidth = font.width("…");
		return maxWidth < suffixWidth ? "" : font.plainSubstrByWidth(label, maxWidth - suffixWidth) + "…";
	}

	static MutableComponent text(String key) {
		return Component.translatable("create_biotech.biocomputing.scratch." + key);
	}

	static MutableComponent text(String key, Object... args) {
		return Component.translatable("create_biotech.biocomputing.scratch." + key, args);
	}

	private String dataLabel(Datum datum) {
		return switch (datum.type) {
			case BOOLEAN -> text("boolean." + datum.text).getString();
			case TEXT, NUMBER -> datum.text;
			case ITEM -> datum.text.isEmpty() ? text("data.item").getString()
				: ComputerItemReference.displayName(datum.text).getString();
		};
	}

	private int dataWidth(Datum datum) {
		int labelWidth = font.width(dataLabel(datum));
		int width = labelWidth + LITERAL_TEXT_PADDING;
		return switch (datum.type) {
			case BOOLEAN -> Math.clamp(width, 40, 76);
			case ITEM -> Math.clamp(labelWidth + 2 * DATA_TEXT_INSET_X + ITEM_ICON_SIZE + ITEM_ICON_GAP, 48, 100);
			case NUMBER -> Math.clamp(width, 17, 82);
			case TEXT -> Math.clamp(width, 26, 82);
		};
	}

	private void drawItem(GuiGraphics graphics, Rect bounds, Datum datum) {
		ItemStack stack = ComputerItemReference.read(datum.text, true);
		int textWidth = bounds.width - 2 * DATA_TEXT_INSET_X;
		if (!stack.isEmpty()) {
			graphics.pose().pushPose();
			graphics.pose().translate(bounds.right() - DATA_TEXT_INSET_X - ITEM_ICON_SIZE,
				centeredY(bounds, ITEM_ICON_SIZE), 0);
			graphics.pose().scale(ITEM_ICON_SIZE / 16f, ITEM_ICON_SIZE / 16f, 1);
			graphics.renderItem(stack, 0, 0);
			graphics.pose().popPose();
			textWidth -= ITEM_ICON_SIZE + ITEM_ICON_GAP;
		}
		String label = shorten(stack.isEmpty() ? dataLabel(datum) : stack.getHoverName().getString(), textWidth);
		graphics.drawString(font, label, bounds.x + DATA_TEXT_INSET_X, centeredTextY(bounds), VALUE_INK, false);
	}

	private static int centeredY(Rect bounds, int height) { return bounds.y + (bounds.height - height) / 2; }

	private int centeredTextY(Rect bounds) { return centeredY(bounds, font.lineHeight) + 1; }

	final class Layout {
		private final int activeCommandIndex;
		private final int problemCommandIndex;
		private final Map<Tile, Header> headers = new IdentityHashMap<>();
		private final List<Piece> pieces = new ArrayList<>();
		private final List<Placed> placed = new ArrayList<>();
		private final List<Drop> drops = new ArrayList<>();
		private final List<Placed> items = new ArrayList<>();
		private final List<Drop> emptySlots = new ArrayList<>();
		private final List<ListControl> listControls = new ArrayList<>();
		private int nextCommandIndex;

		private Layout(int activeCommandIndex, int problemCommandIndex) {
			this.activeCommandIndex = activeCommandIndex;
			this.problemCommandIndex = problemCommandIndex;
		}

		void render(GuiGraphics graphics, Rect visible) {
			for (Piece piece : pieces)
				if (visible == null || piece.bounds.intersects(visible))
					piece.draw.accept(graphics);
		}

		Placed blockAt(double x, double y) {
			for (Placed block : placed.reversed())
				if (block.bounds.contains(x, y))
					return block;
			return null;
		}

		Drop emptyAt(Placed picked, double x, double y) {
			if (picked != null)
				for (Drop slot : emptySlots)
					if (slot.location.owner() == picked.element() && slot.area.contains(x, y))
						return slot;
			return null;
		}

		List<Placed> items() { return items; }

		ListControl listControlAt(Placed picked, double x, double y) {
			for (ListControl control : listControls)
				if (picked != null && control.tile == picked.element && control.bounds.contains(x, y))
					return control;
			return null;
		}

		@Nullable
		Drop nameAt(double x, double y) {
			Placed picked = blockAt(x, y);
			if (picked != null)
				for (Drop drop : drops.reversed()) {
					InputPoint name = drop.nameSlot();
					if (name != null && drop.area.contains(x, y)
						&& (picked.element() == name.owner() || picked.location().equals(name)))
						return drop;
				}
			return null;
		}

		List<Drop> nameFields() {
			return drops.stream().filter(drop -> drop.nameSlot() != null).toList();
		}

		Drop dropAt(ScratchDraft draft, Element element, Location from, double x, double y) {
			Drop name = nameAt(x, y);
			if (name != null)
				return draft.canDrop(element, from, name.location) ? name : null;
			for (Drop drop : drops.reversed()) {
				if (drop.nameSlot() != null || !drop.area.contains(x, y))
					continue;
				// Dropping back onto the original slot must not replace an enclosing reporter.
				if (from instanceof InputPoint && drop.location.equals(from))
					return null;
				if (draft.canDrop(element, from, drop.location))
					return drop;
			}
			return null;
		}

		private int stack(List<Tile> stack, @Nullable Tile owner, int depth, int x, int y, int slotWidth) {
			int lastWidth = MIN_STACK_WIDTH;
			for (int i = 0; i < stack.size(); i++) {
				Tile tile = stack.get(i);
				Header header = measure(tile);
				StackPoint location = new StackPoint(owner, stack, i, depth);
				insertGuide(location, x, y - 2, Math.max(lastWidth, header.width));
				y = command(tile, location, x, y, header) + STACK_SPACING;
				lastWidth = Math.max(MIN_STACK_WIDTH, header.width);
			}
			StackPoint end = new StackPoint(owner, stack, stack.size(), depth);
			if (owner == null && !stack.isEmpty()) {
				insertGuide(end, x, y - 2, lastWidth);
				return y;
			}

			// Each branch keeps room for its insertion target after its command rows.
			String hint = owner == null ? text("drop_here").getString() : "";
			int width = Math.max(slotWidth, font.width(hint) + 2 * (NON_DATA_TEXT_INSET_X + SLOT_MARGIN));
			Rect hole = new Rect(x + SLOT_MARGIN, y, width - SLOT_PADDING, owner == null ? DATUM_HEIGHT : BRANCH_SLOT_HEIGHT);
			if (stack.isEmpty())
				pieces.add(new Piece(hole, graphics -> ScratchPanelArt.recess(graphics, hole, 0xff302e28, 0xff6e6456)));
			if (!hint.isEmpty())
				label(hint, hole.x + NON_DATA_TEXT_INSET_X, hole, 0xffc6bbaa);
			drops.add(new Drop(hole, hole, end));
			return hole.bottom();
		}

		private void insertGuide(StackPoint location, int x, int y, int width) {
			drops.add(new Drop(new Rect(x - 3, y - 6, width + 6, 14), new Rect(x, y, width, 2), location));
		}

		private int command(Tile tile, StackPoint from, int x, int y, Header header) {
			int index = nextCommandIndex++;
			Outline outline = index == problemCommandIndex ? Outline.PROBLEM : index == activeCommandIndex ? Outline.ACTIVE : Outline.NONE;
			header(tile, from, x, y, header, outline);
			return bodies(tile, from, x, y + header.height, header.width);
		}

		private int bodies(Tile tile, @Nullable StackPoint from, int x, int y, int width) {
			for (int body = 0; body < tile.bodies.size(); body++) {
				int start = y + SLOT_MARGIN;
				int end = SLOT_MARGIN + (from == null ? start + BRANCH_SLOT_HEIGHT
					: stack(tile.bodies.get(body), tile, from.depth() + 1, x + INDENT, start, width - INDENT));
				art(new Rect(x, y, INDENT, end - y + 1), tile.op.category(), Region.RAIL);
				y = end;
				Rect edge = new Rect(x, y, width, BRANCH_EDGE_HEIGHT);
				boolean divider = body + 1 < tile.bodies.size();
				art(edge, tile.op.category(), divider ? Region.MIDDLE : Region.FOOT);
				if (divider)
					label(text("else").getString(), x + NON_DATA_TEXT_INSET_X, edge, BLOCK_INK);
				else if (tile.op == Op.FOREVER || tile.op == Op.REPEAT || tile.op == Op.REPEAT_UNTIL)
					symbol(Symbol.LOOP, edge.right() - 16, centeredY(edge, ScratchPanelArt.SYMBOL_SIZE), 0xffe0b58b);
				y = edge.bottom();
			}
			return y;
		}

		private void header(Tile tile, Location from, int x, int y, Header header, Outline outline) {
			Rect bounds = new Rect(x, y, header.width, header.height);
			if (tile.op.shape() == Shape.COMMAND)
				art(bounds, tile.op.category(), commandRegion(tile.op), outline);
			else
				valueArt(bounds, ScratchValueShape.of(tile), ScratchBlockArt.tint(tile.op.category()), outline);
			if (from != null)
				placed.add(new Placed(bounds, tile, from, false));
			int inputDepth = from == null ? 1 : from.depth() + 1;
			if (tile.op.isCollection()) {
				Op parent = from instanceof InputPoint point ? point.owner().op : null;
				itemList(tile, bounds, header, inputDepth, parent);
				return;
			}
			int left = x + header.inset;
			for (int i = 0; i < header.spans.size(); i++) {
				String span = header.spans.get(i);
				if (!span.isEmpty())
					label(span, left, bounds, BLOCK_INK);
				left += font.width(span);
				if (i < tile.fields.size()) {
					Field field = tile.fields.get(i);
					int width = cellWidth(field);
					Rect cell = new Rect(left, y, width, header.height);
					input(tile, field, cell, inputDepth, ScratchValueShape.forInput(tile.op, i), false);
					left += width;
				}
			}
		}

		private Rect element(Element element, Location from, int x, int y) {
			if (element instanceof Tile tile) {
				Header header = measure(tile);
				header(tile, from, x, y, header, Outline.NONE);
				return new Rect(x, y, header.width, header.height);
			}
			Datum datum = (Datum) element;
			Rect bounds = new Rect(x, y, dataWidth(datum), DATUM_HEIGHT);
			datum(datum, from, bounds, false, Outline.NONE);
			return bounds;
		}

		private void input(Tile tile, Field field, Rect cell, int depth, ScratchValueShape expected, boolean coloredItems) {
			InputPoint point = new InputPoint(tile, field, depth);
			int height = socketHeight(field);
			Rect socket = new Rect(cell.x + SLOT_MARGIN, centeredY(cell, height), cell.width - SLOT_PADDING, height);
			ScratchValueShape actual = field.value == null ? null : ScratchValueShape.of(field.value);
			boolean different = expected != ScratchValueShape.ANY && actual != null && actual != expected;
			Outline outline = different && actual != ScratchValueShape.ANY ? Outline.MISMATCH : Outline.NONE;
			Drop drop = new Drop(socket, socket, point, expected);
			// Register the outer slot first so drops can prefer nested slots.
			drops.add(drop);
			if (actual == null || different || field.value instanceof Tile)
				pieces.add(new Piece(socket, graphics -> ScratchBlockArt.drawValue(graphics, socket, expected, SOCKET_FILL, SOCKET_EDGE)));
			switch (field.value) {
				case Tile child -> header(child, point, socket.x, socket.y, measure(child), outline);
				case Datum value -> datum(value, point, socket, coloredItems, outline);
				case null -> emptySlots.add(drop);
			}
		}

		private void datum(Datum datum, Location from, Rect bounds, boolean coloredItems, Outline outline) {
			valueArt(bounds, ScratchValueShape.of(datum), LITERAL_TINT, outline);
			if (from != null) {
				Placed placement = new Placed(bounds, datum, from, coloredItems);
				placed.add(placement);
				if (datum.type == DataType.ITEM && placement.nameSlot() == null)
					items.add(placement);
			}
			if (datum.type == DataType.ITEM) {
				pieces.add(new Piece(bounds, graphics -> drawItem(graphics, bounds, datum)));
			} else {
				String value = shorten(dataLabel(datum), bounds.width - LITERAL_TEXT_PADDING);
				label(value, bounds.x + DATA_TEXT_INSET_X, bounds, VALUE_INK);
			}
		}

		private void itemList(Tile tile, Rect bounds, Header header, int depth, Op parent) {
			int x = bounds.x + header.inset;
			int controlX = bounds.right() - header.inset - LIST_CONTROL_SIZE;
			boolean map = tile.op == Op.MAP;
			boolean coloredItems = parent != null && (parent.isLegacyFrequencyBinding() || parent.hasFrequencyInput());
			boolean itemList = tile.op == Op.LIST && (coloredItems || parent == Op.STOCK_COUNT || parent == Op.HAS_STOCK);
			boolean itemMap = map && (parent == Op.ORDER_BATCH || parent == Op.DISPLAY_ITEM_LIST);
			ScratchValueShape itemShape = itemList || itemMap ? ScratchValueShape.ITEM : ScratchValueShape.ANY;
			ScratchValueShape quantityShape = itemMap ? ScratchValueShape.NUMBER : ScratchValueShape.ANY;
			Rect title = new Rect(bounds.x, bounds.y, bounds.width, BLOCK_HEIGHT);
			label(header.spans.getFirst(), x, title, BLOCK_INK);
			if (tile.fields.size() < tile.op.maxInputs())
				listControl(tile, -1, depth, controlX, centeredY(title, LIST_CONTROL_SIZE));
			ListColumns columns = listColumns(tile);
			int quantityX = x + columns.item() + (map ? mapSeparatorWidth() : 0);
			int rowSize = tile.op.slots().size();
			int y = title.bottom();
			for (int index = 0; index < tile.fields.size(); index += rowSize) {
				int height = listRowHeight(tile, index, rowSize);
				Rect itemCell = new Rect(x, y, columns.item(), height);
				input(tile, tile.fields.get(index), itemCell, depth, itemShape, coloredItems);
				if (map) {
					label(parent == Op.ORDER_BATCH ? "×" : "→", itemCell.right(), itemCell, BLOCK_INK);
					Rect cell = new Rect(quantityX, y, columns.quantity(), height);
					input(tile, tile.fields.get(index + 1), cell, depth, quantityShape, false);
				}
				listControl(tile, index / rowSize, depth, controlX, centeredY(itemCell, LIST_CONTROL_SIZE));
				y += height;
			}
		}

		private int mapSeparatorWidth() { return Math.max(font.width("×"), font.width("→")); }

		private ListColumns listColumns(Tile tile) {
			boolean map = tile.op == Op.MAP;
			int item = LIST_ITEM_WIDTH + SLOT_PADDING;
			int quantity = map ? LIST_QUANTITY_WIDTH + SLOT_PADDING : 0;
			int rowSize = tile.op.slots().size();
			for (int i = 0; i < tile.fields.size(); i += rowSize) {
				item = Math.max(item, cellWidth(tile.fields.get(i)));
				if (map)
					quantity = Math.max(quantity, cellWidth(tile.fields.get(i + 1)));
			}
			return new ListColumns(item, quantity);
		}

		private int listRowHeight(Tile tile, int index, int rowSize) {
			int height = BLOCK_HEIGHT;
			for (int i = index; i < index + rowSize; i++)
				height = Math.max(height, socketHeight(tile.fields.get(i)) + SLOT_PADDING);
			return height;
		}

		private void listControl(Tile tile, int row, int depth, int x, int y) {
			Rect bounds = new Rect(x, y, LIST_CONTROL_SIZE, LIST_CONTROL_SIZE);
			listControls.add(new ListControl(bounds, tile, row, depth));
			pieces.add(new Piece(bounds, graphics -> ScratchPanelArt.recess(graphics, bounds, 0xff3b3933, 0xff9c9078)));
			String sign = row < 0 ? "+" : "−";
			label(sign, x + (LIST_CONTROL_SIZE - font.width(sign)) / 2, bounds, 0xfff1f5f8);
		}

		private Header measure(Tile tile) {
			Header known = headers.get(tile);
			if (known != null)
				return known;
			int inset = tile.isData() ? DATA_TEXT_INSET_X : NON_DATA_TEXT_INSET_X;
			if (tile.op.isCollection()) {
				String title = text("palette." + tile.op.key()).getString();
				ListColumns columns = listColumns(tile);
				int rowWidth = columns.item() + (tile.op == Op.MAP ? columns.quantity() + mapSeparatorWidth() : 0);
				int width = 2 * inset + Math.max(font.width(title), rowWidth) + SLOT_PADDING + LIST_CONTROL_SIZE;
				int height = BLOCK_HEIGHT;
				int rowSize = tile.op.slots().size();
				for (int index = 0; index < tile.fields.size(); index += rowSize)
					height += listRowHeight(tile, index, rowSize);
				Header listHeader = new Header(width, height, inset, List.of(title));
				headers.put(tile, listHeader);
				return listHeader;
			}
			String pattern = text("block." + tile.op.key()).getString();
			List<String> spans = Arrays.stream(pattern.split("\\|", -1)).map(String::strip).toList();
			if (spans.size() != tile.fields.size() + 1) {
				// Keep all inputs available if a resource pack translates the block without its slot markers.
				spans = new ArrayList<>();
				spans.add(text("palette." + tile.op.key()).getString());
				for (int i = 0; i < tile.fields.size(); i++)
					spans.add("");
			}
			int width = inset * 2;
			int height = BLOCK_HEIGHT;
			for (String span : spans)
				width += font.width(span);
			for (Field field : tile.fields) {
				width += cellWidth(field);
				height = Math.max(height, socketHeight(field) + SLOT_PADDING);
			}
			if (tile.op.bodyCount() > 0) {
				width = Math.max(MIN_STACK_WIDTH + INDENT + SLOT_MARGIN, width);
				for (List<Tile> body : tile.bodies)
					for (Tile child : body)
						width = Math.max(width, INDENT + measure(child).width + SLOT_PADDING);
			}
			Header header = new Header(width, height, inset, spans);
			headers.put(tile, header);
			return header;
		}

		private int socketHeight(Field field) {
			return field.value instanceof Tile tile ? measure(tile).height : DATUM_HEIGHT;
		}

		private int cellWidth(Field field) {
			int width = switch (field.value) {
				case Tile tile -> measure(tile).width;
				case Datum datum -> dataWidth(datum);
				case null -> switch (field.slot.socket()) {
					case LIST, MAP, LIST_REFERENCE, MAP_REFERENCE, LIST_TARGET, MAP_TARGET, VARIABLE_NAME, FREQUENCY -> 58;
					case CONDITION -> 40;
					default -> 26;
				};
			};
			return width + SLOT_PADDING;
		}

		private void art(Rect bounds, Category category, Region region) {
			art(bounds, category, region, Outline.NONE);
		}

		private void art(Rect bounds, Category category, Region region, Outline outline) {
			pieces.add(new Piece(ScratchBlockArt.paintedBounds(bounds, region),
				graphics -> ScratchBlockArt.draw(graphics, bounds, category, region, outline)));
		}

		private void valueArt(Rect bounds, ScratchValueShape shape, int tint, Outline outline) {
			pieces.add(new Piece(bounds, graphics -> ScratchBlockArt.drawValue(graphics, bounds, shape, tint, outline.tint())));
		}

		private void label(String label, int x, Rect bounds, int color) {
			int y = centeredTextY(bounds);
			pieces.add(new Piece(new Rect(x, y, font.width(label), font.lineHeight),
				graphics -> graphics.drawString(font, label, x, y, color, color == BLOCK_INK)));
		}

		private void symbol(Symbol symbol, int x, int y, int color) {
			pieces.add(new Piece(new Rect(x, y, ScratchPanelArt.SYMBOL_SIZE, ScratchPanelArt.SYMBOL_SIZE),
				graphics -> symbol.draw(graphics, x, y, color)));
		}
	}

	record Preview(Layout layout, Rect bounds) {
		void render(GuiGraphics graphics, int x, int y, float scale) {
			graphics.pose().pushPose();
			graphics.pose().translate(x - bounds.x * scale, y - bounds.y * scale, 0);
			graphics.pose().scale(scale, scale, 1);
			layout.render(graphics, null);
			graphics.pose().popPose();
		}
	}

	record Rect(int x, int y, int width, int height) {
		int right() { return x + width; }
		int bottom() { return y + height; }
		Rect inflate(int amount) { return new Rect(x - amount, y - amount, width + 2 * amount, height + 2 * amount); }
		boolean contains(double px, double py) { return px >= x && px < right() && py >= y && py < bottom(); }
		boolean intersects(Rect other) {
			return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
		}
	}

	record Drop(Rect area, Rect guide, Location location, @Nullable ScratchValueShape shape) {
		Drop(Rect area, Rect guide, Location location) { this(area, guide, location, null); }

		@Nullable
		InputPoint nameSlot() {
			return location instanceof InputPoint point && point.isFixedName() ? point : null;
		}

		void highlight(GuiGraphics graphics) {
			if (shape != null)
				ScratchBlockArt.drawValue(graphics, guide, shape, (GUIDE & 0xffffff) | 0x66000000, 0);
			else if (guide.height == 2)
				graphics.fill(guide.x, guide.y, guide.right(), guide.bottom(), GUIDE);
			else
				graphics.renderOutline(guide.x, guide.y, guide.width, guide.height, GUIDE);
		}
	}

	record Placed(Rect bounds, Element element, Location location, boolean includeItemColor) {
		@Nullable
		InputPoint nameSlot() {
			return location instanceof InputPoint point && point.isFixedName() ? point : null;
		}

		Datum item() { return element instanceof Datum datum && datum.type == DataType.ITEM ? datum : null; }
	}
	record ListControl(Rect bounds, Tile tile, int row, int depth) {}
	private record ListColumns(int item, int quantity) {}
	private record Header(int width, int height, int inset, List<String> spans) {}
	private record Piece(Rect bounds, Consumer<GuiGraphics> draw) {}
}
