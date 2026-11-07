package com.nobodiiiii.createbiotech.entity.inventory;

import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.CombinationMember;
import com.nobodiiiii.createbiotech.registry.CBMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class BionicCargoMenu extends AbstractContainerMenu {
	private final SlimeBionicEntity entity;
	private final Container cargo;
	private final CombinationMember carrier;
	public final int capacity;
	public BionicCargoMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
		this(id, inventory, inventory.player.level().getEntity(buffer.readInt()) instanceof SlimeBionicEntity b ? b : null,
			new CombinationMember(buffer.readVarInt(), buffer.readVarInt()),
			net.minecraft.util.Mth.clamp(buffer.readVarInt(), 3, 15));
	}
	public BionicCargoMenu(int id, Inventory inventory, SlimeBionicEntity entity, CombinationMember carrier, int capacity) {
		super(CBMenuTypes.BIONIC_CARGO.get(), id);
		this.entity = entity;
		this.carrier = carrier;
		this.capacity = capacity;
		cargo = entity == null || inventory.player.level().isClientSide ? new SimpleContainer(capacity) : entity.getBionicEquipment().cargo(carrier);
		cargo.startOpen(inventory.player);
		int columns = capacity / 3;
		for (int row = 0; row < 3; row++) for (int col = 0; col < columns; col++)
			addSlot(new Slot(cargo, row * columns + col, 44 + col * 18, 18 + row * 18));
		for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
			addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 85 + row * 18));
		for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 143));
	}
	@Override public void removed(Player player) { super.removed(player); cargo.stopOpen(player); }
	@Override public boolean stillValid(Player player) {
		return entity != null && cargo.stillValid(player) && entity.getBionicEquipment().hasChest(carrier)
			&& entity.getBionicEquipment().capacity(carrier) == capacity;
	}
	@Override public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) return ItemStack.EMPTY;
		ItemStack stack = slot.getItem(), previous = stack.copy();
		if (!moveItemStackTo(stack, index < capacity ? capacity : 0, index < capacity ? slots.size() : capacity, index < capacity))
			return ItemStack.EMPTY;
		if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
		slot.onTake(player, stack);
		return previous;
	}
}
