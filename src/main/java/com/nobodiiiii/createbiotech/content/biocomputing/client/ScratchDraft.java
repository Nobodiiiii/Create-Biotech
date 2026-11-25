package com.nobodiiiii.createbiotech.content.biocomputing.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Block;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Category;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.DataType;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.EmptyInput;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Input;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Literal;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Op;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Shape;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Slot;

final class ScratchDraft {
	private static final int MAX_HISTORY = 24;

	final List<Tile> commands;
	final List<LooseData> looseData = new ArrayList<>();
	private final Deque<History> undoStack = new ArrayDeque<>();
	private final Deque<History> redoStack = new ArrayDeque<>();
	private ComputerProgram savedBaseline;
	private long revision;
	private long dirtyCheckRevision = -1;
	private boolean cachedDirty;
	private long snapshotRevision = -1;
	private ComputerProgram cachedSnapshot;

	ScratchDraft(ComputerProgram program) {
		commands = new ArrayList<>(program.commands().stream().map(Tile::new).toList());
		savedBaseline = program;
	}

	ComputerProgram snapshot() {
		if (snapshotRevision != revision) {
			cachedSnapshot = new ComputerProgram(commands.stream().map(Tile::snapshot).toList());
			snapshotRevision = revision;
		}
		return cachedSnapshot;
	}

