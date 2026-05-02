package com.nobodiiiii.createbiotech.content.slimebelt.transport;

import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltBlockEntity;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

public class SlimeItemHandlerBeltSegment implements IItemHandler {

	private final SlimeBeltInventory beltInventory;
	private final int offset;
	private final Direction side;

	public SlimeItemHandlerBeltSegment(SlimeBeltInventory beltInventory, int offset, Direction side) {
		this.beltInventory = beltInventory;
		this.offset = offset;
		this.side = side;
	}

	@Override
	public int getSlots() {
		return 1;
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		TransportedItemStack stackAtOffset = this.beltInventory.getStackAtOffset(offset, side);
		if (stackAtOffset == null)
			return ItemStack.EMPTY;
		return stackAtOffset.stack;
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		if (this.beltInventory.canInsertAtFromSide(offset, side)) {
			ItemStack remainder = ItemHelper.limitCountToMaxStackSize(stack, simulate);
			if (!simulate) {
				TransportedItemStack newStack = new TransportedItemStack(stack);
				this.beltInventory.prepareInsertedItem(newStack, offset, side);
				this.beltInventory.addItem(newStack);
				SlimeBeltBlockEntity belt = this.beltInventory.belt;
				belt.setChanged();
				belt.sendData();
			}
			return remainder;
		}
		return stack;
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		TransportedItemStack transported = this.beltInventory.getStackAtOffset(offset, side);
		if (transported == null)
			return ItemStack.EMPTY;

		amount = Math.min(amount, transported.stack.getCount());
		ItemStack extracted = simulate ? transported.stack.copy()
			.split(amount) : transported.stack.split(amount);
		if (!simulate) {
			if (transported.stack.isEmpty())
				beltInventory.toRemove.add(transported);
			else
				beltInventory.belt.notifyUpdate();
		}

		return extracted;
	}

	@Override
	public int getSlotLimit(int slot) {
		return Math.min(getStackInSlot(slot).getMaxStackSize(), 64);
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		return true;
	}

}
