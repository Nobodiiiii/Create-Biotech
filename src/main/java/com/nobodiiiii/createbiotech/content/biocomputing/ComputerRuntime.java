package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerIO.Channel;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Block;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Literal;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Op;

/** Deterministic tick-driven Scratch interpreter with bounded budgets and decoupled I/O. */
public final class ComputerRuntime {
	static final int MAX_VARIABLES = 24;
	static final int MAX_OUTPUT_LINES = ComputerIO.OUTPUT_LINES;
	static final int MAX_VALUE_LENGTH = 128;
	private static final int MAX_STEPS_PER_TICK = ComputerProgram.MAX_BLOCKS * 4;
	private static final int MAX_EVALS_PER_TICK = ComputerProgram.MAX_BLOCKS * 4;
	private static final int MAX_WAIT_TICKS = 20 * 60 * 60;
	private static final int MAX_PROCEDURE_FRAMES = 32;
	private static final int MAX_COLLECTIONS = 16;
	private static final int MAX_ENTRIES = 64;

	private final List<Script> scripts = new ArrayList<>();
	private final Map<String, List<Block>> procedures = new HashMap<>();
	private final Map<String, String> variables = new LinkedHashMap<>();
	private final Map<String, List<String>> lists = new HashMap<>();
	private final Map<String, Map<String, String>> maps = new HashMap<>();
	private final Map<Block, Long> cooldowns = new IdentityHashMap<>();
	private final Random random = new Random();
	private final int[] lastSignal = new int[Channel.VALUES.length];
	private final String[] lastDisplayInput = new String[ComputerIO.INPUT_SLOTS];
	private Map<Block, Integer> commandIndices = Map.of();
	private Status status = Status.READY;
	private int nextScript;
	private int activeBlockIndex = -1;
	private int rising;
	private int falling;
	private boolean lastOrder;
	private long timerStartTick;
	private int stepBudget;
	private int evalBudget;
	private long version;

	void start(ComputerProgram program, ComputerIO io) {
		scripts.clear();
		procedures.clear();
		variables.clear();
		lists.clear();
		maps.clear();
		cooldowns.clear();
		commandIndices = program.indexCommands();
		nextScript = 0;
		activeBlockIndex = -1;
		lastOrder = false;
		timerStartTick = io.gameTick();
		for (Channel channel : Channel.VALUES)
			lastSignal[channel.ordinal()] = io.readRedstone(channel);
		for (int i = 0; i < lastDisplayInput.length; i++)
			lastDisplayInput[i] = io.readInputSlot(i);

		List<Block> startup = new ArrayList<>();
		for (Block command : program.commands()) {
			if (command.op() == Op.DEFINE_PROCEDURE) {
				procedures.put(declarationName(command), command.bodies().getFirst());
			} else if (command.op().isDeclaration()) {
				List<Block> body = command.bodies().getFirst();
				if (!body.isEmpty())
					scripts.add(new Script(command, body));
			} else {
				startup.add(command);
			}
		}
		if (!startup.isEmpty()) {
			Script script = new Script(null, startup);
			script.begin(timerStartTick);
			scripts.addFirst(script);
		}
		status = scripts.isEmpty() ? Status.FINISHED : Status.RUNNING;
		version++;
	}

	void stop() {
		if (status == Status.RUNNING)
			finish(Status.STOPPED);
	}

	void showLockedLibrary() {
		finish(Status.LIBRARY_LOCKED);
	}

	boolean isRunning() {
		return status == Status.RUNNING;
	}

	void tick(ComputerIO io) {
		if (status != Status.RUNNING)
			return;
		long now = io.gameTick();
		detectEvents(io);
		stepBudget = MAX_STEPS_PER_TICK;
		evalBudget = MAX_EVALS_PER_TICK;
		for (int i = 0; i < scripts.size() && stepBudget > 0 && evalBudget > 0 && status == Status.RUNNING; i++) {
			Script script = scripts.get(nextScript);
			nextScript = (nextScript + 1) % scripts.size();
			if (script.frames.isEmpty() && script.pending)
				script.begin(now);
			tickScript(script, io, now);
		}
		if (status == Status.RUNNING && scripts.stream().noneMatch(script -> script.event != null || !script.frames.isEmpty()))
			finish(scripts.getFirst().stopped ? Status.STOPPED : Status.FINISHED);
	}

