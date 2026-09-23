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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

public class HoneycombGaugeClusterBlock extends DirectionalBlock {
	public static final MapCodec<HoneycombGaugeClusterBlock> CODEC = simpleCodec(HoneycombGaugeClusterBlock::new);

	public HoneycombGaugeClusterBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
	}

	@Override
	protected MapCodec<? extends HoneycombGaugeClusterBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getClickedFace());
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
				return new HoneycombGaugeClusterMenu(id, inventory, pos, state.getValue(FACING), snapshot);
			}
		};
		serverPlayer.openMenu(provider, buffer -> HoneycombGaugeClusterMenu.writeSnapshot(buffer,
			pos, state.getValue(FACING), snapshot));
		return ItemInteractionResult.SUCCESS;
	}
}
