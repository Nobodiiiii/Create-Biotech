package com.nobodiiiii.createbiotech.content.biopackager;

import java.util.List;

import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.CapManipulationBehaviourBase.InterfaceProvider;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

public class BioPackagerBlockEntity extends SmartBlockEntity {

	public static final int CYCLE = 20;

	public ItemStack heldBox;
	public ItemStack previouslyUnwrapped;
	public int animationTicks;
	public boolean animationInward;
	public boolean redstonePowered;

	public InvManipulationBehaviour targetInventory;
	public BioPackagerItemHandler inventory;
	private final LazyOptional<IItemHandler> invProvider;

	public BioPackagerBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.BIO_PACKAGER.get(), pos, state);
		heldBox = ItemStack.EMPTY;
		previouslyUnwrapped = ItemStack.EMPTY;
		animationTicks = 0;
		animationInward = true;
		redstonePowered = state.getOptionalValue(BioPackagerBlock.POWERED).orElse(false);
		inventory = new BioPackagerItemHandler(this);
		invProvider = LazyOptional.of(() -> inventory);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(targetInventory = new InvManipulationBehaviour(this, InterfaceProvider.oppositeOfBlockFacing()));
	}

	@Override
	public void tick() {
		super.tick();

		if (animationTicks == 0) {
			previouslyUnwrapped = ItemStack.EMPTY;
			if (!level.isClientSide && heldBox.isEmpty())
				attemptAutoExtract();
			return;
		}

		if (level.isClientSide) {
			if (animationTicks == CYCLE - (animationInward ? 5 : 1))
				level.playLocalSound(worldPosition, SoundEvents.UI_TOAST_IN, SoundSource.BLOCKS, 0.5f, 1f, true);
			if (animationTicks == (animationInward ? 1 : 5))
				level.playLocalSound(worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.25f, 0.75f,
					true);
		}

		if (!level.isClientSide && animationInward && animationTicks == CYCLE / 2) {
			releaseCapturedEntity();
		}

		animationTicks--;

		if (animationTicks == 0 && !level.isClientSide)
			setChanged();
	}

	private void attemptAutoExtract() {
		if (targetInventory == null)
			return;
		IItemHandler inv = targetInventory.getInventory();
		if (inv == null || inv instanceof BioPackagerItemHandler)
			return;
		for (int slot = 0; slot < inv.getSlots(); slot++) {
			ItemStack candidate = inv.getStackInSlot(slot);
			if (!inventory.isItemValid(0, candidate))
				continue;
			ItemStack extracted = inv.extractItem(slot, 1, false);
			if (extracted.isEmpty())
				continue;
			startUnpacking(extracted);
			return;
		}
	}

	public boolean startUnpacking(ItemStack box) {
		if (!heldBox.isEmpty() || animationTicks > 0)
			return false;
		heldBox = box.copy();
		previouslyUnwrapped = ItemStack.EMPTY;
		animationInward = true;
		animationTicks = CYCLE;
		notifyUpdate();
		return true;
	}

	private void releaseCapturedEntity() {
		if (heldBox.isEmpty() || !CapturedEntityBoxHelper.hasCapturedEntity(heldBox))
			return;

		Entity entity = createEntityFromBox(heldBox);
		if (entity == null)
			return;

		BlockPos releasePos = worldPosition.above();
		entity.setPos(releasePos.getX() + 0.5d, releasePos.getY(), releasePos.getZ() + 0.5d);
		if (entity instanceof Mob mob)
			mob.setNoAi(false);
		CapturedEntityBoxHelper.unmarkAiDisabledByMod(entity);

		if (!level.addFreshEntity(entity))
			return;

		ItemStack emptyBox = heldBox.copy();
		emptyBox.setCount(1);
		CapturedEntityBoxHelper.clearCapturedEntity(emptyBox);

		previouslyUnwrapped = heldBox;
		heldBox = ItemStack.EMPTY;

		ItemStack leftover = pushToTargetOrDrop(emptyBox);
		if (!leftover.isEmpty())
			Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
				worldPosition.getZ() + 0.5, leftover);

		notifyUpdate();
	}

	private Entity createEntityFromBox(ItemStack box) {
		return CapturedEntityBoxHelper.createCapturedEntity(box, level);
	}

	private ItemStack pushToTargetOrDrop(ItemStack stack) {
		if (targetInventory == null)
			return stack;
		IItemHandler inv = targetInventory.getInventory();
		if (inv == null || inv instanceof BioPackagerItemHandler)
			return stack;
		return ItemHandlerHelper.insertItemStacked(inv, stack, false);
	}

	public void triggerStockCheck() {
		// kept for parity with packager-style neighbor change hook
	}

	public void activate() {
		redstonePowered = true;
		setChanged();
		if (!level.isClientSide && heldBox.isEmpty() && animationTicks == 0)
			attemptAutoExtract();
	}

	@Override
	public void invalidate() {
		super.invalidate();
		invProvider.invalidate();
	}

	@Override
	public void destroy() {
		super.destroy();
		if (heldBox.isEmpty())
			return;
		// drop the held box; if it has a captured entity, drop the box (player can release later)
		Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
			heldBox.copy());
		heldBox = ItemStack.EMPTY;
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
		if (cap == ForgeCapabilities.ITEM_HANDLER)
			return invProvider.cast();
		return super.getCapability(cap, side);
	}

	@Override
	protected void read(CompoundTag compound, boolean clientPacket) {
		super.read(compound, clientPacket);
		redstonePowered = compound.getBoolean("Active");
		animationInward = compound.getBoolean("AnimationInward");
		animationTicks = compound.getInt("AnimationTicks");
		heldBox = ItemStack.of(compound.getCompound("HeldBox"));
		previouslyUnwrapped = ItemStack.of(compound.getCompound("InsertedBox"));
	}

	@Override
	protected void write(CompoundTag compound, boolean clientPacket) {
		super.write(compound, clientPacket);
		compound.putBoolean("Active", redstonePowered);
		compound.putBoolean("AnimationInward", animationInward);
		compound.putInt("AnimationTicks", animationTicks);
		compound.put("HeldBox", heldBox.serializeNBT());
		compound.put("InsertedBox", previouslyUnwrapped.serializeNBT());
	}

	public float getTrayOffset(float partialTicks) {
		return calculateTrayOffset(animationInward, animationTicks - partialTicks);
	}

	public static float calculateTrayOffset(boolean animationInward, float remainingTicks) {
		float tickCycle = animationInward ? remainingTicks : remainingTicks - 5;
		float progress = Mth.clamp(tickCycle / (CYCLE - 5) * 2 - 1, -1, 1);
		progress = 1 - progress * progress;
		return progress * progress;
	}

	public ItemStack getRenderedBox() {
		return getRenderedBox(animationInward, animationTicks, heldBox, previouslyUnwrapped);
	}

	public static ItemStack getRenderedBox(boolean animationInward, int animationTicks, ItemStack heldBox,
		ItemStack previouslyUnwrapped) {
		if (animationInward)
			return animationTicks <= CYCLE / 2 ? ItemStack.EMPTY
				: previouslyUnwrapped.isEmpty() ? heldBox : previouslyUnwrapped;
		return animationTicks >= CYCLE / 2 ? ItemStack.EMPTY : heldBox;
	}
}