	private void detectEvents(ComputerIO io) {
		rising = 0;
		falling = 0;
		int signalChanged = 0;
		int inputChanged = 0;
		for (Channel channel : Channel.VALUES) {
			int index = channel.ordinal();
			int value = io.readRedstone(channel);
			int previous = lastSignal[index];
			if (value != previous) {
				lastSignal[index] = value;
				signalChanged |= 1 << index;
				if (previous == 0)
					rising |= 1 << index;
				if (value == 0)
					falling |= 1 << index;
			}
		}
		for (int i = 0; i < lastDisplayInput.length; i++) {
			String value = io.readInputSlot(i);
			if (!value.equals(lastDisplayInput[i])) {
				lastDisplayInput[i] = value;
				inputChanged |= 1 << i;
			}
		}
		for (Script script : scripts) {
			Block event = script.event;
			if (event == null)
				continue;
			script.pending |= switch (event.op()) {
				case WHEN_SIGNAL_CHANGED -> (signalChanged & (1 << channel(event, io).ordinal())) != 0;
				case WHEN_INPUT_CHANGED -> (inputChanged & (1 << slot(event, io))) != 0;
				case ON_BROADCAST -> script.broadcast;
				default -> false;
			};
			script.broadcast = false;
		}
	}

	private void tickScript(Script script, ComputerIO io, long now) {
		boolean executedThisTick = false;
		Deque<Frame> frames = script.frames;
		for (int steps = 0; steps < ComputerProgram.MAX_BLOCKS * 2 && stepBudget > 0 && evalBudget > 0
			&& !frames.isEmpty() && now >= script.wakeTick && status == Status.RUNNING; steps++, stepBudget--) {
			Frame frame = frames.getFirst();
			if (frame.awaitingNextIteration) {
				frame.awaitingNextIteration = false;
				if (frame.loop != null && frame.loop.op() == Op.REPEAT_UNTIL && condition(frame.loop, 0, io)) {
					frames.pop();
					continue;
				}
				frame.next = 0;
				frame.iterationStartTick = now;
			}
			if (frame.next < frame.commands.size()) {
				Block block = frame.commands.get(frame.next);
				setActiveBlock(commandIndices.getOrDefault(block, -1));
				executedThisTick = true;
				if (block.op() == Op.WAIT_UNTIL || block.op() == Op.WAIT_STABLE) {
					if (waitSatisfied(block, frame, io, now))
						frame.next++;
					else
						script.wakeTick = now + 1;
				} else {
					frame.next++;
					execute(block, script, io, now);
				}
			} else if (frame.loop != null && shouldContinueLoop(frame, io)) {
				long elapsed = Math.max(0L, now - frame.iterationStartTick);
				int interval = loopInterval(frame.loop, io);
				int remainingWait = (int) Math.max(0L, interval - elapsed);
				if (remainingWait == 0 && (executedThisTick || frame.commands.isEmpty()))
					remainingWait = 1;
				if (remainingWait > 0) {
					frame.awaitingNextIteration = true;
					setActiveBlock(commandIndices.getOrDefault(frame.loop, -1));
					script.wakeTick = now + remainingWait;
				} else {
					frame.next = 0;
					frame.iterationStartTick = now;
				}
			} else {
				frames.pop();
			}
		}
	}

	private boolean waitSatisfied(Block block, Frame frame, ComputerIO io, long now) {
		if (!condition(block, 0, io)) {
			frame.stableSince = -1;
			return false;
		}
		if (block.op() == Op.WAIT_UNTIL)
			return true;
		if (frame.stableSince < 0 || frame.lastStableCheck != now - 1)
			frame.stableSince = now;
		frame.lastStableCheck = now;
		if (now - frame.stableSince < seconds(number(input(block, 1, io))))
			return false;
		frame.stableSince = -1;
		return true;
	}

	private boolean shouldContinueLoop(Frame frame, ComputerIO io) {
		return switch (frame.loop.op()) {
			case REPEAT -> --frame.remaining > 0;
			case REPEAT_UNTIL -> !condition(frame.loop, 0, io);
			case FOREVER -> true;
			default -> false;
		};
	}

