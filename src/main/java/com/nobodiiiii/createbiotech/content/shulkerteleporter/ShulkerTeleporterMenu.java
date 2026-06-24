package com.nobodiiiii.createbiotech.content.shulkerteleporter;

import com.nobodiiiii.createbiotech.registry.CBMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ShulkerTeleporterMenu extends AbstractContainerMenu {

	private final ShulkerTeleporterBlockEntity blockEntity;
	private final BlockPos blockPos;
	private final String ownAddress;
	private final String targetAddress;

	public ShulkerTeleporterMenu(int id, Inventory playerInventory, FriendlyByteBuf data) {
		this(id, playerInventory, getBlockEntity(playerInventory, data.readBlockPos()), data);
	}

	public ShulkerTeleporterMenu(int id, Inventory playerInventory, ShulkerTeleporterBlockEntity blockEntity) {
		super(CBMenuTypes.SHULKER_TELEPORTER.get(), id);
		this.blockEntity = blockEntity;
		this.blockPos = blockEntity.getBlockPos();
		this.ownAddress = blockEntity.getOwnAddress();
		this.targetAddress = blockEntity.getTargetAddress();
	}

	private ShulkerTeleporterMenu(int id, Inventory playerInventory, ShulkerTeleporterBlockEntity blockEntity,
		FriendlyByteBuf data) {
		super(CBMenuTypes.SHULKER_TELEPORTER.get(), id);
		this.blockEntity = blockEntity;
		this.blockPos = blockEntity.getBlockPos();
		this.ownAddress = data.readUtf(32);
		this.targetAddress = data.readUtf(32);
	}

	@Override
	public boolean stillValid(Player player) {
		return blockEntity.canPlayerUse(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	public BlockPos getBlockPos() {
		return blockPos;
	}

	public String getOwnAddress() {
		return ownAddress;
	}

	public String getTargetAddress() {
		return targetAddress;
	}

	private static ShulkerTeleporterBlockEntity getBlockEntity(Inventory playerInventory, BlockPos pos) {
		BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(pos);
		if (blockEntity instanceof ShulkerTeleporterBlockEntity teleporter)
			return teleporter;
		throw new IllegalStateException("Shulker Teleporter menu opened without a matching block entity");
	}
}
