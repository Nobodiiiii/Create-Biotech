package com.nobodiiiii.createbiotech.content.biocomputing.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Block;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Input;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Literal;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Op;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Shape;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Slot;

final class ScratchDraft {
	private static final int MAX_HISTORY = 24;

	final List<Tile> commands;
	private final Deque<ComputerProgram> undoStack = new ArrayDeque<>();
	private final Deque<ComputerProgram> redoStack = new ArrayDeque<>();
	private ComputerProgram savedBaseline;
	private long revision;
	private long dirtyCheckRevision = -1;
	private boolean cachedDirty;

	ScratchDraft(ComputerProgram program) {
		commands = new ArrayList<>(program.commands().stream().map(Tile::new).toList());
		savedBaseline = program;
	}

	ComputerProgram snapshot() {
		return new ComputerProgram(commands.stream().map(Tile::snapshot).toList());
	}

	int blockCount() {
		return commands.stream().mapToInt(Tile::count).sum();
	}

	long revision() {
		return revision;
	}

	boolean isDirty() {
		if (dirtyCheckRevision != revision) {
			dirtyCheckRevision = revision;
			cachedDirty = !snapshot().equals(savedBaseline);
		}
		return cachedDirty;
	}

	void markSaved(ComputerProgram saved) {
		savedBaseline = saved;
		dirtyCheckRevision = -1;
	}

	void markMutated() {
		revision++;
	}

	void recordUndo() {
		pushUndoSnapshot(snapshot());
	}

	void pushUndoSnapshot(ComputerProgram before) {
		if (!before.equals(undoStack.peek())) {
			undoStack.push(before);
			while (undoStack.size() > MAX_HISTORY)
				undoStack.removeLast();
		}
		redoStack.clear();
	}

	boolean undo() {
		if (undoStack.isEmpty())
			return false;
		redoStack.push(snapshot());
		load(undoStack.pop());
		return true;
	}

	boolean redo() {
		if (redoStack.isEmpty())
			return false;
		undoStack.push(snapshot());
		load(redoStack.pop());
		return true;
	}

	void replaceWith(ComputerProgram program) {
		if (snapshot().equals(program))
			return;
		recordUndo();
		load(program);
	}

	void remove(Location location) {
		recordUndo();
		location.remove();
		revision++;
	}

	void editCollection(Tile list, int row) {
		int rowSize = list.op.slots().size();
		if (!list.op.isCollection() || (row < 0 ? list.fields.size() >= list.op.maxInputs() : row >= list.fields.size() / rowSize))
			return;
		recordUndo();
		if (row < 0) {
			for (int i = 0; i < rowSize; i++)
				list.fields.add(listField(list.op.slot(i), list.itemRows && i == 0, list.itemColorRows));
		} else {
			list.fields.subList(row * rowSize, (row + 1) * rowSize).clear();
		}
		revision++;
	}

	private static Field listField(Slot slot, boolean itemRow, boolean itemColor) {
		Field field = new Field(slot);
		if (itemRow) {
			field.reporter = Tile.create(Op.ITEM_ID, false);
			field.reporter.fields.getFirst().includeItemColor = itemColor;
		}
		return field;
	}

	private void load(ComputerProgram program) {
		commands.clear();
		for (Block block : program.commands())
			commands.add(new Tile(block));
		revision++;
	}

	boolean canDrop(Tile tile, Location from, Location to) {
		if (!to.accepts(tile) || tile.contains(to.owner()))
			return false;
		if (tile.op.isDeclaration() && to.owner() != null)
			return false;
		if (from instanceof ReporterPoint origin && to instanceof ReporterPoint target && origin.field == target.field)
			return false;
		if (to.depth() + tile.depth() - 1 > ComputerProgram.MAX_DEPTH)
			return false;
		if (from == null) {
			int replaced = to instanceof ReporterPoint target && target.field.reporter != null
				? target.field.reporter.count() : 0;
			if (blockCount() - replaced + tile.count() > ComputerProgram.MAX_BLOCKS)
				return false;
		}
		return true;
	}

	void drop(Tile tile, Location from, Location to) {
		if (from instanceof StackPoint origin && to instanceof StackPoint target
			&& origin.stack == target.stack
			&& (origin.index == target.index || origin.index + 1 == target.index))
			return;
		if (from instanceof ReporterPoint origin && to instanceof ReporterPoint target && origin.field == target.field)
			return;

		recordUndo();
		if (from instanceof StackPoint origin && to instanceof StackPoint target
			&& origin.stack == target.stack && origin.index < target.index)
			to = new StackPoint(target.owner, target.stack, target.index - 1, target.depth);
		if (from != null)
			from.remove();
		to.insert(tile);
		revision++;
	}