	private int loopInterval(Block loop, ComputerIO io) {
		int slot = loop.op() == Op.FOREVER ? 0 : 1;
		return rounded(loop, slot, io, 1, MAX_WAIT_TICKS);
	}

	private void execute(Block block, Script script, ComputerIO io, long now) {
		Deque<Frame> frames = script.frames;
		switch (block.op()) {
			case WAIT -> script.wakeTick = now + seconds(number(input(block, 0, io)));
			case WAIT_TICKS -> script.wakeTick = now + rounded(block, 0, io, 1, MAX_WAIT_TICKS);
			case RANDOM_WAIT -> script.wakeTick = now + seconds(randomBetween(number(input(block, 0, io)), number(input(block, 1, io))));
			case STOP -> {
				frames.clear();
				script.stopped = true;
			}
			case CALL_PROCEDURE -> {
				List<Block> body = procedures.get(input(block, 0, io).strip());
				if (body != null && !body.isEmpty()) {
					if (frames.size() >= MAX_PROCEDURE_FRAMES)
						finish(Status.PROCEDURE_LIMIT);
					else
						frames.push(new Frame(body, null, 1, now));
				}
			}
			case BROADCAST -> {
				String message = input(block, 0, io).strip();
				for (Script listener : scripts)
					if (listener.event != null && listener.event.op() == Op.ON_BROADCAST
						&& declarationName(listener.event).equals(message))
						listener.broadcast = true;
			}
			case IF, IF_ELSE -> {
				int body = condition(block, 0, io) ? 0 : 1;
				if (body < block.bodies().size())
					frames.push(new Frame(block.bodies().get(body), null, 1, now));
			}
			case COOLDOWN -> {
				if (now >= cooldowns.getOrDefault(block, 0L)) {
					cooldowns.put(block, now + seconds(number(input(block, 0, io))));
					frames.push(new Frame(block.bodies().getFirst(), null, 1, now));
				}
			}
			case REPEAT -> {
				int count = Math.clamp(Math.round(number(input(block, 0, io))), 0, Integer.MAX_VALUE);
				if (count > 0 && !block.bodies().getFirst().isEmpty())
					frames.push(new Frame(block.bodies().getFirst(), block, count, now));
			}
			case FOREVER, REPEAT_UNTIL -> {
				if (block.op() == Op.FOREVER || !condition(block, 0, io))
					frames.push(new Frame(block.bodies().getFirst(), block, 0, now));
			}
			case SET, CHANGE -> {
				String name = input(block, 0, io).strip();
				String value = clampValue(input(block, 1, io));
				if (block.op() == Op.CHANGE)
					value = format(number(variables.getOrDefault(name, "0")) + number(value));
				putVariable(name, value);
			}
			case TOGGLE -> {
				String name = input(block, 0, io).strip();
				putVariable(name, Boolean.toString(!Boolean.parseBoolean(variables.get(name))));
			}
			case LATCH -> putVariable(input(block, 0, io).strip(), Boolean.toString(condition(block, 1, io)));
			case SEED_RANDOM -> random.setSeed((long) number(input(block, 0, io)));
			case SPLIT_TEXT -> {
				List<String> list = obtain(lists, input(block, 2, io).strip(), ArrayList::new);
				if (list != null) {
					List<String> split = splitText(input(block, 0, io), input(block, 1, io));
					list.clear();
					list.addAll(split.subList(0, Math.min(split.size(), MAX_ENTRIES)));
				}
			}
			case LIST_ADD -> {
				List<String> list = obtain(lists, input(block, 0, io).strip(), ArrayList::new);
				if (list != null && list.size() < MAX_ENTRIES)
					list.add(clampValue(input(block, 1, io)));
			}
			case LIST_REMOVE, LIST_SET -> {
				List<String> list = list(block, io);
				int index = listIndex(block, io);
				if (index >= 0 && index < list.size()) {
					if (block.op() == Op.LIST_REMOVE)
						list.remove(index);
					else
						list.set(index, clampValue(input(block, 2, io)));
				}
			}
			case MAP_SET -> {
				Map<String, String> map = obtain(maps, input(block, 0, io).strip(), LinkedHashMap::new);
				String key = clampValue(input(block, 1, io));
				if (map != null && (map.size() < MAX_ENTRIES || map.containsKey(key)))
					map.put(key, clampValue(input(block, 2, io)));
			}
			case MAP_REMOVE -> {
				Map<String, String> map = maps.get(input(block, 0, io).strip());
				if (map != null)
					map.remove(input(block, 1, io));
			}
			case BIND_FREQUENCY, TUNE_FREQUENCY -> io.tuneRedstone(channel(block, io), input(block, 1, io), input(block, 2, io));
			case SEND_REDSTONE -> io.writeRedstone(channel(block, io), condition(block, 1, io) ? 15 : 0, 0);
			case SET_REDSTONE -> io.writeRedstone(channel(block, io), rounded(block, 1, io, 0, 15), 0);
			case PULSE_REDSTONE -> io.writeRedstone(channel(block, io), rounded(block, 1, io, 0, 15),
				rounded(block, 2, io, 1, MAX_WAIT_TICKS));
			case SET_LINE -> io.setOutputLine(slot(block, io), clampValue(input(block, 1, io)));
			case DISPLAY -> io.appendOutput(clampValue(input(block, 0, io)));
			case CLEAR_OUTPUT -> io.clearOutput();
			case RESET_TIMER -> timerStartTick = now;
			case UPDATE_DISPLAY -> io.updateDisplay();
			case ORDER_ITEM -> {
				String item = input(block, 0, io);
				long count = Math.round(number(input(block, 1, io)));
				lastOrder = !item.contains(",") && io.orderStock(input(block, 2, io), item + "=" + count);
			}
			case ORDER_BATCH -> lastOrder = io.orderStock(input(block, 1, io), input(block, 0, io));
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
			case CEIL -> format(Math.ceil(number(input(block, 0, io))));
			case ABS -> format(Math.abs(number(input(block, 0, io))));
			case MAP_RANGE -> {
				double value = number(input(block, 0, io));
				double fromLow = number(input(block, 1, io));
				double fromHigh = number(input(block, 2, io));
				double toLow = number(input(block, 3, io));
				double toHigh = number(input(block, 4, io));
				yield format(fromLow == fromHigh ? toLow : lerp(toLow, toHigh, (value - fromLow) / (fromHigh - fromLow)));
			}
			case CLAMP -> {
				double value = number(input(block, 0, io));
				double a = number(input(block, 1, io));
				double b = number(input(block, 2, io));
				yield format(Math.clamp(value, Math.min(a, b), Math.max(a, b)));
			}
			case MIN -> format(Math.min(number(input(block, 0, io)), number(input(block, 1, io))));
			case MAX -> format(Math.max(number(input(block, 0, io)), number(input(block, 1, io))));
			case POWER -> format(Math.pow(number(input(block, 0, io)), number(input(block, 1, io))));
			case SQRT -> format(Math.sqrt(number(input(block, 0, io))));
			case SIN -> format(Math.sin(Math.toRadians(number(input(block, 0, io)))));
			case COS -> format(Math.cos(Math.toRadians(number(input(block, 0, io)))));
			case TAN -> format(Math.tan(Math.toRadians(number(input(block, 0, io)))));
			case RANDOM -> random(input(block, 0, io), input(block, 1, io));
			case CHANCE -> Boolean.toString(random.nextDouble() * 100 < number(input(block, 0, io)));
			case PICK_TEXT -> pick(splitText(input(block, 0, io), input(block, 1, io)));
			case PICK_LIST -> {
				String source = input(block, 0, io);
				List<String> entries = lists.get(source.strip());
				yield pick(entries == null ? splitText(source, ",") : entries);
			}
			case EQUALS -> Boolean.toString(compare(input(block, 0, io), input(block, 1, io)) == 0);
			case LESS -> Boolean.toString(compare(input(block, 0, io), input(block, 1, io)) < 0);
			case GREATER -> Boolean.toString(compare(input(block, 0, io), input(block, 1, io)) > 0);
			case AND -> Boolean.toString(condition(block, 0, io) && condition(block, 1, io));
			case OR -> Boolean.toString(condition(block, 0, io) || condition(block, 1, io));
			case XOR -> Boolean.toString(condition(block, 0, io) ^ condition(block, 1, io));
			case NOT -> Boolean.toString(!condition(block, 0, io));
			case PACK_BITS -> {
				int mask = 0;
				for (int i = 0; i < 4; i++)
					if (condition(block, i, io))
						mask |= 1 << i;
				yield Integer.toString(mask);
			}
			case UNPACK_BIT -> {
				int bits = rounded(block, 0, io, 0, 15);
				int bit = rounded(block, 1, io, 1, 4) - 1;
				yield Boolean.toString((bits & (1 << bit)) != 0);
			}
			case JOIN -> clampValue(input(block, 0, io) + input(block, 1, io));
			case LENGTH -> {
				String text = input(block, 0, io);
				yield Integer.toString(text.codePointCount(0, text.length()));
			}
			case CONTAINS -> Boolean.toString(
				input(block, 0, io).toLowerCase(Locale.ROOT).contains(input(block, 1, io).toLowerCase(Locale.ROOT)));
			case PARSE_NUMBER -> format(extractNumber(input(block, 0, io)));
			case SUBSTRING -> {
				String text = input(block, 0, io);
				int length = text.codePointCount(0, text.length());
				int first = rounded(block, 1, io, 1, length + 1) - 1;
				int count = rounded(block, 2, io, 0, length - first);
				int start = text.offsetByCodePoints(0, first);
				yield text.substring(start, text.offsetByCodePoints(start, count));
			}
			case REPLACE_TEXT -> {
				String text = input(block, 0, io);
				String target = input(block, 1, io);
				yield target.isEmpty() ? text : clampValue(text.replace(target, input(block, 2, io)));
			}
			case FORMAT_NUMBER -> {
				double value = number(input(block, 0, io));
				int decimals = rounded(block, 1, io, 0, 8);
				yield clampValue(String.format(Locale.ROOT, "%." + decimals + "f", value));
			}
			case PROGRESS_BAR -> {
				double value = number(input(block, 0, io));
				double total = number(input(block, 1, io));
				int width = rounded(block, 2, io, 1, 32);
				int filled = total <= 0 ? 0 : Math.clamp(Math.round(value / total * width), 0, width);
				yield "█".repeat(filled) + "░".repeat(width - filled);
			}
			case LIST_GET -> {
				List<String> list = list(block, io);
				int index = listIndex(block, io);
				yield index < 0 || index >= list.size() ? "" : list.get(index);
			}
			case LIST_LENGTH -> Integer.toString(list(block, io).size());
			case LIST_JOIN -> clampValue(String.join(input(block, 1, io), list(block, io)));
			case MAP_GET -> map(block, io).getOrDefault(input(block, 1, io), "");
			case MAP_HAS -> Boolean.toString(map(block, io).containsKey(input(block, 1, io)));
			case MAP_LENGTH -> Integer.toString(map(block, io).size());
			case READ_REDSTONE -> Integer.toString(io.readRedstone(channel(block, io)));
			case HAS_REDSTONE -> Boolean.toString(io.readRedstone(channel(block, io)) > 0);
			case RISING_EDGE -> Boolean.toString((rising & (1 << channel(block, io).ordinal())) != 0);
			case FALLING_EDGE -> Boolean.toString((falling & (1 << channel(block, io).ordinal())) != 0);
			case READ_INPUT -> io.readInputSlot(slot(block, io));
			case TIMER -> format(Math.max(0L, io.gameTick() - timerStartTick) / 20.0);
			case STOCK_COUNT -> Long.toString(io.stockCount(input(block, 0, io)));
			case HAS_STOCK -> Boolean.toString(io.stockCount(input(block, 0, io)) >= number(input(block, 1, io)));
			case STOCK_TOTAL -> Long.toString(io.stockTotal());
			case STOCK_TYPES -> Integer.toString(io.stockTypes());
			case LAST_ORDER -> Boolean.toString(lastOrder);
			default -> throw new IllegalStateException("Expected a computer reporter: " + block.op());
		};
	}

