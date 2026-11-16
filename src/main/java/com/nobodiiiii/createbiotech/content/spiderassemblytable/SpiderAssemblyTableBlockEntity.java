package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import java.util.Optional;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;

import net.minecraft.core.HolderLookup;


import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.foundation.advancement.PlacedByPlayerAdvancementTracker;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;

public class SpiderAssemblyTableBlockEntity extends KineticBlockEntity implements MenuProvider {

	private static final String CASING_TAG = "SpiderCasing";
	public static final int LEG_COUNT = 8;
	public static final int MACHINE_SLOT_START = 0;
	public static final int HYBRID_SLOT_START = MACHINE_SLOT_START + LEG_COUNT;
	public static final int SLOT_COUNT = HYBRID_SLOT_START + LEG_COUNT;

	final SpiderAssemblyInventory inventory = new SpiderAssemblyInventory();
	final FluidTank[] fluidTanks = new FluidTank[LEG_COUNT];
	final ItemStack[] itemLocks = new ItemStack[LEG_COUNT];
	final FluidStack[] fluidLocks = new FluidStack[LEG_COUNT];
	final boolean[] slotBlocked = new boolean[LEG_COUNT];
	private final IItemHandler itemCapability = new HybridItemWrapper();
	private final IFluidHandler fluidCapability = new SpiderFluidHandler();

	int nextSlot;
	int activeSlot = -1;
	MachineKind activeMachine;
	int processingTicksRemaining;
	int processingTicksTotal;
	boolean processAppliedThisCycle;
	private boolean impactPending;
	private ItemStack impactDisplayItem = ItemStack.EMPTY;
	ItemStack cachedInput = ItemStack.EMPTY;
	@Nullable
	private Block casing;
	@Nullable
	UUID advancementOwner;

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

		SpiderProcessController.tickAssembly(this);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		beltProcessing = new BeltProcessingBehaviour(this)
			.whenItemEnters(this::onItemReceived)
			.whileItemHeld(this::whileItemHeld);
		behaviours.add(beltProcessing);
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		if (casing != null)
			tag.putString(CASING_TAG, BuiltInRegistries.BLOCK.getKey(casing).toString());
		tag.put("Inventory", inventory.serializeNBT(registries));
		ListTag fluids = new ListTag();
		for (FluidTank tank : fluidTanks)
			fluids.add(tank.writeToNBT(registries, new CompoundTag()));
		tag.put("Fluids", fluids);
		ListTag itemLocksTag = new ListTag();
		for (ItemStack lock : itemLocks)
			itemLocksTag.add(lock.saveOptional(registries));
		tag.put("ItemLocks", itemLocksTag);
		ListTag fluidLocksTag = new ListTag();
		for (FluidStack lock : fluidLocks)
			fluidLocksTag.add(lock.saveOptional(registries));
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
		tag.putBoolean("ProcessApplied", processAppliedThisCycle);
		if (!cachedInput.isEmpty())
			tag.put("CachedInput", cachedInput.saveOptional(registries));
		PlacedByPlayerAdvancementTracker.writeOwner(tag, advancementOwner);
		if (clientPacket && impactPending) {
			tag.putBoolean("Impact", true);
			if (!impactDisplayItem.isEmpty())
				tag.put("ImpactItem", impactDisplayItem.saveOptional(registries));
			impactPending = false;
			impactDisplayItem = ItemStack.EMPTY;
		}
		super.write(tag, registries, clientPacket);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		casing = null;
		ResourceLocation casingId = ResourceLocation.tryParse(tag.getString(CASING_TAG));
		if (casingId != null)
			casing = BuiltInRegistries.BLOCK.getOptional(casingId).orElse(null);
		inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
		ListTag fluids = tag.getList("Fluids", Tag.TAG_COMPOUND);
		for (int i = 0; i < fluidTanks.length && i < fluids.size(); i++)
			fluidTanks[i].readFromNBT(registries, fluids.getCompound(i));
		ListTag itemLocksTag = tag.getList("ItemLocks", Tag.TAG_COMPOUND);
		for (int i = 0; i < itemLocks.length; i++)
			itemLocks[i] = i < itemLocksTag.size()
				? ItemStack.parseOptional(registries, itemLocksTag.getCompound(i))
				: ItemStack.EMPTY;
		ListTag fluidLocksTag = tag.getList("FluidLocks", Tag.TAG_COMPOUND);
		for (int i = 0; i < fluidLocks.length; i++)
			fluidLocks[i] = i < fluidLocksTag.size()
				? FluidStack.parseOptional(registries, fluidLocksTag.getCompound(i))
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
		processAppliedThisCycle = tag.getBoolean("ProcessApplied");
		cachedInput = tag.contains("CachedInput")
			? ItemStack.parseOptional(registries, tag.getCompound("CachedInput"))
			: ItemStack.EMPTY;
		advancementOwner = PlacedByPlayerAdvancementTracker.readOwner(tag);
		super.read(tag, registries, clientPacket);

