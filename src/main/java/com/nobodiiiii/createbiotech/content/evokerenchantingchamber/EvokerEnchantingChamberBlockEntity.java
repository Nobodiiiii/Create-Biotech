package com.nobodiiiii.createbiotech.content.evokerenchantingchamber;

import java.util.List;

import com.nobodiiiii.createbiotech.content.squidprinter.EnchantmentBookCopyItem;
import com.nobodiiiii.createbiotech.content.experience.ExperienceConstants;
import com.nobodiiiii.createbiotech.content.experience.ExperienceHelper;
import com.nobodiiiii.createbiotech.content.experience.ExperienceSink;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.registry.CBParticleTypes;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraftforge.items.IItemHandler;

public class EvokerEnchantingChamberBlockEntity extends BlockEntity
	implements ExperienceSink, IHaveGoggleInformation {

	public static final int CAST_DURATION_TICKS_PER_LEVEL = 40;
	public static final int XP_PER_TICK = ExperienceConstants.CHAMBER_XP_PER_LEVEL / CAST_DURATION_TICKS_PER_LEVEL;
	private static final int CLIENT_SYNC_INTERVAL_TICKS = 10;
	private static final double SPELL_RED = 0.4d;
	private static final double SPELL_GREEN = 0.3d;
	private static final double SPELL_BLUE = 0.35d;
	private static final double BOOK_FRONT_OFFSET = 4d / 16d;
	private static final double BOOK_BASE_Y = 18d / 16d;
	private static final double BOOK_BOB_AMPLITUDE = 0.04d;
	private static final double BOOK_BOB_SPEED = 0.08d;
	private static final double ITEM_BASE_Y = BOOK_BASE_Y + 6d / 16d;
	private static final double ITEM_BOB_AMPLITUDE = 0.06d;
	private static final double ITEM_BOB_SPEED = 0.1d;

	private int xpRemaining;
	private int xpTotal;
	private int storedExperience;
	private boolean waitingForExperience;
	private int clientSyncTimer;
	private ItemStack heldItem = ItemStack.EMPTY;
	private ItemStack pendingOutput = ItemStack.EMPTY;
	private final LazyOptional<IItemHandler> itemHandlerCap;

	public EvokerEnchantingChamberBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.EVOKER_ENCHANTING_CHAMBER.get(), pos, state);
		itemHandlerCap = LazyOptional.of(this::createItemHandler);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, EvokerEnchantingChamberBlockEntity be) {
		be.ensureUpperProxyBlockEntity();
		if (level.isClientSide) {
			if (be.isCastingSpell())
				spawnClientCastingParticles((Level) level, pos, state, be.xpRemaining);
			return;
		}

		if (be.heldItem.isEmpty() || be.xpRemaining <= 0)
			return;

		int drain = Math.min(XP_PER_TICK, Math.min(be.xpRemaining, be.storedExperience));
		if (drain <= 0) {
			if (!be.waitingForExperience) {
				be.waitingForExperience = true;
				be.syncToClient();
			}
			return;
		}

		boolean wasWaiting = be.waitingForExperience;
		be.waitingForExperience = false;
		be.storedExperience -= drain;
		be.xpRemaining -= drain;
		spawnCastingParticles((ServerLevel) level, pos, state, level.getGameTime());
		spawnBookEnchantParticles((ServerLevel) level, pos, state);

		if (be.xpRemaining <= 0) {
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
		xpTotal = totalLevels * ExperienceConstants.CHAMBER_XP_PER_LEVEL;
		xpRemaining = xpTotal;
		waitingForExperience = xpTotal > 0 && storedExperience <= 0;
		clientSyncTimer = 0;
		if (xpTotal <= 0) {
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
		xpRemaining = 0;
		xpTotal = 0;
		waitingForExperience = false;
		clientSyncTimer = 0;
		if (level != null && !level.isClientSide)
			level.playSound(null, worldPosition, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.85f, 1.0f);
	}

	public boolean isCastingSpell() {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.isCastingSpell();
		return !heldItem.isEmpty() && xpRemaining > 0 && !waitingForExperience;
	}

	public boolean isWaitingForExperience() {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.isWaitingForExperience();
		return waitingForExperience;
	}

	public int getStoredExperience() {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.getStoredExperience();
		return storedExperience;
	}

	public int getXpRemaining() {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.getXpRemaining();
		return xpRemaining;
	}

	public int getXpTotal() {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.getXpTotal();
		return xpTotal;
	}

	public int getXpConsumed() {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.getXpConsumed();
		return Math.max(0, xpTotal - xpRemaining);
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

	public float getAnimationTime(float partialTick) {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.getAnimationTime(partialTick);
		return level == null ? partialTick : level.getGameTime() + partialTick;
	}

	@Override
	public AABB getRenderBoundingBox() {
		return new AABB(worldPosition, worldPosition.offset(1, 2, 1));
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		if (getController() != this)
			return;
		tag.putInt("XpRemaining", xpRemaining);
		tag.putInt("XpTotal", xpTotal);
		tag.putInt("StoredExperience", storedExperience);
		tag.putBoolean("WaitingForExperience", waitingForExperience);
		if (!heldItem.isEmpty())
			tag.put("HeldItem", heldItem.serializeNBT());
		if (!pendingOutput.isEmpty())
			tag.put("PendingOutput", pendingOutput.serializeNBT());
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		if (getController() != this) {
			xpRemaining = 0;
			xpTotal = 0;
			storedExperience = 0;
			waitingForExperience = false;
			heldItem = ItemStack.EMPTY;
			pendingOutput = ItemStack.EMPTY;
			return;
		}
		xpRemaining = tag.getInt("XpRemaining");
		xpTotal = tag.getInt("XpTotal");
		storedExperience = tag.getInt("StoredExperience");
		waitingForExperience = tag.getBoolean("WaitingForExperience");
		heldItem = tag.contains("HeldItem") ? ItemStack.of(tag.getCompound("HeldItem")) : ItemStack.EMPTY;
		pendingOutput = tag.contains("PendingOutput") ? ItemStack.of(tag.getCompound("PendingOutput"))
			: ItemStack.EMPTY;
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
		if (cap == ForgeCapabilities.ITEM_HANDLER) {
			EvokerEnchantingChamberBlockEntity controller = getController();
			if (controller != null && controller != this)
				return controller.getCapability(cap, side);
			return itemHandlerCap.cast();
		}
		return super.getCapability(cap, side);
	}

	@Override
	public int insertExperience(int amount, boolean simulate) {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.insertExperience(amount, simulate);
		if (amount <= 0)
			return 0;
		int accepted = Math.min(amount, getExperienceSpace());
		if (simulate || accepted <= 0)
			return accepted;
		storedExperience += accepted;
		syncToClient();
		return accepted;
	}

	@Override
	public int getExperienceSpace() {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.getExperienceSpace();
		return Math.max(0, ExperienceConstants.CHAMBER_CACHE_CAPACITY - storedExperience);
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this)
			return controller.addToGoggleTooltip(tooltip, isPlayerSneaking);

		CreateLang.builder()
			.add(Component.translatable("create_biotech.gui.goggles.enchanting_chamber"))
			.forGoggles(tooltip);

		String unitKey = "create_biotech.generic.unit.experience_points";

		CreateLang.builder()
			.add(Component.translatable("create_biotech.gui.goggles.enchanting_chamber.cache")
				.withStyle(ChatFormatting.GRAY))
			.forGoggles(tooltip, 1);
		CreateLang.builder()
			.add(CreateLang.number(storedExperience)
				.add(Component.translatable(unitKey))
				.style(ChatFormatting.GOLD))
			.text(ChatFormatting.GRAY, " / ")
			.add(CreateLang.number(ExperienceConstants.CHAMBER_CACHE_CAPACITY)
				.add(Component.translatable(unitKey))
				.style(ChatFormatting.DARK_GRAY))
			.forGoggles(tooltip, 1);

		if (xpTotal > 0 && !heldItem.isEmpty()) {
			CreateLang.builder()
				.add(Component.translatable("create_biotech.gui.goggles.enchanting_chamber.progress")
					.withStyle(ChatFormatting.GRAY))
				.forGoggles(tooltip, 1);
			CreateLang.builder()
				.add(CreateLang.number(getXpConsumed())
					.add(Component.translatable(unitKey))
					.style(ChatFormatting.AQUA))
				.text(ChatFormatting.GRAY, " / ")
				.add(CreateLang.number(xpTotal)
					.add(Component.translatable(unitKey))
					.style(ChatFormatting.DARK_GRAY))
				.forGoggles(tooltip, 1);
			if (waitingForExperience) {
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

	public EvokerEnchantingChamberBlockEntity getController() {
		if (level == null || getBlockState().getValue(EvokerEnchantingChamberBlock.HALF) == DoubleBlockHalf.LOWER)
			return this;
		BlockEntity below = level.getBlockEntity(worldPosition.below());
		return below instanceof EvokerEnchantingChamberBlockEntity chamber ? chamber : null;
	}

	public void dropContentsAndExperience() {
		EvokerEnchantingChamberBlockEntity controller = getController();
		if (controller != null && controller != this) {
			controller.dropContentsAndExperience();
			return;
		}
		if (level == null || level.isClientSide)
			return;

		Vec3 dropPos = Vec3.atCenterOf(worldPosition).add(0.0d, 0.35d, 0.0d);
		if (!heldItem.isEmpty())
			Containers.dropItemStack(level, dropPos.x, dropPos.y, dropPos.z, heldItem.copy());
		if (!pendingOutput.isEmpty())
			Containers.dropItemStack(level, dropPos.x, dropPos.y, dropPos.z, pendingOutput.copy());
		if (storedExperience > 0)
			ExperienceHelper.spawnExperience(level, dropPos, storedExperience);
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

	private void clearState(boolean clearStoredExperience) {
		heldItem = ItemStack.EMPTY;
		pendingOutput = ItemStack.EMPTY;
		xpRemaining = 0;
		xpTotal = 0;
		waitingForExperience = false;
		clientSyncTimer = 0;
		if (clearStoredExperience)
			storedExperience = 0;
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

	private static void spawnCastingParticles(ServerLevel level, BlockPos pos, BlockState state, long gameTime) {
		float bodyYaw = 180.0f - state.getValue(EvokerEnchantingChamberBlock.FACING).toYRot();
		float spellAngle = bodyYaw * Mth.DEG_TO_RAD + Mth.cos(gameTime * 0.6662f) * 0.25f;
		float xOffset = Mth.cos(spellAngle) * 0.6f;
		float zOffset = Mth.sin(spellAngle) * 0.6f;
		double centerX = pos.getX() + 0.5d;
		double centerY = pos.getY() + 1.8d;
		double centerZ = pos.getZ() + 0.5d;

		level.sendParticles(ParticleTypes.ENTITY_EFFECT, centerX + xOffset, centerY, centerZ + zOffset, 0,
			SPELL_RED, SPELL_GREEN, SPELL_BLUE, 1.0d);
		level.sendParticles(ParticleTypes.ENTITY_EFFECT, centerX - xOffset, centerY, centerZ - zOffset, 0,
			SPELL_RED, SPELL_GREEN, SPELL_BLUE, 1.0d);
	}

	private static void spawnBookEnchantParticles(ServerLevel level, BlockPos pos, BlockState state) {
		Direction facing = state.getValue(EvokerEnchantingChamberBlock.FACING);
		double anchorX = pos.getX() + 0.5d + facing.getStepX() * BOOK_FRONT_OFFSET;
		double anchorZ = pos.getZ() + 0.5d + facing.getStepZ() * BOOK_FRONT_OFFSET;
		double time = level.getGameTime();
		double sourceY = pos.getY() + BOOK_BASE_Y + Mth.sin((float) (time * BOOK_BOB_SPEED)) * BOOK_BOB_AMPLITUDE;
		double targetY = pos.getY() + ITEM_BASE_Y + Mth.sin((float) (time * ITEM_BOB_SPEED)) * ITEM_BOB_AMPLITUDE;

		for (int i = 0; i < 3; i++) {
			double startX = anchorX + (level.random.nextDouble() - 0.5d) * 0.45d;
			double startY = sourceY - 0.1d + level.random.nextDouble() * 0.12d;
			double startZ = anchorZ + (level.random.nextDouble() - 0.5d) * 0.45d;
			double targetX = anchorX + (level.random.nextDouble() - 0.5d) * 0.1d;
			double targetZ = anchorZ + (level.random.nextDouble() - 0.5d) * 0.1d;
			level.sendParticles(CBParticleTypes.STRAIGHT_ENCHANT.get(), startX, startY, startZ, 0,
				targetX - startX, targetY - startY, targetZ - startZ, 1.0d);
		}
	}

	private static void spawnClientCastingParticles(Level level, BlockPos pos, BlockState state, int xpRemaining) {
		// nothing — client uses BE renderer for visual effects; particles are server-driven
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

	public void dropContents() {
		dropContentsAndExperience();
	}
}
