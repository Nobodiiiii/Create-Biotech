package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import com.simibubi.create.foundation.recipe.RecipeApplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
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

	public static final int LEG_COUNT = 6;
	public static final int MACHINE_SLOT_START = 0;
	public static final int ITEM_CACHE_SLOT_START = MACHINE_SLOT_START + LEG_COUNT;
	public static final int FLUID_CONTAINER_SLOT_START = ITEM_CACHE_SLOT_START + LEG_COUNT;
	public static final int SLOT_COUNT = FLUID_CONTAINER_SLOT_START + LEG_COUNT;
	public static final int FLUID_CAPACITY = 1000;

	private final SpiderAssemblyInventory inventory = new SpiderAssemblyInventory();
	private final FluidTank[] fluidTanks = new FluidTank[LEG_COUNT];
	private final LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> new ItemCacheWrapper(inventory));
	private final LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> new SpiderFluidHandler());

	private int nextSlot;
	private int activeSlot = -1;
	private MachineKind activeMachine;
	private int processingTicksRemaining;
	private int processingTicksTotal;

	public SpiderAssemblyTableBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.SPIDER_ASSEMBLY_TABLE.get(), pos, state);
		for (int i = 0; i < fluidTanks.length; i++)
			fluidTanks[i] = new SpiderFluidTank();
	}

	public static void tick(Level level, BlockPos pos, BlockState state, SpiderAssemblyTableBlockEntity be) {
		be.tick();
	}

	@Override
	public void tick() {
		super.tick();

		if (level == null || level.isClientSide)
			return;

		processFluidContainerSlots();
		tickAssembly();
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		tag.put("Inventory", inventory.serializeNBT());
		ListTag fluids = new ListTag();
		for (FluidTank tank : fluidTanks)
			fluids.add(tank.writeToNBT(new CompoundTag()));
		tag.put("Fluids", fluids);
		tag.putInt("NextSlot", nextSlot);
		tag.putInt("ActiveSlot", activeSlot);
		tag.putInt("ActiveMachine", activeMachine == null ? -1 : activeMachine.ordinal());
		tag.putInt("ProcessingTicksRemaining", processingTicksRemaining);
		tag.putInt("ProcessingTicksTotal", processingTicksTotal);
		super.write(tag, clientPacket);
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		inventory.deserializeNBT(tag.getCompound("Inventory"));
		ListTag fluids = tag.getList("Fluids", Tag.TAG_COMPOUND);
		for (int i = 0; i < fluidTanks.length && i < fluids.size(); i++)
			fluidTanks[i].readFromNBT(fluids.getCompound(i));
		nextSlot = Mth.clamp(tag.getInt("NextSlot"), 0, LEG_COUNT - 1);
		activeSlot = tag.getInt("ActiveSlot");
		int activeMachineId = tag.getInt("ActiveMachine");
		activeMachine = activeMachineId >= 0 && activeMachineId < MachineKind.values().length
			? MachineKind.values()[activeMachineId]
			: null;
		processingTicksRemaining = tag.getInt("ProcessingTicksRemaining");
		processingTicksTotal = tag.getInt("ProcessingTicksTotal");
		super.read(tag, clientPacket);
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

	private void tickAssembly() {
		if (getSpeed() == 0)
			return;

		Optional<DepotBlockEntity> depot = getDepot();
		if (depot.isEmpty())
			return;

		if (activeSlot >= 0) {
			processingTicksRemaining--;
			if (processingTicksRemaining > 0)
				return;

			completeActiveProcess(depot.get());
			activeSlot = -1;
			activeMachine = null;
			processingTicksRemaining = 0;
			processingTicksTotal = 0;
			setChanged();
			sendData();
			return;
		}

		tryStartProcess(depot.get());
	}

	private Optional<DepotBlockEntity> getDepot() {
		if (level == null)
			return Optional.empty();
		BlockPos depotPos = worldPosition.below(2);
		if (!(level.getBlockEntity(depotPos) instanceof DepotBlockEntity depot))
			return Optional.empty();
		return Optional.of(depot);
	}

	private void tryStartProcess(DepotBlockEntity depot) {
		ItemStack input = depot.getHeldItem();
		if (input.isEmpty() || input.getCount() != 1)
			return;

		for (int attempt = 0; attempt < LEG_COUNT; attempt++) {
			int slot = (nextSlot + attempt) % LEG_COUNT;
			MachineKind kind = getMachineKind(slot);
			if (kind == null)
				continue;

			Optional<ProcessingPlan> plan = createPlan(slot, kind, input);
			if (plan.isEmpty())
				continue;

			activeSlot = slot;
			activeMachine = kind;
			processingTicksTotal = Math.max(1, plan.get().duration());
			processingTicksRemaining = processingTicksTotal;
			nextSlot = (slot + 1) % LEG_COUNT;
			setChanged();
			sendData();
			return;
		}
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

	private void completeActiveProcess(DepotBlockEntity depot) {
		if (activeMachine == null || activeSlot < 0)
			return;

		ItemStack input = depot.getHeldItem();
		if (input.isEmpty() || input.getCount() != 1)
			return;

		switch (activeMachine) {
		case DEPLOYER -> processDeploying(depot, activeSlot, input);
		case PRESS -> processPressing(depot, input);
		case SAW -> processCutting(depot, input);
		case SPOUT -> processFilling(depot, activeSlot, input);
		}
	}

	private Optional<PressingRecipe> findPressingRecipe(ItemStack input) {
		return SequencedAssemblyRecipe.getRecipe(level, input, AllRecipeTypes.PRESSING.getType(),
			PressingRecipe.class);
	}

	private Optional<CuttingRecipe> findCuttingRecipe(ItemStack input) {
		return SequencedAssemblyRecipe.getRecipe(level, input, AllRecipeTypes.CUTTING.getType(), CuttingRecipe.class);
	}

	private Optional<DeployerApplicationRecipe> findDeployingRecipe(int slot, ItemStack input) {
		ItemStack held = inventory.getStackInSlot(ITEM_CACHE_SLOT_START + slot);
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

	private void processPressing(DepotBlockEntity depot, ItemStack input) {
		findPressingRecipe(input).ifPresent(recipe -> applyItemOutputs(depot,
			RecipeApplier.applyRecipeOn(level, input.copy(), recipe, true)));
	}

	private void processCutting(DepotBlockEntity depot, ItemStack input) {
		findCuttingRecipe(input).ifPresent(recipe -> applyItemOutputs(depot,
			RecipeApplier.applyRecipeOn(level, input.copy(), recipe, true)));
	}

	private void processDeploying(DepotBlockEntity depot, int slot, ItemStack input) {
		findDeployingRecipe(slot, input).ifPresent(recipe -> {
			applyItemOutputs(depot, RecipeApplier.applyRecipeOn(level, input.copy(), recipe, true));
			consumeDeployingItem(slot, recipe);
		});
	}

	private void processFilling(DepotBlockEntity depot, int slot, ItemStack input) {
		findFillingRecipe(slot, input).ifPresent(recipe -> {
			FluidIngredient requiredFluid = recipe.getRequiredFluid();
			FluidStack available = fluidTanks[slot].getFluid();
			if (!requiredFluid.test(available))
				return;
			fluidTanks[slot].drain(requiredFluid.getRequiredAmount(), FluidAction.EXECUTE);
			applyItemOutputs(depot, recipe.rollResults());
		});
	}

	private void applyItemOutputs(DepotBlockEntity depot, List<ItemStack> outputs) {
		List<ItemStack> nonEmptyOutputs = new ArrayList<>();
		for (ItemStack output : outputs) {
			if (!output.isEmpty())
				nonEmptyOutputs.add(output.copy());
		}

		ItemStack newHeld = nonEmptyOutputs.isEmpty() ? ItemStack.EMPTY : nonEmptyOutputs.remove(0);
		depot.setHeldItem(newHeld);

		for (ItemStack extra : nonEmptyOutputs)
			dropStackNearDepot(depot.getBlockPos(), extra);
	}

	private void consumeDeployingItem(int slot, DeployerApplicationRecipe recipe) {
		if (recipe.shouldKeepHeldItem())
			return;

		int cacheSlot = ITEM_CACHE_SLOT_START + slot;
		ItemStack held = inventory.getStackInSlot(cacheSlot);
		if (held.isEmpty())
			return;

		ItemStack remainder = held.getCraftingRemainingItem();
		if (held.isDamageableItem()) {
			held.setDamageValue(held.getDamageValue() + 1);
			if (held.getDamageValue() >= held.getMaxDamage())
				held.shrink(1);
			inventory.setStackInSlot(cacheSlot, held);
			return;
		}

		held.shrink(1);
		inventory.setStackInSlot(cacheSlot, held);
		if (!remainder.isEmpty())
			insertIntoItemCacheOrDrop(slot, remainder);
	}

	private void insertIntoItemCacheOrDrop(int preferredSlot, ItemStack stack) {
		if (stack.isEmpty())
			return;

		int slot = ITEM_CACHE_SLOT_START + preferredSlot;
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

		for (int i = 0; i < LEG_COUNT; i++) {
			int cacheSlot = ITEM_CACHE_SLOT_START + i;
			ItemStack remainder = inventory.insertItem(cacheSlot, stack, false);
			if (remainder.isEmpty())
				return;
			stack = remainder;
		}

		dropStackNearTable(stack);
	}

	private void dropStackNearDepot(BlockPos depotPos, ItemStack stack) {
		if (level == null || stack.isEmpty())
			return;
		ItemEntity entity = new ItemEntity(level, depotPos.getX() + 0.5d, depotPos.getY() + 1.0d,
			depotPos.getZ() + 0.5d, stack.copy());
		entity.setDefaultPickUpDelay();
		level.addFreshEntity(entity);
	}

	private void dropStackNearTable(ItemStack stack) {
		if (level == null || stack.isEmpty())
			return;
		ItemEntity entity = new ItemEntity(level, worldPosition.getX() + 0.5d, worldPosition.getY() + 0.5d,
			worldPosition.getZ() + 0.5d, stack.copy());
		entity.setDefaultPickUpDelay();
		level.addFreshEntity(entity);
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

	private void processFluidContainerSlots() {
		for (int i = 0; i < LEG_COUNT; i++) {
			int slot = FLUID_CONTAINER_SLOT_START + i;
			ItemStack stack = inventory.getStackInSlot(slot);
			if (stack.isEmpty())
				continue;

			LazyOptional<IFluidHandlerItem> capability = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM);
			if (!capability.isPresent())
				continue;

			int index = i;
			capability.ifPresent(handler -> processFluidContainer(slot, index, handler));
		}
	}

	private void processFluidContainer(int slot, int tankIndex, IFluidHandlerItem handler) {
		FluidTank tank = fluidTanks[tankIndex];
		int space = tank.getCapacity() - tank.getFluidAmount();
		boolean changed = false;

		if (space > 0) {
			FluidStack simulatedDrain = handler.drain(space, FluidAction.SIMULATE);
			if (!simulatedDrain.isEmpty()) {
				int accepted = tank.fill(simulatedDrain, FluidAction.SIMULATE);
				if (accepted > 0) {
					FluidStack drained = handler.drain(accepted, FluidAction.EXECUTE);
					tank.fill(drained, FluidAction.EXECUTE);
					changed = true;
				}
			}
		}

		if (!changed && !tank.getFluid().isEmpty()) {
			FluidStack offered = tank.getFluid().copy();
			int filled = handler.fill(offered, FluidAction.EXECUTE);
			if (filled > 0) {
				tank.drain(filled, FluidAction.EXECUTE);
				changed = true;
			}
		}

		if (changed)
			inventory.setStackInSlot(slot, handler.getContainer());
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
			if (slot < ITEM_CACHE_SLOT_START)
				return 1;
			if (slot >= FLUID_CONTAINER_SLOT_START)
				return 1;
			return 64;
		}

		@Override
		public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
			if (slot < ITEM_CACHE_SLOT_START)
				return MachineKind.fromStack(stack) != null;
			if (slot >= FLUID_CONTAINER_SLOT_START)
				return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent();
			return true;
		}
	}

	private static class ItemCacheWrapper implements IItemHandlerModifiable {
		private final ItemStackHandler inventory;

		private ItemCacheWrapper(ItemStackHandler inventory) {
			this.inventory = inventory;
		}

		@Override
		public int getSlots() {
			return LEG_COUNT;
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			return inventory.getStackInSlot(ITEM_CACHE_SLOT_START + slot);
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			return inventory.insertItem(ITEM_CACHE_SLOT_START + slot, stack, simulate);
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			return inventory.extractItem(ITEM_CACHE_SLOT_START + slot, amount, simulate);
		}

		@Override
		public int getSlotLimit(int slot) {
			return inventory.getSlotLimit(ITEM_CACHE_SLOT_START + slot);
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return inventory.isItemValid(ITEM_CACHE_SLOT_START + slot, stack);
		}

		@Override
		public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
			inventory.setStackInSlot(ITEM_CACHE_SLOT_START + slot, stack);
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
			return fluidTanks[tank].isFluidValid(stack);
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			if (resource.isEmpty())
				return 0;

			FluidStack remaining = resource.copy();
			int filled = fillTanks(remaining, action, false);
			remaining.setAmount(resource.getAmount() - filled);
			return filled + fillTanks(remaining, action, true);
		}

		private int fillTanks(FluidStack resource, FluidAction action, boolean emptyOnly) {
			int filled = 0;
			for (FluidTank tank : fluidTanks) {
				if (emptyOnly != tank.getFluid().isEmpty())
					continue;
				FluidStack attempt = resource.copy();
				attempt.shrink(filled);
				if (attempt.isEmpty())
					break;
				filled += tank.fill(attempt, action);
			}
			return filled;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			if (resource.isEmpty())
				return FluidStack.EMPTY;
			FluidStack drained = FluidStack.EMPTY;
			int remaining = resource.getAmount();
			for (FluidTank tank : fluidTanks) {
				if (remaining <= 0)
					break;
				if (!tank.getFluid().isFluidEqual(resource))
					continue;
				FluidStack part = tank.drain(remaining, action);
				if (part.isEmpty())
					continue;
				if (drained.isEmpty())
					drained = part.copy();
				else
					drained.grow(part.getAmount());
				remaining -= part.getAmount();
			}
			return drained;
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			if (maxDrain <= 0)
				return FluidStack.EMPTY;
			for (FluidTank tank : fluidTanks) {
				FluidStack drained = tank.drain(maxDrain, action);
				if (!drained.isEmpty())
					return drained;
			}
			return FluidStack.EMPTY;
		}
	}

	public static BlockEntityType<SpiderAssemblyTableBlockEntity> type() {
		return CBBlockEntityTypes.SPIDER_ASSEMBLY_TABLE.get();
	}
}
