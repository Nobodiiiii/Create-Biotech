package com.nobodiiiii.createbiotech.content.bioreactor;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class BiotechReactorBlock extends BaseEntityBlock {

	public BiotechReactorBlock(Properties properties) {
		super(properties);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BiotechReactorBlockEntity(pos, state);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
																	BlockEntityType<T> type) {
		return createTickerHelper(type, CBBlockEntityTypes.BIOTECH_REACTOR.get(),
			BiotechReactorBlockEntity::tick);
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
		super.onPlace(state, level, pos, oldState, moved);
		BiotechReactorBlockEntity.checkStructure(level, pos);
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
								  InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide)
			return InteractionResult.SUCCESS;

		boolean valid = BiotechReactorBlockEntity.checkStructure(level, pos);

		if (level.getBlockEntity(pos) instanceof BiotechReactorBlockEntity be) {
			be.forceStructureCheck();
		}

		if (valid) {
			player.displayClientMessage(
				Component.translatable("block.create_biotech.biotech_reactor.status.formed"), true);
		} else {
			player.displayClientMessage(
				Component.translatable("block.create_biotech.biotech_reactor.status.not_formed"), true);
		}

		return InteractionResult.SUCCESS;
	}
}
