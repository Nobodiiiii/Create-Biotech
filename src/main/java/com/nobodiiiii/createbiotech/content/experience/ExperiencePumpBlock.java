package com.nobodiiiii.createbiotech.content.experience;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.ticks.TickPriority;

public class ExperiencePumpBlock extends PumpBlock {

	public ExperiencePumpBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide)
			return;
		withBlockEntityDo(level, pos, be -> {
			if (be instanceof ExperiencePumpBlockEntity pump)
				pump.setAdvancementOwner(placer);
		});
	}

	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block otherBlock, BlockPos neighborPos,
		boolean isMoving) {
		super.neighborChanged(state, level, pos, otherBlock, neighborPos, isMoving);
		if (level.isClientSide)
			return;
		for (Direction direction : Direction.values()) {
			if (pos.relative(direction).equals(neighborPos) && isOpenAt(state, direction)) {
				level.scheduleTick(pos, this, 1, TickPriority.HIGH);
				return;
			}
		}
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!state.is(newState.getBlock())) {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (blockEntity instanceof ExperiencePumpBlockEntity pump)
				pump.dropBufferedExperience();
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}

	@Override
	public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof ExperiencePumpBlockEntity pump)
			pump.updatePressureChange();
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Class getBlockEntityClass() {
		return ExperiencePumpBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends PumpBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.EXPERIENCE_PUMP.get();
	}
}