		if (clientPacket && tag.getBoolean("Impact")) {
			ItemStack item = tag.contains("ImpactItem")
				? ItemStack.parseOptional(registries, tag.getCompound("ImpactItem"))
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

	/**
	 * The exact casing applied to this table. Old saves only had the boolean block-state property,
	 * so an encased table without this field remains an andesite-encased table.
	 */
	@Nullable
	public Block getCasing() {
		if (casing != null)
			return casing;
		return getBlockState().hasProperty(SpiderAssemblyTableBlock.CASING)
			&& getBlockState().getValue(SpiderAssemblyTableBlock.CASING)
			? AllBlocks.ANDESITE_CASING.get()
			: null;
	}

	public void setCasing(@Nullable Block casing) {
		if (this.casing == casing)
			return;
		this.casing = casing;
		setChanged();
		sendData();
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

	@Override
	public void sendToMenu(RegistryFriendlyByteBuf buffer) {
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

	public IItemHandler getItemCapability(@Nullable Direction side) {
		return itemCapability;
	}

	public IFluidHandler getFluidCapability(@Nullable Direction side) {
		return fluidCapability;
	}

	public boolean isHybridSlotItemOnly(int hybridIndex) {
		return SpiderHybridStorage.isHybridSlotItemOnly(this, hybridIndex);
	}

	public boolean isHybridSlotFluidOnly(int hybridIndex) {
		return SpiderHybridStorage.isHybridSlotFluidOnly(this, hybridIndex);
	}

	public ItemStack getItemLock(int hybridIndex) {
		return SpiderHybridStorage.getItemLock(this, hybridIndex);
	}

	public FluidStack getFluidLock(int hybridIndex) {
		return SpiderHybridStorage.getFluidLock(this, hybridIndex);
	}

	public boolean isHybridSlotLocked(int hybridIndex) {
		return SpiderHybridStorage.isHybridSlotLocked(this, hybridIndex);
	}

	public boolean areAllHybridSlotsLocked() {
		return SpiderHybridStorage.areAllHybridSlotsLocked(this);
	}

	public boolean isHybridSlotBlocked(int hybridIndex) {
		return SpiderHybridStorage.isHybridSlotBlocked(this, hybridIndex);
	}

	public boolean canHybridSlotAcceptItem(int hybridIndex, ItemStack stack) {
		return SpiderHybridStorage.canHybridSlotAcceptItem(this, hybridIndex, stack);
	}

	public boolean canHybridTankAcceptFluid(int hybridIndex, FluidStack stack) {
		return SpiderHybridStorage.canHybridTankAcceptFluid(this, hybridIndex, stack);
	}

	public void handleLockButton(int hybridIndex, ItemStack carried) {
		SpiderHybridStorage.handleLockButton(this, hybridIndex, carried);
	}

	public void handleLockAllButton() {
		SpiderHybridStorage.handleLockAllButton(this);
	}

	public FluidExchangeResult exchangeFluidWithItem(int hybridIndex, ItemStack singleItem) {
		return SpiderHybridStorage.exchangeFluidWithItem(this, hybridIndex, singleItem);
	}

	protected ProcessingResult onItemReceived(TransportedItemStack transported,
		TransportedItemStackHandlerBehaviour handler) {
		return SpiderProcessController.onItemReceived(this, transported, handler);
	}

	protected ProcessingResult whileItemHeld(TransportedItemStack transported,
		TransportedItemStackHandlerBehaviour handler) {
		return SpiderProcessController.whileItemHeld(this, transported, handler);
	}

	void triggerImpact(ItemStack impactItem) {
		if (level == null || activeMachine == null)
			return;
		playMachineSound(impactItem);
		impactDisplayItem = impactItem.copy();
		impactPending = true;
		sendData();
	}

	ItemStack resolveImpactItem() {
		if (activeMachine == MachineKind.DEPLOYER && activeSlot >= 0) {
			ItemStack held = inventory.getStackInSlot(HYBRID_SLOT_START + activeSlot);
			if (!held.isEmpty())
				return held;
		}
		return cachedInput;
	}

	private void playMachineSound(ItemStack impactItem) {
		if (level == null || activeMachine == null)
			return;
		float speedFactor = Math.abs(getSpeed()) / 1024f;
		switch (activeMachine) {
		case PRESS -> {
			BlockPos supportPos = worldPosition.below(2);
			if (level.getBlockState(supportPos).getSoundType(level, supportPos, null) == SoundType.WOOL)
				AllSoundEvents.MECHANICAL_PRESS_ACTIVATION_ON_BELT.playOnServer(level, worldPosition);
			else
				AllSoundEvents.MECHANICAL_PRESS_ACTIVATION.playOnServer(level, worldPosition, 0.5f,
					0.75f + speedFactor);
		}
		case SAW -> {
			boolean isWood = impactItem.getItem() instanceof BlockItem blockItem
				&& blockItem.getBlock().getSoundType(blockItem.getBlock().defaultBlockState(), level,
					worldPosition, null) == SoundType.WOOD;
			(isWood ? AllSoundEvents.SAW_ACTIVATE_WOOD : AllSoundEvents.SAW_ACTIVATE_STONE)
				.playOnServer(level, worldPosition, 3, 1);
		}
		case SPOUT -> AllSoundEvents.SPOUTING.playOnServer(level, worldPosition, 0.75f,
			0.9f + 0.2f * level.random.nextFloat());
		case DEPLOYER -> level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS,
			0.25f, 0.75f);
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

	boolean containsCompletedSequencedAssembly(List<ItemStack> outputs) {
		if (outputs.isEmpty())
			return false;
		ItemStack workpiece = outputs.getFirst();
		return !workpiece.isEmpty() && !workpiece.has(AllDataComponents.SEQUENCED_ASSEMBLY);
	}

	int getPressDuration() {
		int tickSpeed = Math.max(1, (int) Mth.lerp(Mth.clamp(Math.abs(getSpeed()) / 512f, 0, 1), 1, 60));
		return Mth.ceil(PressingBehaviour.CYCLE / (float) tickSpeed);
	}

	int getDeployerDuration() {
		int timerSpeed = Math.max(1, (int) Mth.clamp(Math.abs(getSpeed() * 2), 8, 512));
		return Mth.ceil((float) getDeployerBaseDuration() / timerSpeed);
	}

	int getSawDuration(CuttingRecipe recipe) {
		int recipeDuration = recipe.getProcessingDuration();
		if (recipeDuration <= 0)
			recipeDuration = getSawFallbackDuration();
		float processingSpeed = Mth.clamp((float) (Math.abs(getSpeed()) / getSawSpeedDivisor()), 1, 128);
		return Mth.ceil(recipeDuration / processingSpeed);
	}

	private static int getFluidCapacityPerLeg() {
		return CBConfigs.SERVER.spiderAssemblyTable.fluidCapacityPerLeg.get();
	}

	private static double getDeployerBaseDuration() {
		return CBConfigs.SERVER.spiderAssemblyTable.deployerBaseDuration.get();
	}

	private static int getSawFallbackDuration() {
		return CBConfigs.SERVER.spiderAssemblyTable.sawFallbackDuration.get();
	}

	private static double getSawSpeedDivisor() {
		return CBConfigs.SERVER.spiderAssemblyTable.sawSpeedDivisor.get();
	}

	MachineKind getMachineKind(int slot) {
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

	record ProcessingPlan(MachineKind kind, int duration) {}

	public record FluidExchangeResult(boolean success, ItemStack remainingContainer) {
		public static FluidExchangeResult failure() {
			return new FluidExchangeResult(false, ItemStack.EMPTY);
		}

		public static FluidExchangeResult success(ItemStack remainingContainer) {
			return new FluidExchangeResult(true, remainingContainer);
		}
	}

	class SpiderAssemblyInventory extends ItemStackHandler {

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
			return SpiderHybridStorage.canHybridSlotAcceptItem(SpiderAssemblyTableBlockEntity.this, hybridIndex, stack);
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
			int[] order = SpiderHybridStorage.computeItemInsertOrder(SpiderAssemblyTableBlockEntity.this);
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
			int[] order = SpiderHybridStorage.computeItemExtractOrder(SpiderAssemblyTableBlockEntity.this);
			if (visibleSlot < 0 || visibleSlot >= order.length)
				return 0;
			return order[visibleSlot];
		}
	}

	private class SpiderFluidTank extends FluidTank {
		private SpiderFluidTank() {
			super(getFluidCapacityPerLeg());
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
			return SpiderHybridStorage.canHybridTankAcceptFluid(SpiderAssemblyTableBlockEntity.this, tank, stack);
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
					if (!SpiderHybridStorage.canHybridTankAcceptFluid(SpiderAssemblyTableBlockEntity.this, i, resource))
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
					if (!FluidStack.isSameFluidSameComponents(tank.getFluid(), resource))
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

	// Keep dispatch beside the private enum to retain its direct ordinal switch.
	Optional<ProcessingPlan> createPlan(int slot, MachineKind kind, ItemStack input) {
		if (this.getLevel() == null)
			return Optional.empty();
		return switch (kind) {
		case DEPLOYER -> SpiderProcessPlanner.findDeployingRecipe(this, slot, input).map(recipe -> new SpiderAssemblyTableBlockEntity.ProcessingPlan(kind, this.getDeployerDuration()));
		case PRESS -> SpiderProcessPlanner.findPressingRecipe(this, input).map(recipe -> new SpiderAssemblyTableBlockEntity.ProcessingPlan(kind, this.getPressDuration()));
		case SAW -> SpiderProcessPlanner.findCuttingRecipe(this, input).map(recipe -> new SpiderAssemblyTableBlockEntity.ProcessingPlan(kind, this.getSawDuration(recipe)));
		case SPOUT -> SpiderProcessPlanner.findFillingRecipe(this, slot, input).map(recipe -> new SpiderAssemblyTableBlockEntity.ProcessingPlan(kind, SpoutBlockEntity.FILLING_TIME));
		};
	}

	// Keep dispatch beside the private enum to retain its direct ordinal switch.
	List<ItemStack> applyActiveProcess(ItemStack input) {
		if (this.activeMachine == null || this.activeSlot < 0 || input.isEmpty())
			return List.of();

		ItemStack singleInput = input.copy();
		singleInput.setCount(1);

		return switch (this.activeMachine) {
		case DEPLOYER -> SpiderProcessPlanner.processDeploying(this, this.activeSlot, singleInput);
		case PRESS -> SpiderProcessPlanner.processPressing(this, singleInput);
		case SAW -> SpiderProcessPlanner.processCutting(this, singleInput);
		case SPOUT -> SpiderProcessPlanner.processFilling(this, this.activeSlot, singleInput);
		};
	}
}
