package com.nobodiiiii.createbiotech.entity.inventory;

import java.util.Collection;

import com.nobodiiiii.createbiotech.content.slimemimic.SlimeCompanionHandler;
import com.nobodiiiii.createbiotech.entity.DevourerEntity;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.registry.CBAttachmentTypes;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
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

	public static boolean tryOpen(Mob holder, Player player, InteractionHand hand) {
		if (!player.isSecondaryUseActive() || !player.getItemInHand(hand).isEmpty()
			|| !(canCollect(holder) || holder.hasData(CBAttachmentTypes.DEVOURER_INVENTORY))) return false;
		var owner = SlimeCompanionHandler.ownerOf(holder);
		if (!holder.isAlive() || owner != null && !owner.equals(player.getUUID())) return false;
		if (!holder.level().isClientSide) {
			var inventory = holder.getData(CBAttachmentTypes.DEVOURER_INVENTORY);
			player.openMenu(new SimpleMenuProvider((id, slots, opener) -> ChestMenu.threeRows(id, slots, inventory),
				holder.getDisplayName()));
		}
		return true;
	}

	@Override
	public boolean stillValid(Player player) {
		var owner = SlimeCompanionHandler.ownerOf(holder);
		return holder.isAlive() && holder.level() == player.level() && player.canInteractWithEntity(holder, 4)
			&& (owner == null || owner.equals(player.getUUID()));
	}

	public void collect(Collection<ItemEntity> drops) {
		for (var iterator = drops.iterator(); iterator.hasNext();) {
			var drop = iterator.next();
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
			if (remaining == 0) iterator.remove();
			else if (remaining != stack.getCount()) drop.setItem(stack.copyWithCount(remaining));
		}
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
