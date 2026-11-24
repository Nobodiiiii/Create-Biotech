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

	private void load(ComputerProgram program) {
		commands.clear();
		for (Block block : program.commands())
			commands.add(new Tile(block));
		revision++;
	}

	boolean canDrop(Tile tile, Location from, Location to) {
		if (!to.accepts(tile) || tile.contains(to.owner()))
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

		Tile(Op op) {
			this.op = op;
			fields = op.slots().stream().map(Field::new).toList();
			for (int i = 0; i < op.bodyCount(); i++)
				bodies.add(new ArrayList<>());
		}

		Tile(Block block) {
			this(block.op());
			for (int i = 0; i < fields.size(); i++) {
				Field field = fields.get(i);
				switch (block.inputs().get(i)) {
					case Literal literal -> field.literal = literal.text();
					case Block reporter -> field.reporter = new Tile(reporter);
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
		@Override public boolean accepts(Tile tile) { return field.slot.socket().accepts(tile.op.shape()); }
		@Override public void insert(Tile tile) { field.reporter = tile; }
		@Override public void remove() { field.reporter = null; }
	}
}
