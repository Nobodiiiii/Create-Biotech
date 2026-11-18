package com.nobodiiiii.createbiotech.content.slimemimic;

import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.registry.CBMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class GreenprintTableMenu extends AbstractContainerMenu {

	private final SimpleContainer workspace = new SimpleContainer(2);
	private final ContainerLevelAccess access;
	private final BlockPos pos;

	public GreenprintTableMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
		this(id, inventory, buffer.readBlockPos());
	}

	GreenprintTableMenu(int id, Inventory inventory, BlockPos pos) {
		super(CBMenuTypes.GREENPRINT_TABLE.get(), id);
		this.pos = pos.immutable();
		access = ContainerLevelAccess.create(inventory.player.level(), this.pos);
		addSlot(new Slot(workspace, 0, 32, 77) {
			@Override public boolean mayPlace(ItemStack stack) { return stack.is(CBItems.GREENPRINT.get()); }
		});
		addSlot(new Slot(workspace, 1, 128, 77) {
			@Override public boolean mayPlace(ItemStack stack) { return false; }
		});
		for (int row = 0; row < 3; row++)
			for (int column = 0; column < 9; column++)
				addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 120 + row * 18));
		for (int column = 0; column < 9; column++)
			addSlot(new Slot(inventory, column, 8 + column * 18, 178));
	}

	ItemStack input() { return workspace.getItem(0); }

	boolean canImport(Player player) {
		return player.isCreative() && !player.isSpectator() && GreenprintItem.isBlank(input())
			&& workspace.getItem(1).isEmpty();
	}

	boolean canReceive(ServerPlayer player) {
		return player.containerMenu == this && canImport(player) && stillValid(player)
			&& player.level().mayInteract(player, pos);
	}

	boolean importFile(ServerPlayer player, CompoundTag file) {
		if (!canReceive(player))
			return false;
		ItemStack bound = GreenprintItem.importBinding(input(), file, player.serverLevel());
		if (bound == null)
			return false;
		workspace.setItem(0, ItemStack.EMPTY);
		workspace.setItem(1, bound);
		broadcastChanges();
		return true;
	}

	@Override
	public boolean stillValid(Player player) {
		return player.level().isLoaded(pos) && stillValid(access, player, CBBlocks.GREENPRINT_TABLE.get());
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		if (index < 0 || index >= slots.size())
			return ItemStack.EMPTY;
		Slot slot = getSlot(index);
		ItemStack stack = slot.getItem();
		if (stack.isEmpty())
			return ItemStack.EMPTY;
		ItemStack original = stack.copy();
		boolean moved = index < 2 ? moveItemStackTo(stack, 2, slots.size(), true)
			: moveItemStackTo(stack, 0, 1, false);
		if (!moved)
			return ItemStack.EMPTY;
		if (stack.isEmpty())
			slot.setByPlayer(ItemStack.EMPTY);
		else
			slot.setChanged();
		slot.onTake(player, stack);
		return original;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (player instanceof ServerPlayer)
			clearContainer(player, workspace);
	}
}
