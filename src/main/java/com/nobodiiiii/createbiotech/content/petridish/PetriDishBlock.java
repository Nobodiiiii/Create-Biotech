package com.nobodiiiii.createbiotech.content.petridish;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PetriDishBlock extends HorizontalDirectionalBlock implements IBE<PetriDishBlockEntity>, IWrenchable {

	private static final VoxelShape SHAPE = Shapes.or(
		box(0, 0, 0, 16, 2, 16),
		box(0, 2, 2, 1, 4, 14),
		box(15, 2, 2, 16, 4, 14),
		box(2, 2, 0, 14, 4, 1),
		box(2, 2, 15, 14, 4, 16),
		box(2, 2, 1, 15, 8, 2),
		box(1, 2, 14, 14, 8, 15),
		box(1, 2, 1, 2, 8, 14),
		box(14, 2, 2, 15, 8, 15));
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

	public PetriDishBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		return onBlockEntityUse(level, pos, be -> be.use(player, hand));
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @org.jetbrains.annotations.Nullable LivingEntity placer,
		ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide)
			return;
		withBlockEntityDo(level, pos, be -> be.setAdvancementOwner(placer));
	}

	@Override
	public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
		return false;
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		IBE.onRemove(state, level, pos, newState);
	}

	@Override
	public Class<PetriDishBlockEntity> getBlockEntityClass() {
		return PetriDishBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends PetriDishBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.PETRI_DISH.get();
	}
}
