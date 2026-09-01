package com.nobodiiiii.createbiotech.content.surgery;

import java.util.List;
import java.util.function.Predicate;

import com.mojang.serialization.MapCodec;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxItem;
import com.nobodiiiii.createbiotech.foundation.block.CBWrenchHelper;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.IHaveBigOutline;

import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SurgicalTableBlock extends Block
	implements IBE<SurgicalTableBlockEntity>, IWrenchable, IHaveBigOutline {
	public static final MapCodec<SurgicalTableBlock> CODEC = simpleCodec(SurgicalTableBlock::new);
	private static final int PLACEMENT_HELPER_ID = PlacementHelpers.register(new PlacementHelper());

	public SurgicalTableBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return AllShapes.TABLE_CLOTH;
	}

	@Override
	public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
		return AllShapes.TABLE_CLOTH;
	}

	@Override
	public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
		return AllShapes.TABLE_CLOTH_OCCLUSION;
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
		CollisionContext context) {
		return AllShapes.TABLE_CLOTH_OCCLUSION;
	}

	public static boolean connectsVisuallyTo(BlockState adjacentState) {
		return adjacentState.getBlock() instanceof SurgicalTableBlock;
	}

	@Override
	public boolean skipRendering(BlockState state, BlockState adjacentState, Direction side) {
		if (side.getAxis().isHorizontal() && connectsVisuallyTo(adjacentState))
			return true;
		return super.skipRendering(state, adjacentState, side);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
		Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
		if (CBWrenchHelper.isWrench(stack))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (!player.isShiftKeyDown() && player.mayBuild()) {
			IPlacementHelper placementHelper = PlacementHelpers.get(PLACEMENT_HELPER_ID);
			if (placementHelper.matchesItem(stack))
				return placementHelper.getOffset(player, level, state, pos, hit)
					.placeInWorld(level, (BlockItem) stack.getItem(), player, hand, hit);
		}
		if (!(stack.getItem() instanceof CapturedEntityBoxItem)
			|| !CapturedEntityBoxHelper.hasCapturedEntity(stack))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

		SurgicalTablePlane.Plane plane = SurgicalTablePlane.scan(level, pos);
		if (!plane.valid())
			return ItemInteractionResult.FAIL;
		// Filled-box placement is measured and sent by SurgicalTableClientHandler. Consuming the
		// vanilla interaction here prevents an unmeasured server-side fallback from bypassing the
		// work-area check.
		return ItemInteractionResult.SUCCESS;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!state.is(newState.getBlock())) {
			SurgicalTableBlockEntity.invalidateTableLayout();
			if (level.getBlockEntity(pos) instanceof SurgicalTableBlockEntity table)
				SurgicalTableSupportManager.enqueue(level, pos, table);
		}
		IBE.onRemove(state, level, pos, newState);
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
		if (!state.is(oldState.getBlock()))
			SurgicalTableBlockEntity.invalidateTableLayout();
		super.onPlace(state, level, pos, oldState, isMoving);
	}

	@Override
	public Class<SurgicalTableBlockEntity> getBlockEntityClass() {
		return SurgicalTableBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SurgicalTableBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.SURGICAL_TABLE.get();
	}

	@MethodsReturnNonnullByDefault
	private static class PlacementHelper implements IPlacementHelper {
		@Override
		public Predicate<ItemStack> getItemPredicate() {
			return stack -> stack.getItem() instanceof BlockItem blockItem
				&& blockItem.getBlock() instanceof SurgicalTableBlock;
		}

		@Override
		public Predicate<BlockState> getStatePredicate() {
			return state -> state.getBlock() instanceof SurgicalTableBlock;
		}

		@Override
		public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos,
			BlockHitResult hit) {
			List<Direction> directions = IPlacementHelper.orderedByDistanceExceptAxis(pos, hit.getLocation(),
				Direction.Axis.Y, direction -> {
					BlockPos destination = pos.relative(direction);
					return level.getBlockState(destination).canBeReplaced();
				});
			if (directions.isEmpty())
				return PlacementOffset.fail();
			return PlacementOffset.success(pos.relative(directions.getFirst()), placed -> placed);
		}
	}
}
