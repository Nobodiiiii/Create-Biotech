package com.nobodiiiii.createbiotech.content.allay.block.allayport;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.item.ItemHandlerWrapper;
import com.nobodiiiii.createbiotech.content.allay.logistics.address.AllayAddressRules;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

final class AllayPortAutomationInventoryWrapper extends ItemHandlerWrapper {
	private final AllayPortBlockEntity port;

	AllayPortAutomationInventoryWrapper(IItemHandlerModifiable wrapped, AllayPortBlockEntity port) {
		super(wrapped);
		this.port = port;
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		ItemStack preview = super.extractItem(slot, amount, true);
		if (!PackageItem.isPackage(preview)) {
			return ItemStack.EMPTY;
		}

		String filterString = port.getFilterString();
		if (filterString == null || !AllayAddressRules.matchesPackage(preview, filterString)) {
			return ItemStack.EMPTY;
		}

		return simulate ? preview : super.extractItem(slot, amount, false);
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		if (!PackageItem.isPackage(stack)) {
			return stack;
		}

		String filterString = port.getFilterString();
		if (filterString != null && AllayAddressRules.matchesPackage(stack, filterString)) {
			return stack;
		}

		return super.insertItem(slot, stack, simulate);
	}
}
