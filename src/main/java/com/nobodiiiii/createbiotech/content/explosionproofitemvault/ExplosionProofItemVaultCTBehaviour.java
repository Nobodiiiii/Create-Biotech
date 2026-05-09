package com.nobodiiiii.createbiotech.content.explosionproofitemvault;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.client.CBSpriteShifts;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

public class ExplosionProofItemVaultCTBehaviour extends ConnectedTextureBehaviour.Base {

	@Override
	public CTSpriteShiftEntry getShift(BlockState state, Direction direction, @Nullable TextureAtlasSprite sprite) {
		Axis vaultBlockAxis = ExplosionProofItemVaultBlock.getVaultBlockAxis(state);
		boolean small = !ExplosionProofItemVaultBlock.isLarge(state);
		if (vaultBlockAxis == null)
			return null;

		if (direction.getAxis() == vaultBlockAxis)
			return CBSpriteShifts.EXPLOSION_PROOF_ITEM_VAULT_FRONT.get(small);
		if (direction == Direction.UP)
			return CBSpriteShifts.EXPLOSION_PROOF_ITEM_VAULT_TOP.get(small);
		if (direction == Direction.DOWN)
			return CBSpriteShifts.EXPLOSION_PROOF_ITEM_VAULT_BOTTOM.get(small);

		return CBSpriteShifts.EXPLOSION_PROOF_ITEM_VAULT_SIDE.get(small);
	}

	@Override
	protected Direction getUpDirection(BlockAndTintGetter reader, BlockPos pos, BlockState state, Direction face) {
		Axis vaultBlockAxis = ExplosionProofItemVaultBlock.getVaultBlockAxis(state);
		boolean alongX = vaultBlockAxis == Axis.X;
		if (face.getAxis()
			.isVertical() && alongX)
			return super.getUpDirection(reader, pos, state, face).getClockWise();
		if (face.getAxis() == vaultBlockAxis || face.getAxis()
			.isVertical())
			return super.getUpDirection(reader, pos, state, face);
		return Direction.fromAxisAndDirection(vaultBlockAxis, alongX ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE);
	}

	@Override
	protected Direction getRightDirection(BlockAndTintGetter reader, BlockPos pos, BlockState state, Direction face) {
		Axis vaultBlockAxis = ExplosionProofItemVaultBlock.getVaultBlockAxis(state);
		if (face.getAxis()
			.isVertical() && vaultBlockAxis == Axis.X)
			return super.getRightDirection(reader, pos, state, face).getClockWise();
		if (face.getAxis() == vaultBlockAxis || face.getAxis()
			.isVertical())
			return super.getRightDirection(reader, pos, state, face);
		return Direction.fromAxisAndDirection(Axis.Y, face.getAxisDirection());
	}

	@Override
	public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader, BlockPos pos,
		BlockPos otherPos, Direction face) {
		return state == other && ConnectivityHandler.isConnected(reader, pos, otherPos);
	}
}
