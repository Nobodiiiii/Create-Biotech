package com.nobodiiiii.createbiotech.content.powerbelt;

import com.simibubi.create.content.kinetics.belt.BeltSlope;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkEvent.Context;

public class PowerBeltSurfaceMovementPacket {

	private static final float MAX_PLAYER_SURFACE_SPEED = 1.0f;

	private final BlockPos pos;
	private final float surfaceSpeed;

	public PowerBeltSurfaceMovementPacket(BlockPos pos, float surfaceSpeed) {
		this.pos = pos;
		this.surfaceSpeed = surfaceSpeed;
	}

	public PowerBeltSurfaceMovementPacket(FriendlyByteBuf buffer) {
		this(buffer.readBlockPos(), buffer.readFloat());
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeBlockPos(pos);
		buffer.writeFloat(surfaceSpeed);
	}

	public boolean handle(Context context) {
		context.enqueueWork(() -> apply(context.getSender()));
		return true;
	}

	private void apply(ServerPlayer player) {
		if (player == null || player.isSpectator() || player.getAbilities().flying)
			return;
		if (!Float.isFinite(surfaceSpeed))
			return;

		Level level = player.level();
		if (level == null || !level.isLoaded(pos))
			return;
		if (!pos.closerThan(player.blockPosition(), 4))
			return;

		BlockState state = level.getBlockState(pos);
		if (!PowerBeltBlock.isPowerBelt(state)
			|| state.getValue(PowerBeltBlock.SLOPE) != BeltSlope.HORIZONTAL)
			return;
		if (!PowerBeltBlock.isEntityOnBeltSurface(pos, player) || !isHorizontallyOverBelt(player, pos))
			return;

		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof PowerBeltBlockEntity powerBelt)
			powerBelt.addSurfaceMovement(Mth.clamp(surfaceSpeed, -MAX_PLAYER_SURFACE_SPEED,
				MAX_PLAYER_SURFACE_SPEED));
	}

	private static boolean isHorizontallyOverBelt(Player player, BlockPos pos) {
		AABB box = player.getBoundingBox();
		return box.maxX > pos.getX() && box.minX < pos.getX() + 1
			&& box.maxZ > pos.getZ() && box.minZ < pos.getZ() + 1;
	}
}
