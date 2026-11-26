package com.nobodiiiii.createbiotech.content.biocomputing;

import static com.nobodiiiii.createbiotech.content.biocomputing.ComputerLibrary.*;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

/** The standalone computer's startup script and its inline value blocks. */
public record ComputerProgram(List<Block> commands) {

	static final int FORMAT_VERSION = 4;
	public static final int MAX_BLOCKS = 128;
	public static final int MAX_DEPTH = 10;
	public static final int MAX_LITERAL_LENGTH = 48;
	public static final int MAX_COLLECTION_ROWS = 8;
	private static final String EXAMPLE_ITEM = "minecraft:iron_ingot";
	private static final String DEFAULT_NUMBER = "0";
	public static final ComputerProgram EMPTY = new ComputerProgram(List.of());

	public ComputerProgram {
		commands = List.copyOf(commands);
		int remaining = MAX_BLOCKS;
		for (Block command : commands) {
			requireCommand(command);
			remaining = count(command, 1, remaining);
		}
	}

	public boolean hasEmptyInputs() {
		return commands.stream().anyMatch(Block::hasEmptyInputs);
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

	private static int count(Block block, int depth, int remaining) {
		if (depth > MAX_DEPTH || remaining <= 0)
			throw new IllegalArgumentException("Computer program exceeds its block or nesting limit");
		if (depth > 1 && block.op().isDeclaration())
			throw new IllegalArgumentException("Procedure and event declarations belong at the top level");
		remaining--;
		for (Input input : block.inputs()) {
			if (input instanceof Block reporter)
				remaining = count(reporter, depth + 1, remaining);
			else if (input instanceof Literal literal && literal.type() == DataType.ITEM) {
				if (depth >= MAX_DEPTH || remaining <= 0)
					throw new IllegalArgumentException("Computer program exceeds its block or nesting limit");
				remaining--;
			}
		}
		for (List<Block> body : block.bodies())
			for (Block command : body)
				remaining = count(command, depth + 1, remaining);
		return remaining;
	}

	private static void requireCommand(Block block) {
		if (block.op().shape() != Shape.COMMAND)
			throw new IllegalArgumentException("A script body requires command blocks");
	}

	public sealed interface Input permits Block, Literal, EmptyInput {}

	public enum EmptyInput implements Input { INSTANCE }

	public record Literal(String text, DataType type) implements Input {
		public Literal {
			if (text == null || type == null || text.length() > type.maxLength() || !type.isValid(text))
				throw new IllegalArgumentException("Computer literal is invalid or too long");
		}

		String evaluatedText() {
			// A blank numeric preset stays distinguishable from an explicitly entered zero.
			return type == DataType.NUMBER && text.isEmpty() ? DEFAULT_NUMBER : text;
		}
	}

	public record Block(Op op, List<Input> inputs, List<List<Block>> bodies) implements Input {
		public Block {
			if (op == null)
				throw new IllegalArgumentException("Computer block op cannot be null");
			bodies = bodies.stream().map(List::copyOf).toList();
			if (!op.acceptsInputCount(inputs.size()) || bodies.size() != op.bodyCount())
				throw new IllegalArgumentException("Incorrect inputs or bodies for " + op);
			inputs = List.copyOf(inputs);
			for (int i = 0; i < inputs.size(); i++) {
				if (inputs.get(i) instanceof Block reporter) {
					if (op.slot(i).isFixedName())
						throw new IllegalArgumentException("A variable name requires a literal");
					if (reporter.op().shape() == Shape.COMMAND)
						throw new IllegalArgumentException("A value slot requires a value for " + op);
				}
			}
			for (List<Block> body : bodies)
				body.forEach(ComputerProgram::requireCommand);
		}

		private boolean hasEmptyInputs() {
			for (Input input : inputs)
				if (input == EmptyInput.INSTANCE || (input instanceof Block reporter && reporter.hasEmptyInputs()))
					return true;
			return bodies.stream().flatMap(List::stream).anyMatch(Block::hasEmptyInputs);
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

	public enum Category { EVENTS, CONTROL, OPERATORS, VARIABLES, SENSING, OUTPUT, DATA }

	public enum Shape { COMMAND, REPORTER, BOOLEAN }

	public enum DataType {
		NUMBER(""), TEXT(""), BOOLEAN("false"), ITEM(EXAMPLE_ITEM);

		private final String initial;
		private final String key;

		DataType(String initial) {
			this.initial = initial;
			key = name().toLowerCase(Locale.ROOT);
		}

		public String initial() { return initial; }
		public String key() { return key; }

		public int maxLength() {
			return this == NUMBER || this == BOOLEAN ? MAX_LITERAL_LENGTH : ComputerRuntime.MAX_VALUE_LENGTH;
		}

		public boolean isValid(String text) {
			return switch (this) {
				case NUMBER -> text.isEmpty() || isNumber(text);
				case BOOLEAN -> text.equals("true") || text.equals("false");
				case TEXT, ITEM -> true;
			};
		}

		static DataType byKey(String key) {
			DataType type = valueOf(key.toUpperCase(Locale.ROOT));
			if (!type.key.equals(key))
				throw new IllegalArgumentException("Unknown computer data type");
			return type;
		}

		private static boolean isNumber(String text) {
			return Double.isFinite(ComputerValue.Scalar.parseNumber(text, false));
		}
	}

	/** Supplies editor defaults and hints for a value slot. */
	public enum Socket {
		VALUE, CONDITION, VARIABLE_NAME, FIXED_NAME, CHANNEL, SLOT, FREQUENCY, LIST, MAP, LIST_REFERENCE, MAP_REFERENCE, LIST_TARGET, MAP_TARGET;

		@Nullable
		public Op defaultStructure() {
			return switch (this) {
				case LIST -> Op.LIST;
				case MAP -> Op.MAP;
				case VARIABLE_NAME, SLOT, FREQUENCY, LIST_REFERENCE, MAP_REFERENCE, LIST_TARGET, MAP_TARGET -> Op.VARIABLE;
				default -> null;
			};
		}
	}

	public record Slot(Socket socket, String initial, DataType dataType) {
		public boolean isFixedName() {
			return socket == Socket.FIXED_NAME;
		}
	}

	public enum Op {
		DEFINE_PROCEDURE(Category.EVENTS, 1, value("procedure")),
		CALL_PROCEDURE(Category.EVENTS, 0, value("procedure")),
		WHEN_SIGNAL_CHANGED(TIMING, Category.EVENTS, 1, frequency()),
		WHEN_INPUT_CHANGED(TIMING, Category.EVENTS, 1, slot()),
		ON_BROADCAST(TIMING, Category.EVENTS, 1, value("message")),
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
		TEXT_EQUALS(Category.OPERATORS, Shape.BOOLEAN, value(""), value("")),
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
		PICK_LIST(RANDOMNESS, Category.OPERATORS, Shape.REPORTER, value("example")),
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
		VARIABLE(Category.VARIABLES, Shape.REPORTER, new Slot(Socket.FIXED_NAME, "", DataType.TEXT)),
		TOGGLE(SIGNAL, Category.VARIABLES, 0, variable()),
		LATCH(SIGNAL, Category.VARIABLES, 0, variable(), condition()),
		SPLIT_TEXT(TEXT, Category.VARIABLES, 0, value("a,b,c"), value(","), listTarget()),
		LIST_ADD(TEXT, Category.VARIABLES, 0, listTarget(), value("")),
		LIST_REMOVE(TEXT, Category.VARIABLES, 0, listTarget(), value("1")),
		LIST_GET(TEXT, Category.VARIABLES, Shape.REPORTER, listReference(), value("1")),
		LIST_SET(TEXT, Category.VARIABLES, 0, listTarget(), value("1"), value("")),
		LIST_LENGTH(TEXT, Category.VARIABLES, Shape.REPORTER, listReference()),
		LIST_JOIN(TEXT, Category.VARIABLES, Shape.REPORTER, listReference(), value(",")),
		MAP_SET(TEXT, Category.VARIABLES, 0, mapTarget(), value("key"), value("")),
		MAP_REMOVE(TEXT, Category.VARIABLES, 0, mapTarget(), value("key")),
		MAP_GET(TEXT, Category.VARIABLES, Shape.REPORTER, mapReference(), value("key")),
		MAP_HAS(TEXT, Category.VARIABLES, Shape.BOOLEAN, mapReference(), value("key")),
		MAP_LENGTH(TEXT, Category.VARIABLES, Shape.REPORTER, mapReference()),

		HAS_REDSTONE(Category.SENSING, Shape.BOOLEAN, frequency()),
		READ_INPUT(Category.SENSING, Shape.REPORTER, slot()),
		READ_REDSTONE(SIGNAL, Category.SENSING, Shape.REPORTER, frequency()),
		RISING_EDGE(SIGNAL, Category.SENSING, Shape.BOOLEAN, frequency()),
		FALLING_EDGE(SIGNAL, Category.SENSING, Shape.BOOLEAN, frequency()),
		TIMER(TIMING, Category.SENSING, Shape.REPORTER),
		STOCK_COUNT(LOGISTICS, Category.SENSING, Shape.REPORTER, collection(Socket.LIST)),
		HAS_STOCK(LOGISTICS, Category.SENSING, Shape.BOOLEAN, collection(Socket.LIST), value("1")),
		STOCK_TOTAL(LOGISTICS, Category.SENSING, Shape.REPORTER),
		STOCK_TYPES(LOGISTICS, Category.SENSING, Shape.REPORTER),
		LAST_ORDER(LOGISTICS, Category.SENSING, Shape.BOOLEAN),

		BIND_FREQUENCY(Category.OUTPUT, 0, channel(), collection(Socket.LIST)),
		SEND_REDSTONE(Category.OUTPUT, 0, frequency(), condition()),
		DISPLAY(Category.OUTPUT, 0, value("")),
		DISPLAY_LINES(Category.OUTPUT, 0, value("")),
		DISPLAY_ITEM_LIST(Category.OUTPUT, 0, collection(Socket.MAP)),
		CLEAR_OUTPUT(Category.OUTPUT, 0),
		UPDATE_DISPLAY(Category.OUTPUT, 0),
		SET_REDSTONE(SIGNAL, Category.OUTPUT, 0, frequency(), value("15")),
		TUNE_FREQUENCY(SIGNAL, Category.OUTPUT, 0, channel(), collection(Socket.LIST)),
		PULSE_REDSTONE(SIGNAL, Category.OUTPUT, 0, frequency(), value("15"), value("10")),
		SET_LINE(TEXT, Category.OUTPUT, 0, slot(), value("")),
		RESET_TIMER(TIMING, Category.OUTPUT, 0),
		ORDER_ITEM(LOGISTICS, Category.OUTPUT, 0, item(), value("1"), value("Workshop")),
		ORDER_BATCH(LOGISTICS, Category.OUTPUT, 0, collection(Socket.MAP), value("Workshop")),
		LIST(Category.DATA, Shape.REPORTER, item()),
		MAP(Category.DATA, Shape.REPORTER, item(), value("1")),
		TO_TEXT(Category.DATA, Shape.REPORTER, value("")),
		TO_NUMBER(Category.DATA, Shape.REPORTER, value("0")),
		TO_BOOLEAN(Category.DATA, Shape.BOOLEAN, condition()),
		TO_ITEM(Category.DATA, Shape.REPORTER, value(EXAMPLE_ITEM)),
		TO_LIST(Category.DATA, Shape.REPORTER, value("")),
		TO_MAP(Category.DATA, Shape.REPORTER, value(""));

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

		public boolean isCollection() {
			return this == LIST || this == MAP;
		}

		public boolean isLegacyFrequencyBinding() {
			return this == BIND_FREQUENCY || this == TUNE_FREQUENCY;
		}

		public boolean hasFrequencyInput() {
			return !slots.isEmpty() && slots.getFirst().socket() == Socket.FREQUENCY;
		}

		public Slot slot(int index) {
			return slots.get(isCollection() ? index % slots.size() : index);
		}

		public int maxInputs() {
			return slots.size() * (isCollection() ? MAX_COLLECTION_ROWS : 1);
		}

		public boolean acceptsInputCount(int count) {
			return isCollection() ? count >= 0 && count <= maxInputs() && count % slots.size() == 0 : count == slots.size();
		}

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

		private static Slot value(String initial) {
		DataType type = DataType.isNumber(initial) ? DataType.NUMBER : DataType.TEXT;
		String defaultText = DEFAULT_NUMBER.equals(initial) ? DataType.NUMBER.initial() : initial;
		return new Slot(Socket.VALUE, defaultText, type);
		}
		private static Slot item() { return new Slot(Socket.VALUE, EXAMPLE_ITEM, DataType.ITEM); }
		private static Slot condition() { return new Slot(Socket.CONDITION, "false", DataType.BOOLEAN); }
		private static Slot variable() { return variableName(Socket.VARIABLE_NAME); }
		private static Slot channel() { return new Slot(Socket.CHANNEL, "1", DataType.NUMBER); }
		private static Slot slot() { return variableName(Socket.SLOT); }
		private static Slot frequency() { return variableName(Socket.FREQUENCY); }
		private static Slot collection(Socket socket) { return new Slot(socket, "", DataType.TEXT); }
		private static Slot listReference() { return variableName(Socket.LIST_REFERENCE); }
		private static Slot listTarget() { return variableName(Socket.LIST_TARGET); }
		private static Slot mapReference() { return variableName(Socket.MAP_REFERENCE); }
		private static Slot mapTarget() { return variableName(Socket.MAP_TARGET); }
		private static Slot variableName(Socket socket) { return new Slot(socket, "", DataType.TEXT); }
	}
}
