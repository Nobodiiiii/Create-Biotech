package com.nobodiiiii.createbiotech.entity.inventory;

import java.util.List;
import java.util.function.IntSupplier;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** A cached slot map over real equipment containers, without copying their items. */
public final class BionicInventoryContainer implements Container {
	private final List<Container> parts;
	private final Container[] owners;
	private final int[] indices;

	public BionicInventoryContainer(List<Container> parts) {
		this.parts = List.copyOf(parts);
		int size = parts.stream().mapToInt(Container::getContainerSize).sum();
		owners = new Container[size];
		indices = new int[size];
		int index = 0;
		for (Container part : parts)
			for (int slot = 0; slot < part.getContainerSize(); slot++) {
				owners[index] = part;
				indices[index++] = slot;
			}
	}

	@Override public int getContainerSize() { return owners.length; }
	@Override public ItemStack getItem(int slot) { return owners[slot].getItem(indices[slot]); }
	@Override public ItemStack removeItem(int slot, int amount) { return owners[slot].removeItem(indices[slot], amount); }
	@Override public ItemStack removeItemNoUpdate(int slot) { return owners[slot].removeItemNoUpdate(indices[slot]); }
	@Override public void setItem(int slot, ItemStack stack) { owners[slot].setItem(indices[slot], stack); }
	@Override public boolean canPlaceItem(int slot, ItemStack stack) { return owners[slot].canPlaceItem(indices[slot], stack); }
	@Override public int getMaxStackSize() { return parts.isEmpty() ? 64 : parts.getFirst().getMaxStackSize(); }
	@Override public boolean isEmpty() { return parts.stream().allMatch(Container::isEmpty); }
	@Override public void setChanged() { parts.forEach(Container::setChanged); }
	private void setSlotChanged(int slot) { owners[slot].setChanged(); }
	@Override public void clearContent() { parts.forEach(Container::clearContent); }
	@Override public boolean stillValid(Player player) { return parts.stream().allMatch(part -> part.stillValid(player)); }
	@Override public void startOpen(Player player) { parts.forEach(part -> part.startOpen(player)); }
	@Override public void stopOpen(Player player) { parts.forEach(part -> part.stopOpen(player)); }

	/** Menus keep a fixed number of visible slots even when hundreds of boxes are combined. */
	public static Container page(Container inventory, IntSupplier offset, int size) {
		return new Container() {
			private int index(int slot) { return offset.getAsInt() + slot; }
			@Override public int getContainerSize() { return size; }
			@Override public ItemStack getItem(int slot) {
				return index(slot) < inventory.getContainerSize() ? inventory.getItem(index(slot)) : ItemStack.EMPTY;
			}
			@Override public ItemStack removeItem(int slot, int amount) {
				return index(slot) < inventory.getContainerSize() ? inventory.removeItem(index(slot), amount) : ItemStack.EMPTY;
			}
			@Override public ItemStack removeItemNoUpdate(int slot) {
				return index(slot) < inventory.getContainerSize() ? inventory.removeItemNoUpdate(index(slot)) : ItemStack.EMPTY;
			}
			@Override public void setItem(int slot, ItemStack stack) {
				if (index(slot) < inventory.getContainerSize()) inventory.setItem(index(slot), stack);
			}
			@Override public boolean canPlaceItem(int slot, ItemStack stack) {
				return index(slot) < inventory.getContainerSize() && inventory.canPlaceItem(index(slot), stack);
			}
			@Override public int getMaxStackSize() { return inventory.getMaxStackSize(); }
			@Override public void setChanged() {
				if (inventory instanceof BionicInventoryContainer combined) {
					for (int slot = 0; slot < size && index(slot) < inventory.getContainerSize(); slot++)
						combined.setSlotChanged(index(slot));
				} else inventory.setChanged();
			}
			@Override public boolean stillValid(Player player) { return inventory.stillValid(player); }
			@Override public boolean isEmpty() {
				for (int slot = 0; slot < size; slot++) if (!getItem(slot).isEmpty()) return false;
				return true;
			}
			@Override public void clearContent() {
				for (int slot = 0; slot < size; slot++) removeItemNoUpdate(slot);
				setChanged();
			}
		};
	}
}
