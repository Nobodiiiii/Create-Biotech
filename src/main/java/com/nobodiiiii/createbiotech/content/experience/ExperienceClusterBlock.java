package com.nobodiiiii.createbiotech.content.experience;

import java.util.Collections;
import java.util.List;
import java.util.function.IntSupplier;

import javax.annotation.Nullable;

import com.simibubi.create.foundation.block.ProperWaterloggedBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ExperienceClusterBlock extends Block implements ProperWaterloggedBlock {
	public static final DirectionProperty FACING = BlockStateProperties.FACING;

	private final IntSupplier xpValue;
	private final VoxelShape northShape;
	private final VoxelShape southShape;
	private final VoxelShape eastShape;
	private final VoxelShape westShape;
	private final VoxelShape upShape;
	private final VoxelShape downShape;

	public ExperienceClusterBlock(int height, int xzOffset, int xpValue, Properties properties) {
		this(height, xzOffset, () -> xpValue, properties);
	}

	public ExperienceClusterBlock(int height, int xzOffset, IntSupplier xpValue, Properties properties) {
		super(properties);
		this.xpValue = xpValue;
		this.upShape = Block.box(xzOffset, 0.0, xzOffset, 16 - xzOffset, height, 16 - xzOffset);
		this.downShape = Block.box(xzOffset, 16 - height, xzOffset, 16 - xzOffset, 16, 16 - xzOffset);
		this.northShape = Block.box(xzOffset, xzOffset, 16 - height, 16 - xzOffset, 16 - xzOffset, 16);
		this.southShape = Block.box(xzOffset, xzOffset, 0, 16 - xzOffset, 16 - xzOffset, height);
		this.eastShape = Block.box(0, xzOffset, xzOffset, height, 16 - xzOffset, 16 - xzOffset);
		this.westShape = Block.box(16 - height, xzOffset, xzOffset, 16, 16 - xzOffset, 16 - xzOffset);
		registerDefaultState(stateDefinition.any()
			.setValue(FACING, Direction.UP)
			.setValue(WATERLOGGED, Boolean.FALSE));
	}

	public int getXpValue() {
		return xpValue.getAsInt();
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, WATERLOGGED);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACING)) {
			case NORTH -> northShape;
			case SOUTH -> southShape;
			case EAST -> eastShape;
			case WEST -> westShape;
			case DOWN -> downShape;
			default -> upShape;
		};
	}

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState placement = defaultBlockState().setValue(FACING, context.getClickedFace());
		return withWater(placement, context);
	}

	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction facing = state.getValue(FACING);
		BlockPos attachedPos = pos.relative(facing.getOpposite());
		BlockState attachedState = level.getBlockState(attachedPos);
		return attachedState.isFaceSturdy(level, attachedPos, facing)
			|| attachedState.getBlock() instanceof BuddingExperienceBlock;
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
		BlockPos pos, BlockPos neighborPos) {
		updateWater(level, state, pos);
		return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
	}

	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos,
		boolean isMoving) {
		super.neighborChanged(state, level, pos, neighborBlock, neighborPos, isMoving);
		if (level.isClientSide)
			return;
		Direction facing = state.getValue(FACING);
		if (!neighborPos.equals(pos.relative(facing.getOpposite())))
			return;
		if (state.canSurvive(level, pos))
			return;
		level.destroyBlock(pos, true);
	}

	@Override
	public void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!oldState.is(newState.getBlock()) && !(newState.getBlock() instanceof ExperienceClusterBlock)) {
			Direction facing = oldState.getValue(FACING);
			BlockPos buddingPos = pos.relative(facing.getOpposite());
			BlockState buddingState = level.getBlockState(buddingPos);
			if (buddingState.getBlock() instanceof BuddingExperienceBlock) {
				BlockEntity be = level.getBlockEntity(buddingPos);
				if (be instanceof BuddingExperienceBlockEntity budding)
					budding.resetFaceState(facing);
			}
		}
		super.onRemove(oldState, level, pos, newState, isMoving);
	}

	@Override
	public FluidState getFluidState(BlockState state) {
		return fluidState(state);
	}

	@Override
	@SuppressWarnings("deprecation")
	public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
		ItemStack tool = builder.getOptionalParameter(LootContextParams.TOOL);
		Entity entity = builder.getOptionalParameter(LootContextParams.THIS_ENTITY);
		boolean silkTouch = tool != null
			&& EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) > 0;
		if (silkTouch)
			return super.getDrops(state, builder);
		if (!(entity instanceof Player)) {
			ServerLevel serverLevel = builder.getLevel();
			Vec3 origin = builder.getOptionalParameter(LootContextParams.ORIGIN);
			if (origin == null)
				origin = Vec3.atCenterOf(BlockPos.ZERO);
			ExperienceOrb.award(serverLevel, origin, getXpValue());
		}
		return Collections.emptyList();
	}

	@Override
	public int getExpDrop(BlockState state, LevelReader level, RandomSource random, BlockPos pos, int fortuneLevel,
		int silkTouchLevel) {
		if (silkTouchLevel > 0)
			return 0;
		return getXpValue();
	}
}
