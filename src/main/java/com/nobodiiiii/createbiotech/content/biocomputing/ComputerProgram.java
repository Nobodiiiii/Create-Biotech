package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

/** The standalone computer's startup script and its inline value blocks. */
public record ComputerProgram(List<Block> commands) {

	public static final int MAX_BLOCKS = 128;
	public static final int MAX_DEPTH = 10;
	public static final int MAX_LITERAL_LENGTH = 48;
	public static final ComputerProgram EMPTY = new ComputerProgram(List.of());

	public ComputerProgram {
		commands = List.copyOf(commands);
		int remaining = MAX_BLOCKS;
		for (Block command : commands) {
			requireCommand(command);
			remaining = count(command, 1, remaining);
		}
	}

	/** Assigns deterministic pre-order indices to every command block (matching ScratchCanvas layout traversal). */
	public Map<Block, Integer> indexCommands() {
		Map<Block, Integer> indices = new IdentityHashMap<>();
		indexCommands(commands, indices, new int[1]);
		return indices;
	}

	private static void indexCommands(List<Block> stack, Map<Block, Integer> indices, int[] next) {
		for (Block command : stack) {
			indices.put(command, next[0]++);
			for (List<Block> body : command.bodies())
				indexCommands(body, indices, next);
		}
	}

	public static int parseSlotIndex(String text) {
		if (text == null || text.length() != 1)
			return -1;
		char c = text.charAt(0);
		int index = c - '1';
		return index >= 0 && index < ComputerIO.INPUT_SLOTS ? index : -1;
	}

	public static String nextSlot(String text) {
		int index = parseSlotIndex(text);
		int next = index < 0 ? 0 : (index + 1) % ComputerIO.INPUT_SLOTS;
		return Integer.toString(next + 1);
	}

	private static int count(Block block, int depth, int remaining) {
		if (depth > MAX_DEPTH || remaining <= 0)
			throw new IllegalArgumentException("Computer program exceeds its block or nesting limit");
		remaining--;
		for (Input input : block.inputs())
			if (input instanceof Block reporter)
				remaining = count(reporter, depth + 1, remaining);
		for (List<Block> body : block.bodies())
			for (Block command : body)
				remaining = count(command, depth + 1, remaining);
		return remaining;
	}

	private static void requireCommand(Block block) {
		if (block.op().shape() != Shape.COMMAND)
			throw new IllegalArgumentException("A script body requires command blocks");
	}

	public sealed interface Input permits Block, Literal {}

	public record Literal(String text) implements Input {
		public Literal {
			if (text == null || text.length() > MAX_LITERAL_LENGTH)
				throw new IllegalArgumentException("Computer literal is invalid or too long");
		}
	}

	public record Block(Op op, List<Input> inputs, List<List<Block>> bodies) implements Input {
		public Block {
			if (op == null)
				throw new IllegalArgumentException("Computer block op cannot be null");
			inputs = List.copyOf(inputs);
			bodies = bodies.stream().map(List::copyOf).toList();
			if (inputs.size() != op.slots().size() || bodies.size() != op.bodyCount())
				throw new IllegalArgumentException("Incorrect inputs or bodies for " + op);
			for (int i = 0; i < inputs.size(); i++) {
				Socket socket = op.slots().get(i).socket();
				switch (inputs.get(i)) {
					case Block reporter -> {
						if (!socket.accepts(reporter.op().shape()))
							throw new IllegalArgumentException("Incompatible reporter for " + op);
					}
					case Literal literal -> {
						if (socket == Socket.CONDITION && !literal.text().equals("true") && !literal.text().equals("false"))
							throw new IllegalArgumentException("A condition requires true or false");
						if (socket == Socket.PORT && ComputerIO.Port.parse(literal.text()) == null)
							throw new IllegalArgumentException("A port socket requires left, right, back, or top");
						if (socket == Socket.SLOT && parseSlotIndex(literal.text()) < 0)
							throw new IllegalArgumentException("A slot socket requires a number from 1 to 8");
					}
				}
			}
			for (List<Block> body : bodies)
				body.forEach(ComputerProgram::requireCommand);
		}
	}

	public enum Category { EVENTS, CONTROL, OPERATORS, VARIABLES, SENSING, OUTPUT }

	public enum Shape { COMMAND, REPORTER, BOOLEAN }

	public enum Socket {
		VALUE, CONDITION, NAME, PORT, SLOT;

		public boolean accepts(Shape shape) {
			return switch (this) {
				case VALUE -> shape != Shape.COMMAND;
				case CONDITION -> shape == Shape.BOOLEAN;
				case NAME, PORT, SLOT -> false;
			};
		}

		public boolean isSelector() {
			return this == CONDITION || this == PORT || this == SLOT;
		}
	}

	public record Slot(Socket socket, String initial) {}

