package com.nobodiiiii.createbiotech.content.honeycombgauge;

import com.mojang.serialization.MapCodec;
import com.nobodiiiii.createbiotech.registry.CBMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public class HoneycombGaugeClusterBlock extends DirectionalBlock implements EntityBlock {
	public static final MapCodec<HoneycombGaugeClusterBlock> CODEC = simpleCodec(HoneycombGaugeClusterBlock::new);
	public static final DirectionProperty WORKSPACE_UP = DirectionProperty.create("workspace_up");

	public HoneycombGaugeClusterBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)
			.setValue(WORKSPACE_UP, Direction.UP));
	}

	@Override
	protected MapCodec<? extends HoneycombGaugeClusterBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new HoneycombGaugeClusterBlockEntity(pos, state);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FACING, WORKSPACE_UP);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing = context.getNearestLookingDirection().getOpposite();
		// The player's camera-up vector supplies the initial orientation in the plane.
		Direction up = facing.getAxis() == Direction.Axis.Y
			? (facing == Direction.UP ? context.getHorizontalDirection()
				: context.getHorizontalDirection().getOpposite())
			: Direction.UP;
		return defaultBlockState().setValue(FACING, facing).setValue(WORKSPACE_UP, up);
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)))
			.setValue(WORKSPACE_UP, rotation.rotate(state.getValue(WORKSPACE_UP)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		Direction facing = state.getValue(FACING);
		Direction up = state.getValue(WORKSPACE_UP);
		return state.setValue(FACING, mirror.getRotation(facing).rotate(facing))
			.setValue(WORKSPACE_UP, mirror.getRotation(up).rotate(up));
	}

	/** A clockwise quarter turn when looking at the instrument's reading face. */
	public static Direction nextWorkspaceUp(Direction facing, Direction up) {
		up = normalizedWorkspaceUp(facing, up);
		var right = up.getNormal().cross(facing.getNormal());
		for (Direction direction : Direction.values())
			if (direction.getStepX() == right.getX() && direction.getStepY() == right.getY()
				&& direction.getStepZ() == right.getZ())
				return direction;
		return up;
	}

	public static Direction normalizedWorkspaceUp(Direction facing, Direction up) {
		return facing.getAxis() == up.getAxis()
			? (facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP) : up;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (placer instanceof ServerPlayer player) {
			HoneycombGaugeClusterScanner.Snapshot snapshot =
				HoneycombGaugeClusterScanner.scan(level, pos, state.getValue(FACING));
			player.displayClientMessage(Component.translatable("create_biotech.honeycomb_gauge_cluster.summary",
				snapshot.honeycombs().size(), snapshot.gauges().size()), true);
		}
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
		Player player, InteractionHand hand, BlockHitResult hit) {
		return interact(state, level, pos, player);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
		BlockHitResult hit) {
		return interact(state, level, pos, player).result();
	}

	private ItemInteractionResult interact(BlockState state, Level level, BlockPos pos, Player player) {
		if (level.isClientSide)
			return ItemInteractionResult.SUCCESS;
		if (!(player instanceof ServerPlayer serverPlayer))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		HoneycombGaugeClusterScanner.Snapshot snapshot =
			HoneycombGaugeClusterScanner.scan(level, pos, state.getValue(FACING));
		MenuProvider provider = new MenuProvider() {
			@Override
			public Component getDisplayName() {
				return Component.translatable("block.create_biotech.honeycomb_gauge_cluster");
			}

			@Override
			public AbstractContainerMenu createMenu(int id, Inventory inventory, Player menuPlayer) {
				return new HoneycombGaugeClusterMenu(id, inventory, pos, state.getValue(FACING),
					state.getValue(WORKSPACE_UP), snapshot);
			}
		};
		serverPlayer.openMenu(provider, buffer -> HoneycombGaugeClusterMenu.writeSnapshot(buffer,
			pos, state.getValue(FACING), state.getValue(WORKSPACE_UP), snapshot));
		return ItemInteractionResult.SUCCESS;
	}
}
