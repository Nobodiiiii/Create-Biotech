package com.nobodiiiii.createbiotech.entity.inventory;

import java.util.Collection;
import java.util.List;

import com.nobodiiiii.createbiotech.entity.DevourerEntity;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.registry.CBAttachmentTypes;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** The animal's possessions travel with its live entity and its captured box. */
public final class DevourerInventory extends SimpleContainer implements INBTSerializable<CompoundTag> {
	private final Mob holder;

	public DevourerInventory(Mob holder) {
		super(27);
		this.holder = holder;
	}

	public static boolean canCollect(Mob hunter) {
		return hunter instanceof DevourerEntity || hunter instanceof SlimeBionicEntity bionic
			&& (bionic.wildSlimeSize() > 0 || bionic.getBionicTraits().has(BionicTrait.DEVOUR));
	}

	public static boolean hasEmptySlot(Mob hunter) {
		var inventory = hunter.getExistingDataOrNull(CBAttachmentTypes.DEVOURER_INVENTORY);
		if (inventory == null) return true;
		for (var stack : inventory.getItems())
			if (stack.isEmpty()) return true;
		return false;
	}

	public void collect(Collection<ItemEntity> drops) {
		for (var iterator = drops.iterator(); iterator.hasNext();) {
			if (insertDrop(iterator.next())) iterator.remove();
		}
	}

	public void collectSpawned(ServerLevel level, Collection<ItemEntity> drops) {
		for (var drop : List.copyOf(drops)) {
			if (!holder.isAlive() || !holder.isAddedToLevel() || holder.level() != level) break;
			if (drop.level() != level || !drop.isAddedToLevel() || drop.isRemoved() || drop.getItem().isEmpty()) continue;
			if (insertDrop(drop)) drop.discard();
		}
	}

	private boolean insertDrop(ItemEntity drop) {
		var stack = drop.getItem();
		int remaining = stack.getCount();
		// A modded loot entry may be larger than one legal slot.
		while (remaining > 0) {
			int batch = Math.min(remaining, getMaxStackSize(stack));
			if (batch <= 0) break;
			int inserted = batch - addItem(stack.copyWithCount(batch)).getCount();
			remaining -= inserted;
			if (inserted < batch) break;
		}
		if (remaining == 0) return true;
		if (remaining != stack.getCount()) drop.setItem(stack.copyWithCount(remaining));
		return false;
	}

	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider registries) {
		return ContainerHelper.saveAllItems(new CompoundTag(), getItems(), registries);
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider registries, CompoundTag data) {
		clearContent();
		ContainerHelper.loadAllItems(data, getItems(), registries);
	}
}
