package com.nobodiiiii.createbiotech.content.processing.basin;

import com.simibubi.create.content.processing.basin.BasinBlockEntity;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

public class BasinCapturedSlimeItemHandler implements IItemHandlerModifiable {
	private final BasinBlockEntity basin;
	private final IItemHandlerModifiable wrapped;

	public BasinCapturedSlimeItemHandler(BasinBlockEntity basin, IItemHandlerModifiable wrapped) {
		this.basin = basin;
		this.wrapped = wrapped;
	}

	@Override
	public int getSlots() {
		return wrapped.getSlots() + 1;
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		validateSlot(slot);
		if (isCapturedSlimeSlot(slot))
			return BasinEntityProcessing.getCapturedSmallSlimeItemStack(basin);
		return wrapped.getStackInSlot(slot);
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		validateSlot(slot);
		if (isCapturedSlimeSlot(slot) || BasinEntityProcessing.isCapturedSmallSlimeItem(stack))
			return stack;
		return wrapped.insertItem(slot, stack, simulate);
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		validateSlot(slot);
		if (isCapturedSlimeSlot(slot))
			return ItemStack.EMPTY;
		return wrapped.extractItem(slot, amount, simulate);
	}

	@Override
	public int getSlotLimit(int slot) {
		validateSlot(slot);
		if (isCapturedSlimeSlot(slot))
			return BasinEntityProcessing.MAX_CAPTURED_SMALL_SLIMES;
		return wrapped.getSlotLimit(slot);
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		validateSlot(slot);
		if (isCapturedSlimeSlot(slot) || BasinEntityProcessing.isCapturedSmallSlimeItem(stack))
			return false;
		return wrapped.isItemValid(slot, stack);
	}

	@Override
	public void setStackInSlot(int slot, ItemStack stack) {
		validateSlot(slot);
		if (!isCapturedSlimeSlot(slot)) {
			wrapped.setStackInSlot(slot, stack);
		}
	}

	private boolean isCapturedSlimeSlot(int slot) {
		return slot == wrapped.getSlots();
	}

	private void validateSlot(int slot) {
		if (slot < 0 || slot >= getSlots())
			throw new RuntimeException("Slot " + slot + " not in valid range - [0," + getSlots() + ")");
	}
}
