package com.nobodiiiii.createbiotech.content.shulkerteleporter;

import java.util.ArrayList;
import java.util.List;

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
	private final List<String> candidateAddresses;

	public ShulkerTeleporterMenu(int id, Inventory playerInventory, FriendlyByteBuf data) {
		this(id, playerInventory, getBlockEntity(playerInventory, data.readBlockPos()), data);
	}

	public ShulkerTeleporterMenu(int id, Inventory playerInventory, ShulkerTeleporterBlockEntity blockEntity) {
		super(CBMenuTypes.SHULKER_TELEPORTER.get(), id);
		this.blockEntity = blockEntity;
		this.blockPos = blockEntity.getBlockPos();
		this.ownAddress = blockEntity.getOwnAddress();
		this.targetAddress = blockEntity.getTargetAddress();
		this.candidateAddresses = List.copyOf(blockEntity.getCandidateAddresses());
	}

	private ShulkerTeleporterMenu(int id, Inventory playerInventory, ShulkerTeleporterBlockEntity blockEntity,
		FriendlyByteBuf data) {
		super(CBMenuTypes.SHULKER_TELEPORTER.get(), id);
		this.blockEntity = blockEntity;
		this.blockPos = blockEntity.getBlockPos();
		this.ownAddress = data.readUtf(ShulkerTeleporterBlockEntity.MAX_ADDRESS_LENGTH);
		this.targetAddress = data.readUtf(ShulkerTeleporterBlockEntity.MAX_ADDRESS_LENGTH);
		this.candidateAddresses = readCandidateAddresses(data);
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

	public List<String> getCandidateAddresses() {
		return candidateAddresses;
	}

	private static List<String> readCandidateAddresses(FriendlyByteBuf data) {
		int size = data.readVarInt();
		List<String> addresses = new ArrayList<>(size);
		for (int i = 0; i < size; i++)
			addresses.add(data.readUtf(ShulkerTeleporterBlockEntity.MAX_ADDRESS_LENGTH));
		return List.copyOf(addresses);
	}

	private static ShulkerTeleporterBlockEntity getBlockEntity(Inventory playerInventory, BlockPos pos) {
		BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(pos);
		if (blockEntity instanceof ShulkerTeleporterBlockEntity teleporter)
			return teleporter;
		throw new IllegalStateException("Shulker Teleporter menu opened without a matching block entity");
	}
}
