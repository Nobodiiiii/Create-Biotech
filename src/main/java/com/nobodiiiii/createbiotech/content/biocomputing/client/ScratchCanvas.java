package com.nobodiiiii.createbiotech.content.biocomputing.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerItemReference;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Category;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.ItemInput;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Op;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Shape;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Socket;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchBlockArt.Region;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.Field;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.Location;
import com.nobodiiiii.createbiotech.content.biocomputing.client.ScratchDraft.ReporterPoint;
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
	private static final int LIST_ROW_HEIGHT = 20;
	private static final int LIST_ITEM_WIDTH = 68;
	private static final int LIST_QUANTITY_WIDTH = 32;
	private static final int LIST_QUANTITY_GAP = 12;
	private static final int LIST_CONTROL_SIZE = 14;
	private static final int INK = 0xff151b20;
	private static final int GUIDE = 0xffd2e9ff;
	private static final int ACTIVE_OUTLINE = 0xffffe866;
	private static final int ACTIVE_GLOW = 0x33ffe866;
	private final Font font;

	ScratchCanvas(Font font) {
		this.font = font;
	}

	Layout layout(ScratchDraft draft) {
		return layout(draft, -1);
	}

	Layout layout(ScratchDraft draft, int activeCommandIndex) {
		Layout layout = new Layout(activeCommandIndex);
		boolean hasDeclarations = draft.commands.stream().anyMatch(tile -> tile.op.isDeclaration());
		String label = text(hasDeclarations ? "program_root" : "startup").getString();
		Rect hat = new Rect(14, 14, Math.max(102, font.width(label) + 20), 26);
		layout.art(hat, Category.EVENTS, Region.HAT);
		layout.label(label, hat.x + 10, hat.y + 4, hat.height, INK);
		layout.stack(draft.commands, null, 1, hat.x, hat.bottom() + GAP);
		return layout;
	}

	Preview preview(Tile tile) {
		Layout layout = new Layout(-1);
		Header header = layout.measure(tile);
		layout.header(tile, null, 0, 0, header, false);
		Rect bounds = ScratchBlockArt.paintedBounds(new Rect(0, 0, header.width, header.height), region(tile.op));
		return new Preview(layout, bounds);
	}

	private static Region region(Op op) {
		return switch (op.shape()) {
			case COMMAND -> op.bodyCount() > 0 ? Region.MOUTH : op == Op.STOP ? Region.CAP : Region.STACK;
			case REPORTER -> Region.REPORTER;
			case BOOLEAN -> Region.BOOLEAN;
		};
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

	private String literalLabel(Field field) {
		return switch (field.slot.socket()) {
			case CONDITION -> text("boolean." + field.literal).getString();
			case CHANNEL, SLOT -> text("slot", field.literal).getString();
			case VALUE, NAME, LITERAL -> field.literal.isEmpty() ? "…" : field.literal;
			case LIST, MAP ->
				throw new IllegalStateException("Structured input requires a child block");
		};
	}

	private void drawItemLiteral(GuiGraphics graphics, Rect bounds, Field field) {
		ItemStack stack = ComputerItemReference.read(field.literal.strip(), field.includeItemColor);
		String label = literalLabel(field);
		int labelX;
		if (stack.isEmpty()) {
			label = shorten(label, bounds.width - 10);
			labelX = bounds.x + (bounds.width - font.width(label)) / 2;
		} else {
			graphics.pose().pushPose();
			graphics.pose().translate(bounds.x + 4, bounds.y + 2, 0);
			graphics.pose().scale(0.75f, 0.75f, 1);
			graphics.renderItem(stack, 0, 0);
			graphics.pose().popPose();
			label = shorten(label.substring(label.indexOf(':') + 1), bounds.width - 23);
			labelX = bounds.x + 18;
		}
		graphics.drawString(font, label, labelX, bounds.y + (bounds.height - font.lineHeight) / 2, INK, false);
	}

	final class Layout {
		private final int activeCommandIndex;
		private final Map<Tile, Header> headers = new IdentityHashMap<>();
		private final List<Piece> pieces = new ArrayList<>();
		private final List<Placed> placed = new ArrayList<>();
		private final List<Drop> drops = new ArrayList<>();
		private final List<LiteralSpot> literals = new ArrayList<>();
		private final List<ListControl> listControls = new ArrayList<>();
		private int nextCommandIndex;

		private Layout(int activeCommandIndex) {
			this.activeCommandIndex = activeCommandIndex;
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

		LiteralSpot literalAt(double x, double y) {
			for (LiteralSpot literal : literals)
				if (literal.bounds.contains(x, y))
					return literal;
			return null;
		}

		List<LiteralSpot> literals() { return literals; }

		ListControl listControlAt(double x, double y) {
			for (ListControl control : listControls)
				if (control.bounds.contains(x, y))
					return control;
			return null;
		}

		Drop dropAt(ScratchDraft draft, Tile tile, Location from, double x, double y) {
			for (Drop drop : drops.reversed()) {
				if (!drop.area.contains(x, y))
					continue;
				// Dropping back onto the original slot must not replace an enclosing reporter.
				if (from instanceof ReporterPoint && drop.location.equals(from))
					return null;
				if (draft.canDrop(tile, from, drop.location))
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
			boolean active = nextCommandIndex++ == activeCommandIndex;
			header(tile, from, x, y, header, active);
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

		private void header(Tile tile, Location from, int x, int y, Header header, boolean active) {
			Rect bounds = new Rect(x, y, header.width, header.height);
			art(bounds, tile.op.category(), region(tile.op));
			if (active) {
				Rect glow = new Rect(x - 2, y - 2, header.width + 4, header.height + 4);
				pieces.add(new Piece(glow, graphics -> {
					graphics.fill(bounds.x, bounds.y, bounds.right(), bounds.bottom(), ACTIVE_GLOW);
					graphics.renderOutline(glow.x, glow.y, glow.width, glow.height, ACTIVE_OUTLINE);
				}));
			}
			if (from != null && !(from instanceof ReporterPoint point && point.field().slot.socket().requiredStructure() != null))
				placed.add(new Placed(bounds, tile, from));
			int inputDepth = from == null ? 1 : from.depth() + 1;
			if (tile.op.isCollection()) {
				itemList(tile, bounds, header, inputDepth);
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
					input(tile, field, left, y, width, header.height, inputDepth);
					left += width + 4;
				}
			}
		}

		private void input(Tile tile, Field field, int x, int y, int width, int height, int depth) {
			ReporterPoint point = new ReporterPoint(tile, field, depth);
			if (field.reporter != null) {
				Header child = measure(field.reporter);
				int childY = y + (height - child.height) / 2;
				Rect childBounds = new Rect(x, childY, child.width, child.height);
				// Register replacement target before recursing so nested slots keep hit priority.
				drops.add(new Drop(childBounds, childBounds, point));
				header(field.reporter, point, x, childY, child, false);
			} else {
				Rect slot = literal(tile.op.category(), field, x, y, width, height);
				if (field.slot.socket().acceptsReporter())
					drops.add(new Drop(slot, slot, point));
			}
		}

		private Rect literal(Category category, Field field, int x, int y, int width, int height) {
			Rect slot = new Rect(x, y + (height - 15) / 2, width, 15);
			boolean bool = field.slot.socket() == Socket.CONDITION;
			art(slot, category, bool ? Region.BOOLEAN : Region.LITERAL);
			if (field.slot.itemInput() != ItemInput.NONE) {
				pieces.add(new Piece(slot, graphics -> drawItemLiteral(graphics, slot, field)));
			} else {
				String value = shorten(literalLabel(field), width - (bool ? 20 : 10));
				label(value, slot.x + (width - font.width(value)) / 2, slot.y, slot.height, INK);
			}
			literals.add(new LiteralSpot(slot, field));
			return slot;
		}

		private void itemList(Tile tile, Rect bounds, Header header, int depth) {
			int x = bounds.x + header.inset;
			int controlX = bounds.right() - header.inset - LIST_CONTROL_SIZE;
			label(header.spans.getFirst(), x, bounds.y, HEADER_HEIGHT, INK);
			if (tile.fields.size() < tile.op.maxInputs())
				listControl(tile, -1, controlX, bounds.y + (HEADER_HEIGHT - LIST_CONTROL_SIZE) / 2);
			ListColumns columns = listColumns(tile);
			int rowSize = tile.op.slots().size();
			int y = bounds.y + HEADER_HEIGHT;
			for (int index = 0; index < tile.fields.size(); index += rowSize) {
				int rowHeight = listRowHeight(tile, index, rowSize);
				input(tile, tile.fields.get(index), x, y, columns.item(), rowHeight, depth);
				if (tile.op == Op.MAP) {
					label("×", x + columns.item() + 3, y, rowHeight, INK);
					input(tile, tile.fields.get(index + 1), x + columns.item() + LIST_QUANTITY_GAP,
						y, columns.quantity(), rowHeight, depth);
				}
				listControl(tile, index / rowSize, controlX, y + (rowHeight - LIST_CONTROL_SIZE) / 2);
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

		private void listControl(Tile tile, int row, int x, int y) {
			Rect bounds = new Rect(x, y, LIST_CONTROL_SIZE, LIST_CONTROL_SIZE);
			listControls.add(new ListControl(bounds, tile, row));
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
			return field.reporter == null ? 0 : measure(field.reporter).height + 6;
		}

		private int fieldWidth(Field field) {
			if (field.reporter != null)
				return measure(field.reporter).width;
			if (field.slot.socket() == Socket.CONDITION)
				return Math.clamp(font.width(literalLabel(field)) + 22, 40, 76);
			if (field.slot.itemInput() != ItemInput.NONE)
				return Math.clamp(font.width(literalLabel(field)) + 12, 42, 76);
			return Math.clamp(font.width(literalLabel(field)) + 12, 26, 76);
		}

		private void art(Rect bounds, Category category, Region region) {
			pieces.add(new Piece(ScratchBlockArt.paintedBounds(bounds, region),
				graphics -> ScratchBlockArt.draw(graphics, bounds, category, region)));
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
		boolean contains(double px, double py) { return px >= x && px < right() && py >= y && py < bottom(); }
		boolean intersects(Rect other) {
			return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
		}
	}

	record Drop(Rect area, Rect guide, Location location) {
		void highlight(GuiGraphics graphics) {
			if (guide.height == 2)
				graphics.fill(guide.x, guide.y, guide.right(), guide.bottom(), GUIDE);
			else
				graphics.renderOutline(guide.x, guide.y, guide.width, guide.height, GUIDE);
		}
	}

	record Placed(Rect bounds, Tile tile, Location location) {}
	record LiteralSpot(Rect bounds, Field field) {}
	record ListControl(Rect bounds, Tile tile, int row) {}
	private record ListColumns(int item, int quantity) {}
	private record Header(int width, int height, int inset, List<String> spans) {}
	private record Piece(Rect bounds, Consumer<GuiGraphics> draw) {}
}
