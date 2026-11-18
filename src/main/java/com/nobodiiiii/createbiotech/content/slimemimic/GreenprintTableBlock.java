package com.nobodiiiii.createbiotech.content.slimemimic;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class GreenprintTableBlock extends Block {

	public static final MapCodec<GreenprintTableBlock> CODEC = simpleCodec(GreenprintTableBlock::new);
	private static final VoxelShape SHAPE = Shapes.or(box(0, 12, 0, 16, 16, 16),
		box(2, 0, 2, 14, 2, 14), box(4, 2, 4, 12, 12, 12));

	public GreenprintTableBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
		Player player, InteractionHand hand, BlockHitResult hit) {
		// The table uses a right-click with a bound Greenprint for its menu, before the item can spawn it.
		open(player, pos);
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
		BlockHitResult hit) {
		open(player, pos);
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	private void open(Player player, BlockPos pos) {
		if (player instanceof ServerPlayer serverPlayer)
			serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, owner) ->
				new GreenprintTableMenu(id, inventory, pos), getName()), buffer -> buffer.writeBlockPos(pos));
	}
}
