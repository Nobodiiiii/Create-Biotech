package com.yision.allay.block.allayport;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.item.ItemHelper;
import com.yision.allay.logistics.address.AllayAddressRules;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

final class AllayPortAutomation {

	private final AllayPortBlockEntity port;
	private final AllayPortInventory inventory;

	AllayPortAutomation(AllayPortBlockEntity port, AllayPortInventory inventory) {
		this.port = port;
		this.inventory = inventory;
	}

	void tick() {
		tryPushingToBelow();
		tryPullingFromBelow();
	}

	private void tryPushingToBelow() {
		IItemHandler source = port.getAutomationItemHandler();
		if (source == null)
			return;

		IItemHandler destination = getAdjacentInventory(Direction.DOWN);
		if (destination == null)
			return;

		for (int slot = 0; slot < source.getSlots(); slot++) {
			ItemStack packageInSlot = source.extractItem(slot, 1, true);
			if (packageInSlot.isEmpty())
				continue;

			ItemStack simulatedRemainder = ItemHandlerHelper.insertItemStacked(destination, packageInSlot, true);
			if (!simulatedRemainder.isEmpty())
				continue;

			ItemStack extracted = source.extractItem(slot, 1, false);
			if (extracted.isEmpty())
				continue;

			ItemStack remainder = ItemHandlerHelper.insertItemStacked(destination, extracted, false);
			if (!remainder.isEmpty()) {
				ItemStack rollbackRemainder = port.inventory.insertItem(slot, remainder, false);
				if (!rollbackRemainder.isEmpty())
					port.drop(rollbackRemainder);
			}
			port.markPortContentsChanged();
		}
	}

	boolean tryPullingFromBelow() {
		IItemHandler handler = getAdjacentInventory(Direction.DOWN);
		return handler != null && tryPullingFrom(handler);
	}

	private boolean tryPullingFrom(IItemHandler handler) {
		ItemStack extracted = ItemHelper.extract(handler, stack -> {
			if (!PackageItem.isPackage(stack)) {
				return false;
			}
			String filterString = port.getFilterString();
			return filterString == null || !AllayAddressRules.matchesPackage(stack, filterString);
		}, false);
		if (extracted.isEmpty()) {
			return false;
		}
		return inventory.addPackage(extracted, false);
	}

	private @Nullable IItemHandler getAdjacentInventory(Direction side) {
		if (port.getLevel() == null) {
			return null;
		}
		BlockEntity blockEntity = port.getLevel().getBlockEntity(port.getBlockPos().relative(side));
		if (blockEntity == null || blockEntity instanceof AllayPortBlockEntity) {
			return null;
		}
		return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side.getOpposite()).orElse(null);
	}
}
