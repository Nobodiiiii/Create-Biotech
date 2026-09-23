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

public class HoneycombGaugeClusterMenu extends AbstractContainerMenu {
	private final BlockPos origin;
	private final Direction facing;
	private final Snapshot snapshot;

	public HoneycombGaugeClusterMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
		super(CBMenuTypes.HONEYCOMB_GAUGE_CLUSTER.get(), id);
		origin = data.readBlockPos();
		facing = Direction.from3DDataValue(data.readByte());
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

	public HoneycombGaugeClusterMenu(int id, Inventory inventory, BlockPos origin, Direction facing,
		Snapshot snapshot) {
		super(CBMenuTypes.HONEYCOMB_GAUGE_CLUSTER.get(), id);
		this.origin = origin.immutable();
		this.facing = facing;
		this.snapshot = snapshot;
	}

	public static void writeSnapshot(RegistryFriendlyByteBuf data, BlockPos origin, Direction facing,
		Snapshot snapshot) {
		data.writeBlockPos(origin);
		data.writeByte(facing.get3DDataValue());
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
	public Snapshot snapshot() { return snapshot; }

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
