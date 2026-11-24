package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerIO.Port;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Block;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Literal;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Op;

/** Deterministic tick-driven Scratch interpreter with bounded budgets and decoupled I/O. */
public final class ComputerRuntime {
	static final int MAX_VARIABLES = 24;
	static final int MAX_OUTPUT_LINES = ComputerIO.OUTPUT_LINES;
	static final int MAX_VALUE_LENGTH = 128;
	private static final int STEPS_PER_TICK = 24;
	private static final int MAX_EVALS_PER_TICK = 256;
	private static final int MAX_WAIT_TICKS = 20 * 60 * 60;

	private final Deque<Frame> frames = new ArrayDeque<>();
	private final Map<String, String> variables = new LinkedHashMap<>();
	private Map<Block, Integer> commandIndices = Map.of();
	private Status status = Status.READY;
	private int waitTicks;
	private int activeBlockIndex = -1;
	private long timerStartTick;
	private int evalBudget;
	private long version;

	void start(ComputerProgram program, long gameTick) {
		frames.clear();
		variables.clear();
		commandIndices = program.indexCommands();
		waitTicks = 0;
		activeBlockIndex = -1;
		timerStartTick = gameTick;
		if (!program.commands().isEmpty())
			frames.push(new Frame(program.commands(), null, 1));
		status = frames.isEmpty() ? Status.FINISHED : Status.RUNNING;
		version++;
	}

	void stop() {
		if (status == Status.RUNNING)
			finish(Status.STOPPED);
	}

	Status status() {
		return status;
	}

	boolean isRunning() {
		return status == Status.RUNNING;
	}

	void tick(ComputerIO io) {
		if (status != Status.RUNNING || (waitTicks > 0 && --waitTicks > 0))
			return;
		evalBudget = MAX_EVALS_PER_TICK;
		// Empty loop bodies and loop transitions also spend the tick budget.
		for (int steps = 0; steps < STEPS_PER_TICK && !frames.isEmpty() && waitTicks == 0 && status == Status.RUNNING; steps++) {
			Frame frame = frames.peek();
			if (frame.next < frame.commands.size()) {
				Block block = frame.commands.get(frame.next);
				setActiveBlock(commandIndices.getOrDefault(block, -1));
				if (block.op() == Op.WAIT_UNTIL) {
					if (condition(block, 0, io)) {
						frame.next++;
					} else {
						break; // Yield the rest of this tick until the condition becomes true.
					}
				} else {
					frame.next++;
					execute(block, io);
				}
			} else if (frame.loop != null && switch (frame.loop.op()) {
				case REPEAT -> --frame.remaining > 0;
				case REPEAT_UNTIL -> !condition(frame.loop, 0, io);
				case FOREVER -> true;
				default -> false;
			}) {
				frame.next = 0;
			} else {
				frames.pop();
			}
			if (evalBudget <= 0)
				break;
		}
		if (frames.isEmpty() && status == Status.RUNNING)
			finish(Status.FINISHED);
	}

	private void execute(Block block, ComputerIO io) {
		switch (block.op()) {
			case WAIT -> waitTicks = (int) Math.clamp(Math.ceil(number(input(block, 0, io)) * 20.0), 1, MAX_WAIT_TICKS);
			case WAIT_TICKS -> waitTicks = (int) Math.clamp(Math.round(number(input(block, 0, io))), 1, MAX_WAIT_TICKS);
			case STOP -> stop();
			case IF, IF_ELSE -> {
				int body = condition(block, 0, io) ? 0 : 1;
				if (body < block.bodies().size())
					frames.push(new Frame(block.bodies().get(body), null, 1));
			}
			case REPEAT -> {
				int count = Math.clamp(Math.round(number(input(block, 0, io))), 0, Integer.MAX_VALUE);
				if (count > 0 && !block.bodies().getFirst().isEmpty())
					frames.push(new Frame(block.bodies().getFirst(), block, count));
			}
			case FOREVER, REPEAT_UNTIL -> {
				if (block.op() == Op.FOREVER || !condition(block, 0, io))
					frames.push(new Frame(block.bodies().getFirst(), block, 0));
			}
			case SET, CHANGE -> {
				String name = input(block, 0, io).strip();
				String value = clampValue(input(block, 1, io));
				if (block.op() == Op.CHANGE)
					value = format(number(variables.getOrDefault(name, "0")) + number(value));
				putVariable(name, value);
			}
			case SET_REDSTONE -> {
				Port port = Port.parse(input(block, 0, io));
				if (port != null) {
					int power = (int) Math.clamp(Math.round(number(input(block, 1, io))), 0, 15);
					io.writeRedstone(port, power);
				}
			}
			case SET_LINE -> {
				int slotIndex = ComputerProgram.parseSlotIndex(input(block, 0, io));
				if (slotIndex >= 0)
					io.setOutputLine(slotIndex, clampValue(input(block, 1, io)));
			}
			case DISPLAY -> io.appendOutput(clampValue(input(block, 0, io)));
			case CLEAR_OUTPUT -> io.clearOutput();
			case RESET_TIMER -> timerStartTick = io.gameTick();
			case UPDATE_DISPLAY -> io.updateDisplay();
			default -> throw new IllegalStateException("Expected a computer command: " + block.op());
		}
	}

