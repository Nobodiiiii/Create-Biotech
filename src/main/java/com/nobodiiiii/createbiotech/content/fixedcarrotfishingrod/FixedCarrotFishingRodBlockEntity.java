package com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class FixedCarrotFishingRodBlockEntity extends BlockEntity {

	private ItemStack baitItem = ItemStack.EMPTY;

	public FixedCarrotFishingRodBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.FIXED_CARROT_FISHING_ROD.get(), pos, state);
	}

	public ItemStack getBaitItem() {
		return baitItem;
	}

	public void setBaitItem(ItemStack item) {
		baitItem = item;
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	@Override
	public AABB getRenderBoundingBox() {
		return new AABB(worldPosition).expandTowards(0, -1, 0);
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		if (!baitItem.isEmpty()) {
			tag.put("BaitItem", baitItem.save(new CompoundTag()));
		}
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		baitItem = tag.contains("BaitItem") ? ItemStack.of(tag.getCompound("BaitItem")) : ItemStack.EMPTY;
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = super.getUpdateTag();
		if (!baitItem.isEmpty()) {
			tag.put("BaitItem", baitItem.save(new CompoundTag()));
		}
		return tag;
	}

	@Override
	public void handleUpdateTag(CompoundTag tag) {
		super.handleUpdateTag(tag);
		baitItem = tag.contains("BaitItem") ? ItemStack.of(tag.getCompound("BaitItem")) : ItemStack.EMPTY;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
