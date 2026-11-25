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
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerIO.Channel;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerIO.FrequencyPair;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerIO.OrderEntry;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerIO.RedstoneFrequency;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Block;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.DataType;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.EmptyInput;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Input;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Literal;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Op;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerValue.ListValue;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerValue.MapValue;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerValue.Missing;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerValue.Scalar;

import net.minecraft.network.chat.Component;

/** Deterministic tick-driven Scratch interpreter with bounded budgets and decoupled I/O. */
public final class ComputerRuntime {
	static final int MAX_VARIABLES = 24;
	static final int MAX_OUTPUT_LINES = ComputerIO.OUTPUT_LINES;
	static final int MAX_VALUE_LENGTH = 128;
	private static final int MAX_STEPS_PER_TICK = ComputerProgram.MAX_BLOCKS * 4;
	private static final int MAX_EVALS_PER_TICK = ComputerProgram.MAX_BLOCKS * 4;
	private static final int MAX_WAIT_TICKS = 20 * 60 * 60;
	private static final int MAX_PROCEDURE_FRAMES = 32;
	static final int MAX_ENTRIES = 64;
	private static final ListValue EMPTY_LIST = new ListValue(List.of());
	private static final MapValue EMPTY_MAP = new MapValue(Map.of());

	private final List<Script> scripts = new ArrayList<>();
	private final Map<String, List<Block>> procedures = new HashMap<>();
	private final Map<String, ValueCell> variables = new LinkedHashMap<>();
	private final Map<Block, ValueCell> inlineCollections = new IdentityHashMap<>();
	private final Map<Block, Long> cooldowns = new IdentityHashMap<>();
	private final Random random = new Random();
	private final Map<Block, FrequencyPair> watchedFrequencies = new IdentityHashMap<>();
	private final Map<RedstoneFrequency, Integer> previousSignals = new HashMap<>();
	private final Map<RedstoneFrequency, Integer> sampledSignals = new HashMap<>();
	private final String[] lastDisplayInput = new String[ComputerIO.INPUT_SLOTS];
	private Map<Block, Integer> commandIndices = Map.of();
	private Status status = Status.READY;
	private int nextScript;
	private int activeBlockIndex = -1;
	@Nullable private ComputerProgram executionProgram;
	@Nullable private Diagnostic diagnostic;
	private boolean lastOrder;
	private long timerStartTick;
	private int stepBudget;
	private int evalBudget;
	private long version;

	void start(ComputerProgram program, ComputerIO io) {
		scripts.clear();
		procedures.clear();
		variables.clear();
		inlineCollections.clear();
		cooldowns.clear();
		commandIndices = program.indexCommands();
		executionProgram = program;
		diagnostic = null;
		nextScript = 0;
		activeBlockIndex = -1;
		lastOrder = false;
		watchedFrequencies.clear();
		previousSignals.clear();
		sampledSignals.clear();
		evalBudget = MAX_EVALS_PER_TICK;
		timerStartTick = io.gameTick();
		for (Channel channel : Channel.VALUES)
			sampledSignals.put(channel, io.readRedstone(channel));
		for (int i = 0; i < lastDisplayInput.length; i++)
			lastDisplayInput[i] = io.readInputSlot(i);

		List<Block> startup = new ArrayList<>();
		for (Block command : program.commands()) {
			if (!command.op().isDeclaration()) {
				startup.add(command);
				continue;
			}
			try {
				List<Block> body = command.bodies().getFirst();
				if (command.op() == Op.DEFINE_PROCEDURE) {
					procedures.put(input(command, 0, io).strip(), body);
				} else if (!body.isEmpty()) {
					Event event = switch (command.op()) {
						case WHEN_SIGNAL_CHANGED, WHEN_INPUT_CHANGED -> new Event(command, "");
						case ON_BROADCAST -> new Event(command, input(command, 0, io).strip());
						default -> throw new IllegalStateException("Expected an event declaration");
					};
					scripts.add(new Script(event, body));
				}
			} catch (InvalidInput invalid) {
				report(command, invalid.problem);
			} catch (ComputerValue.LimitExceeded ignored) {
				finish(Status.COLLECTION_LIMIT);
				return;
			}
		}
		if (!startup.isEmpty()) {
			Script script = new Script(null, startup);
			script.begin(timerStartTick);
			scripts.addFirst(script);
		}
		if (scripts.isEmpty()) {
			finish(Status.FINISHED);
		} else {
			status = Status.RUNNING;
			version++;
		}
	}

	void stop() {
		if (status == Status.RUNNING)
			finish(Status.STOPPED);
	}

	void showLockedLibrary() {
		diagnostic = null;
		finish(Status.LIBRARY_LOCKED);
	}

	@Nullable ComputerProgram diagnosedProgram() {
		return diagnostic == null ? null : executionProgram;
	}

	boolean isRunning() {
		return status == Status.RUNNING;
	}