	private String input(Block block, int index, ComputerIO io) {
		return switch (block.inputs().get(index)) {
			case Literal literal -> literal.text();
			case Block reporter -> evaluate(reporter, io);
		};
	}

	private String evaluate(Block block, ComputerIO io) {
		evalBudget--;
		return switch (block.op()) {
			case VARIABLE -> variables.getOrDefault(input(block, 0, io).strip(), "0");
			case ADD -> format(number(input(block, 0, io)) + number(input(block, 1, io)));
			case SUBTRACT -> format(number(input(block, 0, io)) - number(input(block, 1, io)));
			case MULTIPLY -> format(number(input(block, 0, io)) * number(input(block, 1, io)));
			case DIVIDE -> format(number(input(block, 0, io)) / number(input(block, 1, io)));
			case MOD -> {
				double a = number(input(block, 0, io));
				double b = number(input(block, 1, io));
				yield b == 0 ? "0" : format(a - Math.floor(a / b) * b);
			}
			case ROUND -> format(Math.floor(number(input(block, 0, io)) + 0.5));
			case FLOOR -> format(Math.floor(number(input(block, 0, io))));
			case ABS -> format(Math.abs(number(input(block, 0, io))));
			case RANDOM -> random(input(block, 0, io), input(block, 1, io));
			case EQUALS -> Boolean.toString(compare(input(block, 0, io), input(block, 1, io)) == 0);
			case LESS -> Boolean.toString(compare(input(block, 0, io), input(block, 1, io)) < 0);
			case GREATER -> Boolean.toString(compare(input(block, 0, io), input(block, 1, io)) > 0);
			case AND -> Boolean.toString(condition(block, 0, io) && condition(block, 1, io));
			case OR -> Boolean.toString(condition(block, 0, io) || condition(block, 1, io));
			case NOT -> Boolean.toString(!condition(block, 0, io));
			case JOIN -> clampValue(input(block, 0, io) + input(block, 1, io));
			case LENGTH -> Integer.toString(input(block, 0, io).length());
			case CONTAINS -> Boolean.toString(
				input(block, 0, io).toLowerCase(Locale.ROOT).contains(input(block, 1, io).toLowerCase(Locale.ROOT)));
			case PARSE_NUMBER -> format(extractNumber(input(block, 0, io)));
			case READ_REDSTONE -> {
				Port port = Port.parse(input(block, 0, io));
				yield port == null ? "0" : Integer.toString(io.readRedstone(port));
			}
			case HAS_REDSTONE -> {
				Port port = Port.parse(input(block, 0, io));
				yield Boolean.toString(port != null && io.readRedstone(port) > 0);
			}
			case READ_INPUT -> {
				int slotIndex = ComputerProgram.parseSlotIndex(input(block, 0, io));
				yield slotIndex < 0 ? "" : io.readInputSlot(slotIndex);
			}
			case TIMER -> format(Math.max(0L, io.gameTick() - timerStartTick) / 20.0);
			default -> throw new IllegalStateException("Expected a computer reporter: " + block.op());
		};
	}

	private boolean condition(Block block, int index, ComputerIO io) {
		return Boolean.parseBoolean(input(block, index, io));
	}

	private void putVariable(String name, String value) {
		if (name.isEmpty()) {
			finish(Status.EMPTY_VARIABLE);
		} else if (variables.size() == MAX_VARIABLES && !variables.containsKey(name)) {
			finish(Status.VARIABLE_LIMIT);
		} else if (!value.equals(variables.put(name, value))) {
			version++;
		}
	}

	private void setActiveBlock(int index) {
		if (activeBlockIndex != index) {
			activeBlockIndex = index;
			version++;
		}
	}

	private void finish(Status result) {
		frames.clear();
		waitTicks = 0;
		activeBlockIndex = -1;
		status = result;
		version++;
	}

	long version() { return version; }

	Snapshot snapshot(List<String> output, int[] redstoneIn, int[] redstoneOut, List<String> inputSlots) {
		return new Snapshot(status, activeBlockIndex, output,
			variables.entrySet().stream().map(entry -> new Variable(entry.getKey(), entry.getValue())).toList(),
			redstoneIn, redstoneOut, inputSlots);
	}

	static String clampValue(String value) {
		if (value == null)
			return "";
		if (value.length() <= MAX_VALUE_LENGTH)
			return value;
		int end = MAX_VALUE_LENGTH;
		if (Character.isHighSurrogate(value.charAt(end - 1)))
			end--;
		return value.substring(0, end);
	}

