package com.nobodiiiii.createbiotech.content.honeycombgauge;

import java.util.ArrayList;
import java.util.List;

import com.nobodiiiii.createbiotech.content.honeycombgauge.HoneycombGaugeClusterScanner.Gauge;
import com.nobodiiiii.createbiotech.content.honeycombgauge.HoneycombGaugeClusterScanner.Snapshot;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBMenuTypes;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class HoneycombGaugeClusterMenu extends AbstractContainerMenu {
	public static final int ROTATE_UP_BUTTON = 0;
	private final Player player;
	private final BlockPos origin;
	private final Direction facing;
	private final Direction initialUp;
	private Snapshot snapshot;

	public HoneycombGaugeClusterMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
		super(CBMenuTypes.HONEYCOMB_GAUGE_CLUSTER.get(), id);
		player = inventory.player;
		origin = data.readBlockPos();
		facing = Direction.from3DDataValue(data.readByte());
		initialUp = Direction.from3DDataValue(data.readByte());
		int blockCount = data.readVarInt();
		if (blockCount < 0 || blockCount > HoneycombGaugeClusterScanner.MAX_BLOCKS)
			throw new IllegalArgumentException("Invalid honeycomb gauge cluster size");
		List<BlockPos> blocks = new ArrayList<>(blockCount);
		for (int i = 0; i < blockCount; i++)
			blocks.add(data.readBlockPos());
		int gaugeCount = data.readVarInt();
		if (gaugeCount < 0 || gaugeCount > HoneycombGaugeClusterScanner.MAX_BLOCKS * 4)
			throw new IllegalArgumentException("Invalid honeycomb gauge count");
		List<Gauge> gauges = new ArrayList<>(gaugeCount);
		for (int i = 0; i < gaugeCount; i++) {
			BlockPos pos = data.readBlockPos();
			int slot = data.readUnsignedByte();
			if (slot >= PanelSlot.values().length)
				throw new IllegalArgumentException("Invalid factory gauge slot");
			gauges.add(new Gauge(pos, PanelSlot.values()[slot], ItemStack.OPTIONAL_STREAM_CODEC.decode(data)));
		}
		snapshot = new Snapshot(List.copyOf(blocks), List.copyOf(gauges), data.readBoolean());
	}

	public HoneycombGaugeClusterMenu(int id, Inventory inventory, BlockPos origin, Direction facing, Direction up,
		Snapshot snapshot) {
		super(CBMenuTypes.HONEYCOMB_GAUGE_CLUSTER.get(), id);
		player = inventory.player;
		this.origin = origin.immutable();
		this.facing = facing;
		this.initialUp = up;
		this.snapshot = snapshot;
	}

	public static void writeSnapshot(RegistryFriendlyByteBuf data, BlockPos origin, Direction facing, Direction up,
		Snapshot snapshot) {
		data.writeBlockPos(origin);
		data.writeByte(facing.get3DDataValue());
		data.writeByte(up.get3DDataValue());
		data.writeVarInt(snapshot.honeycombs().size());
		for (BlockPos pos : snapshot.honeycombs())
			data.writeBlockPos(pos);
		data.writeVarInt(snapshot.gauges().size());
		for (Gauge gauge : snapshot.gauges()) {
			data.writeBlockPos(gauge.pos());
			data.writeByte(gauge.slot().ordinal());
			ItemStack.OPTIONAL_STREAM_CODEC.encode(data, gauge.filter());
		}
		data.writeBoolean(snapshot.limited());
	}

	public BlockPos origin() { return origin; }
	public Direction facing() { return facing; }
	public Direction workspaceUp() {
		BlockState state = player.level().getBlockState(origin);
		Direction up = state.is(CBBlocks.HONEYCOMB_GAUGE_CLUSTER.get())
			&& state.getValue(HoneycombGaugeClusterBlock.FACING) == facing
			? state.getValue(HoneycombGaugeClusterBlock.WORKSPACE_UP) : initialUp;
		return HoneycombGaugeClusterBlock.normalizedWorkspaceUp(facing, up);
	}
	public Snapshot snapshot() { return snapshot; }
	public void refreshSnapshot() {
		if (player.level().isClientSide && player.level().isLoaded(origin))
			snapshot = HoneycombGaugeClusterScanner.scan(player.level(), origin, facing);
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id != ROTATE_UP_BUTTON || player.containerMenu != this || !stillValid(player))
			return false;
		if (!player.level().isClientSide) {
			BlockState state = player.level().getBlockState(origin);
			if (state.getValue(HoneycombGaugeClusterBlock.FACING) != facing)
				return false;
			Direction next = HoneycombGaugeClusterBlock.nextWorkspaceUp(facing,
				state.getValue(HoneycombGaugeClusterBlock.WORKSPACE_UP));
			player.level().setBlock(origin, state.setValue(HoneycombGaugeClusterBlock.WORKSPACE_UP, next), 3);
		}
		return true;
	}

	@Override
	public boolean stillValid(Player player) {
		return player.level().getBlockState(origin).is(CBBlocks.HONEYCOMB_GAUGE_CLUSTER.get())
			&& player.distanceToSqr(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5) <= 64;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}
}
