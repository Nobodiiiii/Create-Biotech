package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerIO.Port;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ComputerBlockEntity extends BlockEntity {
	private static final int DISPLAY_NOTIFY_COOLDOWN = 5;
	private static final int DISPLAY_RETRY_INTERVAL = 20;

	private static final String KEY_PROGRAM = "Program";
	private static final String KEY_AUTO_RUN = "AutoRun";
	private static final String KEY_RUNNING_PROGRAM = "RunningProgram";
	private static final String KEY_INPUT_SLOTS = "InputSlots";
	private static final String KEY_OUTPUT_LINES = "OutputLines";
	private static final String KEY_REDSTONE_OUT = "RedstoneOut";
	private static final String KEY_DISPLAY_PENDING = "DisplayPending";

	private ComputerProgram program = ComputerProgram.EMPTY;
	private long programRevision;
	@Nullable
	private ComputerProgram restartProgram;
	private final ComputerRuntime runtime = new ComputerRuntime();
	private final int[] redstoneIn = new int[ComputerIO.PORT_COUNT];
	private final int[] redstoneOut = new int[ComputerIO.PORT_COUNT];
	private final int[] publishedRedstoneOut = new int[ComputerIO.PORT_COUNT];
	private final String[] inputSlots = createEmptySlots();
	private final List<String> outputLines = new ArrayList<>();
	private boolean pendingAutoStart;
	private final Set<BlockPos> pendingDisplayLinks = new HashSet<>();
	private boolean displayDirty;
	private int displayCooldown;
	private long nextDisplayRetry;
	private long ioVersion;

	private final ComputerIO io = new ComputerIO() {
		@Override
		public int readRedstone(Port port) {
			return redstoneIn[port.ordinal()];
		}

		@Override
		public void writeRedstone(Port port, int power) {
			int clamped = Math.clamp(power, 0, 15);
			int index = port.ordinal();
			if (redstoneOut[index] != clamped) {
				redstoneOut[index] = clamped;
				ioVersion++;
			}
		}

		@Override
		public String readInputSlot(int slotIndex) {
			return slotIndex >= 0 && slotIndex < inputSlots.length ? inputSlots[slotIndex] : "";
		}

		@Override
		public void setOutputLine(int lineIndex, String text) {
			if (lineIndex < 0 || lineIndex >= ComputerIO.OUTPUT_LINES)
				return;
			String clamped = ComputerRuntime.clampValue(text);
			boolean changed = false;
			while (outputLines.size() <= lineIndex) {
				outputLines.add("");
				changed = true;
			}
			if (!clamped.equals(outputLines.get(lineIndex))) {
				outputLines.set(lineIndex, clamped);
				changed = true;
			}
			if (changed)
				markOutputChanged();
		}

		@Override
		public void appendOutput(String text) {
			String clamped = ComputerRuntime.clampValue(text);
			if (outputLines.size() == ComputerIO.OUTPUT_LINES)
				outputLines.removeFirst();
			outputLines.add(clamped);
			markOutputChanged();
		}

		@Override
		public void clearOutput() {
			clearOutputInternal();
		}

		@Override
		public void updateDisplay() {
			if (!displayDirty) {
				displayDirty = true;
				setChanged();
			}
		}

		@Override
		public long gameTick() {
			return currentGameTick();
		}
	};

	public ComputerBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.COMPUTER.get(), pos, state);
	}

	private static String[] createEmptySlots() {
		String[] slots = new String[ComputerIO.INPUT_SLOTS];
		Arrays.fill(slots, "");
		return slots;
	}

	ComputerProgram program() { return program; }
	long programRevision() { return programRevision; }
	@Nullable ComputerProgram restartProgram() { return restartProgram; }
	ComputerRuntime runtime() { return runtime; }
	long syncVersion() { return runtime.version() + ioVersion; }

	ComputerRuntime.Snapshot snapshot() {
		return runtime.snapshot(List.copyOf(outputLines), redstoneIn, redstoneOut, Arrays.asList(inputSlots));
	}

	public List<String> outputLinesSnapshot() {
		return List.copyOf(outputLines);
	}

	public int getRedstoneOutput(Port port) {
		return redstoneOut[port.ordinal()];
	}

	public void setInputSlot(int slotIndex, String text) {
		if (slotIndex < 0 || slotIndex >= inputSlots.length)
			return;
		String sanitized = ComputerRuntime.clampValue(sanitizeDisplayText(text));
		if (!sanitized.equals(inputSlots[slotIndex])) {
			inputSlots[slotIndex] = sanitized;
			ioVersion++;
			setChanged();
		}
	}

	void saveProgram(ComputerProgram newProgram) {
		if (!this.program.equals(newProgram)) {
			this.program = newProgram;
			programRevision++;
			setChanged();
		}
	}

	void run(ComputerProgram newProgram) {
		saveProgram(newProgram);
		this.restartProgram = newProgram.commands().isEmpty() ? null : newProgram;
		this.pendingAutoStart = false;
		clearOutputInternal();
		resetRedstoneOutputs();
		runtime.start(newProgram, currentGameTick());
		setChanged();
	}

	void stopProgram() {
		this.restartProgram = null;
		this.pendingAutoStart = false;
		runtime.stop();
		resetRedstoneOutputs();
		setChanged();
	}

	void serverTick() {
		if (level == null || level.isClientSide)
			return;
		sampleRedstoneInputs();
		tickProgram();
		flushRedstoneOutputs();
		tickDisplayOutput();
	}

	private void tickProgram() {
		if (pendingAutoStart) {
			pendingAutoStart = false;
			if (restartProgram != null)
				runtime.start(restartProgram, currentGameTick());
		}
		runtime.tick(io);
		if (restartProgram != null && !runtime.isRunning()) {
			restartProgram = null;
			setChanged();
		}
	}

	private void tickDisplayOutput() {
		if (displayCooldown > 0)
			displayCooldown--;
		if (displayDirty && displayCooldown == 0) {
			displayDirty = false;
			displayCooldown = DISPLAY_NOTIFY_COOLDOWN;
			publishDisplayOutput(worldPosition);
			publishDisplayOutput(worldPosition.above());
			nextDisplayRetry = currentGameTick() + DISPLAY_RETRY_INTERVAL;
			if (pendingDisplayLinks.isEmpty())
				setChanged();
		} else if (!pendingDisplayLinks.isEmpty() && currentGameTick() >= nextDisplayRetry) {
			nextDisplayRetry = currentGameTick() + DISPLAY_RETRY_INTERVAL;
			pendingDisplayLinks.removeIf(this::retryDisplayLink);
			if (pendingDisplayLinks.isEmpty() && !displayDirty)
				setChanged();
		}
	}

	private void publishDisplayOutput(BlockPos sourcePos) {
		DisplayLinkBlock.sendToGatherers(level, sourcePos, (link, source) -> {
			if (trySendDisplayOutput(link))
				pendingDisplayLinks.remove(link.getBlockPos());
			else
				pendingDisplayLinks.add(link.getBlockPos());
		}, ComputerDisplaySource.class);
	}

	private boolean retryDisplayLink(BlockPos linkPos) {
		if (!level.isLoaded(linkPos))
			return false;
		if (!(level.getBlockEntity(linkPos) instanceof DisplayLinkBlockEntity link)
			|| !(link.activeSource instanceof ComputerDisplaySource))
			return true;
		return trySendDisplayOutput(link);
	}

	private boolean trySendDisplayOutput(DisplayLinkBlockEntity link) {
		BlockPos sourcePos = link.getSourcePosition();
		if (!sourcePos.equals(worldPosition) && !sourcePos.equals(worldPosition.above()))
			return true;
		if (!level.isLoaded(link.getTargetPosition()) || link.getBlockState().getValue(DisplayLinkBlock.POWERED))
			return false;
		link.tickSource();
		return true;
	}

	private void sampleRedstoneInputs() {
		BlockState state = getBlockState();
		if (!(state.getBlock() instanceof ComputerBlock))
			return;
		Direction facing = state.getValue(ComputerBlock.FACING);
		BlockPos lowerPos = worldPosition;
		BlockPos upperPos = worldPosition.above();
		for (Port port : Port.VALUES) {
			Direction dir = port.worldDirection(facing);
			int power = port == Port.TOP
				? sampleNeighborSignal(upperPos, dir)
				: Math.max(sampleNeighborSignal(lowerPos, dir), sampleNeighborSignal(upperPos, dir));
			int index = port.ordinal();
			if (redstoneIn[index] != power) {
				redstoneIn[index] = power;
				ioVersion++;
			}
		}
	}

	private int sampleNeighborSignal(BlockPos selfPos, Direction dirToNeighbor) {
		BlockPos neighborPos = selfPos.relative(dirToNeighbor);
		if (level == null || !level.isLoaded(neighborPos))
			return 0;
		return Math.clamp(level.getSignal(neighborPos, dirToNeighbor), 0, 15);
	}

	private void resetRedstoneOutputs() {
		boolean anyChanged = false;
		for (int i = 0; i < ComputerIO.PORT_COUNT; i++) {
			if (redstoneOut[i] != 0) {
				redstoneOut[i] = 0;
				anyChanged = true;
			}
		}
		if (anyChanged)
			ioVersion++;
		flushRedstoneOutputs();
	}

	private void flushRedstoneOutputs() {
		boolean horizontalChanged = false;
		boolean topChanged = false;
		for (Port port : Port.VALUES) {
			int idx = port.ordinal();
			if (redstoneOut[idx] != publishedRedstoneOut[idx]) {
				publishedRedstoneOut[idx] = redstoneOut[idx];
				if (port == Port.TOP)
					topChanged = true;
				else
					horizontalChanged = true;
			}
		}
		if (!horizontalChanged && !topChanged)
			return;
		setChanged();
		if (level == null || level.isClientSide)
			return;
		BlockState state = getBlockState();
		if (horizontalChanged)
			level.updateNeighborsAt(worldPosition, state.getBlock());
		if (horizontalChanged || topChanged)
			level.updateNeighborsAt(worldPosition.above(), state.getBlock());
	}

	private void clearOutputInternal() {
		if (!outputLines.isEmpty()) {
			outputLines.clear();
			markOutputChanged();
		}
	}

	private void markOutputChanged() {
		displayDirty = true;
		ioVersion++;
		setChanged();
	}

	private long currentGameTick() {
		return level == null ? 0L : level.getGameTime();
	}

	private static String sanitizeDisplayText(String raw) {
		if (raw == null)
			return "";
		return raw.replace('\r', ' ').replace('\n', ' ').strip();
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put(KEY_PROGRAM, ComputerProgramNbt.write(program));
		tag.putBoolean(KEY_AUTO_RUN, restartProgram != null);
		if (restartProgram != null && !restartProgram.equals(program))
			tag.put(KEY_RUNNING_PROGRAM, ComputerProgramNbt.write(restartProgram));
		tag.putBoolean(KEY_DISPLAY_PENDING, displayDirty || !pendingDisplayLinks.isEmpty());

		ListTag inputList = new ListTag();
		for (String slot : inputSlots)
			inputList.add(StringTag.valueOf(slot));
		tag.put(KEY_INPUT_SLOTS, inputList);

		ListTag outputList = new ListTag();
		for (String line : outputLines)
			outputList.add(StringTag.valueOf(line));
		tag.put(KEY_OUTPUT_LINES, outputList);

		tag.putIntArray(KEY_REDSTONE_OUT, Arrays.copyOf(redstoneOut, ComputerIO.PORT_COUNT));
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		ComputerProgram savedProgram = tag.contains(KEY_PROGRAM, Tag.TAG_COMPOUND)
			? ComputerProgramNbt.read(tag.getCompound(KEY_PROGRAM))
			: ComputerProgram.EMPTY;
		if (!program.equals(savedProgram)) {
			program = savedProgram;
			programRevision++;
		}
		ComputerProgram savedRunningProgram = tag.contains(KEY_RUNNING_PROGRAM, Tag.TAG_COMPOUND)
			? ComputerProgramNbt.read(tag.getCompound(KEY_RUNNING_PROGRAM))
			: program;
		restartProgram = tag.getBoolean(KEY_AUTO_RUN) && !savedRunningProgram.commands().isEmpty()
			? savedRunningProgram : null;
		pendingAutoStart = restartProgram != null;
		runtime.stop();

		Arrays.fill(inputSlots, "");
		if (tag.contains(KEY_INPUT_SLOTS, Tag.TAG_LIST)) {
			ListTag inputList = tag.getList(KEY_INPUT_SLOTS, Tag.TAG_STRING);
			for (int i = 0; i < Math.min(inputList.size(), inputSlots.length); i++)
				inputSlots[i] = ComputerRuntime.clampValue(sanitizeDisplayText(inputList.getString(i)));
		}

		outputLines.clear();
		if (tag.contains(KEY_OUTPUT_LINES, Tag.TAG_LIST)) {
			ListTag outputList = tag.getList(KEY_OUTPUT_LINES, Tag.TAG_STRING);
			for (int i = 0; i < Math.min(outputList.size(), ComputerIO.OUTPUT_LINES); i++)
				outputLines.add(ComputerRuntime.clampValue(outputList.getString(i)));
		}

		Arrays.fill(redstoneOut, 0);
		Arrays.fill(publishedRedstoneOut, 0);
		if (tag.contains(KEY_REDSTONE_OUT, Tag.TAG_INT_ARRAY)) {
			int[] saved = tag.getIntArray(KEY_REDSTONE_OUT);
			for (int i = 0; i < Math.min(saved.length, ComputerIO.PORT_COUNT); i++) {
				int clamped = Math.clamp(saved[i], 0, 15);
				redstoneOut[i] = clamped;
				publishedRedstoneOut[i] = clamped;
			}
		}

		pendingDisplayLinks.clear();
		displayDirty = tag.getBoolean(KEY_DISPLAY_PENDING);
		displayCooldown = 0;
		nextDisplayRetry = 0;
		ioVersion++;
	}
}
