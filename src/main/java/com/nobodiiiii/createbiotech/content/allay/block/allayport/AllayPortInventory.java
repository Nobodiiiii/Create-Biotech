package com.nobodiiiii.createbiotech.content.allay.block.allayport;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.nobodiiiii.createbiotech.content.allay.item.allaycourier.AllayCourierItem;
import com.nobodiiiii.createbiotech.content.allay.logistics.address.AllayAddressRules;
import com.nobodiiiii.createbiotech.registry.CBItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

final class AllayPortInventory {

	private final AllayPortBlockEntity port;
	final ItemStackHandler carrierInventory;
	final IItemHandler combinedHandler;

	AllayPortInventory(AllayPortBlockEntity port) {
		this.port = port;

		this.carrierInventory = new ItemStackHandler(1) {
			@Override
			public boolean isItemValid(int slot, @NotNull ItemStack stack) {
				return isEmptyCarrier(stack);
			}

			@Override
			protected void onContentsChanged(int slot) {
				port.markPortContentsChanged();
			}
		};

		this.combinedHandler = new IItemHandler() {
			@Override
			public int getSlots() {
				return port.inventory.getSlots() + carrierInventory.getSlots();
			}

			@Override
			public @NotNull ItemStack getStackInSlot(int slot) {
				return getCombinedStackInSlot(slot);
			}

			@Override
			public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
				return insertIntoCombinedSlot(slot, stack, simulate);
			}

			@Override
			public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
				return extractFromCombinedSlot(slot, amount, simulate);
			}

			@Override
			public int getSlotLimit(int slot) {
				return getCombinedSlotLimit(slot);
			}

