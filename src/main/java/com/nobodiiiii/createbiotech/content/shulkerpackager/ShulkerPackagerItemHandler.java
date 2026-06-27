package com.nobodiiiii.createbiotech.content.shulkerpackager;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerItemHandler;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

public class ShulkerPackagerItemHandler extends PackagerItemHandler {

	private final ShulkerPackagerBlockEntity blockEntity;

	public ShulkerPackagerItemHandler(ShulkerPackagerBlockEntity blockEntity) {
		super(blockEntity);
		this.blockEntity = blockEntity;
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		ItemStack remainder = super.insertItem(slot, stack, simulate);
		if (remainder.getCount() < stack.getCount())
			return remainder;
		if (slot != 0 || stack.isEmpty() || !isItemValid(slot, stack))
			return stack;
		if (!blockEntity.heldBox.isEmpty() || !blockEntity.queuedExitingPackages.isEmpty()
			|| blockEntity.animationTicks != 0)
			return stack;

		ItemStack accepted = stack.copy();
		accepted.setCount(1);
		if (!simulate) {
			blockEntity.heldBox = accepted;
			blockEntity.previouslyUnwrapped = ItemStack.EMPTY;
			blockEntity.animationInward = false;
			blockEntity.animationTicks = 0;
			blockEntity.heldBoxIdleTicks = 0;
			blockEntity.triggerStockCheck();
			blockEntity.notifyUpdate();
			blockEntity.setChanged();
		}
		return ItemHandlerHelper.copyStackWithSize(stack, stack.getCount() - 1);
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		return PackageItem.isPackage(stack);
	}
}
