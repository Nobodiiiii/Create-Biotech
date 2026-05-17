package com.nobodiiiii.createbiotech.content.biopackager;

import com.nobodiiiii.createbiotech.client.BioPackagerContraptionClientAnimationHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public class BioPackagerContraptionAnimationPacket {

	private final int entityId;
	private final BlockPos localPos;
	private final ItemStack heldBox;
	private final ItemStack previouslyUnwrapped;
	private final boolean animationInward;

	public BioPackagerContraptionAnimationPacket(int entityId, BlockPos localPos, ItemStack heldBox,
		ItemStack previouslyUnwrapped, boolean animationInward) {
		this.entityId = entityId;
		this.localPos = localPos.immutable();
		this.heldBox = heldBox.copy();
		this.previouslyUnwrapped = previouslyUnwrapped.copy();
		this.animationInward = animationInward;
	}

	public BioPackagerContraptionAnimationPacket(FriendlyByteBuf buffer) {
		this(buffer.readVarInt(), buffer.readBlockPos(), buffer.readItem(), buffer.readItem(), buffer.readBoolean());
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeVarInt(entityId);
		buffer.writeBlockPos(localPos);
		buffer.writeItem(heldBox);
		buffer.writeItem(previouslyUnwrapped);
		buffer.writeBoolean(animationInward);
	}

	public boolean handle(Context context) {
		context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
			() -> () -> BioPackagerContraptionClientAnimationHandler.startAnimation(entityId, localPos, heldBox,
				previouslyUnwrapped, animationInward)));
		return true;
	}
}