	private boolean condition(Block block, int index, ComputerIO io) {
		return Boolean.parseBoolean(input(block, index, io));
	}

	private Channel channel(Block block, ComputerIO io) {
		return Channel.parse(input(block, 0, io));
	}

	private int slot(Block block, ComputerIO io) {
		return ComputerProgram.parseSlotIndex(input(block, 0, io));
	}

	private int rounded(Block block, int index, ComputerIO io, int min, int max) {
		return Math.clamp(Math.round(number(input(block, index, io))), min, max);
	}

	private static String declarationName(Block block) {
		return ((Literal) block.inputs().getFirst()).text().strip();
	}

	private static int seconds(double value) {
		return (int) Math.clamp(Math.ceil(value * 20.0), 1, MAX_WAIT_TICKS);
	}

	private static double lerp(double a, double b, double share) {
		return a * (1 - share) + b * share;
	}

	private static List<String> splitText(String text, String delimiter) {
		return delimiter.isEmpty() ? List.of(text) : List.of(text.split(Pattern.quote(delimiter), -1));
	}

	@Nullable
	private static <T> T obtain(Map<String, T> collections, String name, Supplier<T> create) {
		T existing = collections.get(name);
		if (existing != null)
			return existing;
		if (name.isEmpty() || collections.size() >= MAX_COLLECTIONS)
			return null;
		T created = create.get();
		collections.put(name, created);
		return created;
	}

