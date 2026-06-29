package com.nobodiiiii.createbiotech.content.processing.basin;

import com.simibubi.create.content.processing.basin.BasinBlockEntity;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

public class BasinCapturedSlimeItemHandler implements IItemHandlerModifiable {
	private final IItemHandlerModifiable wrapped;

	public BasinCapturedSlimeItemHandler(BasinBlockEntity basin, IItemHandlerModifiable wrapped) {
		this.wrapped = wrapped;
	}

	@Override
	public int getSlots() {
		return wrapped.getSlots();
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		validateSlot(slot);
		return wrapped.getStackInSlot(slot);
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		validateSlot(slot);
		if (BasinEntityProcessing.isCapturedSmallSlimeItem(stack)
			&& !BasinEntityProcessing.canMoveCapturedSmallSlimeItems())
			return stack;
		return wrapped.insertItem(slot, stack, simulate);
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		validateSlot(slot);
		if (BasinEntityProcessing.isCapturedSmallSlimeItem(wrapped.getStackInSlot(slot))
			&& !BasinEntityProcessing.canMoveCapturedSmallSlimeItems())
			return ItemStack.EMPTY;
		return wrapped.extractItem(slot, amount, simulate);
	}

	@Override
	public int getSlotLimit(int slot) {
		validateSlot(slot);
		return wrapped.getSlotLimit(slot);
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		validateSlot(slot);
		if (BasinEntityProcessing.isCapturedSmallSlimeItem(stack)
			&& !BasinEntityProcessing.canMoveCapturedSmallSlimeItems())
			return false;
		return wrapped.isItemValid(slot, stack);
	}

	@Override
	public void setStackInSlot(int slot, ItemStack stack) {
		validateSlot(slot);
		ItemStack current = wrapped.getStackInSlot(slot);
		if ((BasinEntityProcessing.isCapturedSmallSlimeItem(current)
			|| BasinEntityProcessing.isCapturedSmallSlimeItem(stack))
			&& !BasinEntityProcessing.canMoveCapturedSmallSlimeItems())
			return;
		wrapped.setStackInSlot(slot, stack);
	}

	private void validateSlot(int slot) {
		if (slot < 0 || slot >= getSlots())
			throw new RuntimeException("Slot " + slot + " not in valid range - [0," + getSlots() + ")");
	}
}