	private static String random(String first, String second) {
		double a = number(first);
		double b = number(second);
		double low = Math.min(a, b);
		double high = Math.max(a, b);
		if (low == high)
			return format(low);
		boolean ints = !first.contains(".") && !second.contains(".") && low == Math.rint(low) && high == Math.rint(high)
			&& low >= Integer.MIN_VALUE && high <= Integer.MAX_VALUE;
		if (ints) {
			long min = (long) low;
			long max = (long) high;
			return Long.toString(ThreadLocalRandom.current().nextLong(min, max + 1));
		}
		return format(ThreadLocalRandom.current().nextDouble(low, high));
	}

	/** Extracts the first signed number from text such as "64 RPM", "1,024 SU", "Lvl 4", or "-12.5%". */
	static double extractNumber(String text) {
		if (text == null || text.isEmpty())
			return 0;
		int len = text.length();
		for (int i = 0; i < len; i++) {
			char c = text.charAt(i);
			boolean sign = c == '-' || c == '+';
			int start = i;
			int pos = sign ? i + 1 : i;
			if (pos >= len)
				break;
			char first = text.charAt(pos);
			if (!Character.isDigit(first) && !(first == '.' && pos + 1 < len && Character.isDigit(text.charAt(pos + 1))))
				continue;

			StringBuilder sb = new StringBuilder();
			if (sign)
				sb.append(c);
			boolean seenDot = false;
			boolean seenDigit = false;
			while (pos < len) {
				char ch = text.charAt(pos);
				if (Character.isDigit(ch)) {
					sb.append(ch);
					seenDigit = true;
					pos++;
				} else if (ch == '.' && !seenDot && pos + 1 < len && Character.isDigit(text.charAt(pos + 1))) {
					sb.append('.');
					seenDot = true;
					pos++;
				} else if (ch == ',' && !seenDot && seenDigit && pos + 3 < len
					&& Character.isDigit(text.charAt(pos + 1))
					&& Character.isDigit(text.charAt(pos + 2))
					&& Character.isDigit(text.charAt(pos + 3))
					&& (pos + 4 >= len || !Character.isDigit(text.charAt(pos + 4)))) {
					pos++; // Skip thousands separator like 1,024.
				} else {
					break;
				}
			}
			if (seenDigit) {
				double parsed = parse(sb.toString());
				if (Double.isFinite(parsed))
					return parsed;
			}
			i = Math.max(start, pos - 1);
		}
		return 0;
	}

	private static double parse(String value) {
		try {
			return Double.parseDouble(value.strip());
		} catch (NumberFormatException ignored) {
			return Double.NaN;
		}
	}

	private static double number(String value) {
		double parsed = parse(value);
		return Double.isFinite(parsed) ? parsed : 0;
	}

	private static int compare(String first, String second) {
		double a = parse(first);
		double b = parse(second);
		if (Double.isFinite(a) && Double.isFinite(b))
			return a == b ? 0 : Double.compare(a, b);
		return first.compareToIgnoreCase(second);
	}

	private static String format(double number) {
		if (!Double.isFinite(number) || number == 0)
			return "0";
		String value = Double.toString(number);
		return value.endsWith(".0") ? value.substring(0, value.length() - 2) : value;
	}

	public enum Status { READY, RUNNING, FINISHED, STOPPED, EMPTY_VARIABLE, VARIABLE_LIMIT }

	public record Variable(String name, String value) {}

	public record Snapshot(
		Status status,
		int activeBlockIndex,
		List<String> output,
		List<Variable> variables,
		int[] redstoneIn,
		int[] redstoneOut,
		List<String> inputSlots
	) {
		static final Snapshot EMPTY = new Snapshot(
			Status.READY, -1, List.of(), List.of(),
			new int[ComputerIO.PORT_COUNT], new int[ComputerIO.PORT_COUNT],
			List.of("", "", "", "", "", "", "", "")
		);

		public Snapshot {
			output = List.copyOf(output);
			variables = List.copyOf(variables);
			redstoneIn = Arrays.copyOf(redstoneIn, ComputerIO.PORT_COUNT);
			redstoneOut = Arrays.copyOf(redstoneOut, ComputerIO.PORT_COUNT);
			List<String> normalized = new java.util.ArrayList<>(ComputerIO.INPUT_SLOTS);
			for (int i = 0; i < ComputerIO.INPUT_SLOTS; i++)
				normalized.add(i < inputSlots.size() && inputSlots.get(i) != null ? inputSlots.get(i) : "");
			inputSlots = List.copyOf(normalized);
		}
	}

	private static final class Frame {
		private final List<Block> commands;
		private final Block loop;
		private int remaining;
		private int next;

		private Frame(List<Block> commands, Block loop, int remaining) {
			this.commands = commands;
			this.loop = loop;
			this.remaining = remaining;
		}
	}
}
