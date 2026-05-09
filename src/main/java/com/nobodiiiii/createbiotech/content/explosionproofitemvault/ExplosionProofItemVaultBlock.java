package com.nobodiiiii.createbiotech.content.explosionproofitemvault;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;

import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ExplosionProofItemVaultBlock extends ItemVaultBlock {

	public ExplosionProofItemVaultBlock(Properties properties) {
		super(properties);
	}

	public static boolean isVault(BlockState state) {
		return state.getBlock() instanceof ExplosionProofItemVaultBlock;
	}

	@Nullable
	public static Axis getVaultBlockAxis(BlockState state) {
		if (!isVault(state))
			return null;
		return state.getValue(HORIZONTAL_AXIS);
	}

	public static boolean isLarge(BlockState state) {
		if (!isVault(state))
			return false;
		return state.getValue(LARGE);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		if (context.getPlayer() == null || !context.getPlayer()
			.isShiftKeyDown()) {
			BlockState placedOn = context.getLevel()
				.getBlockState(context.getClickedPos()
					.relative(context.getClickedFace()
						.getOpposite()));
			Axis preferredAxis = getVaultBlockAxis(placedOn);
			if (preferredAxis != null)
				return defaultBlockState().setValue(HORIZONTAL_AXIS, preferredAxis);
		}

		return defaultBlockState().setValue(HORIZONTAL_AXIS, context.getHorizontalDirection()
			.getAxis());
	}

	@Override
	public BlockEntityType<? extends ItemVaultBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.EXPLOSION_PROOF_ITEM_VAULT.get();
	}
}