	void tick(ComputerIO io) {
		if (status != Status.RUNNING)
			return;
		long now = io.gameTick();
		stepBudget = MAX_STEPS_PER_TICK;
		evalBudget = MAX_EVALS_PER_TICK;
		try {
			detectEvents(io);
		} catch (ComputerValue.LimitExceeded ignored) {
			finish(Status.COLLECTION_LIMIT);
			return;
		}
		for (int i = 0; i < scripts.size() && stepBudget > 0 && evalBudget > 0 && status == Status.RUNNING; i++) {
			Script script = scripts.get(nextScript);
			nextScript = (nextScript + 1) % scripts.size();
			if (script.frames.isEmpty() && script.pending)
				script.begin(now);
			try {
				tickScript(script, io, now);
			} catch (ComputerValue.LimitExceeded ignored) {
				finish(Status.COLLECTION_LIMIT);
				return;
			}
		}
		if (status == Status.RUNNING && scripts.stream().noneMatch(script -> script.event != null || !script.frames.isEmpty()))
			finish(scripts.getFirst().stopped ? Status.STOPPED : Status.FINISHED);
	}

	private void detectEvents(ComputerIO io) {
		previousSignals.clear();
		previousSignals.putAll(sampledSignals);
		sampledSignals.clear();
		for (Channel channel : Channel.VALUES)
			sampledSignals.put(channel, io.readRedstone(channel));
		for (FrequencyPair frequency : watchedFrequencies.values())
			sampledSignals.computeIfAbsent(frequency, io::readRedstone);

		int inputChanged = 0;
		for (int i = 0; i < lastDisplayInput.length; i++) {
			String value = io.readInputSlot(i);
			if (!value.equals(lastDisplayInput[i])) {
				lastDisplayInput[i] = value;
				inputChanged |= 1 << i;
			}
		}
		for (Script script : scripts) {
			Event event = script.event;
			if (event == null)
				continue;
			try {
				script.pending |= switch (event.op()) {
					case WHEN_SIGNAL_CHANGED -> signalChange(event.declaration(), io).changed();
					case WHEN_INPUT_CHANGED -> inputChanged(event.declaration(), io, inputChanged);
					case ON_BROADCAST -> script.broadcast;
					default -> false;
				};
			} catch (InvalidInput invalid) {
				watchedFrequencies.remove(event.declaration());
				report(event.declaration(), invalid.problem);
			}
			script.broadcast = false;
		}
	}

	private boolean inputChanged(Block block, ComputerIO io, int changedSlots) {
		ComputerValue slot = value(block, 0, io);
		return slot != Missing.INSTANCE && (changedSlots & (1 << index(slot, ComputerIO.INPUT_SLOTS, Problem.INPUT_SLOT))) != 0;
	}

	private void tickScript(Script script, ComputerIO io, long now) {
		boolean executedThisTick = false;
		Deque<Frame> frames = script.frames;
		for (int steps = 0; steps < ComputerProgram.MAX_BLOCKS * 2 && stepBudget > 0 && evalBudget > 0
			&& !frames.isEmpty() && now >= script.wakeTick && status == Status.RUNNING; steps++, stepBudget--) {
			Frame frame = frames.getFirst();
			try {
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
					executeStep(block, frame, script, io, now);
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
			} catch (InvalidInput invalid) {
				report(frame.loop, invalid.problem);
				frames.pop();
			}
		}
	}