			@Override
			public boolean isItemValid(int slot, @NotNull ItemStack stack) {
				return isValidForCombinedSlot(slot, stack);
			}
		};
	}

	ItemStackHandler carrierInventory() {
		return carrierInventory;
	}

	IItemHandler combinedHandler() {
		return combinedHandler;
	}

	private boolean isCarrierSlot(int slot) {
		return slot == port.inventory.getSlots();
	}

	private boolean isPackageSlot(int slot) {
		return slot >= 0 && slot < port.inventory.getSlots();
	}

	private @NotNull ItemStack getCombinedStackInSlot(int slot) {
		if (isPackageSlot(slot)) {
			return port.inventory.getStackInSlot(slot);
		}
		if (isCarrierSlot(slot)) {
			return carrierInventory.getStackInSlot(0);
		}
		return ItemStack.EMPTY;
	}

	private @NotNull ItemStack insertIntoCombinedSlot(int slot, @NotNull ItemStack stack, boolean simulate) {
		ItemStack remainder;
		if (stack.is(CBItems.ALLAY_COURIER.get())) {
			if (!isCarrierSlot(slot) || !isEmptyCarrier(stack)) {
				return stack;
			}
			remainder = carrierInventory.insertItem(0, stack, simulate);
		} else if (!PackageItem.isPackage(stack) || !isPackageSlot(slot)) {
			return stack;
		} else {
			String filterString = port.getFilterString();
			if (filterString != null && !AllayAddressRules.isBlank(filterString)
				&& AllayAddressRules.matchesPackage(stack, filterString)) {
				return stack;
			}
			remainder = port.inventory.insertItem(slot, stack, simulate);
		}
		return remainder;
	}

	private @NotNull ItemStack extractFromCombinedSlot(int slot, int amount, boolean simulate) {
		if (amount <= 0) {
			return ItemStack.EMPTY;
		}
		if (isPackageSlot(slot)) {
			ItemStack preview = port.inventory.extractItem(slot, 64, true);
			String filterString = port.getFilterString();
			if (!PackageItem.isPackage(preview) || filterString == null || AllayAddressRules.isBlank(filterString)
				|| !AllayAddressRules.matchesPackage(preview, filterString)) {
				return ItemStack.EMPTY;
			}
		} else if (!isCarrierSlot(slot)) {
			return ItemStack.EMPTY;
		}
		ItemStack extracted = isCarrierSlot(slot)
			? carrierInventory.extractItem(0, amount, simulate)
			: port.inventory.extractItem(slot, amount, simulate);
		if (!simulate && !extracted.isEmpty()) {
			port.markPortContentsChanged();
		}
		return extracted;
	}

	private int getCombinedSlotLimit(int slot) {
		if (isCarrierSlot(slot)) {
			return carrierInventory.getSlotLimit(0);
		}
		if (isPackageSlot(slot)) {
			return port.inventory.getSlotLimit(slot);
		}
		return 0;
	}

	private boolean isValidForCombinedSlot(int slot, @NotNull ItemStack stack) {
		if (isCarrierSlot(slot)) {
			return isEmptyCarrier(stack);
		}
		if (isPackageSlot(slot)) {
			return PackageItem.isPackage(stack);
		}
		return false;
	}

	static boolean isEmptyCarrier(ItemStack stack) {
		return AllayCourierItem.isPlainCarrier(stack);
	}

	boolean hasStoredCarrier() {
		return !carrierInventory.getStackInSlot(0).isEmpty();
	}

	ItemStack extractOneCarrier(boolean simulate) {
		return carrierInventory.extractItem(0, 1, simulate);
	}

	boolean addPackage(ItemStack stack, boolean simulate) {
		if (!PackageItem.isPackage(stack)) {
			return false;
		}
		for (int slot = 0; slot < port.inventory.getSlots(); slot++) {
			ItemStack remainder = port.inventory.insertItem(slot, stack, simulate);
			if (!remainder.isEmpty()) {
				continue;
			}
			if (!simulate) {
				port.markPortContentsChanged();
			}
			return true;
		}
		return false;
	}

	boolean canReceiveCourier(ItemStack box) {
		ItemStack carrier = new ItemStack(CBItems.ALLAY_COURIER.get());
		return carrierInventory.insertItem(0, carrier, true).isEmpty() && addPackage(box.copy(), true);
	}

	boolean receiveCourier(ItemStack box) {
		if (!canReceiveCourier(box)) {
			return false;
		}
		ItemStack carrier = new ItemStack(CBItems.ALLAY_COURIER.get());
		carrierInventory.insertItem(0, carrier.copy(), false);
		addPackage(box.copy(), false);
		port.markPortContentsChanged();
		return true;
	}

	boolean canReceivePackage(ItemStack box) {
		return addPackage(box.copy(), true);
	}

	boolean receivePackage(ItemStack box) {
		if (!canReceivePackage(box)) {
			return false;
		}
		addPackage(box.copy(), false);
		return true;
	}

	boolean canReceiveCarrier() {
		ItemStack carrier = new ItemStack(CBItems.ALLAY_COURIER.get());
		return carrierInventory.insertItem(0, carrier, true).isEmpty();
	}

	boolean receiveCarrier() {
		if (!canReceiveCarrier()) {
			return false;
		}
		carrierInventory.insertItem(0, new ItemStack(CBItems.ALLAY_COURIER.get()).copy(), false);
		port.markPortContentsChanged();
		return true;
	}

	void returnCarrier(ItemStack carrier) {
		carrierInventory.insertItem(0, carrier, false);
	}

	void dropOneCarrier() {
		ItemStack carrier = carrierInventory.extractItem(0, 1, false);
		if (carrier.isEmpty() || port.getLevel() == null) {
			return;
		}
		BlockPos pos = port.getBlockPos();
		port.getLevel().addFreshEntity(
			new ItemEntity(port.getLevel(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
				carrier.copy()));
	}

	void dropAllCarriers() {
		if (port.getLevel() == null) {
			return;
		}
		int carrierCount = carrierInventory.getStackInSlot(0).getCount();
		ItemStack carrier = carrierInventory.extractItem(0, carrierCount, false);
		if (carrier.isEmpty()) {
			return;
		}
		BlockPos pos = port.getBlockPos();
		port.getLevel().addFreshEntity(
			new ItemEntity(port.getLevel(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
				carrier));
	}

	void write(CompoundTag tag, HolderLookup.Provider registries) {
		tag.put("CarrierInventory", carrierInventory.serializeNBT(registries));
	}

	void read(CompoundTag tag, HolderLookup.Provider registries) {
		if (tag.contains("CarrierInventory")) {
			carrierInventory.deserializeNBT(registries, tag.getCompound("CarrierInventory"));
		}
	}
}
