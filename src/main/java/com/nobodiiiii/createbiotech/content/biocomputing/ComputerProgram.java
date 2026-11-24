package com.nobodiiiii.createbiotech.content.biocomputing;

import static com.nobodiiiii.createbiotech.content.biocomputing.ComputerLibrary.*;

import java.util.ArrayList;
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

	@Nullable
	public Op firstUnavailable(ComputerLibrary library) {
		return library == ALL ? null : firstUnavailable(commands, library);
	}

	@Nullable
	private static Op firstUnavailable(List<Block> stack, ComputerLibrary library) {
		for (Block block : stack) {
			Op unavailable = block.firstUnavailable(library);
			if (unavailable != null)
				return unavailable;
		}
		return null;
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
		if (depth > 1 && block.op().isDeclaration())
			throw new IllegalArgumentException("Procedure and event declarations belong at the top level");
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
						if (socket == Socket.CHANNEL) {
							String channel = ComputerIO.Channel.migrate(literal.text());
							if (ComputerIO.Channel.parse(channel) == null)
								throw new IllegalArgumentException("A channel socket requires a number from 1 to 4");
							if (!channel.equals(literal.text())) {
								List<Input> migrated = new ArrayList<>(inputs);
								migrated.set(i, new Literal(channel));
								inputs = List.copyOf(migrated);
							}
						}
						if (socket == Socket.SLOT && parseSlotIndex(literal.text()) < 0)
							throw new IllegalArgumentException("A slot socket requires a number from 1 to 8");
					}
				}
			}
			for (List<Block> body : bodies)
				body.forEach(ComputerProgram::requireCommand);
		}

		@Nullable
		private Op firstUnavailable(ComputerLibrary library) {
			if (!library.allows(op))
				return op;
			for (Input input : inputs) {
				if (input instanceof Block reporter) {
					Op unavailable = reporter.firstUnavailable(library);
					if (unavailable != null)
						return unavailable;
				}
			}
			for (List<Block> body : bodies) {
				Op unavailable = ComputerProgram.firstUnavailable(body, library);
				if (unavailable != null)
					return unavailable;
			}
			return null;
		}
	}

	public enum Category { EVENTS, CONTROL, OPERATORS, VARIABLES, SENSING, OUTPUT }

	public enum Shape { COMMAND, REPORTER, BOOLEAN }

	public enum Socket {
		VALUE, CONDITION, NAME, CHANNEL, SLOT;

		public boolean accepts(Shape shape) {
			return switch (this) {
				case VALUE -> shape != Shape.COMMAND;
				case CONDITION -> shape == Shape.BOOLEAN;
				case NAME, CHANNEL, SLOT -> false;
			};
		}

		public boolean isSelector() {
			return this == CONDITION || this == CHANNEL || this == SLOT;
		}
	}

	public record Slot(Socket socket, String initial) {}

	public enum Op {
		DEFINE_PROCEDURE(Category.EVENTS, 1, name("procedure")),
		CALL_PROCEDURE(Category.EVENTS, 0, name("procedure")),
		WHEN_SIGNAL_CHANGED(TIMING, Category.EVENTS, 1, channel()),
		WHEN_INPUT_CHANGED(TIMING, Category.EVENTS, 1, slot("1")),
		ON_BROADCAST(TIMING, Category.EVENTS, 1, name("message")),
		BROADCAST(TIMING, Category.EVENTS, 0, value("message")),

		WAIT(Category.CONTROL, 0, value("1")),
		WAIT_TICKS(Category.CONTROL, 0, value("1")),
		WAIT_UNTIL(TIMING, Category.CONTROL, 0, condition()),
		WAIT_STABLE(TIMING, Category.CONTROL, 0, condition(), value("1")),
		COOLDOWN(TIMING, Category.CONTROL, 1, value("1")),
		REPEAT(TIMING, Category.CONTROL, 1, value("10"), value("1")),
		FOREVER(Category.CONTROL, 1, value("1")),
		REPEAT_UNTIL(TIMING, Category.CONTROL, 1, condition(), value("1")),
		IF(Category.CONTROL, 1, condition()),
		IF_ELSE(Category.CONTROL, 2, condition()),
		STOP(Category.CONTROL, 0),
		RANDOM_WAIT(RANDOMNESS, Category.CONTROL, 0, value("1"), value("5")),

		ADD(Category.OPERATORS, Shape.REPORTER, value("0"), value("0")),
		SUBTRACT(Category.OPERATORS, Shape.REPORTER, value("0"), value("0")),
		EQUALS(Category.OPERATORS, Shape.BOOLEAN, value("0"), value("0")),
		LESS(Category.OPERATORS, Shape.BOOLEAN, value("0"), value("1")),
		GREATER(Category.OPERATORS, Shape.BOOLEAN, value("1"), value("0")),
		NOT(Category.OPERATORS, Shape.BOOLEAN, condition()),
		MULTIPLY(MATH, Category.OPERATORS, Shape.REPORTER, value("0"), value("1")),
		DIVIDE(MATH, Category.OPERATORS, Shape.REPORTER, value("0"), value("1")),
		MOD(MATH, Category.OPERATORS, Shape.REPORTER, value("10"), value("3")),
		ROUND(MATH, Category.OPERATORS, Shape.REPORTER, value("0")),
		FLOOR(MATH, Category.OPERATORS, Shape.REPORTER, value("0")),
		CEIL(MATH, Category.OPERATORS, Shape.REPORTER, value("0")),
		ABS(MATH, Category.OPERATORS, Shape.REPORTER, value("0")),
		MAP_RANGE(MATH, Category.OPERATORS, Shape.REPORTER, value("0"), value("0"), value("100"), value("0"), value("15")),
		CLAMP(MATH, Category.OPERATORS, Shape.REPORTER, value("0"), value("0"), value("15")),
		MIN(MATH, Category.OPERATORS, Shape.REPORTER, value("0"), value("1")),
		MAX(MATH, Category.OPERATORS, Shape.REPORTER, value("0"), value("1")),
		POWER(MATH, Category.OPERATORS, Shape.REPORTER, value("2"), value("2")),
		SQRT(MATH, Category.OPERATORS, Shape.REPORTER, value("4")),
		SIN(MATH, Category.OPERATORS, Shape.REPORTER, value("0")),
		COS(MATH, Category.OPERATORS, Shape.REPORTER, value("0")),
		TAN(MATH, Category.OPERATORS, Shape.REPORTER, value("0")),
		RANDOM(RANDOMNESS, Category.OPERATORS, Shape.REPORTER, value("1"), value("10")),
		CHANCE(RANDOMNESS, Category.OPERATORS, Shape.BOOLEAN, value("50")),
		PICK_TEXT(RANDOMNESS, Category.OPERATORS, Shape.REPORTER, value("a,b,c"), value(",")),
		PICK_LIST(RANDOMNESS, Category.OPERATORS, Shape.REPORTER, value("a,b,c")),
		SEED_RANDOM(RANDOMNESS, Category.OPERATORS, 0, value("0")),
		AND(SIGNAL, Category.OPERATORS, Shape.BOOLEAN, condition(), condition()),
		OR(SIGNAL, Category.OPERATORS, Shape.BOOLEAN, condition(), condition()),
		XOR(SIGNAL, Category.OPERATORS, Shape.BOOLEAN, condition(), condition()),
		PACK_BITS(SIGNAL, Category.OPERATORS, Shape.REPORTER, condition(), condition(), condition(), condition()),
		UNPACK_BIT(SIGNAL, Category.OPERATORS, Shape.BOOLEAN, value("0"), value("1")),
		JOIN(TEXT, Category.OPERATORS, Shape.REPORTER, value(""), value("")),
		LENGTH(TEXT, Category.OPERATORS, Shape.REPORTER, value("")),
		CONTAINS(TEXT, Category.OPERATORS, Shape.BOOLEAN, value(""), value("")),
		PARSE_NUMBER(TEXT, Category.OPERATORS, Shape.REPORTER, value("0")),
		SUBSTRING(TEXT, Category.OPERATORS, Shape.REPORTER, value(""), value("1"), value("3")),
		REPLACE_TEXT(TEXT, Category.OPERATORS, Shape.REPORTER, value("abc"), value("a"), value("")),
		FORMAT_NUMBER(TEXT, Category.OPERATORS, Shape.REPORTER, value("0"), value("2")),
		PROGRESS_BAR(TEXT, Category.OPERATORS, Shape.REPORTER, value("0"), value("100"), value("10")),

		SET(Category.VARIABLES, 0, variable(), value("0")),
		CHANGE(Category.VARIABLES, 0, variable(), value("1")),
		VARIABLE(Category.VARIABLES, Shape.REPORTER, variable()),
		TOGGLE(SIGNAL, Category.VARIABLES, 0, variable()),
		LATCH(SIGNAL, Category.VARIABLES, 0, variable(), condition()),
		SPLIT_TEXT(TEXT, Category.VARIABLES, 0, value("a,b,c"), value(","), name("list")),
		LIST_ADD(TEXT, Category.VARIABLES, 0, name("list"), value("")),
		LIST_REMOVE(TEXT, Category.VARIABLES, 0, name("list"), value("1")),
		LIST_GET(TEXT, Category.VARIABLES, Shape.REPORTER, name("list"), value("1")),
		LIST_SET(TEXT, Category.VARIABLES, 0, name("list"), value("1"), value("")),
		LIST_LENGTH(TEXT, Category.VARIABLES, Shape.REPORTER, name("list")),
		LIST_JOIN(TEXT, Category.VARIABLES, Shape.REPORTER, name("list"), value(",")),
		MAP_SET(TEXT, Category.VARIABLES, 0, name("map"), value("key"), value("")),
		MAP_REMOVE(TEXT, Category.VARIABLES, 0, name("map"), value("key")),
		MAP_GET(TEXT, Category.VARIABLES, Shape.REPORTER, name("map"), value("key")),
		MAP_HAS(TEXT, Category.VARIABLES, Shape.BOOLEAN, name("map"), value("key")),
		MAP_LENGTH(TEXT, Category.VARIABLES, Shape.REPORTER, name("map")),

		HAS_REDSTONE(Category.SENSING, Shape.BOOLEAN, channel()),
		READ_INPUT(Category.SENSING, Shape.REPORTER, slot("1")),
		READ_REDSTONE(SIGNAL, Category.SENSING, Shape.REPORTER, channel()),
		RISING_EDGE(SIGNAL, Category.SENSING, Shape.BOOLEAN, channel()),
		FALLING_EDGE(SIGNAL, Category.SENSING, Shape.BOOLEAN, channel()),
		TIMER(TIMING, Category.SENSING, Shape.REPORTER),
		STOCK_COUNT(LOGISTICS, Category.SENSING, Shape.REPORTER, value("minecraft:iron_ingot")),
		HAS_STOCK(LOGISTICS, Category.SENSING, Shape.BOOLEAN, value("minecraft:iron_ingot"), value("1")),
		STOCK_TOTAL(LOGISTICS, Category.SENSING, Shape.REPORTER),
		STOCK_TYPES(LOGISTICS, Category.SENSING, Shape.REPORTER),
		LAST_ORDER(LOGISTICS, Category.SENSING, Shape.BOOLEAN),

		BIND_FREQUENCY(Category.OUTPUT, 0, channel(), name("minecraft:redstone"), name("minecraft:stick")),
		SEND_REDSTONE(Category.OUTPUT, 0, channel(), condition()),
		DISPLAY(Category.OUTPUT, 0, value("")),
		CLEAR_OUTPUT(Category.OUTPUT, 0),
		UPDATE_DISPLAY(Category.OUTPUT, 0),
		SET_REDSTONE(SIGNAL, Category.OUTPUT, 0, channel(), value("15")),
		TUNE_FREQUENCY(SIGNAL, Category.OUTPUT, 0, channel(), value("minecraft:redstone"), value("minecraft:stick")),
		PULSE_REDSTONE(SIGNAL, Category.OUTPUT, 0, channel(), value("15"), value("10")),
		SET_LINE(TEXT, Category.OUTPUT, 0, slot("1"), value("")),
		RESET_TIMER(TIMING, Category.OUTPUT, 0),
		ORDER_ITEM(LOGISTICS, Category.OUTPUT, 0, value("minecraft:iron_ingot"), value("1"), value("Workshop")),
		ORDER_BATCH(LOGISTICS, Category.OUTPUT, 0, value("iron_ingot=2,stick=4"), value("Workshop"));

		private static final Map<String, Op> BY_KEY = buildKeyMap();

		private final ComputerLibrary library;
		private final Category category;
		private final Shape shape;
		private final int bodyCount;
		private final List<Slot> slots;
		private final String key;

		Op(Category category, int bodyCount, Slot... slots) {
			this(CORE, category, Shape.COMMAND, bodyCount, slots);
		}

		Op(Category category, Shape shape, Slot... slots) {
			this(CORE, category, shape, 0, slots);
		}

		Op(ComputerLibrary library, Category category, int bodyCount, Slot... slots) {
			this(library, category, Shape.COMMAND, bodyCount, slots);
		}

		Op(ComputerLibrary library, Category category, Shape shape, Slot... slots) {
			this(library, category, shape, 0, slots);
		}

		Op(ComputerLibrary library, Category category, Shape shape, int bodyCount, Slot... slots) {
			this.library = library;
			this.category = category;
			this.shape = shape;
			this.bodyCount = bodyCount;
			this.slots = List.of(slots);
			this.key = name().toLowerCase(Locale.ROOT);
		}

		public ComputerLibrary library() { return library; }
		public Category category() { return category; }
		public Shape shape() { return shape; }
		public int bodyCount() { return bodyCount; }
		public List<Slot> slots() { return slots; }
		public String key() { return key; }

		public boolean isDeclaration() {
			return switch (this) {
				case DEFINE_PROCEDURE, WHEN_SIGNAL_CHANGED, WHEN_INPUT_CHANGED, ON_BROADCAST -> true;
				default -> false;
			};
		}

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
		private static Slot name(String initial) { return new Slot(Socket.NAME, initial); }
		private static Slot variable() { return name("count"); }
		private static Slot channel() { return new Slot(Socket.CHANNEL, "1"); }
		private static Slot slot(String initial) { return new Slot(Socket.SLOT, initial); }
	}
}
