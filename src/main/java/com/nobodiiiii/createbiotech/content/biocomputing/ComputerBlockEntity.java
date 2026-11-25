package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerIO.Channel;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxItem;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
	private static final String KEY_WIRELESS = "WirelessChannels";
	private static final String KEY_LOGISTICS = "Logistics";
	private static final String KEY_VILLAGER = "VillagerBox";
	private static final String KEY_DISPLAY_PENDING = "DisplayPending";

	private ComputerProgram program = ComputerProgram.EMPTY;
	private long programRevision;
	@Nullable
	private ComputerProgram restartProgram;
	private final ComputerRuntime runtime = new ComputerRuntime();
	private final ComputerWireless wireless = new ComputerWireless(this);
	private final ComputerLogistics logistics = new ComputerLogistics();
	private ItemStack villagerBox = ItemStack.EMPTY;
	private ComputerLibrary villagerLibrary = ComputerLibrary.CORE;
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
		public int readRedstone(Channel channel) {
			return wireless.read(channel);
		}

		@Override
		public void writeRedstone(Channel channel, int power, int pulseTicks) {
			if (wireless.write(channel, power, pulseTicks, currentGameTick()))
				markIoChanged();
		}

		@Override
		public void tuneRedstone(Channel channel, FrequencyPair frequency) {
			if (wireless.tune(channel, frequency))
				markIoChanged();
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
		public long stockCount(List<String> filters) {
			return library().includesLogistics() ? logistics.count(filters) : 0;
		}

		@Override
		public long stockTotal() {
			return library().includesLogistics() ? logistics.total() : 0;
		}

		@Override
		public int stockTypes() {
			return library().includesLogistics() ? logistics.types() : 0;
		}

		@Override
		public boolean orderStock(String address, List<OrderEntry> items) {
			return library().includesLogistics()
				&& logistics.order(address.strip(), items, currentGameTick(), ComputerBlockEntity.this::setChanged);
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
	long syncVersion() { return runtime.version() + ioVersion; }

	ComputerRuntime.Snapshot snapshot() {
		return runtime.snapshot(outputLines, wireless.snapshot(), Arrays.asList(inputSlots), logistics.isBound());
	}

	public List<String> outputLinesSnapshot() {
		return List.copyOf(outputLines);
	}

	ComputerLibrary library() {
		return getBlockState().getBlock() instanceof ComputerBlock block && block.isCreative()
			? ComputerLibrary.ALL : villagerLibrary;
	}

	boolean mayEdit(Player player) {
		return logistics.mayEdit(player);
	}

	boolean hasVillagerBox() {
		return !villagerBox.isEmpty();
	}

	void installVillager(Player player, ItemStack stack) {
		if (hasVillagerBox()) {
			showInteractionMessage(player, "villager_occupied");
			return;
		}
		if (!setVillagerBox(stack))
			return;
		stack.shrink(1);
		markIoChanged();
		player.displayClientMessage(Component.translatable("create_biotech.biocomputing.villager_installed",
			Component.translatable("create_biotech.biocomputing.scratch.library." + villagerLibrary.key())), true);
	}

	ItemStack ejectVillager() {
		ItemStack box = takeVillagerBox();
		stopProgram();
		markIoChanged();
		return box;
	}

	ItemStack takeVillagerBox() {
		ItemStack box = villagerBox;
		villagerBox = ItemStack.EMPTY;
		villagerLibrary = ComputerLibrary.CORE;
		return box;
	}

	private boolean setVillagerBox(ItemStack stack) {
		if (!CapturedEntityBoxItem.isBox(stack))
			return false;
		CompoundTag villager = CapturedEntityBoxHelper.getCapturedEntityData(stack, EntityType.VILLAGER);
		if (villager == null)
			return false;
		villagerBox = stack.copyWithCount(1);
		villagerLibrary = ComputerLibrary.forProfession(villager.getCompound("VillagerData").getString("profession"));
		return true;
	}

	void configureStockLink(Player player, ItemStack link) {
		UUID network = LogisticallyLinkedBlockItem.networkFromStack(link);
		if (network != null && !library().includesLogistics()) {
			showInteractionMessage(player, "stock_requires_library");
			return;
		}
		if (network != null && !Create.LOGISTICS.mayInteract(network, player)) {
			showInteractionMessage(player, "stock_protected");
			return;
		}
		if (network != null && !Create.LOGISTICS.logisticsNetworks.containsKey(network)) {
			showInteractionMessage(player, "stock_missing");
			return;
		}
		if (logistics.bind(network)) {
			stopProgram();
			markIoChanged();
		}
		showInteractionMessage(player, network == null ? "stock_unbound" : "stock_bound");
	}

	void showInteractionMessage(Player player, String key) {
		player.displayClientMessage(Component.translatable("create_biotech.biocomputing." + key), true);
	}

	public void setInputSlot(int slotIndex, String text) {
		if (slotIndex < 0 || slotIndex >= inputSlots.length)
			return;
		String sanitized = ComputerRuntime.clampValue(sanitizeDisplayText(text));
		if (!sanitized.equals(inputSlots[slotIndex])) {
			inputSlots[slotIndex] = sanitized;
			markIoChanged();
		}
	}

	void saveProgram(ComputerProgram newProgram) {
		if (!this.program.equals(newProgram)) {
			this.program = newProgram;
			programRevision++;
			setChanged();
		}
	}

	void run(ComputerProgram newProgram, UUID operator) {
		saveProgram(newProgram);
		this.restartProgram = newProgram.commands().isEmpty() ? null : newProgram;
		this.pendingAutoStart = false;
		clearOutputInternal();
		if (wireless.resetOutputs())
			markIoChanged();
		logistics.setOperator(operator);
		runtime.start(newProgram, io);
		setChanged();
	}

	void stopProgram() {
		this.restartProgram = null;
		this.pendingAutoStart = false;
		runtime.stop();
		if (wireless.resetOutputs())
			markIoChanged();
		logistics.setOperator(null);
		setChanged();
	}

	void serverTick() {
		if (!(level instanceof ServerLevel serverLevel))
			return;
		if (wireless.tickInputs(serverLevel, serverLevel.getGameTime()))
			ioVersion++;
		tickProgram();
		wireless.flushOutputs();
		tickDisplayOutput();
	}

	private void tickProgram() {
		if (pendingAutoStart) {
			pendingAutoStart = false;
			if (restartProgram != null) {
				if (restartProgram.firstUnavailable(library()) == null) {
					runtime.start(restartProgram, io);
				} else {
					stopProgram();
					runtime.showLockedLibrary();
				}
			}
		}
		runtime.tick(io);
		if (restartProgram != null && !runtime.isRunning()) {
			restartProgram = null;
			setChanged();
		}
	}

	@Override
	public void onChunkUnloaded() {
		wireless.disconnect();
		super.onChunkUnloaded();
	}

	@Override
	public void setRemoved() {
		wireless.disconnect();
		super.setRemoved();
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

	private void clearOutputInternal() {
		if (!outputLines.isEmpty()) {
			outputLines.clear();
			markOutputChanged();
		}
	}

	private void markOutputChanged() {
		displayDirty = true;
		markIoChanged();
	}

	private void markIoChanged() {
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

		tag.put(KEY_WIRELESS, wireless.save());
		tag.put(KEY_LOGISTICS, logistics.save());
		if (!villagerBox.isEmpty())
			tag.put(KEY_VILLAGER, villagerBox.save(registries));
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

		wireless.load(tag.getList(KEY_WIRELESS, Tag.TAG_COMPOUND));
		logistics.load(tag.getCompound(KEY_LOGISTICS));
		takeVillagerBox();
		setVillagerBox(ItemStack.parseOptional(registries, tag.getCompound(KEY_VILLAGER)));

		pendingDisplayLinks.clear();
		displayDirty = tag.getBoolean(KEY_DISPLAY_PENDING);
		displayCooldown = 0;
		nextDisplayRetry = 0;
		ioVersion++;
	}
}
