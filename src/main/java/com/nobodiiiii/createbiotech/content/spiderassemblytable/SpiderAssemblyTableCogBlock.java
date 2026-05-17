package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SpiderAssemblyTableCogBlock extends HorizontalKineticBlock
	implements IBE<SpiderAssemblyTableCogBlockEntity>, ICogWheel {

	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

	public SpiderAssemblyTableCogBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	public Direction.Axis getRotationAxis(BlockState state) {
		return state.getValue(FACING).getAxis();
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face.getAxis() == getRotationAxis(state);
	}

	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return isValidMainFor(level, pos, state)
			&& CogWheelBlock.isValidCogwheelPosition(false, level, pos, getRotationAxis(state));
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
		BlockPos pos, BlockPos neighborPos) {
		if (!state.canSurvive(level, pos))
			return Blocks.AIR.defaultBlockState();
		return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		return InteractionResult.PASS;
	}

	@Override
	public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide && !SpiderAssemblyTableBlock.isRemovingTailFromMain()) {
			BlockPos mainPos = getMainPos(pos, state);
			SpiderAssemblyTableBlock.removeMainFromTail(level, mainPos, !player.isCreative(), true);
		}
		super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (state.getBlock() != newState.getBlock() && !level.isClientSide && !SpiderAssemblyTableBlock.isRemovingTailFromMain()) {
			BlockPos mainPos = getMainPos(pos, state);
			SpiderAssemblyTableBlock.removeMainFromTail(level, mainPos, !isMoving, false);
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return AllShapes.SMALL_GEAR.get(getRotationAxis(state));
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public MutableComponent getName() {
		return Component.translatable("block.create_biotech.spider_assembly_table");
	}

	@Override
	public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos,
		Player player) {
		return new ItemStack(CBItems.SPIDER_ASSEMBLY_TABLE.get());
	}

	@Override
	public Class<SpiderAssemblyTableCogBlockEntity> getBlockEntityClass() {
		return SpiderAssemblyTableCogBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SpiderAssemblyTableCogBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.SPIDER_ASSEMBLY_TABLE_COG.get();
	}

	public static boolean isValidCogFor(LevelReader level, BlockPos pos, BlockState mainState) {
		BlockState cogState = level.getBlockState(pos);
		return cogState.getBlock() instanceof SpiderAssemblyTableCogBlock
			&& cogState.getValue(FACING) == mainState.getValue(SpiderAssemblyTableBlock.FACING);
	}

	private static boolean isValidMainFor(LevelReader level, BlockPos pos, BlockState cogState) {
		BlockPos mainPos = getMainPos(pos, cogState);
		BlockState mainState = level.getBlockState(mainPos);
		return mainState.getBlock() instanceof SpiderAssemblyTableBlock
			&& mainState.getValue(SpiderAssemblyTableBlock.FACING) == cogState.getValue(FACING);
	}

	private static BlockPos getMainPos(BlockPos pos, BlockState state) {
		return pos.relative(state.getValue(FACING));
	}
}