	static final class Tile {
		final Op op;
		final List<Field> fields;
		final List<List<Tile>> bodies = new ArrayList<>();
		boolean itemRows;
		boolean itemColorRows;

		private Tile(Op op, int inputCount) {
			this.op = op;
			fields = new ArrayList<>(inputCount);
			for (int i = 0; i < inputCount; i++)
				fields.add(new Field(op.slot(i)));
			for (int i = 0; i < op.bodyCount(); i++)
				bodies.add(new ArrayList<>());
		}

		static Tile create(Op op, boolean withItemId) {
			return create(op, withItemId, op.slots().size());
		}

		static Tile create(Op op, boolean withItemId, int inputCount) {
			Tile tile = new Tile(op, inputCount);
			tile.itemRows = op.isCollection() && withItemId;
			for (Field field : tile.fields) {
				Op reporter = field.slot.socket().requiredStructure();
				if (reporter == null && withItemId && field.slot.itemAlternative())
					reporter = Op.ITEM_ID;
				if (reporter != null) {
					int rows = reporter.isCollection() && (op == Op.BIND_FREQUENCY || op == Op.TUNE_FREQUENCY) ? 2 : 1;
					field.reporter = create(reporter, true, reporter.slots().size() * rows);
				field.reporter.itemColorRows = op == Op.BIND_FREQUENCY || op == Op.TUNE_FREQUENCY;
				if (field.reporter.itemColorRows)
					field.reporter.fields.stream().filter(row -> row.reporter != null)
						.forEach(row -> row.reporter.fields.getFirst().includeItemColor = true);
				}
			}
			return tile;
		}

		Tile(Block block) {
			this(block.op(), block.inputs().size());
			itemRows = block.op().isCollection() && !block.inputs().isEmpty()
				&& block.inputs().getFirst() instanceof Block first && first.op() == Op.ITEM_ID;
			for (int i = 0; i < fields.size(); i++) {
				Field field = fields.get(i);
				switch (block.inputs().get(i)) {
					case Literal literal -> field.literal = literal.text();
					case Block reporter -> field.reporter = new Tile(reporter);
				}
				if (field.reporter != null && (block.op() == Op.BIND_FREQUENCY || block.op() == Op.TUNE_FREQUENCY)) {
					field.reporter.itemRows = true;
					field.reporter.itemColorRows = true;
					field.reporter.fields.stream().filter(row -> row.reporter != null)
						.forEach(row -> row.reporter.fields.getFirst().includeItemColor = true);
				}
			}
			for (int i = 0; i < bodies.size(); i++)
				for (Block command : block.bodies().get(i))
					bodies.get(i).add(new Tile(command));
		}

		Block snapshot() {
			return new Block(op, fields.stream().map(Field::snapshot).toList(),
				bodies.stream().map(body -> body.stream().map(Tile::snapshot).toList()).toList());
		}

		boolean contains(Tile tile) {
			if (tile == null)
				return false;
			return this == tile || children().anyMatch(child -> child.contains(tile));
		}

		int depth() { return 1 + children().mapToInt(Tile::depth).max().orElse(0); }
		int count() { return 1 + children().mapToInt(Tile::count).sum(); }

		private Stream<Tile> children() {
			return Stream.concat(fields.stream().map(field -> field.reporter).filter(Objects::nonNull),
				bodies.stream().flatMap(List::stream));
		}
	}

	static final class Field {
		final Slot slot;
		String literal;
		Tile reporter;
		boolean includeItemColor;

		Field(Slot slot) {
			this.slot = slot;
			literal = slot.initial();
		}

		Input snapshot() { return reporter == null ? new Literal(literal) : reporter.snapshot(); }
	}

	sealed interface Location permits StackPoint, ReporterPoint {
		Tile owner();
		int depth();
		boolean accepts(Tile tile);
		void insert(Tile tile);
		void remove();
	}

	record StackPoint(Tile owner, List<Tile> stack, int index, int depth) implements Location {
		@Override public boolean accepts(Tile tile) { return tile.op.shape() == Shape.COMMAND; }
		@Override public void insert(Tile tile) { stack.add(index, tile); }
		@Override public void remove() { stack.remove(index); }
	}

	record ReporterPoint(Tile owner, Field field, int depth) implements Location {
		@Override public boolean accepts(Tile tile) { return field.slot.socket().accepts(tile.op); }
		@Override public void insert(Tile tile) { field.reporter = tile; }

		@Override
		public void remove() {
			Tile reporter = field.reporter;
			if (reporter != null && reporter.op == Op.ITEM_ID && field.slot.itemAlternative()) {
				String text = reporter.fields.getFirst().literal;
				if (text.length() <= field.slot.maxLiteralLength())
					field.literal = text;
			}
			field.reporter = null;
		}
	}
}