	private void executeStep(Block block, Frame frame, Script script, ComputerIO io, long now) {
		boolean waiting = block.op() == Op.WAIT_UNTIL || block.op() == Op.WAIT_STABLE;
		try {
			if (waiting && !waitSatisfied(block, frame, io, now)) {
				script.wakeTick = now + 1;
				return;
			}
			if (!waiting)
				execute(block, script, io, now);
		} catch (InvalidInput invalid) {
			report(block, invalid.problem);
			frame.stableSince = -1;
		}
		frame.next++;
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
		if (now - frame.stableSince < seconds(number(block, 1, io)))
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
			case WAIT -> script.wakeTick = now + seconds(number(block, 0, io));
			case WAIT_TICKS -> script.wakeTick = now + rounded(block, 0, io, 1, MAX_WAIT_TICKS);
			case RANDOM_WAIT -> script.wakeTick = now + seconds(randomBetween(number(block, 0, io), number(block, 1, io)));
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
						&& listener.event.message().equals(message))
						listener.broadcast = true;
			}
			case IF, IF_ELSE -> {
				int body = condition(block, 0, io) ? 0 : 1;
				if (body < block.bodies().size())
					frames.push(new Frame(block.bodies().get(body), null, 1, now));
			}
			case COOLDOWN -> {
				if (now >= cooldowns.getOrDefault(block, 0L)) {
					cooldowns.put(block, now + seconds(number(block, 0, io)));
					frames.push(new Frame(block.bodies().getFirst(), null, 1, now));
				}
			}
			case REPEAT -> {
				int count = Math.clamp(Math.round(number(block, 0, io)), 0, Integer.MAX_VALUE);
				if (count > 0 && !block.bodies().getFirst().isEmpty())
					frames.push(new Frame(block.bodies().getFirst(), block, count, now));
			}
			case FOREVER, REPEAT_UNTIL -> {
				if (block.op() == Op.FOREVER || !condition(block, 0, io))
					frames.push(new Frame(block.bodies().getFirst(), block, 0, now));
			}
			case SET -> putVariable(destinationName(block, io), value(block, 1, io));
			case CHANGE -> {
				String name = destinationName(block, io);
				putVariable(name, numeric(variableValue(name).asNumber() + number(block, 1, io)));
			}
			case TOGGLE -> {
				String name = destinationName(block, io);
				putVariable(name, bool(!variableValue(name).asBoolean()));
			}
			case LATCH -> putVariable(destinationName(block, io), bool(condition(block, 1, io)));
			case SEED_RANDOM -> random.setSeed((long) number(block, 0, io));
			case SPLIT_TEXT -> {
				List<ComputerValue> entries = splitText(input(block, 0, io), input(block, 1, io)).stream()
					.limit(MAX_ENTRIES).<ComputerValue>map(Scalar::new).toList();
				ValueCell target = collectionTarget(block, 2, Op.LIST, true, io);
				if (target != null)
					setValue(target, new ListValue(entries));
			}
			case LIST_ADD -> {
				ComputerValue entry = value(block, 1, io);
				ValueCell target = collectionTarget(block, 0, Op.LIST, true, io);
				if (target != null) {
					List<ComputerValue> entries = target.value.asList();
					if (entries.size() < MAX_ENTRIES) {
						List<ComputerValue> updated = new ArrayList<>(entries);
						updated.add(entry);
						setValue(target, new ListValue(updated));
					}
				}
			}
			case LIST_REMOVE, LIST_SET -> {
				ValueCell target = collectionTarget(block, 0, Op.LIST, false, io);
				int index = listIndex(block, io);
				if (target == null)
					break;
				List<ComputerValue> entries = target.value.asList();
				if (index >= 0 && index < entries.size()) {
					List<ComputerValue> updated = new ArrayList<>(entries);
					if (block.op() == Op.LIST_REMOVE)
						updated.remove(index);
					else
						updated.set(index, value(block, 2, io));
					setValue(target, new ListValue(updated));
				}
			}
			case MAP_SET -> {
				Scalar key = value(block, 1, io).asScalar();
				ComputerValue entry = value(block, 2, io);
				ValueCell target = collectionTarget(block, 0, Op.MAP, true, io);
				if (target != null) {
					Map<String, MapValue.Entry> entries = target.value.asMap();
					if (entries.size() < MAX_ENTRIES || entries.containsKey(key.text())) {
						Map<String, MapValue.Entry> updated = new LinkedHashMap<>(entries);
						updated.put(key.text(), new MapValue.Entry(key, entry));
						setValue(target, new MapValue(updated));
					}
				}
			}
			case MAP_REMOVE -> {
				ValueCell target = collectionTarget(block, 0, Op.MAP, false, io);
				if (target != null) {
					String key = input(block, 1, io);
					Map<String, MapValue.Entry> entries = target.value.asMap();
					if (entries.containsKey(key)) {
						Map<String, MapValue.Entry> updated = new LinkedHashMap<>(entries);
						updated.remove(key);
						setValue(target, new MapValue(updated));
					}
				}
			}
			case BIND_FREQUENCY, TUNE_FREQUENCY -> tuneFrequency(block, io);
			case SEND_REDSTONE -> writeRedstone(redstoneFrequency(block, io), condition(block, 1, io) ? 15 : 0, 0, io);
			case SET_REDSTONE -> writeRedstone(redstoneFrequency(block, io), power(block, io), 0, io);
			case PULSE_REDSTONE -> writeRedstone(redstoneFrequency(block, io), power(block, io),
				Math.clamp(Math.round(requireNumber(value(block, 2, io), Problem.DURATION)), 1, MAX_WAIT_TICKS), io);
			case SET_LINE -> io.setOutputLine(index(block, io, ComputerIO.OUTPUT_LINES, Problem.OUTPUT_LINE),
				value(block, 1, io).asDisplayLine());
			case DISPLAY -> io.appendOutput(value(block, 0, io).asDisplayLine());
			case DISPLAY_LINES, DISPLAY_ITEM_LIST -> value(block, 0, io)
				.asDisplayLines(MAX_OUTPUT_LINES, block.op() == Op.DISPLAY_ITEM_LIST)
				.forEach(io::appendOutput);
			case CLEAR_OUTPUT -> io.clearOutput();
			case RESET_TIMER -> timerStartTick = now;
			case UPDATE_DISPLAY -> io.updateDisplay();
			case ORDER_ITEM, ORDER_BATCH -> {
				lastOrder = false;
				lastOrder = order(block, io);
			}
			default -> throw new IllegalStateException("Expected a computer command: " + block.op());
		}
	}

	private String input(Block block, int index, ComputerIO io) {
		return value(block, index, io).asText();
	}

	private ComputerValue value(Block block, int index, ComputerIO io) {
		return switch (block.inputs().get(index)) {
			case Literal literal -> new Scalar(literal.text(), literal.type());
			case Block reporter -> evaluate(reporter, io);
			case EmptyInput ignored -> throw new IllegalStateException("Unfilled computer input in " + block.op());
		};
	}

	private ComputerValue evaluate(Block block, ComputerIO io) {
		evalBudget--;
		return switch (block.op()) {
			case ADD -> add(value(block, 0, io), value(block, 1, io));
			case SUBTRACT -> numeric(number(block, 0, io) - number(block, 1, io));
			case MULTIPLY -> numeric(number(block, 0, io) * number(block, 1, io));
			case DIVIDE -> numeric(number(block, 0, io) / number(block, 1, io));
			case MOD -> {
				double a = number(block, 0, io);
				double b = number(block, 1, io);
				yield numeric(b == 0 ? 0 : a - Math.floor(a / b) * b);
			}
			case ROUND -> numeric(Math.floor(number(block, 0, io) + 0.5));
			case FLOOR -> numeric(Math.floor(number(block, 0, io)));
			case CEIL -> numeric(Math.ceil(number(block, 0, io)));
			case ABS -> numeric(Math.abs(number(block, 0, io)));
			case MAP_RANGE -> {
				double value = number(block, 0, io);
				double fromLow = number(block, 1, io);
				double fromHigh = number(block, 2, io);
				double toLow = number(block, 3, io);
				double toHigh = number(block, 4, io);
				yield numeric(fromLow == fromHigh ? toLow : lerp(toLow, toHigh, (value - fromLow) / (fromHigh - fromLow)));
			}
			case CLAMP -> {
				double value = number(block, 0, io);
				double a = number(block, 1, io);
				double b = number(block, 2, io);
				yield numeric(Math.clamp(value, Math.min(a, b), Math.max(a, b)));
			}
			case MIN -> numeric(Math.min(number(block, 0, io), number(block, 1, io)));
			case MAX -> numeric(Math.max(number(block, 0, io), number(block, 1, io)));
			case POWER -> numeric(Math.pow(number(block, 0, io), number(block, 1, io)));
			case SQRT -> numeric(Math.sqrt(number(block, 0, io)));
			case SIN -> numeric(Math.sin(Math.toRadians(number(block, 0, io))));
			case COS -> numeric(Math.cos(Math.toRadians(number(block, 0, io))));
			case TAN -> numeric(Math.tan(Math.toRadians(number(block, 0, io))));
			case RANDOM -> random(value(block, 0, io), value(block, 1, io));
			case CHANCE -> bool(random.nextDouble() * 100 < number(block, 0, io));
			case PICK_TEXT -> new Scalar(pick(splitText(input(block, 0, io), input(block, 1, io))));
			case EQUALS -> bool(value(block, 0, io).equivalent(value(block, 1, io)));
			case TEXT_EQUALS -> bool(input(block, 0, io).equals(input(block, 1, io)));
			case LESS -> bool(compare(value(block, 0, io), value(block, 1, io)) < 0);
			case GREATER -> bool(compare(value(block, 0, io), value(block, 1, io)) > 0);
			case AND -> bool(condition(block, 0, io) && condition(block, 1, io));
			case OR -> bool(condition(block, 0, io) || condition(block, 1, io));
			case XOR -> bool(condition(block, 0, io) ^ condition(block, 1, io));
			case NOT -> bool(!condition(block, 0, io));
			case PACK_BITS -> {
				int mask = 0;
				for (int i = 0; i < 4; i++)
					if (condition(block, i, io))
						mask |= 1 << i;
				yield numeric(mask);
			}
			case UNPACK_BIT -> {
				int bits = rounded(block, 0, io, 0, 15);
				int bit = rounded(block, 1, io, 1, 4) - 1;
				yield bool((bits & (1 << bit)) != 0);
			}
			case JOIN -> ComputerValue.join(List.of(value(block, 0, io), value(block, 1, io)), Missing.INSTANCE);
			case LENGTH -> {
				String text = input(block, 0, io);
				yield numeric(text.codePointCount(0, text.length()));
			}
			case CONTAINS -> bool(
				input(block, 0, io).toLowerCase(Locale.ROOT).contains(input(block, 1, io).toLowerCase(Locale.ROOT)));
			case PARSE_NUMBER -> numeric(extractNumber(input(block, 0, io)));
			case SUBSTRING -> {
				String text = input(block, 0, io);
				int length = text.codePointCount(0, text.length());
				int first = rounded(block, 1, io, 1, length + 1) - 1;
				int count = rounded(block, 2, io, 0, length - first);
				int start = text.offsetByCodePoints(0, first);
				yield new Scalar(text.substring(start, text.offsetByCodePoints(start, count)));
			}
			case REPLACE_TEXT -> {
				String text = input(block, 0, io);
				String target = input(block, 1, io);
				yield new Scalar(target.isEmpty() ? text : text.replace(target, input(block, 2, io)));
			}
			case FORMAT_NUMBER -> {
				double value = number(block, 0, io);
				int decimals = rounded(block, 1, io, 0, 8);
				yield new Scalar(String.format(Locale.ROOT, "%." + decimals + "f", value));
			}
			case PROGRESS_BAR -> {
				double value = number(block, 0, io);
				double total = number(block, 1, io);
				int width = rounded(block, 2, io, 1, 32);
				int filled = total <= 0 ? 0 : Math.clamp(Math.round(value / total * width), 0, width);
				yield new Scalar("█".repeat(filled) + "░".repeat(width - filled));
			}
			case LIST_LENGTH -> numeric(list(block, io).size());
			case LIST_JOIN -> ComputerValue.join(list(block, io), value(block, 1, io));
			case MAP_HAS -> bool(map(block, io).containsKey(input(block, 1, io)));
			case MAP_LENGTH -> numeric(map(block, io).size());
			case READ_REDSTONE -> numeric(io.readRedstone(redstoneFrequency(block, io)));
			case HAS_REDSTONE -> bool(io.readRedstone(redstoneFrequency(block, io)) > 0);
			case RISING_EDGE -> bool(signalChange(block, io).rising());
			case FALLING_EDGE -> bool(signalChange(block, io).falling());
			case READ_INPUT -> new Scalar(io.readInputSlot(index(block, io, ComputerIO.INPUT_SLOTS, Problem.INPUT_SLOT)));
			case TIMER -> numeric(Math.max(0L, io.gameTick() - timerStartTick) / 20.0);
			case STOCK_COUNT -> numeric(io.stockCount(stockFilters(list(block, io))));
			case HAS_STOCK -> {
				List<String> filters = stockFilters(list(block, io));
				yield bool(!filters.isEmpty() && io.stockCount(filters) >= number(block, 1, io));
			}
			case STOCK_TOTAL -> numeric(io.stockTotal());
			case STOCK_TYPES -> numeric(io.stockTypes());
			case LAST_ORDER -> bool(lastOrder);
			case VARIABLE -> variableValue(fixedVariableName(block));
			case LIST, MAP -> evaluateCollection(block, io);
			case LIST_GET -> {
				List<ComputerValue> entries = list(block, io);
				int index = listIndex(block, io);
				yield index < 0 || index >= entries.size() ? Missing.INSTANCE : entries.get(index);
			}
			case MAP_GET -> {
				MapValue.Entry entry = map(block, io).get(input(block, 1, io));
				yield entry == null ? Missing.INSTANCE : entry.value();
			}
			case PICK_LIST -> {
				List<ComputerValue> entries = list(block, io);
				yield entries.isEmpty() ? Missing.INSTANCE : entries.get(random.nextInt(entries.size()));
			}
			case TO_TEXT -> value(block, 0, io).toText();
			case TO_NUMBER -> numeric(number(block, 0, io));
			case TO_BOOLEAN -> bool(condition(block, 0, io));
			case TO_ITEM -> new Scalar(input(block, 0, io).strip(), DataType.ITEM);
			case TO_LIST -> {
				ComputerValue value = value(block, 0, io);
				yield value instanceof ListValue ? value : new ListValue(value.asList());
			}
			case TO_MAP -> {
				ComputerValue value = value(block, 0, io);
				yield value instanceof MapValue ? value : new MapValue(value.asMap());
			}
			default -> throw new IllegalStateException("Expected a computer reporter: " + block.op());
		};
	}

	private ComputerValue evaluateCollection(Block block, ComputerIO io) {
		ValueCell stored = inlineCollections.get(block);
		if (stored != null)
			return stored.value;
		if (block.op() == Op.LIST) {
			List<ComputerValue> entries = new ArrayList<>(block.inputs().size());
			for (int i = 0; i < block.inputs().size(); i++)
				entries.add(value(block, i, io));
			return new ListValue(entries);
		}
		Map<String, MapValue.Entry> entries = new LinkedHashMap<>();
		for (int i = 0; i < block.inputs().size(); i += 2) {
			Scalar key = value(block, i, io).asScalar();
			entries.put(key.text(), new MapValue.Entry(key, value(block, i + 1, io)));
		}
		return new MapValue(entries);
	}

	private static ComputerValue add(ComputerValue first, ComputerValue second) {
		if (first instanceof MapValue left && second instanceof MapValue right) {
			Map<String, MapValue.Entry> entries = new LinkedHashMap<>(left.entries());
			entries.putAll(right.entries());
			if (entries.size() > MAX_ENTRIES)
				throw new ComputerValue.LimitExceeded();
			return new MapValue(entries);
		}
		if (first instanceof ListValue || second instanceof ListValue) {
			List<ComputerValue> entries = new ArrayList<>(first.asList());
			entries.addAll(second.asList());
			if (entries.size() > MAX_ENTRIES)
				throw new ComputerValue.LimitExceeded();
			return new ListValue(entries);
		}
		if (isNumericOperand(first) && isNumericOperand(second))
			return numeric(first.asNumber() + second.asNumber());
		return ComputerValue.join(List.of(first, second), Missing.INSTANCE);
	}

	private static boolean isNumericOperand(ComputerValue value) {
		return value == Missing.INSTANCE
			|| value instanceof Scalar scalar && (scalar.type() == DataType.NUMBER || scalar.type() == DataType.BOOLEAN);
	}

	private void tuneFrequency(Block block, ComputerIO io) {
		Channel channel = Channel.VALUES[index(block, io, Channel.VALUES.length, Problem.CHANNEL)];
		String first = "";
		String second = "";
		if (block.inputs().get(1) instanceof Block inline && inline.op() == Op.LIST && !inlineCollections.containsKey(inline)) {
			// Evaluate only the two frequency items, even if the inline list has additional rows.
			if (!inline.inputs().isEmpty())
				first = input(inline, 0, io).strip();
			if (inline.inputs().size() >= 2)
				second = input(inline, 1, io).strip();
		} else {
			List<ComputerValue> entries = value(block, 1, io).asList();
			if (!entries.isEmpty())
				first = entries.getFirst().asText().strip();
			if (entries.size() >= 2)
				second = entries.get(1).asText().strip();
		}
		io.tuneRedstone(channel, frequencyPair(first, second, io));
	}

	private static List<String> stockFilters(List<ComputerValue> entries) {
		List<String> filters = new ArrayList<>(entries.size());
		for (ComputerValue entry : entries) {
			if (entry instanceof Scalar scalar && !scalar.text().isBlank())
				filters.add(scalar.text().strip());
		}
		return filters;
	}

	private boolean order(Block block, ComputerIO io) {
		String address = input(block, block.op() == Op.ORDER_ITEM ? 2 : 1, io);
		if (address.isBlank())
			throw new InvalidInput(Problem.ADDRESS);
		if (block.op() == Op.ORDER_ITEM)
			return io.orderStock(address, List.of(orderEntry(input(block, 0, io), value(block, 1, io), io)));

		List<OrderEntry> entries = new ArrayList<>();
		if (block.inputs().getFirst() instanceof Block inline && inline.op() == Op.MAP && !inlineCollections.containsKey(inline)) {
			// Inline orders can have repeated item rows, which the logistics layer sums.
			for (int i = 0; i < inline.inputs().size(); i += 2) {
				String item = input(inline, i, io).strip();
				if (!item.isEmpty())
					entries.add(orderEntry(item, value(inline, i + 1, io), io));
			}
		} else {
			map(block, io).forEach((key, value) -> {
				if (!key.isBlank())
					entries.add(orderEntry(key, value.value(), io));
			});
		}
		if (entries.isEmpty())
			throw new InvalidInput(Problem.ORDER_ITEMS);
		return io.orderStock(address, entries);
	}

	private static OrderEntry orderEntry(String reference, ComputerValue quantity, ComputerIO io) {
		String item = reference.strip();
		if (item.isEmpty() || !io.isItem(item, false))
			throw new InvalidInput(Problem.ITEM);
		long count = Math.round(requireNumber(quantity, Problem.QUANTITY));
		if (count < 1)
			throw new InvalidInput(Problem.QUANTITY);
		return new OrderEntry(item, count);
	}

	private boolean condition(Block block, int index, ComputerIO io) {
		return value(block, index, io).asBoolean();
	}

	private RedstoneFrequency redstoneFrequency(Block block, ComputerIO io) {
		ComputerValue source = value(block, 0, io);
		if (Double.isFinite(source.tryNumber(false)))
			return Channel.VALUES[index(source, Channel.VALUES.length, Problem.CHANNEL)];
		List<ComputerValue> items = source.asList();
		String first = items.isEmpty() ? "" : items.getFirst().asText().strip();
		String second = items.size() < 2 ? "" : items.get(1).asText().strip();
		return frequencyPair(first, second, io);
	}

	private static FrequencyPair frequencyPair(String first, String second, ComputerIO io) {
		if ((!first.isEmpty() && !io.isItem(first, true)) || (!second.isEmpty() && !io.isItem(second, true)))
			throw new InvalidInput(Problem.FREQUENCY);
		return new FrequencyPair(first, second);
	}

	private static void writeRedstone(RedstoneFrequency frequency, int power, int duration, ComputerIO io) {
		if (!io.writeRedstone(frequency, power, duration))
			throw new InvalidInput(Problem.FREQUENCY_LIMIT);
	}

	private SignalChange signalChange(Block block, ComputerIO io) {
		RedstoneFrequency frequency = redstoneFrequency(block, io);
		if (frequency instanceof FrequencyPair pair && !pair.isEmpty()) {
			watchedFrequencies.put(block, pair);
		} else {
			watchedFrequencies.remove(block);
			if (frequency instanceof FrequencyPair)
				return SignalChange.NONE;
		}
		int current = sampledSignals.computeIfAbsent(frequency, io::readRedstone);
		return new SignalChange(previousSignals.getOrDefault(frequency, current), current);
	}

	private int index(Block block, ComputerIO io, int range, Problem problem) {
		return index(value(block, 0, io), range, problem);
	}

	private static int index(ComputerValue value, int range, Problem problem) {
		double number = value.tryNumber(false);
		if (!Double.isFinite(number) || number < 1 || number > range || number != Math.rint(number))
			throw new InvalidInput(problem);
		return (int) number - 1;
	}

	private int power(Block block, ComputerIO io) {
		return Math.clamp(Math.round(requireNumber(value(block, 1, io), Problem.SIGNAL_STRENGTH)), 0, 15);
	}

	private static double requireNumber(ComputerValue value, Problem problem) {
		double number = value.tryNumber(true);
		if (!Double.isFinite(number))
			throw new InvalidInput(problem);
		return number;
	}

	private double number(Block block, int index, ComputerIO io) {
		return value(block, index, io).asNumber();
	}

	private int rounded(Block block, int index, ComputerIO io, int min, int max) {
		return Math.clamp(Math.round(number(block, index, io)), min, max);
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

	private List<ComputerValue> list(Block block, ComputerIO io) {
		return value(block, 0, io).asList();
	}

	private int listIndex(Block block, ComputerIO io) {
		return rounded(block, 1, io, 0, MAX_ENTRIES + 1) - 1;
	}

	private Map<String, MapValue.Entry> map(Block block, ComputerIO io) {
		return value(block, 0, io).asMap();
	}

	private String destinationName(Block block, ComputerIO io) {
		String name = referencedVariableName(block.inputs().getFirst());
		return name == null ? input(block, 0, io).strip() : name;
	}

	@Nullable
	private static String referencedVariableName(Input input) {
		if (input instanceof Block reporter && reporter.op() == Op.VARIABLE)
			return fixedVariableName(reporter);
		return null;
	}

	private static String fixedVariableName(Block block) {
		if (block.inputs().getFirst() instanceof Literal name)
			return name.text().strip();
		throw new IllegalStateException("Unfilled variable name");
	}

	@Nullable
	private ValueCell collectionTarget(Block block, int index, Op kind, boolean create, ComputerIO io) {
		Input source = block.inputs().get(index);
		String name = referencedVariableName(source);
		if (name != null)
			return namedCollection(name, kind, create);
		ComputerValue value = value(block, index, io);
		Block owner = source instanceof Block reporter && reporter.op().isCollection() ? reporter : block;
		// An inline destination holds its edits until the program is restarted.
		return inlineCollections.computeIfAbsent(owner, ignored -> new ValueCell(value));
	}

	@Nullable
	private ValueCell namedCollection(String name, Op kind, boolean create) {
		ValueCell variable = variables.get(name);
		if (variable != null || !create)
			return variable;
		putVariable(name, kind == Op.LIST ? EMPTY_LIST : EMPTY_MAP);
		return variables.get(name);
	}

	private ComputerValue variableValue(String name) {
		ValueCell cell = variables.get(name);
		return cell == null ? Missing.INSTANCE : cell.value;
	}

	private String pick(List<String> values) {
		return values.isEmpty() ? "" : values.get(random.nextInt(values.size()));
	}

	private void putVariable(String name, ComputerValue value) {
		if (name.isEmpty()) {
			finish(Status.EMPTY_VARIABLE);
		} else if (variables.size() == MAX_VARIABLES && !variables.containsKey(name)) {
			finish(Status.VARIABLE_LIMIT);
		} else {
			ValueCell cell = variables.get(name);
			if (cell == null) {
				variables.put(name, new ValueCell(value));
				version++;
			} else {
				setValue(cell, value);
			}
		}
	}

	private void setValue(ValueCell cell, ComputerValue value) {
		ComputerValue previous = cell.value;
		cell.value = value;
		if (value != previous && (!value.equals(previous) || !value.asText().equals(previous.asText())))
			version++;
	}

	private void setActiveBlock(int index) {
		if (activeBlockIndex != index) {
			activeBlockIndex = index;
			version++;
		}
	}

	private void report(Block block, Problem problem) {
		Diagnostic next = new Diagnostic(problem, commandIndices.getOrDefault(block, -1));
		if (!next.equals(diagnostic)) {
			diagnostic = next;
			version++;
		}
	}

	private void finish(Status result) {
		scripts.clear();
		procedures.clear();
		inlineCollections.clear();
		watchedFrequencies.clear();
		previousSignals.clear();
		sampledSignals.clear();
		commandIndices = Map.of();
		if (diagnostic == null)
			executionProgram = null;
		activeBlockIndex = -1;
		status = result;
		version++;
	}

	long version() { return version; }

	Snapshot snapshot(List<Component> output, List<ChannelState> channels, List<String> inputSlots, boolean stockLinkBound) {
		return new Snapshot(status, activeBlockIndex, diagnostic, output,
			variables.entrySet().stream().map(entry -> new Variable(entry.getKey(), entry.getValue().value.asDisplay())).toList(),
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

	private Scalar random(ComputerValue first, ComputerValue second) {
		double a = first.asNumber();
		double b = second.asNumber();
		double low = Math.min(a, b);
		double high = Math.max(a, b);
		if (low == high)
			return numeric(low);
		boolean ints = !first.asText().contains(".") && !second.asText().contains(".") && low == Math.rint(low) && high == Math.rint(high)
			&& low >= Integer.MIN_VALUE && high <= Integer.MAX_VALUE;
		if (ints) {
			long min = (long) low;
			long max = (long) high;
			return numeric(random.nextLong(min, max + 1));
		}
		return numeric(randomBetween(low, high));
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
				double parsed = Scalar.parseNumber(sb.toString(), false);
				if (Double.isFinite(parsed))
					return parsed;
			}
			i = Math.max(start, pos - 1);
		}
		return 0;
	}

	private static int compare(ComputerValue first, ComputerValue second) {
		double a = first.tryNumber(true);
		double b = second.tryNumber(true);
		if (Double.isFinite(a) && Double.isFinite(b))
			return a == b ? 0 : Double.compare(a, b);
		return first.asText().compareToIgnoreCase(second.asText());
	}

	private static Scalar numeric(double number) {
		if (!Double.isFinite(number) || number == 0)
			return numeric(0L);
		String value = Double.toString(number);
		return new Scalar(value.endsWith(".0") ? value.substring(0, value.length() - 2) : value, DataType.NUMBER);
	}

	private static Scalar numeric(long number) {
		return new Scalar(Long.toString(number), DataType.NUMBER);
	}

	private static Scalar bool(boolean value) {
		return new Scalar(Boolean.toString(value), DataType.BOOLEAN);
	}

	public enum Status { READY, RUNNING, FINISHED, STOPPED, EMPTY_VARIABLE, VARIABLE_LIMIT, LIBRARY_LOCKED, PROCEDURE_LIMIT, COLLECTION_LIMIT }

	public enum Problem { CHANNEL, INPUT_SLOT, OUTPUT_LINE, SIGNAL_STRENGTH, DURATION, FREQUENCY, ITEM, QUANTITY, ADDRESS, ORDER_ITEMS, FREQUENCY_LIMIT }

	public record Diagnostic(Problem problem, int blockIndex) {}

	public record Variable(String name, Component value) {}

	public record ChannelState(String first, String second, int input, int output) {}

	public record Snapshot(
		Status status,
		int activeBlockIndex,
		@Nullable Diagnostic diagnostic,
		List<Component> output,
		List<Variable> variables,
		List<ChannelState> channels,
		List<String> inputSlots,
		boolean stockLinkBound
	) {
		static final Snapshot EMPTY = new Snapshot(
			Status.READY, -1, null, List.of(), List.of(), List.of(),
			Collections.nCopies(ComputerIO.INPUT_SLOTS, ""), false
		);

		public Snapshot {
			output = List.copyOf(output);
			variables = List.copyOf(variables);
			channels = List.copyOf(channels);
			inputSlots = List.copyOf(inputSlots);
		}
	}

	private static final class InvalidInput extends RuntimeException {
		private final Problem problem;

		private InvalidInput(Problem problem) {
			super(null, null, false, false);
			this.problem = problem;
		}
	}

	private record Event(Block declaration, String message) {
		Op op() { return declaration.op(); }
	}

	private record SignalChange(int previous, int current) {
		private static final SignalChange NONE = new SignalChange(0, 0);

		boolean changed() { return previous != current; }
		boolean rising() { return previous == 0 && current > 0; }
		boolean falling() { return previous > 0 && current == 0; }
	}

	private static final class ValueCell {
		private ComputerValue value;

		private ValueCell(ComputerValue value) {
			this.value = value;
		}
	}

	private static final class Script {
		@Nullable
		private final Event event;
		private final List<Block> commands;
		private final Deque<Frame> frames = new ArrayDeque<>();
		private boolean broadcast, pending, stopped;
		private long wakeTick;

		private Script(@Nullable Event event, List<Block> commands) {
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
