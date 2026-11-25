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

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

final class ScratchCanvas {
	private static final int GAP = 5;
	private static final int INDENT = 18;
	private static final int HEADER_HEIGHT = 22;
	private static final int DATA_HEIGHT = 15;
	private static final int LIST_ROW_HEIGHT = 20;
	private static final int LIST_ITEM_WIDTH = 68;
	private static final int LIST_QUANTITY_WIDTH = 32;
	private static final int LIST_QUANTITY_GAP = 12;
	private static final int LIST_CONTROL_SIZE = 14;
	private static final int INK = 0xff151b20;
	private static final int GUIDE = 0xffd2e9ff;
	private static final int SOCKET_FILL = 0xff25303b;
	private static final int SOCKET_EDGE = 0xff526977;
	private static final int MISMATCH_EDGE = 0xffffc084;
	private static final int ACTIVE_OUTLINE = 0xffffe866;
	private static final int PROBLEM_OUTLINE = 0xffff8983;
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
		Rect hat = new Rect(14, 14, Math.max(102, font.width(label) + 20), 26);
		layout.art(hat, Category.EVENTS, Region.HAT);
		layout.label(label, hat.x + 10, hat.y + 4, hat.height, INK);
		layout.stack(draft.commands, null, 1, hat.x, hat.bottom() + GAP);
		for (LooseData data : draft.looseData)
			layout.element(data.element(), new LoosePoint(draft.looseData, data), data.x(), data.y());
		return layout;
	}

	Preview preview(Element element) {
		Layout layout = new Layout(-1, -1);
		Rect bounds = layout.element(element, null, 0, 0);
		if (element instanceof Tile tile && tile.op.shape() == Shape.COMMAND)
			bounds = ScratchBlockArt.paintedBounds(bounds, commandRegion(tile.op));
		return new Preview(layout, bounds);
	}

	private static Region commandRegion(Op op) {
		return op.bodyCount() > 0 ? Region.MOUTH : op == Op.STOP ? Region.CAP : Region.STACK;
	}

	String shorten(String label, int maxWidth) {
		if (font.width(label) <= maxWidth)
			return label;
		return font.plainSubstrByWidth(label, Math.max(0, maxWidth - font.width("…"))) + "…";
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
		int label = font.width(dataLabel(datum));
		return switch (datum.type) {
			case BOOLEAN -> Math.clamp(label + 22, 40, 76);
			case ITEM -> Math.clamp(label + 24, 48, 100);
			default -> Math.clamp(label + 14, 26, 82);
		};
	}

	private void drawItem(GuiGraphics graphics, Rect bounds, Datum datum) {
		ItemStack stack = ComputerItemReference.read(datum.text, true);
		String label;
		int labelX;
		if (stack.isEmpty()) {
			label = shorten(datum.text.isEmpty() ? text("data.item").getString() : datum.text, bounds.width - 10);
			labelX = bounds.x + (bounds.width - font.width(label)) / 2;
		} else {
			graphics.pose().pushPose();
			graphics.pose().translate(bounds.x + 4, bounds.y + 2, 0);
			graphics.pose().scale(0.75f, 0.75f, 1);
			graphics.renderItem(stack, 0, 0);
			graphics.pose().popPose();
			label = shorten(stack.getHoverName().getString(), bounds.width - 23);
			labelX = bounds.x + 18;
		}
		graphics.drawString(font, label, labelX, bounds.y + (bounds.height - font.lineHeight) / 2, INK, false);
	}

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

		private int stack(List<Tile> stack, Tile owner, int depth, int x, int y) {
			if (stack.isEmpty()) {
				String hint = owner == null ? text("drop_here").getString() : "";
				Rect hole = new Rect(x, y, Math.max(98, font.width(hint) + 14), 18);
				pieces.add(new Piece(hole, graphics -> {
					graphics.fill(hole.x, hole.y, hole.right(), hole.bottom(), 0xff303c48);
					graphics.renderOutline(hole.x, hole.y, hole.width, hole.height, 0xff4a5e70);
				}));
				if (!hint.isEmpty())
					label(hint, hole.x + 7, hole.y, hole.height, 0xffb6c5d4);
				drops.add(new Drop(hole, hole, new StackPoint(owner, stack, 0, depth)));
				return hole.bottom();
			}
			int lastWidth = 98;
			for (int i = 0; i < stack.size(); i++) {
				Tile tile = stack.get(i);
				Header header = measure(tile);
				StackPoint location = new StackPoint(owner, stack, i, depth);
				insertGuide(location, x, y - 2, Math.max(lastWidth, header.width));
				y = command(tile, location, x, y, header) + GAP;
				lastWidth = Math.max(98, header.width);
			}
			insertGuide(new StackPoint(owner, stack, stack.size(), depth), x, y - 2, lastWidth);
			return y;
		}

		private void insertGuide(StackPoint location, int x, int y, int width) {
			drops.add(new Drop(new Rect(x - 3, y - 6, width + 6, 14), new Rect(x, y, width, 2), location));
		}

		private int command(Tile tile, StackPoint from, int x, int y, Header header) {
			int index = nextCommandIndex++;
			int outline = index == problemCommandIndex ? PROBLEM_OUTLINE : index == activeCommandIndex ? ACTIVE_OUTLINE : 0;
			header(tile, from, x, y, header, outline);
			y += header.height;
			for (int body = 0; body < tile.bodies.size(); body++) {
				int end = stack(tile.bodies.get(body), tile, from.depth() + 1, x + INDENT, y + GAP);
				art(new Rect(x, y, INDENT, end - y + 2), tile.op.category(), Region.RAIL);
				y = end + 2;
				if (body + 1 < tile.bodies.size()) {
					Rect divider = new Rect(x, y, header.width, 19);
					art(divider, tile.op.category(), Region.MIDDLE);
					label(text("else").getString(), x + 9, divider.y, divider.height, INK);
					y = divider.bottom();
				} else {
					Rect foot = new Rect(x, y, header.width, 12);
					art(foot, tile.op.category(), Region.FOOT);
					y = foot.bottom();
				}
			}
			return y;
		}

		private void header(Tile tile, Location from, int x, int y, Header header, int outline) {
			Rect bounds = new Rect(x, y, header.width, header.height);
			if (tile.op.shape() == Shape.COMMAND)
				art(bounds, tile.op.category(), commandRegion(tile.op));
			else
				valueArt(bounds, tile.op.category(), ScratchValueShape.of(tile));
			if (outline != 0) {
				Rect glow = new Rect(x - 2, y - 2, header.width + 4, header.height + 4);
				pieces.add(new Piece(glow, graphics -> {
					graphics.fill(bounds.x, bounds.y, bounds.right(), bounds.bottom(), (outline & 0xffffff) | 0x33000000);
					graphics.renderOutline(glow.x, glow.y, glow.width, glow.height, outline);
				}));
			}
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
				if (!span.isEmpty()) {
					label(span, left, y, header.height, INK);
					left += font.width(span) + 4;
				}
				if (i < tile.fields.size()) {
					Field field = tile.fields.get(i);
					int width = fieldWidth(field);
					Rect cell = new Rect(left, y, width, header.height);
					input(tile, field, cell, inputDepth, ScratchValueShape.forInput(tile.op, i), false);
					left += width + 4;
				}
			}
		}

		private Rect element(Element element, Location from, int x, int y) {
			if (element instanceof Tile tile) {
				Header header = measure(tile);
				header(tile, from, x, y, header, 0);
				return new Rect(x, y, header.width, header.height);
			}
			Datum datum = (Datum) element;
			Rect bounds = new Rect(x, y, dataWidth(datum), DATA_HEIGHT);
			datum(datum, from, bounds, false);
			return bounds;
		}

		private void input(Tile tile, Field field, Rect cell, int depth, ScratchValueShape expected, boolean coloredItems) {
			InputPoint point = new InputPoint(tile, field, depth);
			Header childHeader = field.value instanceof Tile child ? measure(child) : null;
			int inputHeight = childHeader == null ? DATA_HEIGHT : childHeader.height;
			int inputWidth = childHeader == null ? cell.width : childHeader.width;
			Rect slot = new Rect(cell.x, cell.y + (cell.height - inputHeight) / 2, inputWidth, inputHeight);
			ScratchValueShape actual = field.value == null ? null : ScratchValueShape.of(field.value);
			boolean different = expected != ScratchValueShape.ANY && actual != null && actual != expected;
			Rect socket = different ? slot.inflate(2) : slot;
			Drop drop = new Drop(socket, socket, point, expected);
			// Register the outer slot first so drops can prefer nested slots.
			drops.add(drop);
			if (actual == null || different) {
				int edge = different && actual != ScratchValueShape.ANY ? MISMATCH_EDGE : SOCKET_EDGE;
				pieces.add(new Piece(socket, graphics -> ScratchBlockArt.drawValue(graphics, socket, expected, SOCKET_FILL, edge)));
			}
			switch (field.value) {
				case Tile child -> header(child, point, slot.x, slot.y, childHeader, 0);
				case Datum value -> datum(value, point, slot, coloredItems);
				case null -> emptySlots.add(drop);
			}
		}

		private void datum(Datum datum, Location from, Rect bounds, boolean coloredItems) {
			valueArt(bounds, Category.DATA, ScratchValueShape.of(datum));
			if (from != null) {
				Placed placement = new Placed(bounds, datum, from, coloredItems);
				placed.add(placement);
				if (datum.type == DataType.ITEM && placement.nameSlot() == null)
					items.add(placement);
			}
			if (datum.type == DataType.ITEM) {
				pieces.add(new Piece(bounds, graphics -> drawItem(graphics, bounds, datum)));
			} else {
				String value = shorten(dataLabel(datum), bounds.width - (datum.type == DataType.BOOLEAN ? 20 : 10));
				label(value, bounds.x + (bounds.width - font.width(value)) / 2, bounds.y, bounds.height, INK);
			}
		}

		private void itemList(Tile tile, Rect bounds, Header header, int depth, Op parent) {
			int x = bounds.x + header.inset;
			int controlX = bounds.right() - header.inset - LIST_CONTROL_SIZE;
			boolean coloredItems = parent != null && (parent.isLegacyFrequencyBinding() || parent.hasFrequencyInput());
			boolean itemList = tile.op == Op.LIST && (coloredItems || parent == Op.STOCK_COUNT || parent == Op.HAS_STOCK);
			boolean itemMap = tile.op == Op.MAP && (parent == Op.ORDER_BATCH || parent == Op.DISPLAY_ITEM_LIST);
			ScratchValueShape itemShape = itemList || itemMap ? ScratchValueShape.ITEM : ScratchValueShape.ANY;
			ScratchValueShape quantityShape = itemMap ? ScratchValueShape.NUMBER : ScratchValueShape.ANY;
			label(header.spans.getFirst(), x, bounds.y, HEADER_HEIGHT, INK);
			if (tile.fields.size() < tile.op.maxInputs())
				listControl(tile, -1, depth, controlX, bounds.y + (HEADER_HEIGHT - LIST_CONTROL_SIZE) / 2);
			ListColumns columns = listColumns(tile);
			int rowSize = tile.op.slots().size();
			int y = bounds.y + HEADER_HEIGHT;
			for (int index = 0; index < tile.fields.size(); index += rowSize) {
				int rowHeight = listRowHeight(tile, index, rowSize);
				input(tile, tile.fields.get(index), new Rect(x, y, columns.item(), rowHeight), depth, itemShape, coloredItems);
				if (tile.op == Op.MAP) {
					label(parent == Op.ORDER_BATCH ? "×" : "→", x + columns.item() + 3, y, rowHeight, INK);
					Rect cell = new Rect(x + columns.item() + LIST_QUANTITY_GAP, y, columns.quantity(), rowHeight);
					input(tile, tile.fields.get(index + 1), cell, depth, quantityShape, false);
				}
				listControl(tile, index / rowSize, depth, controlX, y + (rowHeight - LIST_CONTROL_SIZE) / 2);
				y += rowHeight;
			}
		}

		private ListColumns listColumns(Tile tile) {
			int item = LIST_ITEM_WIDTH;
			int quantity = tile.op == Op.MAP ? LIST_QUANTITY_WIDTH : 0;
			int rowSize = tile.op.slots().size();
			for (int i = 0; i < tile.fields.size(); i += rowSize) {
				item = Math.max(item, fieldWidth(tile.fields.get(i)));
				if (tile.op == Op.MAP)
					quantity = Math.max(quantity, fieldWidth(tile.fields.get(i + 1)));
			}
			return new ListColumns(item, quantity);
		}

		private int listRowHeight(Tile tile, int index, int rowSize) {
			int height = LIST_ROW_HEIGHT;
			for (int i = index; i < index + rowSize; i++)
				height = Math.max(height, nestedHeight(tile.fields.get(i)));
			return height;
		}

		private void listControl(Tile tile, int row, int depth, int x, int y) {
			Rect bounds = new Rect(x, y, LIST_CONTROL_SIZE, LIST_CONTROL_SIZE);
			listControls.add(new ListControl(bounds, tile, row, depth));
			pieces.add(new Piece(bounds, graphics -> graphics.fill(x, y, bounds.right(), bounds.bottom(), 0xff314350)));
			String sign = row < 0 ? "+" : "−";
			label(sign, x + (LIST_CONTROL_SIZE - font.width(sign)) / 2, y, LIST_CONTROL_SIZE, 0xfff1f5f8);
		}

		private Header measure(Tile tile) {
			Header known = headers.get(tile);
			if (known != null)
				return known;
			if (tile.op.isCollection()) {
				String title = text("palette." + tile.op.key()).getString();
				ListColumns columns = listColumns(tile);
				int rowWidth = columns.item() + (tile.op == Op.MAP ? columns.quantity() + LIST_QUANTITY_GAP : 0);
				int width = 24 + Math.max(font.width(title), rowWidth) + GAP + LIST_CONTROL_SIZE;
				int height = HEADER_HEIGHT + (tile.fields.isEmpty() ? 0 : 3);
				int rowSize = tile.op.slots().size();
				for (int index = 0; index < tile.fields.size(); index += rowSize)
					height += listRowHeight(tile, index, rowSize);
				Header listHeader = new Header(width, height, 12, List.of(title));
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
			int inset = tile.op.shape() == Shape.BOOLEAN ? 15 : tile.op.shape() == Shape.REPORTER ? 12 : 9;
			int width = inset * 2;
			int height = HEADER_HEIGHT;
			for (String span : spans)
				if (!span.isEmpty())
					width += font.width(span) + 4;
			for (Field field : tile.fields) {
				width += fieldWidth(field) + 4;
				height = Math.max(height, nestedHeight(field));
			}
			if (tile.op.bodyCount() > 0)
				width = Math.max(118, width);
			Header header = new Header(width, height, inset, spans);
			headers.put(tile, header);
			return header;
		}

		private int nestedHeight(Field field) {
			return field.value instanceof Tile tile ? measure(tile).height + 6 : 0;
		}

		private int fieldWidth(Field field) {
			return switch (field.value) {
				case Tile tile -> measure(tile).width;
				case Datum datum -> dataWidth(datum);
				case null -> switch (field.slot.socket()) {
					case LIST, MAP, LIST_REFERENCE, MAP_REFERENCE, LIST_TARGET, MAP_TARGET, VARIABLE_NAME, FREQUENCY -> 58;
					case CONDITION -> 40;
					default -> 26;
				};
			};
		}

		private void art(Rect bounds, Category category, Region region) {
			pieces.add(new Piece(ScratchBlockArt.paintedBounds(bounds, region),
				graphics -> ScratchBlockArt.draw(graphics, bounds, category, region)));
		}

		private void valueArt(Rect bounds, Category category, ScratchValueShape shape) {
			pieces.add(new Piece(bounds, graphics -> ScratchBlockArt.drawValue(graphics, bounds, category, shape)));
		}

		private void label(String label, int x, int y, int height, int color) {
			int baseline = y + (height - font.lineHeight) / 2;
			pieces.add(new Piece(new Rect(x, baseline, font.width(label), font.lineHeight),
				graphics -> graphics.drawString(font, label, x, baseline, color, false)));
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
