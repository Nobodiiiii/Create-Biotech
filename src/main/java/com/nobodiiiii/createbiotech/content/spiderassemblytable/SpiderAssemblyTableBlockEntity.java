package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import static com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult.HOLD;
import static com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult.PASS;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.foundation.advancement.PlacedByPlayerAdvancementTracker;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import com.simibubi.create.foundation.recipe.RecipeApplier;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;

public class SpiderAssemblyTableBlockEntity extends KineticBlockEntity implements MenuProvider {

	public static final int LEG_COUNT = 8;
	public static final int MACHINE_SLOT_START = 0;
	public static final int HYBRID_SLOT_START = MACHINE_SLOT_START + LEG_COUNT;
	public static final int SLOT_COUNT = HYBRID_SLOT_START + LEG_COUNT;
	public static final int FLUID_CAPACITY = 1000;

	private final SpiderAssemblyInventory inventory = new SpiderAssemblyInventory();
	private final FluidTank[] fluidTanks = new FluidTank[LEG_COUNT];
	private final ItemStack[] itemLocks = new ItemStack[LEG_COUNT];
	private final FluidStack[] fluidLocks = new FluidStack[LEG_COUNT];
	private final boolean[] slotBlocked = new boolean[LEG_COUNT];
	private final LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> new HybridItemWrapper());
	private final LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> new SpiderFluidHandler());

	private int nextSlot;
	private int activeSlot = -1;
	private MachineKind activeMachine;
	private int processingTicksRemaining;
	private int processingTicksTotal;
	private boolean impactFiredThisCycle;
	private boolean impactPending;
	private ItemStack impactDisplayItem = ItemStack.EMPTY;
	private ItemStack cachedInput = ItemStack.EMPTY;
	@Nullable
	private UUID advancementOwner;

	protected BeltProcessingBehaviour beltProcessing;

	public SpiderAssemblyTableBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.SPIDER_ASSEMBLY_TABLE.get(), pos, state);
		for (int i = 0; i < LEG_COUNT; i++) {
			fluidTanks[i] = new SpiderFluidTank();
			itemLocks[i] = ItemStack.EMPTY;
			fluidLocks[i] = FluidStack.EMPTY;
		}
	}

	public static void tick(Level level, BlockPos pos, BlockState state, SpiderAssemblyTableBlockEntity be) {
		be.tick();
	}

	@Override
	public void tick() {
		super.tick();

		if (level == null)
			return;

		if (level.isClientSide) {
			if (activeSlot >= 0 && processingTicksRemaining > 0 && getSpeed() != 0)
				processingTicksRemaining--;
			return;
		}

		tickAssembly();
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		beltProcessing = new BeltProcessingBehaviour(this)
			.whenItemEnters(this::onItemReceived)
			.whileItemHeld(this::whileItemHeld);
		behaviours.add(beltProcessing);
	}

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		tag.put("Inventory", inventory.serializeNBT());
		ListTag fluids = new ListTag();
		for (FluidTank tank : fluidTanks)
			fluids.add(tank.writeToNBT(new CompoundTag()));
		tag.put("Fluids", fluids);
		ListTag itemLocksTag = new ListTag();
		for (ItemStack lock : itemLocks)
			itemLocksTag.add(lock.save(new CompoundTag()));
		tag.put("ItemLocks", itemLocksTag);
		ListTag fluidLocksTag = new ListTag();
		for (FluidStack lock : fluidLocks)
			fluidLocksTag.add(lock.writeToNBT(new CompoundTag()));
		tag.put("FluidLocks", fluidLocksTag);
		byte[] blockedBytes = new byte[LEG_COUNT];
		for (int i = 0; i < LEG_COUNT; i++)
			blockedBytes[i] = slotBlocked[i] ? (byte) 1 : (byte) 0;
		tag.putByteArray("BlockedSlots", blockedBytes);
		tag.putInt("NextSlot", nextSlot);
		tag.putInt("ActiveSlot", activeSlot);
		tag.putInt("ActiveMachine", activeMachine == null ? -1 : activeMachine.ordinal());
		tag.putInt("ProcessingTicksRemaining", processingTicksRemaining);
		tag.putInt("ProcessingTicksTotal", processingTicksTotal);
		if (!cachedInput.isEmpty())
			tag.put("CachedInput", cachedInput.serializeNBT());
		PlacedByPlayerAdvancementTracker.writeOwner(tag, advancementOwner);
		if (clientPacket && impactPending) {
			tag.putBoolean("Impact", true);
			if (!impactDisplayItem.isEmpty())
				tag.put("ImpactItem", impactDisplayItem.serializeNBT());
			impactPending = false;
			impactDisplayItem = ItemStack.EMPTY;
		}
		super.write(tag, clientPacket);
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		inventory.deserializeNBT(tag.getCompound("Inventory"));
		ListTag fluids = tag.getList("Fluids", Tag.TAG_COMPOUND);
		for (int i = 0; i < fluidTanks.length && i < fluids.size(); i++)
			fluidTanks[i].readFromNBT(fluids.getCompound(i));
		ListTag itemLocksTag = tag.getList("ItemLocks", Tag.TAG_COMPOUND);
		for (int i = 0; i < itemLocks.length; i++)
			itemLocks[i] = i < itemLocksTag.size() ? ItemStack.of(itemLocksTag.getCompound(i)) : ItemStack.EMPTY;
		ListTag fluidLocksTag = tag.getList("FluidLocks", Tag.TAG_COMPOUND);
		for (int i = 0; i < fluidLocks.length; i++)
			fluidLocks[i] = i < fluidLocksTag.size()
				? FluidStack.loadFluidStackFromNBT(fluidLocksTag.getCompound(i))
				: FluidStack.EMPTY;
		byte[] blockedBytes = tag.getByteArray("BlockedSlots");
		for (int i = 0; i < LEG_COUNT; i++)
			slotBlocked[i] = i < blockedBytes.length && blockedBytes[i] != 0;
		nextSlot = Mth.clamp(tag.getInt("NextSlot"), 0, LEG_COUNT - 1);
		activeSlot = tag.getInt("ActiveSlot");
		int activeMachineId = tag.getInt("ActiveMachine");
		activeMachine = activeMachineId >= 0 && activeMachineId < MachineKind.values().length
			? MachineKind.values()[activeMachineId]
			: null;
		processingTicksRemaining = tag.getInt("ProcessingTicksRemaining");
		processingTicksTotal = tag.getInt("ProcessingTicksTotal");
		cachedInput = tag.contains("CachedInput") ? ItemStack.of(tag.getCompound("CachedInput")) : ItemStack.EMPTY;
		advancementOwner = PlacedByPlayerAdvancementTracker.readOwner(tag);
		super.read(tag, clientPacket);

		if (clientPacket && tag.getBoolean("Impact")) {
			ItemStack item = tag.contains("ImpactItem")
				? ItemStack.of(tag.getCompound("ImpactItem"))
				: ItemStack.EMPTY;
			spawnImpactParticles(activeMachine, item);
		}
	}

	@Override
	public float calculateStressApplied() {
		float stress = 0;
		for (int i = 0; i < LEG_COUNT; i++) {
			MachineKind kind = getMachineKind(i);
			if (kind != null)
				stress += BlockStressValues.getImpact(kind.block());
		}
		lastStressApplied = stress;
		return stress;
	}

	public ItemStackHandler getInventory() {
		return inventory;
	}

	public FluidTank getFluidTank(int index) {
		return fluidTanks[index];
	}

	public int getActiveSlot() {
		return activeSlot;
	}

	public float getProcessingProgress(float partialTicks) {
		if (activeSlot < 0 || processingTicksTotal <= 0)
			return 0;
		float remaining = Math.max(0, processingTicksRemaining - partialTicks);
		return Mth.clamp(1 - remaining / processingTicksTotal, 0, 1);
	}

	public void sendToMenu(FriendlyByteBuf buffer) {
		buffer.writeBlockPos(worldPosition);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.create_biotech.spider_assembly_table");
	}

	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
		return new SpiderAssemblyTableMenu(id, playerInventory, this);
	}

	public boolean canPlayerUse(Player player) {
		return !isRemoved() && player.distanceToSqr(worldPosition.getX() + 0.5d, worldPosition.getY() + 0.5d,
			worldPosition.getZ() + 0.5d) <= 64;
	}

	@Override
	public AABB createRenderBoundingBox() {
		return new AABB(worldPosition).inflate(1);
	}

	@Nonnull
	@Override
	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
		if (cap == ForgeCapabilities.ITEM_HANDLER)
			return itemCapability.cast();
		if (cap == ForgeCapabilities.FLUID_HANDLER)
			return fluidCapability.cast();
		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		itemCapability.invalidate();
		fluidCapability.invalidate();
	}

	public boolean isHybridSlotItemOnly(int hybridIndex) {
		return fluidTanks[hybridIndex].getFluid().isEmpty();
	}

	public boolean isHybridSlotFluidOnly(int hybridIndex) {
		return !fluidTanks[hybridIndex].getFluid().isEmpty();
	}

	public ItemStack getItemLock(int hybridIndex) {
		return itemLocks[hybridIndex];
	}

	public FluidStack getFluidLock(int hybridIndex) {
		return fluidLocks[hybridIndex];
	}

	public boolean isHybridSlotLocked(int hybridIndex) {
		return !itemLocks[hybridIndex].isEmpty() || !fluidLocks[hybridIndex].isEmpty() || slotBlocked[hybridIndex];
	}

	public boolean areAllHybridSlotsLocked() {
		for (int i = 0; i < LEG_COUNT; i++)
			if (!isHybridSlotLocked(i))
				return false;
		return true;
	}

	public boolean isHybridSlotBlocked(int hybridIndex) {
		return slotBlocked[hybridIndex];
	}

	public boolean canHybridSlotAcceptItem(int hybridIndex, ItemStack stack) {
		if (hybridIndex < 0 || hybridIndex >= LEG_COUNT)
			return false;
		if (stack.isEmpty())
			return true;
		if (slotBlocked[hybridIndex])
			return false;
		if (!fluidTanks[hybridIndex].getFluid().isEmpty())
			return false;
		if (!fluidLocks[hybridIndex].isEmpty())
			return false;
		ItemStack lock = itemLocks[hybridIndex];
		if (!lock.isEmpty() && !ItemStack.isSameItemSameTags(lock, stack))
			return false;
		return true;
	}

	public boolean canHybridTankAcceptFluid(int hybridIndex, FluidStack stack) {
		if (hybridIndex < 0 || hybridIndex >= LEG_COUNT)
			return false;
		if (stack.isEmpty())
			return true;
		if (slotBlocked[hybridIndex])
			return false;
		if (!inventory.getStackInSlot(HYBRID_SLOT_START + hybridIndex).isEmpty())
			return false;
		if (!itemLocks[hybridIndex].isEmpty())
			return false;
		FluidStack lock = fluidLocks[hybridIndex];
		if (!lock.isEmpty() && !lock.isFluidEqual(stack))
			return false;
		return fluidTanks[hybridIndex].isFluidValid(stack);
	}

	public void handleLockButton(int hybridIndex, ItemStack carried) {
		if (hybridIndex < 0 || hybridIndex >= LEG_COUNT)
			return;

		if (!carried.isEmpty()) {
			IFluidHandlerItem fluidHandler = carried.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
			FluidStack containerFluid = fluidHandler != null && fluidHandler.getTanks() > 0
				? fluidHandler.getFluidInTank(0)
				: FluidStack.EMPTY;
			if (!containerFluid.isEmpty()) {
				if (!inventory.getStackInSlot(HYBRID_SLOT_START + hybridIndex).isEmpty())
					return;
				FluidStack currentFluid = fluidTanks[hybridIndex].getFluid();
				if (!currentFluid.isEmpty() && !currentFluid.isFluidEqual(containerFluid))
					return;
				FluidStack lock = containerFluid.copy();
				lock.setAmount(1);
				fluidLocks[hybridIndex] = lock;
				itemLocks[hybridIndex] = ItemStack.EMPTY;
				slotBlocked[hybridIndex] = false;
			} else {
				if (!fluidTanks[hybridIndex].getFluid().isEmpty())
					return;
				ItemStack slotItem = inventory.getStackInSlot(HYBRID_SLOT_START + hybridIndex);
				if (!slotItem.isEmpty() && !ItemStack.isSameItemSameTags(slotItem, carried))
					return;
				ItemStack lock = carried.copy();
				lock.setCount(1);
				itemLocks[hybridIndex] = lock;
				fluidLocks[hybridIndex] = FluidStack.EMPTY;
				slotBlocked[hybridIndex] = false;
			}
		} else {
			if (isHybridSlotLocked(hybridIndex)) {
				itemLocks[hybridIndex] = ItemStack.EMPTY;
				fluidLocks[hybridIndex] = FluidStack.EMPTY;
				slotBlocked[hybridIndex] = false;
			} else {
				ItemStack slotItem = inventory.getStackInSlot(HYBRID_SLOT_START + hybridIndex);
				FluidStack slotFluid = fluidTanks[hybridIndex].getFluid();
				if (!slotItem.isEmpty()) {
					ItemStack lock = slotItem.copy();
					lock.setCount(1);
					itemLocks[hybridIndex] = lock;
				} else if (!slotFluid.isEmpty()) {
					FluidStack lock = slotFluid.copy();
					lock.setAmount(1);
					fluidLocks[hybridIndex] = lock;
				} else {
					slotBlocked[hybridIndex] = true;
				}
			}
		}

		setChanged();
		sendData();
	}

	public void handleLockAllButton() {
		boolean anyUnlocked = false;
		for (int i = 0; i < LEG_COUNT; i++) {
			if (!isHybridSlotLocked(i)) {
				anyUnlocked = true;
				break;
			}
		}

		if (anyUnlocked) {
			for (int i = 0; i < LEG_COUNT; i++) {
				if (isHybridSlotLocked(i))
					continue;
				ItemStack slotItem = inventory.getStackInSlot(HYBRID_SLOT_START + i);
				FluidStack slotFluid = fluidTanks[i].getFluid();
				if (!slotItem.isEmpty()) {
					ItemStack lock = slotItem.copy();
					lock.setCount(1);
					itemLocks[i] = lock;
				} else if (!slotFluid.isEmpty()) {
					FluidStack lock = slotFluid.copy();
					lock.setAmount(1);
					fluidLocks[i] = lock;
				} else {
					slotBlocked[i] = true;
				}
			}
		} else {
			for (int i = 0; i < LEG_COUNT; i++) {
				itemLocks[i] = ItemStack.EMPTY;
				fluidLocks[i] = FluidStack.EMPTY;
				slotBlocked[i] = false;
			}
		}

		setChanged();
		sendData();
	}

	private int[] computeItemInsertOrder() {
		int[] order = new int[LEG_COUNT];
		int idx = 0;
		for (int i = 0; i < LEG_COUNT; i++)
			if (getMachineKind(i) == MachineKind.DEPLOYER)
				order[idx++] = i;
		for (int i = 0; i < LEG_COUNT; i++)
			if (getMachineKind(i) != MachineKind.DEPLOYER)
				order[idx++] = i;
		return order;
	}

	private int[] computeItemExtractOrder() {
		int[] order = new int[LEG_COUNT];
		int idx = 0;
		for (int i = 0; i < LEG_COUNT; i++)
			if (getMachineKind(i) != MachineKind.DEPLOYER)
				order[idx++] = i;
		for (int i = 0; i < LEG_COUNT; i++)
			if (getMachineKind(i) == MachineKind.DEPLOYER)
				order[idx++] = i;
		return order;
	}

	public FluidExchangeResult exchangeFluidWithItem(int hybridIndex, ItemStack singleItem) {
		if (hybridIndex < 0 || hybridIndex >= LEG_COUNT)
			return FluidExchangeResult.failure();

		ItemStack slotItem = inventory.getStackInSlot(HYBRID_SLOT_START + hybridIndex);
		if (!slotItem.isEmpty())
			return FluidExchangeResult.failure();

		IFluidHandlerItem itemHandler = singleItem.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
		if (itemHandler == null)
			return FluidExchangeResult.failure();

		FluidTank tank = fluidTanks[hybridIndex];
		boolean changed = false;

		FluidStack itemFluid = itemHandler.getTanks() > 0 ? itemHandler.getFluidInTank(0).copy() : FluidStack.EMPTY;
		if (!itemFluid.isEmpty() && canHybridTankAcceptFluid(hybridIndex, itemFluid)) {
			int canFill = tank.fill(itemFluid, FluidAction.SIMULATE);
			if (canFill > 0) {
				FluidStack drained = itemHandler.drain(canFill, FluidAction.EXECUTE);
				if (!drained.isEmpty()) {
					tank.fill(drained, FluidAction.EXECUTE);
					changed = true;
				}
			}
		}

		if (!changed && !tank.getFluid().isEmpty()) {
			FluidStack tankFluid = tank.getFluid().copy();
			int filled = itemHandler.fill(tankFluid, FluidAction.EXECUTE);
			if (filled > 0) {
				tank.drain(filled, FluidAction.EXECUTE);
				changed = true;
			}
		}

		if (!changed)
			return FluidExchangeResult.failure();

		setChanged();
		sendData();
		return FluidExchangeResult.success(itemHandler.getContainer());
	}

	private void tickAssembly() {
		if (activeSlot < 0)
			return;
		if (getSpeed() == 0)
			return;

		int previousRemaining = processingTicksRemaining;
		processingTicksRemaining--;

		int midpoint = processingTicksTotal / 2;
		if (!impactFiredThisCycle && previousRemaining > midpoint && processingTicksRemaining <= midpoint) {
			triggerImpact();
			impactFiredThisCycle = true;
		}

		if (processingTicksRemaining < 0)
			processingTicksRemaining = 0;
	}

	protected ProcessingResult onItemReceived(TransportedItemStack transported,
		TransportedItemStackHandlerBehaviour handler) {
		if (handler.blockEntity.isVirtual())
			return PASS;
		if (getSpeed() == 0)
			return PASS;
		if (activeSlot >= 0)
			return HOLD;
		if (transported.stack.isEmpty())
			return PASS;
		if (!startProcessFor(transported.stack))
			return PASS;
		return HOLD;
	}

	protected ProcessingResult whileItemHeld(TransportedItemStack transported,
		TransportedItemStackHandlerBehaviour handler) {
		if (getSpeed() == 0)
			return PASS;

		if (activeSlot < 0) {
			if (transported.stack.isEmpty())
				return PASS;
			if (!startProcessFor(transported.stack))
				return PASS;
			return HOLD;
		}

		if (processingTicksRemaining > 0)
			return HOLD;

		List<ItemStack> outputs = applyActiveProcess(cachedInput.isEmpty() ? transported.stack : cachedInput);

		List<TransportedItemStack> outList = new ArrayList<>();
		for (ItemStack out : outputs) {
			if (out.isEmpty())
				continue;
			TransportedItemStack copy = transported.copy();
			copy.stack = out;
			copy.locked = true;
			outList.add(copy);
		}

		ItemStack remaining = transported.stack.copy();
		remaining.shrink(1);
		TransportedItemStack held = null;
		if (!remaining.isEmpty()) {
			held = transported.copy();
			held.stack = remaining;
		}

		transported.clearFanProcessingData();

		if (outList.isEmpty() && held == null) {
			handler.handleProcessingOnItem(transported, TransportedResult.removeItem());
		} else if (held == null) {
			handler.handleProcessingOnItem(transported, TransportedResult.convertTo(outList));
		} else if (outList.isEmpty()) {
			handler.handleProcessingOnItem(transported, TransportedResult.convertTo(List.of(held)));
		} else {
			handler.handleProcessingOnItem(transported, TransportedResult.convertToAndLeaveHeld(outList, held));
		}

		if (containsCompletedSequencedAssembly(outputs))
			PlacedByPlayerAdvancementTracker.awardPlacedBy(level, advancementOwner, CBAdvancements.SPIDER_ASSEMBLY_TABLE);

		resetActiveProcess();
		setChanged();
		sendData();
		return HOLD;
	}

	private void resetActiveProcess() {
		activeSlot = -1;
		activeMachine = null;
		processingTicksRemaining = 0;
		processingTicksTotal = 0;
		impactFiredThisCycle = false;
		cachedInput = ItemStack.EMPTY;
	}

	private void triggerImpact() {
		if (level == null || activeMachine == null)
			return;
		playMachineSound();
		impactDisplayItem = resolveImpactItem().copy();
		impactPending = true;
		sendData();
	}

	private ItemStack resolveImpactItem() {
		if (activeMachine == MachineKind.DEPLOYER && activeSlot >= 0) {
			ItemStack held = inventory.getStackInSlot(HYBRID_SLOT_START + activeSlot);
			if (!held.isEmpty())
				return held;
		}
		return cachedInput;
	}

	private void playMachineSound() {
		if (level == null || activeMachine == null)
			return;
		float speedFactor = Math.abs(getSpeed()) / 1024f;
		switch (activeMachine) {
		case PRESS -> AllSoundEvents.MECHANICAL_PRESS_ACTIVATION.playOnServer(level, worldPosition, 0.5f,
			0.75f + speedFactor);
		case SAW -> AllSoundEvents.SAW_ACTIVATE_WOOD.playOnServer(level, worldPosition, 0.75f,
			0.85f + 0.15f * level.random.nextFloat());
		case SPOUT -> AllSoundEvents.SPOUTING.playOnServer(level, worldPosition, 0.75f,
			0.9f + 0.2f * level.random.nextFloat());
		case DEPLOYER -> AllSoundEvents.CRAFTER_CLICK.playOnServer(level, worldPosition, 0.75f,
			0.85f + 0.15f * level.random.nextFloat());
		}
	}

	private void spawnImpactParticles(MachineKind machine, ItemStack item) {
		if (level == null || !level.isClientSide || machine == null || item.isEmpty())
			return;

		Vec3 origin = Vec3.atCenterOf(worldPosition.below(2)).add(0, 7 / 16f, 0);
		int amount;
		float upward;
		switch (machine) {
		case PRESS -> {
			amount = 16;
			upward = 0.18f;
		}
		case SAW -> {
			amount = 12;
			upward = 0.25f;
		}
		case SPOUT -> {
			amount = 8;
			upward = 0.04f;
		}
		default -> {
			amount = 6;
			upward = 0.1f;
		}
		}

		for (int i = 0; i < amount; i++) {
			Vec3 motion = VecHelper.offsetRandomly(Vec3.ZERO, level.random, 0.125f).multiply(1, 0, 1);
			motion = motion.add(0, upward, 0);
			level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, item),
				origin.x, origin.y, origin.z, motion.x, motion.y, motion.z);
		}
	}

	private boolean startProcessFor(ItemStack input) {
		if (input.isEmpty())
			return false;
		ItemStack singleInput = input.copy();
		singleInput.setCount(1);

		for (int attempt = 0; attempt < LEG_COUNT; attempt++) {
			int slot = (nextSlot + attempt) % LEG_COUNT;
			MachineKind kind = getMachineKind(slot);
			if (kind == null)
				continue;

			Optional<ProcessingPlan> plan = createPlan(slot, kind, singleInput);
			if (plan.isEmpty())
				continue;

			activeSlot = slot;
			activeMachine = kind;
			processingTicksTotal = Math.max(1, plan.get().duration());
			processingTicksRemaining = processingTicksTotal;
			impactFiredThisCycle = false;
			nextSlot = (slot + 1) % LEG_COUNT;
			cachedInput = singleInput;
			setChanged();
			sendData();
			return true;
		}
		return false;
	}

	private Optional<ProcessingPlan> createPlan(int slot, MachineKind kind, ItemStack input) {
		if (level == null)
			return Optional.empty();
		return switch (kind) {
		case DEPLOYER -> findDeployingRecipe(slot, input).map(recipe -> new ProcessingPlan(kind, getDeployerDuration()));
		case PRESS -> findPressingRecipe(input).map(recipe -> new ProcessingPlan(kind, getPressDuration()));
		case SAW -> findCuttingRecipe(input).map(recipe -> new ProcessingPlan(kind, getSawDuration(recipe)));
		case SPOUT -> findFillingRecipe(slot, input).map(recipe -> new ProcessingPlan(kind, SpoutBlockEntity.FILLING_TIME));
		};
	}

	private List<ItemStack> applyActiveProcess(ItemStack input) {
		if (activeMachine == null || activeSlot < 0 || input.isEmpty())
			return List.of();

		ItemStack singleInput = input.copy();
		singleInput.setCount(1);

		return switch (activeMachine) {
		case DEPLOYER -> processDeploying(activeSlot, singleInput);
		case PRESS -> processPressing(singleInput);
		case SAW -> processCutting(singleInput);
		case SPOUT -> processFilling(activeSlot, singleInput);
		};
	}

	private Optional<PressingRecipe> findPressingRecipe(ItemStack input) {
		return SequencedAssemblyRecipe.getRecipe(level, input, AllRecipeTypes.PRESSING.getType(),
			PressingRecipe.class);
	}

	private Optional<CuttingRecipe> findCuttingRecipe(ItemStack input) {
		return SequencedAssemblyRecipe.getRecipe(level, input, AllRecipeTypes.CUTTING.getType(), CuttingRecipe.class);
	}

	private Optional<DeployerApplicationRecipe> findDeployingRecipe(int slot, ItemStack input) {
		ItemStack held = inventory.getStackInSlot(HYBRID_SLOT_START + slot);
		if (held.isEmpty())
			return Optional.empty();

		ItemStackHandler recipeInventory = new ItemStackHandler(2);
		recipeInventory.setStackInSlot(0, input.copy());
		recipeInventory.setStackInSlot(1, held.copy());
		RecipeWrapper wrapper = new RecipeWrapper(recipeInventory);
		return SequencedAssemblyRecipe.getRecipe(level, wrapper, AllRecipeTypes.DEPLOYING.getType(),
			DeployerApplicationRecipe.class, recipe -> recipe.matches(wrapper, level));
	}

	private Optional<FillingRecipe> findFillingRecipe(int slot, ItemStack input) {
		FluidStack fluid = fluidTanks[slot].getFluid();
		if (fluid.isEmpty())
			return Optional.empty();

		ItemStackHandler recipeInventory = new ItemStackHandler(1);
		recipeInventory.setStackInSlot(0, input.copy());
		RecipeWrapper wrapper = new RecipeWrapper(recipeInventory);
		return SequencedAssemblyRecipe.getRecipe(level, wrapper, AllRecipeTypes.FILLING.getType(), FillingRecipe.class,
			recipe -> recipe.matches(wrapper, level) && recipe.getRequiredFluid().test(fluid));
	}

	private List<ItemStack> processPressing(ItemStack input) {
		return findPressingRecipe(input)
			.map(recipe -> RecipeApplier.applyRecipeOn(level, input.copy(), recipe, true))
			.orElse(List.of());
	}

	private List<ItemStack> processCutting(ItemStack input) {
		return findCuttingRecipe(input)
			.map(recipe -> RecipeApplier.applyRecipeOn(level, input.copy(), recipe, true))
			.orElse(List.of());
	}

	private List<ItemStack> processDeploying(int slot, ItemStack input) {
		Optional<DeployerApplicationRecipe> recipe = findDeployingRecipe(slot, input);
		if (recipe.isEmpty())
			return List.of();
		List<ItemStack> outputs = RecipeApplier.applyRecipeOn(level, input.copy(), recipe.get(), true);
		consumeDeployingItem(slot, recipe.get());
		return outputs;
	}

	private List<ItemStack> processFilling(int slot, ItemStack input) {
		Optional<FillingRecipe> recipeOpt = findFillingRecipe(slot, input);
		if (recipeOpt.isEmpty())
			return List.of();
		FillingRecipe recipe = recipeOpt.get();
		FluidIngredient requiredFluid = recipe.getRequiredFluid();
		FluidStack available = fluidTanks[slot].getFluid();
		if (!requiredFluid.test(available))
			return List.of();
		fluidTanks[slot].drain(requiredFluid.getRequiredAmount(), FluidAction.EXECUTE);
		return recipe.rollResults();
	}

	private void consumeDeployingItem(int slot, DeployerApplicationRecipe recipe) {
		if (recipe.shouldKeepHeldItem())
			return;

		int hybridSlot = HYBRID_SLOT_START + slot;
		ItemStack held = inventory.getStackInSlot(hybridSlot);
		if (held.isEmpty())
			return;

		ItemStack remainder = held.getCraftingRemainingItem();
		if (held.isDamageableItem()) {
			held.setDamageValue(held.getDamageValue() + 1);
			if (held.getDamageValue() >= held.getMaxDamage())
				held.shrink(1);
			inventory.setStackInSlot(hybridSlot, held);
			return;
		}

		held.shrink(1);
		inventory.setStackInSlot(hybridSlot, held);
		if (!remainder.isEmpty())
			insertIntoItemCacheOrDrop(slot, remainder);
	}

	private void insertIntoItemCacheOrDrop(int preferredSlot, ItemStack stack) {
		if (stack.isEmpty())
			return;

		int slot = HYBRID_SLOT_START + preferredSlot;
		if (canHoldItemInHybrid(preferredSlot)) {
			ItemStack existing = inventory.getStackInSlot(slot);
			if (existing.isEmpty()) {
				inventory.setStackInSlot(slot, stack);
				return;
			}
			if (ItemHandlerHelper.canItemStacksStack(existing, stack)) {
				int moved = Math.min(existing.getMaxStackSize() - existing.getCount(), stack.getCount());
				existing.grow(moved);
				stack.shrink(moved);
				inventory.setStackInSlot(slot, existing);
				if (stack.isEmpty())
					return;
			}
		}

		for (int i = 0; i < LEG_COUNT; i++) {
			if (!canHoldItemInHybrid(i))
				continue;
			int hybridSlot = HYBRID_SLOT_START + i;
			ItemStack remainder = inventory.insertItem(hybridSlot, stack, false);
			if (remainder.isEmpty())
				return;
			stack = remainder;
		}

		dropStackNearTable(stack);
	}

	private boolean canHoldItemInHybrid(int hybridIndex) {
		return fluidTanks[hybridIndex].getFluid().isEmpty();
	}

	private void dropStackNearTable(ItemStack stack) {
		if (level == null || stack.isEmpty())
			return;
		ItemEntity entity = new ItemEntity(level, worldPosition.getX() + 0.5d, worldPosition.getY() + 0.5d,
			worldPosition.getZ() + 0.5d, stack.copy());
		entity.setDefaultPickUpDelay();
		level.addFreshEntity(entity);
	}

	private boolean containsCompletedSequencedAssembly(List<ItemStack> outputs) {
		for (ItemStack output : outputs) {
			if (output.isEmpty())
				continue;
			if (!output.hasTag() || !output.getTag().contains("SequencedAssembly"))
				return true;
		}
		return false;
	}

	private int getPressDuration() {
		int tickSpeed = Math.max(1, (int) Mth.lerp(Mth.clamp(Math.abs(getSpeed()) / 512f, 0, 1), 1, 60));
		return Mth.ceil(PressingBehaviour.CYCLE / (float) tickSpeed);
	}

	private int getDeployerDuration() {
		int timerSpeed = Math.max(1, (int) Mth.clamp(Math.abs(getSpeed() * 2), 8, 512));
		return Mth.ceil(2000f / timerSpeed);
	}

	private int getSawDuration(CuttingRecipe recipe) {
		int recipeDuration = recipe.getProcessingDuration();
		if (recipeDuration <= 0)
			recipeDuration = 50;
		float processingSpeed = Mth.clamp(Math.abs(getSpeed()) / 24f, 1, 128);
		return Mth.ceil(recipeDuration / processingSpeed);
	}

	private MachineKind getMachineKind(int slot) {
		return MachineKind.fromStack(inventory.getStackInSlot(MACHINE_SLOT_START + slot));
	}

	public void setAdvancementOwner(@Nullable LivingEntity placer) {
		advancementOwner = PlacedByPlayerAdvancementTracker.ownerFrom(placer);
		setChanged();
	}

	private void updateStressFromMachineSlots() {
		if (level == null || level.isClientSide || !hasNetwork())
			return;
		getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
	}

	private void onInventoryChanged(int slot) {
		setChanged();
		if (slot >= MACHINE_SLOT_START && slot < MACHINE_SLOT_START + LEG_COUNT)
			updateStressFromMachineSlots();
		sendData();
	}

	private void onFluidChanged() {
		setChanged();
		sendData();
	}

	public enum MachineKind {
		DEPLOYER(AllBlocks.DEPLOYER.get()),
		PRESS(AllBlocks.MECHANICAL_PRESS.get()),
		SAW(AllBlocks.MECHANICAL_SAW.get()),
		SPOUT(AllBlocks.SPOUT.get());

		private final Block block;

		MachineKind(Block block) {
			this.block = block;
		}

		public Block block() {
			return block;
		}

		@Nullable
		public static MachineKind fromStack(ItemStack stack) {
			if (AllBlocks.DEPLOYER.isIn(stack))
				return DEPLOYER;
			if (AllBlocks.MECHANICAL_PRESS.isIn(stack))
				return PRESS;
			if (AllBlocks.MECHANICAL_SAW.isIn(stack))
				return SAW;
			if (AllBlocks.SPOUT.isIn(stack))
				return SPOUT;
			return null;
		}
	}

	private record ProcessingPlan(MachineKind kind, int duration) {}

	public record FluidExchangeResult(boolean success, ItemStack remainingContainer) {
		public static FluidExchangeResult failure() {
			return new FluidExchangeResult(false, ItemStack.EMPTY);
		}

		public static FluidExchangeResult success(ItemStack remainingContainer) {
			return new FluidExchangeResult(true, remainingContainer);
		}
	}

	private class SpiderAssemblyInventory extends ItemStackHandler {

		private SpiderAssemblyInventory() {
			super(SLOT_COUNT);
		}

		@Override
		protected void onContentsChanged(int slot) {
			SpiderAssemblyTableBlockEntity.this.onInventoryChanged(slot);
		}

		@Override
		public int getSlotLimit(int slot) {
			if (slot < HYBRID_SLOT_START)
				return 1;
			return 64;
		}

		@Override
		public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
			if (slot < HYBRID_SLOT_START)
				return MachineKind.fromStack(stack) != null;
			int hybridIndex = slot - HYBRID_SLOT_START;
			return canHybridSlotAcceptItem(hybridIndex, stack);
		}
	}

	private class HybridItemWrapper implements IItemHandlerModifiable {

		@Override
		public int getSlots() {
			return LEG_COUNT;
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			return inventory.getStackInSlot(HYBRID_SLOT_START + mapExtractIndex(slot));
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			if (stack.isEmpty())
				return ItemStack.EMPTY;
			int[] order = computeItemInsertOrder();
			ItemStack remaining = stack;
			for (int hybridIdx : order) {
				remaining = inventory.insertItem(HYBRID_SLOT_START + hybridIdx, remaining, simulate);
				if (remaining.isEmpty())
					break;
			}
			return remaining;
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			return inventory.extractItem(HYBRID_SLOT_START + mapExtractIndex(slot), amount, simulate);
		}

		@Override
		public int getSlotLimit(int slot) {
			return inventory.getSlotLimit(HYBRID_SLOT_START + mapExtractIndex(slot));
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return inventory.isItemValid(HYBRID_SLOT_START + mapExtractIndex(slot), stack);
		}

		@Override
		public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
			inventory.setStackInSlot(HYBRID_SLOT_START + mapExtractIndex(slot), stack);
		}

		private int mapExtractIndex(int visibleSlot) {
			int[] order = computeItemExtractOrder();
			if (visibleSlot < 0 || visibleSlot >= order.length)
				return 0;
			return order[visibleSlot];
		}
	}

	private class SpiderFluidTank extends FluidTank {
		private SpiderFluidTank() {
			super(FLUID_CAPACITY);
		}

		@Override
		protected void onContentsChanged() {
			SpiderAssemblyTableBlockEntity.this.onFluidChanged();
		}
	}

	private class SpiderFluidHandler implements IFluidHandler {
		@Override
		public int getTanks() {
			return LEG_COUNT;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			return fluidTanks[tank].getFluid();
		}

		@Override
		public int getTankCapacity(int tank) {
			return fluidTanks[tank].getCapacity();
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return canHybridTankAcceptFluid(tank, stack);
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			if (resource.isEmpty())
				return 0;

			int totalFilled = 0;
			boolean[] passMatching = { true, true, false, false };
			boolean[] passNonEmpty = { true, false, true, false };

			for (int pass = 0; pass < 4; pass++) {
				for (int i = 0; i < LEG_COUNT; i++) {
					boolean isMatching = getMachineKind(i) == MachineKind.SPOUT;
					if (passMatching[pass] != isMatching)
						continue;
					FluidTank tank = fluidTanks[i];
					boolean tankNonEmpty = !tank.getFluid().isEmpty();
					if (passNonEmpty[pass] != tankNonEmpty)
						continue;
					if (!canHybridTankAcceptFluid(i, resource))
						continue;

					int remaining = resource.getAmount() - totalFilled;
					if (remaining <= 0)
						return totalFilled;

					FluidStack attempt = resource.copy();
					attempt.setAmount(remaining);
					totalFilled += tank.fill(attempt, action);
				}
				if (totalFilled >= resource.getAmount())
					return totalFilled;
			}
			return totalFilled;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			if (resource.isEmpty())
				return FluidStack.EMPTY;

			FluidStack totalDrained = FluidStack.EMPTY;
			int remaining = resource.getAmount();
			boolean[] passMatching = { false, true };

			for (int pass = 0; pass < 2; pass++) {
				for (int i = 0; i < LEG_COUNT; i++) {
					boolean isMatching = getMachineKind(i) == MachineKind.SPOUT;
					if (passMatching[pass] != isMatching)
						continue;
					FluidTank tank = fluidTanks[i];
					if (!tank.getFluid().isFluidEqual(resource))
						continue;
					FluidStack part = tank.drain(remaining, action);
					if (part.isEmpty())
						continue;
					if (totalDrained.isEmpty())
						totalDrained = part.copy();
					else
						totalDrained.grow(part.getAmount());
					remaining -= part.getAmount();
					if (remaining <= 0)
						return totalDrained;
				}
			}
			return totalDrained;
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			if (maxDrain <= 0)
				return FluidStack.EMPTY;
			boolean[] passMatching = { false, true };
			for (int pass = 0; pass < 2; pass++) {
				for (int i = 0; i < LEG_COUNT; i++) {
					boolean isMatching = getMachineKind(i) == MachineKind.SPOUT;
					if (passMatching[pass] != isMatching)
						continue;
					FluidStack drained = fluidTanks[i].drain(maxDrain, action);
					if (!drained.isEmpty())
						return drained;
				}
			}
			return FluidStack.EMPTY;
		}
	}

	public static BlockEntityType<SpiderAssemblyTableBlockEntity> type() {
		return CBBlockEntityTypes.SPIDER_ASSEMBLY_TABLE.get();
	}
}