	public enum Op {
		WAIT(Category.CONTROL, 0, value("1")),
		WAIT_TICKS(Category.CONTROL, 0, value("1")),
		WAIT_UNTIL(Category.CONTROL, 0, condition()),
		REPEAT(Category.CONTROL, 1, value("10")),
		FOREVER(Category.CONTROL, 1),
		REPEAT_UNTIL(Category.CONTROL, 1, condition()),
		IF(Category.CONTROL, 1, condition()),
		IF_ELSE(Category.CONTROL, 2, condition()),
		STOP(Category.CONTROL, 0),

		ADD(Category.OPERATORS, Shape.REPORTER, value("0"), value("0")),
		SUBTRACT(Category.OPERATORS, Shape.REPORTER, value("0"), value("0")),
		MULTIPLY(Category.OPERATORS, Shape.REPORTER, value("0"), value("1")),
		DIVIDE(Category.OPERATORS, Shape.REPORTER, value("0"), value("1")),
		MOD(Category.OPERATORS, Shape.REPORTER, value("10"), value("3")),
		ROUND(Category.OPERATORS, Shape.REPORTER, value("0")),
		FLOOR(Category.OPERATORS, Shape.REPORTER, value("0")),
		ABS(Category.OPERATORS, Shape.REPORTER, value("0")),
		RANDOM(Category.OPERATORS, Shape.REPORTER, value("1"), value("10")),
		EQUALS(Category.OPERATORS, Shape.BOOLEAN, value("0"), value("0")),
		LESS(Category.OPERATORS, Shape.BOOLEAN, value("0"), value("1")),
		GREATER(Category.OPERATORS, Shape.BOOLEAN, value("1"), value("0")),
		AND(Category.OPERATORS, Shape.BOOLEAN, condition(), condition()),
		OR(Category.OPERATORS, Shape.BOOLEAN, condition(), condition()),
		NOT(Category.OPERATORS, Shape.BOOLEAN, condition()),
		JOIN(Category.OPERATORS, Shape.REPORTER, value(""), value("")),
		LENGTH(Category.OPERATORS, Shape.REPORTER, value("")),
		CONTAINS(Category.OPERATORS, Shape.BOOLEAN, value(""), value("")),
		PARSE_NUMBER(Category.OPERATORS, Shape.REPORTER, value("0")),

		SET(Category.VARIABLES, 0, variable(), value("0")),
		CHANGE(Category.VARIABLES, 0, variable(), value("1")),
		VARIABLE(Category.VARIABLES, Shape.REPORTER, variable()),

		READ_REDSTONE(Category.SENSING, Shape.REPORTER, port("left")),
		HAS_REDSTONE(Category.SENSING, Shape.BOOLEAN, port("left")),
		READ_INPUT(Category.SENSING, Shape.REPORTER, slot("1")),
		TIMER(Category.SENSING, Shape.REPORTER),

		SET_REDSTONE(Category.OUTPUT, 0, port("left"), value("15")),
		SET_LINE(Category.OUTPUT, 0, slot("1"), value("")),
		DISPLAY(Category.OUTPUT, 0, value("")),
		CLEAR_OUTPUT(Category.OUTPUT, 0),
		RESET_TIMER(Category.OUTPUT, 0),
		UPDATE_DISPLAY(Category.OUTPUT, 0);

		private static final Map<String, Op> BY_KEY = buildKeyMap();

		private final Category category;
		private final Shape shape;
		private final int bodyCount;
		private final List<Slot> slots;
		private final String key;

		Op(Category category, int bodyCount, Slot... slots) {
			this(category, Shape.COMMAND, bodyCount, slots);
		}

		Op(Category category, Shape shape, Slot... slots) {
			this(category, shape, 0, slots);
		}

		Op(Category category, Shape shape, int bodyCount, Slot... slots) {
			this.category = category;
			this.shape = shape;
			this.bodyCount = bodyCount;
			this.slots = List.of(slots);
			this.key = name().toLowerCase(Locale.ROOT);
		}

		public Category category() { return category; }
		public Shape shape() { return shape; }
		public int bodyCount() { return bodyCount; }
		public List<Slot> slots() { return slots; }
		public String key() { return key; }

		@Nullable
		public static Op byKey(String key) {
			return key == null ? null : BY_KEY.get(key.toLowerCase(Locale.ROOT));
		}

		private static Map<String, Op> buildKeyMap() {
			Map<String, Op> map = new java.util.HashMap<>();
			for (Op op : values())
				map.put(op.key, op);
			return Map.copyOf(map);
		}

		private static Slot value(String initial) { return new Slot(Socket.VALUE, initial); }
		private static Slot condition() { return new Slot(Socket.CONDITION, "false"); }
		private static Slot variable() { return new Slot(Socket.NAME, "count"); }
		private static Slot port(String initial) { return new Slot(Socket.PORT, initial); }
		private static Slot slot(String initial) { return new Slot(Socket.SLOT, initial); }
	}
}