	int blockCount() {
		return commands.stream().mapToInt(Tile::count).sum() + looseData.stream().mapToInt(data -> data.element.count()).sum();
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

	private void recordUndo() {
		History before = history();
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
		redoStack.push(history());
		load(undoStack.pop());
		return true;
	}

	boolean redo() {
		if (redoStack.isEmpty())
			return false;
		undoStack.push(history());
		load(redoStack.pop());
		return true;
	}

	void replaceWith(ComputerProgram program) {
		if (snapshot().equals(program))
			return;
		recordUndo();
		load(new History(program, List.of()));
	}

	void remove(Location location) {
		recordUndo();
		location.remove();
		revision++;
	}

	void setData(Datum datum, String value) {
		DataType type = datum.type.isValid(value) ? datum.type : DataType.TEXT;
		if (datum.type != type || !datum.text.equals(value)) {
			recordUndo();
			datum.type = type;
			datum.text = value;
			revision++;
		}
	}

	void editCollection(Tile list, int row, int depth) {
		int rowSize = list.op.slots().size();
		if (row < 0) {
			List<Field> fields = list.op.slots().stream().map(Field::withDefault).toList();
			if (list.fields.size() >= list.op.maxInputs()
				|| blockCount() + fields.stream().mapToInt(field -> field.value.count()).sum() > ComputerProgram.MAX_BLOCKS
				|| fields.stream().anyMatch(field -> depth + field.value.depth() - 1 > ComputerProgram.MAX_DEPTH))
				return;
			recordUndo();
			list.fields.addAll(fields);
		} else {
			recordUndo();
			list.fields.subList(row * rowSize, (row + 1) * rowSize).clear();
		}
		revision++;
	}

	private History history() {
		return new History(snapshot(), looseData.stream()
			.map(data -> new LooseSnapshot(data.element.snapshot(), data.x, data.y)).toList());
	}

	private void load(History history) {
		commands.clear();
		for (Block block : history.program.commands())
			commands.add(new Tile(block));
		looseData.clear();
		for (LooseSnapshot data : history.looseData)
			looseData.add(new LooseData(element(data.input), data.x, data.y));
		revision++;
	}

	boolean canDrop(Element element, Location from, Location to) {
		if (!to.accepts(element) || element.contains(to.owner()) || to.equals(from))
			return false;
		Element replaced = to instanceof InputPoint target ? target.field.value : null;
		if (from != null && replaced != null && replaced.contains(from.owner()))
			return false;
		if (element instanceof Tile tile && tile.op.isDeclaration() && to.owner() != null)
			return false;
		if (to instanceof LoosePoint && !(from instanceof LoosePoint) && looseData.size() >= ComputerProgram.MAX_BLOCKS)
			return false;
		if (to.depth() + element.depth() - 1 > ComputerProgram.MAX_DEPTH)
			return false;
		if (from != null)
			return true;
		int replacedCount = replaced == null ? 0 : replaced.count();
		return blockCount() - replacedCount + element.count() <= ComputerProgram.MAX_BLOCKS;
	}

	void placeLoose(Element element, Location from, int x, int y) {
		Location to = new LoosePoint(looseData, new LooseData(element, x, y));
		if (canDrop(element, from, to))
			drop(element, from, to);
	}

	void drop(Element element, Location from, Location to) {
		if (from instanceof StackPoint origin && to instanceof StackPoint target
			&& origin.stack == target.stack
			&& (origin.index == target.index || origin.index + 1 == target.index))
			return;
		recordUndo();
		if (from instanceof StackPoint origin && to instanceof StackPoint target
			&& origin.stack == target.stack && origin.index < target.index)
			to = new StackPoint(target.owner, target.stack, target.index - 1, target.depth);
		if (from != null)
			from.remove();
		to.insert(element);
		revision++;
	}

	static Element element(Input input) {
		return switch (input) {
			case EmptyInput ignored -> null;
			case Literal literal -> new Datum(literal.type(), literal.text());
			case Block block -> new Tile(block);
		};
	}

	sealed interface Element permits Tile, Datum {
		Input snapshot();
		int count();
		int depth();
		boolean isData();
		default boolean contains(Tile tile) { return false; }
	}

	static final class Datum implements Element {
		DataType type;
		String text;

		Datum(DataType type, String text) {
			this.type = type;
			this.text = text;
		}

		@Override
		public Input snapshot() {
			return new Literal(text, type);
		}

		@Override public int count() { return type == DataType.ITEM ? 1 : 0; }
		@Override public int depth() { return count(); }
		@Override public boolean isData() { return true; }
	}

	static final class Tile implements Element {
		final Op op;
		final List<Field> fields;
		final List<List<Tile>> bodies = new ArrayList<>();

		private Tile(Op op, int inputCount) {
			this.op = op;
			fields = new ArrayList<>(inputCount);
			for (int i = 0; i < op.bodyCount(); i++)
				bodies.add(new ArrayList<>());
		}

		static Tile create(Op op) {
			return create(op, op.slots().size());
		}

		private static Tile create(Op op, int inputCount) {
			Tile tile = new Tile(op, inputCount);
			for (int i = 0; i < inputCount; i++) {
				Slot slot = op.slot(i);
				Op structure = slot.socket().defaultStructure();
				if (structure == null) {
					tile.fields.add(Field.withDefault(slot));
				} else {
					int rows = op.isLegacyFrequencyBinding() ? 2 : 1;
					tile.fields.add(new Field(slot, create(structure, structure.slots().size() * rows)));
				}
			}
			return tile;
		}

		Tile(Block block) {
			this(block.op(), block.inputs().size());
			for (int i = 0; i < block.inputs().size(); i++)
				fields.add(new Field(op.slot(i), element(block.inputs().get(i))));
			for (int i = 0; i < bodies.size(); i++)
				for (Block command : block.bodies().get(i))
					bodies.get(i).add(new Tile(command));
		}

		@Override
		public Block snapshot() {
			return new Block(op, fields.stream().map(Field::snapshot).toList(),
				bodies.stream().map(body -> body.stream().map(Tile::snapshot).toList()).toList());
		}

		@Override
		public boolean contains(Tile tile) {
			if (tile == null)
				return false;
			return this == tile || children().anyMatch(child -> child.contains(tile));
		}

		@Override public int depth() { return 1 + children().mapToInt(Element::depth).max().orElse(0); }
		@Override public int count() { return 1 + children().mapToInt(Element::count).sum(); }
		@Override public boolean isData() { return op.category() == Category.DATA || op == Op.VARIABLE; }

		private Stream<Element> children() {
			return Stream.concat(fields.stream().map(field -> field.value).filter(Objects::nonNull),
				bodies.stream().flatMap(List::stream));
		}
	}

	static final class Field {
		final Slot slot;
		Element value;

		Field(Slot slot, Element value) {
			this.slot = slot;
			this.value = value;
		}

		static Field withDefault(Slot slot) {
			return new Field(slot, slot.isFixedName() ? null : new Datum(slot.dataType(), slot.initial()));
		}

		Input snapshot() { return value == null ? EmptyInput.INSTANCE : value.snapshot(); }
	}

	sealed interface Location permits StackPoint, InputPoint, LoosePoint {
		Tile owner();
		int depth();
		boolean accepts(Element element);
		void insert(Element element);
		void remove();
	}

	record StackPoint(Tile owner, List<Tile> stack, int index, int depth) implements Location {
		@Override public boolean accepts(Element element) { return element instanceof Tile tile && tile.op.shape() == Shape.COMMAND; }
		@Override public void insert(Element element) { stack.add(index, (Tile) element); }
		@Override public void remove() { stack.remove(index); }
	}

	record InputPoint(Tile owner, Field field, int depth) implements Location {
		boolean isFixedName() { return field.slot.isFixedName(); }

		@Override
		public boolean accepts(Element element) {
			return element instanceof Datum || (element instanceof Tile tile && !isFixedName() && tile.op.shape() != Shape.COMMAND);
		}

		@Override public void insert(Element element) { field.value = element; }

		@Override public void remove() { field.value = null; }
	}

	record LoosePoint(List<LooseData> entries, LooseData data) implements Location {
		@Override public Tile owner() { return null; }
		@Override public int depth() { return 1; }
		@Override public boolean accepts(Element element) { return element.isData(); }
		@Override public void insert(Element element) { entries.add(data); }
		@Override public void remove() { entries.remove(data); }
	}

	record LooseData(Element element, int x, int y) {}
	private record LooseSnapshot(Input input, int x, int y) {}
	private record History(ComputerProgram program, List<LooseSnapshot> looseData) {}
}
