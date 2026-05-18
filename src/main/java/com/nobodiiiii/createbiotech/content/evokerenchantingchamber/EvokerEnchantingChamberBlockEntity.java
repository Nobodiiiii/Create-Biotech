package com.nobodiiiii.createbiotech.content.evokerenchantingchamber;

import com.nobodiiiii.createbiotech.content.squidprinter.EnchantmentBookCopyItem;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

public class EvokerEnchantingChamberBlockEntity extends BlockEntity {

	public static final int CAST_DURATION_TICKS = 40;
	private static final double SPELL_RED = 0.4d;
	private static final double SPELL_GREEN = 0.3d;
	private static final double SPELL_BLUE = 0.35d;

	private int castingTicksRemaining;
	private ItemStack heldItem = ItemStack.EMPTY;
	private ItemStack pendingOutput = ItemStack.EMPTY;
	private final LazyOptional<IItemHandler> itemHandlerCap;

	public EvokerEnchantingChamberBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.EVOKER_ENCHANTING_CHAMBER.get(), pos, state);
		itemHandlerCap = LazyOptional.of(this::createItemHandler);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, EvokerEnchantingChamberBlockEntity be) {
		if (level.isClientSide) {
			if (be.castingTicksRemaining > 0) {
				spawnClientCastingParticles((Level) level, pos, state, be.castingTicksRemaining);
			}
			return;
		}

		if (be.castingTicksRemaining > 0) {
			spawnCastingParticles((ServerLevel) level, pos, state, be.castingTicksRemaining);
			be.castingTicksRemaining--;
			if (be.castingTicksRemaining == 0) {
				be.completeCasting();
				be.syncToClient();
			}
		}
	}

	public boolean canInteract(ItemStack heldStack) {
		if (!heldStack.isEmpty() && heldStack.getItem() == CBItems.ENCHANTMENT_BOOK_COPY.get())
			return heldItem.isEmpty() && pendingOutput.isEmpty();
		return !pendingOutput.isEmpty();
	}

	public boolean tryInsertFromPlayer(Player player, InteractionHand hand, ItemStack heldStack) {
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
		if (pendingOutput.isEmpty())
			return false;
		ItemStack out = pendingOutput.copy();
		pendingOutput = ItemStack.EMPTY;
		setChanged();
		syncToClient();
		if (!player.getInventory().add(out)) {
			BlockPos at = worldPosition.above();
			Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, out);
		}
		return true;
	}

	private void startCasting(ItemStack copyStack) {
		heldItem = copyStack;
		castingTicksRemaining = CAST_DURATION_TICKS;
		setChanged();
		syncToClient();
	}

	private void completeCasting() {
		if (heldItem.isEmpty())
			return;
		pendingOutput = EnchantmentBookCopyItem.toEnchantedBook(heldItem);
		heldItem = ItemStack.EMPTY;
		if (level != null && !level.isClientSide)
			level.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.6f, 1.4f);
	}

	public boolean isCastingSpell() {
		return castingTicksRemaining > 0;
	}

	public boolean isBlocked() {
		return !heldItem.isEmpty() || !pendingOutput.isEmpty();
	}

	public ItemStack getHeldItem() {
		return heldItem;
	}

	public ItemStack getPendingOutput() {
		return pendingOutput;
	}

	public float getAnimationTime(float partialTick) {
		return level == null ? partialTick : level.getGameTime() + partialTick;
	}

	@Override
	public AABB getRenderBoundingBox() {
		return new AABB(worldPosition, worldPosition.offset(1, 2, 1));
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putInt("CastingTicks", castingTicksRemaining);
		if (!heldItem.isEmpty())
			tag.put("HeldItem", heldItem.serializeNBT());
		if (!pendingOutput.isEmpty())
			tag.put("PendingOutput", pendingOutput.serializeNBT());
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		castingTicksRemaining = tag.getInt("CastingTicks");
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
		if (cap == ForgeCapabilities.ITEM_HANDLER)
			return itemHandlerCap.cast();
		return super.getCapability(cap, side);
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

	private static void spawnCastingParticles(ServerLevel level, BlockPos pos, BlockState state, int animationTicks) {
		float bodyYaw = 180.0f - state.getValue(EvokerEnchantingChamberBlock.FACING).toYRot();
		float spellAngle = bodyYaw * Mth.DEG_TO_RAD + Mth.cos(animationTicks * 0.6662f) * 0.25f;
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

	private static void spawnClientCastingParticles(Level level, BlockPos pos, BlockState state, int animationTicks) {
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
			if (slot != 1 || amount <= 0 || pendingOutput.isEmpty())
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
			return slot == 0 && stack.getItem() == CBItems.ENCHANTMENT_BOOK_COPY.get();
		}
	}

	public void dropContents() {
		if (level == null)
			return;
		if (!heldItem.isEmpty())
			Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
				heldItem.copy());
		if (!pendingOutput.isEmpty())
			Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
				pendingOutput.copy());
		heldItem = ItemStack.EMPTY;
		pendingOutput = ItemStack.EMPTY;
	}
}
