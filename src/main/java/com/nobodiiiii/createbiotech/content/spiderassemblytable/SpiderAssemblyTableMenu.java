package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableBlockEntity.FluidExchangeResult;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableBlockEntity.MachineKind;
import com.nobodiiiii.createbiotech.registry.CBMenuTypes;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

public class SpiderAssemblyTableMenu extends AbstractContainerMenu {

	public static final int TABLE_SLOT_COUNT = SpiderAssemblyTableBlockEntity.SLOT_COUNT;
	public static final int PLAYER_INVENTORY_START = TABLE_SLOT_COUNT;
	public static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
	public static final int HOTBAR_START = PLAYER_INVENTORY_END;
	public static final int HOTBAR_END = HOTBAR_START + 9;
	public static final int HYBRID_SLOT_ROW_Y = 40;

	private final SpiderAssemblyTableBlockEntity blockEntity;

	public SpiderAssemblyTableMenu(int id, Inventory playerInventory, FriendlyByteBuf data) {
		this(id, playerInventory, getBlockEntity(playerInventory, data));
	}

	public SpiderAssemblyTableMenu(int id, Inventory playerInventory, SpiderAssemblyTableBlockEntity blockEntity) {
		super(CBMenuTypes.SPIDER_ASSEMBLY_TABLE.get(), id);
		this.blockEntity = blockEntity;

		IItemHandler inventory = blockEntity.getInventory();
		int xStart = 17;
		for (int i = 0; i < SpiderAssemblyTableBlockEntity.LEG_COUNT; i++)
			addSlot(new MachineSlot(inventory, SpiderAssemblyTableBlockEntity.MACHINE_SLOT_START + i,
				xStart + i * 18, 18));
		for (int i = 0; i < SpiderAssemblyTableBlockEntity.LEG_COUNT; i++)
			addSlot(new HybridSlot(inventory, SpiderAssemblyTableBlockEntity.HYBRID_SLOT_START + i,
				xStart + i * 18, HYBRID_SLOT_ROW_Y, blockEntity, i));

		for (int row = 0; row < 3; row++)
			for (int col = 0; col < 9; col++)
				addSlot(new net.minecraft.world.inventory.Slot(playerInventory, col + row * 9 + 9,
					8 + col * 18, 82 + row * 18));

		for (int col = 0; col < 9; col++)
			addSlot(new net.minecraft.world.inventory.Slot(playerInventory, col, 8 + col * 18, 140));
	}

	public SpiderAssemblyTableBlockEntity getBlockEntity() {
		return blockEntity;
	}

	@Override
	public boolean stillValid(Player player) {
		return blockEntity.canPlayerUse(player);
	}

	@Override
	public void clicked(int slotId, int button, ClickType clickType, Player player) {
		if (clickType == ClickType.PICKUP && button == 1 && slotId >= 0 && slotId < slots.size()) {
			net.minecraft.world.inventory.Slot slot = slots.get(slotId);
			if (slot instanceof HybridSlot hybridSlot) {
				ItemStack carried = getCarried();
				if (!carried.isEmpty()
					&& carried.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent()) {
					if (tryFluidExchange(hybridSlot.getHybridIndex(), carried, player))
						return;
				}
			}
		}
		super.clicked(slotId, button, clickType, player);
	}

	private boolean tryFluidExchange(int hybridIndex, ItemStack carried, Player player) {
		ItemStack singleItem = carried.copy();
		singleItem.setCount(1);

		FluidExchangeResult result = blockEntity.exchangeFluidWithItem(hybridIndex, singleItem);
		if (!result.success())
			return false;

		ItemStack remainder = result.remainingContainer();
		if (carried.getCount() <= 1) {
			setCarried(remainder.isEmpty() ? ItemStack.EMPTY : remainder);
		} else {
			carried.shrink(1);
			if (!remainder.isEmpty() && !player.getInventory().add(remainder))
				player.drop(remainder, false);
		}
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack empty = ItemStack.EMPTY;
		net.minecraft.world.inventory.Slot slot = slots.get(index);
		if (slot == null || !slot.hasItem())
			return empty;

		ItemStack original = slot.getItem();
		ItemStack copy = original.copy();

		if (index < TABLE_SLOT_COUNT) {
			if (!moveItemStackTo(original, PLAYER_INVENTORY_START, HOTBAR_END, true))
				return empty;
		} else if (MachineKind.fromStack(original) != null) {
			if (!moveItemStackTo(original, SpiderAssemblyTableBlockEntity.MACHINE_SLOT_START,
				SpiderAssemblyTableBlockEntity.HYBRID_SLOT_START, false))
				return empty;
		} else if (!moveItemStackTo(original, SpiderAssemblyTableBlockEntity.HYBRID_SLOT_START,
			SpiderAssemblyTableBlockEntity.SLOT_COUNT, false)) {
			return empty;
		}

		if (original.isEmpty())
			slot.set(ItemStack.EMPTY);
		else
			slot.setChanged();

		return copy;
	}

	private static SpiderAssemblyTableBlockEntity getBlockEntity(Inventory playerInventory, FriendlyByteBuf data) {
		BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(data.readBlockPos());
		if (blockEntity instanceof SpiderAssemblyTableBlockEntity spiderAssemblyTable)
			return spiderAssemblyTable;
		throw new IllegalStateException("Spider Assembly Table menu opened without a matching block entity");
	}

	private static class MachineSlot extends SlotItemHandler {
		private MachineSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
			super(itemHandler, index, xPosition, yPosition);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public int getMaxStackSize(ItemStack stack) {
			return 1;
		}
	}

	public static class HybridSlot extends SlotItemHandler {
		private final SpiderAssemblyTableBlockEntity blockEntity;
		private final int hybridIndex;

		private HybridSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition,
			SpiderAssemblyTableBlockEntity blockEntity, int hybridIndex) {
			super(itemHandler, index, xPosition, yPosition);
			this.blockEntity = blockEntity;
			this.hybridIndex = hybridIndex;
		}

		public int getHybridIndex() {
			return hybridIndex;
		}

		public SpiderAssemblyTableBlockEntity getBlockEntity() {
			return blockEntity;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			if (!super.mayPlace(stack))
				return false;
			return blockEntity.isHybridSlotItemOnly(hybridIndex);
		}
	}
}
