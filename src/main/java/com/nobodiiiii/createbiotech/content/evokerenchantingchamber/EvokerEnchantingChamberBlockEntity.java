package com.nobodiiiii.createbiotech.content.evokerenchantingchamber;

import java.util.List;

import com.nobodiiiii.createbiotech.compat.jade.JadeFluidProvider;
import com.nobodiiiii.createbiotech.content.experience.ExperienceConstants;
import com.nobodiiiii.createbiotech.content.experience.ExperienceFluidHelper;
import com.nobodiiiii.createbiotech.content.experience.ExperienceHelper;
import com.nobodiiiii.createbiotech.content.squidprinter.EnchantmentBookCopyItem;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.registry.CBParticleTypes;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

public class EvokerEnchantingChamberBlockEntity extends BlockEntity
	implements IHaveGoggleInformation, JadeFluidProvider {

	public static final int CAST_DURATION_TICKS_PER_LEVEL = 40;
	private static final int CLIENT_SYNC_INTERVAL_TICKS = 10;
	private static final double BOOK_FRONT_OFFSET = 4d / 16d;
	private static final double BOOK_BASE_Y = 20d / 16d;
	private static final double BOOK_BOB_AMPLITUDE = 0.04d;
	private static final double BOOK_BOB_SPEED = 0.08d;
	private static final double BOOK_PARTICLE_SOURCE_SPREAD = 0.72d;
	private static final double BOOK_PARTICLE_TARGET_SPREAD = 0.16d;
	private static final double ITEM_BASE_Y = BOOK_BASE_Y + 6d / 16d;
	private static final double ITEM_BOB_AMPLITUDE = 0.06d;
	private static final double ITEM_BOB_SPEED = 0.1d;

	private int fluidRemaining;
	private int fluidTotal;
	private FluidStack storedFluid = FluidStack.EMPTY;
	private boolean waitingForFluid;
	private int clientSyncTimer;
	private ItemStack heldItem = ItemStack.EMPTY;
	private ItemStack pendingOutput = ItemStack.EMPTY;
	private final LazyOptional<IItemHandler> itemHandlerCap;
	private final LazyOptional<IFluidHandler> fluidHandlerCap;

	public EvokerEnchantingChamberBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.EVOKER_ENCHANTING_CHAMBER.get(), pos, state);
		itemHandlerCap = LazyOptional.of(this::createItemHandler);
		fluidHandlerCap = LazyOptional.of(() -> new ChamberFluidHandler());
	}

	public static void tick(Level level, BlockPos pos, BlockState state, EvokerEnchantingChamberBlockEntity be) {
		be.ensureUpperProxyBlockEntity();
		if (level.isClientSide) {
			if (be.isCastingSpell())
				spawnBookEnchantParticlesClient(level, pos, state);
			return;
		}

		if (be.heldItem.isEmpty() || be.fluidRemaining <= 0)
			return;

		int drain = Math.min(getFluidPerTick(), Math.min(be.fluidRemaining, be.getStoredFluidAmount()));
		if (drain <= 0) {
			if (!be.waitingForFluid) {
				be.waitingForFluid = true;
				be.syncToClient();
			}
			return;
		}

		boolean wasWaiting = be.waitingForFluid;
		be.waitingForFluid = false;
		be.shrinkStoredFluid(drain);
		be.fluidRemaining -= drain;

		if (be.fluidRemaining <= 0) {
			be.completeCasting();
			be.syncToClient();
			return;
		}

		if (wasWaiting || ++be.clientSyncTimer >= CLIENT_SYNC_INTERVAL_TICKS) {
			be.clientSyncTimer = 0;
			be.syncToClient();
		}
	}

	public boolean canInteract(ItemStack heldStack) {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.canInteract(heldStack);
		if (!heldItem.isEmpty() || !pendingOutput.isEmpty())
			return true;
		return !heldStack.isEmpty() && heldStack.getItem() == CBItems.ENCHANTMENT_BOOK_COPY.get()
			&& EnchantmentBookCopyItem.hasStoredEnchantments(heldStack);
	}

	public boolean tryInsertFromPlayer(Player player, InteractionHand hand, ItemStack heldStack) {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.tryInsertFromPlayer(player, hand, heldStack);
		if (heldStack.isEmpty() || heldStack.getItem() != CBItems.ENCHANTMENT_BOOK_COPY.get())
			return false;
		if (!heldItem.isEmpty() || !pendingOutput.isEmpty())
			return false;
		if (!EnchantmentBookCopyItem.hasStoredEnchantments(heldStack))
			return false;

		ItemStack toInsert = heldStack.copy();
		toInsert.setCount(1);
		startCasting(toInsert);
		if (!player.isCreative()) {
			heldStack.shrink(1);
			if (heldStack.isEmpty())
				player.setItemInHand(hand, ItemStack.EMPTY);
		}
		if (level != null)
			level.playSound(null, worldPosition, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.BLOCKS, 0.7f, 1.0f);
		return true;
	}

	public boolean tryExtractToPlayer(Player player) {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.tryExtractToPlayer(player);
		if (pendingOutput.isEmpty() && heldItem.isEmpty())
			return false;
		ItemStack out;
		if (!pendingOutput.isEmpty()) {
			out = pendingOutput.copy();
			pendingOutput = ItemStack.EMPTY;
			setChanged();
			syncToClient();
		} else {
			out = abortCastingAndExtract(false);
		}
		if (!player.getInventory().add(out)) {
			BlockPos at = worldPosition.above();
			Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, out);
		}
		return true;
	}

	private void startCasting(ItemStack copyStack) {
		heldItem = copyStack;
		int totalLevels = ExperienceHelper.sumStoredEnchantmentLevels(copyStack);
		fluidTotal = totalLevels * ExperienceConstants.chamberFluidPerLevel();
		fluidRemaining = fluidTotal;
		waitingForFluid = fluidTotal > 0 && getStoredFluidAmount() <= 0;
		clientSyncTimer = 0;
		if (fluidTotal <= 0) {
			completeCasting();
		}
		setChanged();
		syncToClient();
	}

	private void completeCasting() {
		if (heldItem.isEmpty())
			return;
		pendingOutput = EnchantmentBookCopyItem.toEnchantedBook(heldItem);
		heldItem = ItemStack.EMPTY;
		fluidRemaining = 0;
		fluidTotal = 0;
		waitingForFluid = false;
		clientSyncTimer = 0;
		if (level != null && !level.isClientSide)
			level.playSound(null, worldPosition, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.85f, 1.0f);
	}

	public boolean isCastingSpell() {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.isCastingSpell();
		return !heldItem.isEmpty() && fluidRemaining > 0 && !waitingForFluid;
	}

	public boolean isWaitingForFluid() {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.isWaitingForFluid();
		return waitingForFluid;
	}

	public int getStoredFluidAmount() {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.getStoredFluidAmount();
		return storedFluid.getAmount();
	}

	public int getFluidRemaining() {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.getFluidRemaining();
		return fluidRemaining;
	}

	public int getFluidTotal() {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.getFluidTotal();
		return fluidTotal;
	}

	public int getFluidConsumed() {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.getFluidConsumed();
		return Math.max(0, fluidTotal - fluidRemaining);
	}

	@Override
	public int getJadeCurrentFluidAmount() {
		return getStoredFluidAmount();
	}

	@Override
	public int getJadeMaxFluidAmount() {
		return ExperienceConstants.chamberCacheCapacity();
	}

	public boolean isBlocked() {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.isBlocked();
		return !heldItem.isEmpty() || !pendingOutput.isEmpty();
	}

	public ItemStack getHeldItem() {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.getHeldItem();
		return heldItem;
	}

	public ItemStack getPendingOutput() {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.getPendingOutput();
		return pendingOutput;
	}

	public void setRenderPreviewState(ItemStack heldItem, ItemStack pendingOutput, int storedFluidAmount,
		int fluidRemaining, int fluidTotal, boolean waitingForFluid) {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this) {
			controller.setRenderPreviewState(heldItem, pendingOutput, storedFluidAmount, fluidRemaining, fluidTotal,
				waitingForFluid);
			return;
		}

		this.heldItem = heldItem.copy();
		this.pendingOutput = pendingOutput.copy();
		this.storedFluid = ExperienceFluidHelper.experienceStack(storedFluidAmount);
		this.fluidRemaining = fluidRemaining;
		this.fluidTotal = fluidTotal;
		this.waitingForFluid = waitingForFluid;
		this.clientSyncTimer = 0;
	}

	@Override
	public AABB getRenderBoundingBox() {
		return new AABB(worldPosition, worldPosition.offset(1, 2, 1));
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		if (isUpperHalf())
			return;
		tag.putInt("FluidRemaining", fluidRemaining);
		tag.putInt("FluidTotal", fluidTotal);
		if (!storedFluid.isEmpty())
			tag.put("TankContent", storedFluid.writeToNBT(new CompoundTag()));
		tag.putBoolean("WaitingForFluid", waitingForFluid);
		if (!heldItem.isEmpty())
			tag.put("HeldItem", heldItem.serializeNBT());
		if (!pendingOutput.isEmpty())
			tag.put("PendingOutput", pendingOutput.serializeNBT());
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		if (isUpperHalf()) {
			fluidRemaining = 0;
			fluidTotal = 0;
			storedFluid = FluidStack.EMPTY;
			waitingForFluid = false;
			heldItem = ItemStack.EMPTY;
			pendingOutput = ItemStack.EMPTY;
			return;
		}
		fluidRemaining = tag.contains("FluidRemaining") ? tag.getInt("FluidRemaining") : tag.getInt("XpRemaining");
		fluidTotal = tag.contains("FluidTotal") ? tag.getInt("FluidTotal") : tag.getInt("XpTotal");
		storedFluid = readStoredFluid(tag);
		waitingForFluid = tag.contains("WaitingForFluid")
			? tag.getBoolean("WaitingForFluid")
			: tag.getBoolean("WaitingForExperience");
		heldItem = tag.contains("HeldItem") ? ItemStack.of(tag.getCompound("HeldItem")) : ItemStack.EMPTY;
		pendingOutput = tag.contains("PendingOutput") ? ItemStack.of(tag.getCompound("PendingOutput"))
			: ItemStack.EMPTY;
	}

	private static FluidStack readStoredFluid(CompoundTag tag) {
		if (tag.contains("TankContent", Tag.TAG_COMPOUND)) {
			FluidStack fluid = FluidStack.loadFluidStackFromNBT(tag.getCompound("TankContent"));
			return ExperienceFluidHelper.isExperience(fluid) ? fluid : FluidStack.EMPTY;
		}
		if (tag.contains("StoredExperience", Tag.TAG_INT))
			return ExperienceFluidHelper.experienceStack(tag.getInt("StoredExperience"));
		return FluidStack.EMPTY;
	}

	@Override
	public CompoundTag getUpdateTag() {
		return saveWithoutMetadata();
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
		if (cap == ForgeCapabilities.ITEM_HANDLER || cap == ForgeCapabilities.FLUID_HANDLER) {
			EvokerEnchantingChamberBlockEntity controller = getController();
			if (controller != null && controller != this)
				return controller.getCapability(cap, side);
			if (cap == ForgeCapabilities.FLUID_HANDLER)
				return fluidHandlerCap.cast();
			return itemHandlerCap.cast();
		}
		return super.getCapability(cap, side);
	}

	public int insertFluid(FluidStack resource, boolean simulate) {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.insertFluid(resource, simulate);
		if (!canFillWith(resource))
			return 0;
		int accepted = Math.min(resource.getAmount(), getFluidSpace());
		if (simulate || accepted <= 0)
			return accepted;
		if (storedFluid.isEmpty()) {
			storedFluid = resource.copy();
			storedFluid.setAmount(accepted);
		} else {
			storedFluid.grow(accepted);
		}
		syncToClient();
		return accepted;
	}

	public int getFluidSpace() {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.getFluidSpace();
		return Math.max(0, ExperienceConstants.chamberCacheCapacity() - getStoredFluidAmount());
	}

	private boolean canFillWith(FluidStack resource) {
		return ExperienceFluidHelper.isExperience(resource)
			&& (storedFluid.isEmpty() || storedFluid.isFluidEqual(resource))
			&& getFluidSpace() > 0;
	}

	private void shrinkStoredFluid(int amount) {
		if (amount <= 0 || storedFluid.isEmpty())
			return;
		storedFluid.shrink(amount);
		if (storedFluid.isEmpty())
			storedFluid = FluidStack.EMPTY;
	}

	private FluidStack drainFluid(FluidStack resource, boolean simulate) {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.drainFluid(resource, simulate);
		if (resource.isEmpty() || storedFluid.isEmpty() || !storedFluid.isFluidEqual(resource))
			return FluidStack.EMPTY;
		return drainFluid(resource.getAmount(), simulate);
	}

	private FluidStack drainFluid(int maxDrain, boolean simulate) {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.drainFluid(maxDrain, simulate);
		if (maxDrain <= 0 || storedFluid.isEmpty())
			return FluidStack.EMPTY;

		int drained = Math.min(maxDrain, storedFluid.getAmount());
		FluidStack result = storedFluid.copy();
		result.setAmount(drained);
		if (!simulate) {
			shrinkStoredFluid(drained);
			syncToClient();
		}
		return result;
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.addToGoggleTooltip(tooltip, isPlayerSneaking);

		CreateLang.builder()
			.add(Component.translatable("create_biotech.gui.goggles.enchanting_chamber"))
			.forGoggles(tooltip);

		String unitKey = "create_biotech.generic.unit.millibuckets";

		CreateLang.builder()
			.add(Component.translatable("create_biotech.gui.goggles.enchanting_chamber.cache")
				.withStyle(ChatFormatting.GRAY))
			.forGoggles(tooltip, 1);
		CreateLang.builder()
			.add(CreateLang.number(getStoredFluidAmount())
				.add(Component.translatable(unitKey))
				.style(ChatFormatting.GOLD))
			.text(ChatFormatting.GRAY, " / ")
			.add(CreateLang.number(ExperienceConstants.chamberCacheCapacity())
				.add(Component.translatable(unitKey))
				.style(ChatFormatting.DARK_GRAY))
			.forGoggles(tooltip, 1);

		if (fluidTotal > 0 && !heldItem.isEmpty()) {
			CreateLang.builder()
				.add(Component.translatable("create_biotech.gui.goggles.enchanting_chamber.progress")
					.withStyle(ChatFormatting.GRAY))
				.forGoggles(tooltip, 1);
			CreateLang.builder()
				.add(CreateLang.number(getFluidConsumed())
					.add(Component.translatable(unitKey))
					.style(ChatFormatting.AQUA))
				.text(ChatFormatting.GRAY, " / ")
				.add(CreateLang.number(fluidTotal)
					.add(Component.translatable(unitKey))
					.style(ChatFormatting.DARK_GRAY))
				.forGoggles(tooltip, 1);
			if (waitingForFluid) {
				CreateLang.builder()
					.add(Component.translatable("create_biotech.gui.goggles.enchanting_chamber.waiting")
						.withStyle(ChatFormatting.RED))
					.forGoggles(tooltip, 1);
			}
		} else if (!pendingOutput.isEmpty()) {
			CreateLang.builder()
				.add(Component.translatable("create_biotech.gui.goggles.enchanting_chamber.ready")
					.withStyle(ChatFormatting.GREEN))
				.forGoggles(tooltip, 1);
		}

		return true;
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		itemHandlerCap.invalidate();
		fluidHandlerCap.invalidate();
	}

	private void syncToClient() {
		if (level == null)
			return;
		setChanged();
		BlockState state = getBlockState();
		level.sendBlockUpdated(worldPosition, state, state, 3);
	}

	private IItemHandler createItemHandler() {
		return new ChamberItemHandler();
	}

	private boolean isUpperHalf() {
		return getBlockState().hasProperty(EvokerEnchantingChamberBlock.HALF)
			&& getBlockState().getValue(EvokerEnchantingChamberBlock.HALF) == DoubleBlockHalf.UPPER;
	}

	public EvokerEnchantingChamberBlockEntity getController() {
		if (level == null || getBlockState().getValue(EvokerEnchantingChamberBlock.HALF) == DoubleBlockHalf.LOWER)
			return this;
		BlockEntity below = level.getBlockEntity(worldPosition.below());
		return below instanceof EvokerEnchantingChamberBlockEntity chamber ? chamber : null;
	}

	public void dropContentsAndFluid() {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this) {
			controller.dropContentsAndFluid();
			return;
		}
		if (level == null || level.isClientSide)
			return;

		Vec3 dropPos = Vec3.atCenterOf(worldPosition).add(0.0d, 0.35d, 0.0d);
		if (!heldItem.isEmpty())
			Containers.dropItemStack(level, dropPos.x, dropPos.y, dropPos.z, heldItem.copy());
		if (!pendingOutput.isEmpty())
			Containers.dropItemStack(level, dropPos.x, dropPos.y, dropPos.z, pendingOutput.copy());
		clearState(true);
	}

	private void ensureUpperProxyBlockEntity() {
		if (level == null || getController() != this)
			return;
		BlockPos upperPos = worldPosition.above();
		BlockState upperState = level.getBlockState(upperPos);
		if (!(upperState.getBlock() instanceof EvokerEnchantingChamberBlock)
			|| upperState.getValue(EvokerEnchantingChamberBlock.HALF) != DoubleBlockHalf.UPPER
			|| level.getBlockEntity(upperPos) != null) {
			return;
		}

		BlockEntity proxy = CBBlockEntityTypes.EVOKER_ENCHANTING_CHAMBER.get().create(upperPos, upperState);
		if (proxy != null)
			level.setBlockEntity(proxy);
	}

	private void clearState(boolean clearStoredFluid) {
		heldItem = ItemStack.EMPTY;
		pendingOutput = ItemStack.EMPTY;
		fluidRemaining = 0;
		fluidTotal = 0;
		waitingForFluid = false;
		clientSyncTimer = 0;
		if (clearStoredFluid)
			storedFluid = FluidStack.EMPTY;
		syncToClient();
	}

	private ItemStack abortCastingAndExtract(boolean simulate) {
		if (heldItem.isEmpty())
			return ItemStack.EMPTY;
		ItemStack extracted = heldItem.copy();
		if (!simulate)
			clearState(false);
		return extracted;
	}

	@FunctionalInterface
	public interface StraightEnchantParticleEmitter {
		void emit(double x, double y, double z, double dx, double dy, double dz);
	}

	public static void forEachStraightEnchantParticle(Level level, BlockPos pos, BlockState state,
		StraightEnchantParticleEmitter emitter) {
		Direction facing = state.getValue(EvokerEnchantingChamberBlock.FACING);
		double anchorX = pos.getX() + 0.5d + facing.getStepX() * BOOK_FRONT_OFFSET;
		double anchorZ = pos.getZ() + 0.5d + facing.getStepZ() * BOOK_FRONT_OFFSET;
		double time = level.getGameTime();
		double sourceY = pos.getY() + BOOK_BASE_Y + Mth.sin((float) (time * BOOK_BOB_SPEED)) * BOOK_BOB_AMPLITUDE;
		double targetY = pos.getY() + ITEM_BASE_Y + Mth.sin((float) (time * ITEM_BOB_SPEED)) * ITEM_BOB_AMPLITUDE;

		for (int i = 0; i < 3; i++) {
			double startX = anchorX + (level.random.nextDouble() - 0.5d) * BOOK_PARTICLE_SOURCE_SPREAD;
			double startY = sourceY - 0.1d + level.random.nextDouble() * 0.12d;
			double startZ = anchorZ + (level.random.nextDouble() - 0.5d) * BOOK_PARTICLE_SOURCE_SPREAD;
			double targetX = anchorX + (level.random.nextDouble() - 0.5d) * BOOK_PARTICLE_TARGET_SPREAD;
			double targetZ = anchorZ + (level.random.nextDouble() - 0.5d) * BOOK_PARTICLE_TARGET_SPREAD;
			emitter.emit(startX, startY, startZ, targetX - startX, targetY - startY, targetZ - startZ);
		}
	}

	private static void spawnBookEnchantParticlesClient(Level level, BlockPos pos, BlockState state) {
		forEachStraightEnchantParticle(level, pos, state,
			(x, y, z, dx, dy, dz) -> level.addParticle(CBParticleTypes.STRAIGHT_ENCHANT.get(), x, y, z, dx, dy, dz));
	}

	public class ChamberItemHandler implements IItemHandler {

		@Override
		public int getSlots() {
			return 2;
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			if (slot == 0)
				return heldItem;
			if (slot == 1)
				return pendingOutput;
			return ItemStack.EMPTY;
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			if (slot != 0 || stack.isEmpty())
				return stack;
			if (stack.getItem() != CBItems.ENCHANTMENT_BOOK_COPY.get())
				return stack;
			if (!EnchantmentBookCopyItem.hasStoredEnchantments(stack))
				return stack;
			if (!heldItem.isEmpty() || !pendingOutput.isEmpty())
				return stack;
			if (!simulate) {
				ItemStack inserted = stack.copy();
				inserted.setCount(1);
				startCasting(inserted);
			}
			ItemStack remainder = stack.copy();
			remainder.shrink(1);
			return remainder;
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			if (amount <= 0)
				return ItemStack.EMPTY;
			if (slot == 0)
				return abortCastingAndExtract(simulate);
			if (slot != 1 || pendingOutput.isEmpty())
				return ItemStack.EMPTY;
			ItemStack out = pendingOutput.copy();
			if (simulate)
				return out;
			pendingOutput = ItemStack.EMPTY;
			setChanged();
			syncToClient();
			return out;
		}

		@Override
		public int getSlotLimit(int slot) {
			return slot == 0 ? 1 : 64;
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return slot == 0 && stack.getItem() == CBItems.ENCHANTMENT_BOOK_COPY.get()
				&& EnchantmentBookCopyItem.hasStoredEnchantments(stack);
		}
	}

	private class ChamberFluidHandler implements IFluidHandler {
		@Override
		public int getTanks() {
			return 1;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			return tank == 0 ? storedFluid.copy() : FluidStack.EMPTY;
		}

		@Override
		public int getTankCapacity(int tank) {
			return tank == 0 ? ExperienceConstants.chamberCacheCapacity() : 0;
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return tank == 0 && ExperienceFluidHelper.isExperience(stack);
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			return insertFluid(resource, action.simulate());
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			return drainFluid(resource, action.simulate());
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			return drainFluid(maxDrain, action.simulate());
		}
	}

	public void dropContents() {
		dropContentsAndFluid();
	}

	private static int getFluidPerTick() {
		return Math.max(1, Mth.ceil((float) ExperienceConstants.chamberFluidPerLevel()
			/ Math.max(1, com.nobodiiiii.createbiotech.registry.CBConfigs.SERVER.evokerEnchantingChamber.castDurationTicksPerLevel.get())));
	}
}
