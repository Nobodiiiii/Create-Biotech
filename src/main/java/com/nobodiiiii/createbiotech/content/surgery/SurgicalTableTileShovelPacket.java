package com.nobodiiiii.createbiotech.content.surgery;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Shovels every connected component whose stored projection intersects one targeted table tile. */
public record SurgicalTableTileShovelPacket(BlockPos pos, InteractionHand hand, double volume) {

	public SurgicalTableTileShovelPacket(FriendlyByteBuf buffer) {
		this(buffer.readBlockPos(), buffer.readEnum(InteractionHand.class), buffer.readDouble());
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeBlockPos(pos);
		buffer.writeEnum(hand);
		buffer.writeDouble(volume);
	}

	public void handle(ServerPlayer player) {
		if (player == null || player.isSpectator() || !player.mayBuild() || !player.level().isLoaded(pos)
			|| !Double.isFinite(volume) || volume < 0.0d)
			return;

		double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1.0d;
		if (player.distanceToSqr(Vec3.atCenterOf(pos)) > range * range)
			return;
		SurgicalTablePlane.Plane plane = SurgicalTablePlane.scan(player.level(), pos);
		if (!plane.valid() || !plane.workArea().containsTile(pos))
			return;
		SurgicalTableBlockEntity table = SurgicalTableBlockEntity.controller(player.level(), plane);
		if (table == null)
			return;

		ItemStack held = player.getItemInHand(hand);
		if (SurgicalKitItem.isShovel(held))
			table.shovelIntersectingTile(player, held, hand, pos, plane, volume);
	}
}