	private List<String> list(Block block, ComputerIO io) {
		return lists.getOrDefault(input(block, 0, io).strip(), List.of());
	}

	private int listIndex(Block block, ComputerIO io) {
		return rounded(block, 1, io, 0, MAX_ENTRIES + 1) - 1;
	}

	private Map<String, String> map(Block block, ComputerIO io) {
		return maps.getOrDefault(input(block, 0, io).strip(), Map.of());
	}

	private String pick(List<String> values) {
		return values.isEmpty() ? "" : values.get(random.nextInt(values.size()));
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
		scripts.clear();
		procedures.clear();
		commandIndices = Map.of();
		activeBlockIndex = -1;
		status = result;
		version++;
	}

	long version() { return version; }

	Snapshot snapshot(List<String> output, List<ChannelState> channels, List<String> inputSlots, boolean stockLinkBound) {
		return new Snapshot(status, activeBlockIndex, output,
			variables.entrySet().stream().map(entry -> new Variable(entry.getKey(), entry.getValue())).toList(),
			channels, inputSlots, stockLinkBound);
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

	private String random(String first, String second) {
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
			return Long.toString(random.nextLong(min, max + 1));
		}
		return format(randomBetween(low, high));
	}

	private double randomBetween(double a, double b) {
		return a == b ? a : lerp(Math.min(a, b), Math.max(a, b), random.nextDouble());
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

	public enum Status { READY, RUNNING, FINISHED, STOPPED, EMPTY_VARIABLE, VARIABLE_LIMIT, LIBRARY_LOCKED, PROCEDURE_LIMIT }

	public record Variable(String name, String value) {}

	public record ChannelState(String first, String second, int input, int output) {
		public boolean isBound() { return !first.isEmpty() || !second.isEmpty(); }
	}

	public record Snapshot(
		Status status,
		int activeBlockIndex,
		List<String> output,
		List<Variable> variables,
		List<ChannelState> channels,
		List<String> inputSlots,
		boolean stockLinkBound
	) {
		static final Snapshot EMPTY = new Snapshot(
			Status.READY, -1, List.of(), List.of(),
			Collections.nCopies(Channel.VALUES.length, new ChannelState("", "", 0, 0)),
			Collections.nCopies(ComputerIO.INPUT_SLOTS, ""), false
		);

		public Snapshot {
			output = List.copyOf(output);
			variables = List.copyOf(variables);
			channels = List.copyOf(channels);
			inputSlots = List.copyOf(inputSlots);
		}
	}

	private static final class Script {
		@Nullable
		private final Block event;
		private final List<Block> commands;
		private final Deque<Frame> frames = new ArrayDeque<>();
		private boolean broadcast, pending, stopped;
		private long wakeTick;

		private Script(@Nullable Block event, List<Block> commands) {
			this.event = event;
			this.commands = commands;
		}

		private void begin(long now) {
			frames.push(new Frame(commands, null, 1, now));
			pending = false;
			stopped = false;
			wakeTick = now;
		}
	}

	private static final class Frame {
		private final List<Block> commands;
		private final Block loop;
		private int remaining;
		private int next;
		private long iterationStartTick;
		private long stableSince = -1;
		private long lastStableCheck;
		private boolean awaitingNextIteration;

		private Frame(List<Block> commands, Block loop, int remaining, long iterationStartTick) {
			this.commands = commands;
			this.loop = loop;
			this.remaining = remaining;
			this.iterationStartTick = iterationStartTick;
		}
	}
}
