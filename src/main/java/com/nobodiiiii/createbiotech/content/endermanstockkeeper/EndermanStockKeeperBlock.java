package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

public class EndermanStockKeeperBlock extends HorizontalDirectionalBlock {

	public static final MapCodec<EndermanStockKeeperBlock> CODEC = simpleCodec(EndermanStockKeeperBlock::new);
	private static final VoxelShape BASE = Block.box(0, 0, 0, 16, 16, 16);

	public EndermanStockKeeperBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return BASE;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
		Player player, InteractionHand hand, BlockHitResult hit) {
		InteractionResult result = interact(level, pos, player);
		return result.consumesAction() ? ItemInteractionResult.sidedSuccess(level.isClientSide)
			: ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
		BlockHitResult hit) {
		return interact(level, pos, player);
	}

	private InteractionResult interact(Level level, BlockPos pos, Player player) {
		StockTickerBlockEntity stockTicker = findStockTicker(level, pos);
		if (stockTicker == null) {
			if (!level.isClientSide)
				player.displayClientMessage(Component.translatable(
					"block.create_biotech.enderman_stock_keeper.missing_stock_ticker").withStyle(ChatFormatting.RED), true);
			return InteractionResult.sidedSuccess(level.isClientSide);
		}

		if (level.isClientSide)
			return InteractionResult.SUCCESS;
		if (!(player instanceof ServerPlayer serverPlayer))
			return InteractionResult.PASS;
		if (!stockTicker.behaviour.mayInteract(player)) {
			player.displayClientMessage(CreateLang.translate("stock_keeper.locked")
				.style(ChatFormatting.RED)
				.component(), true);
			return InteractionResult.SUCCESS;
		}

		boolean showLockOption = stockTicker.behaviour.mayAdministrate(player)
			&& Create.LOGISTICS.isLockable(stockTicker.behaviour.freqId);
		boolean isCurrentlyLocked = Create.LOGISTICS.isLocked(stockTicker.behaviour.freqId);
		List<ItemStack> gaugeOutputs = FactoryGaugeCatalog.getCraftableOutputs(stockTicker.behaviour.freqId);

		MenuProvider provider = new MenuProvider() {
			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player menuPlayer) {
				return new EndermanStockKeeperRequestMenu(containerId, inventory, stockTicker, pos, gaugeOutputs);
			}

			@Override
			public Component getDisplayName() {
				return Component.translatable("block.create_biotech.enderman_stock_keeper");
			}
		};

		serverPlayer.openMenu(provider, buffer -> {
			buffer.writeBoolean(showLockOption);
			buffer.writeBoolean(isCurrentlyLocked);
			buffer.writeBlockPos(stockTicker.getBlockPos());
			buffer.writeBlockPos(pos);
			ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buffer, gaugeOutputs);
		});
		stockTicker.getRecentSummary().divideAndSendTo(serverPlayer, stockTicker.getBlockPos());
		return InteractionResult.SUCCESS;
	}

	@Nullable
	public static StockTickerBlockEntity findStockTicker(Level level, BlockPos keeperPos) {
		StockTickerBlockEntity result = null;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			for (int yOffset = 0; yOffset <= 1; yOffset++) {
				BlockPos candidate = keeperPos.relative(direction).above(yOffset);
				if (!(level.getBlockEntity(candidate) instanceof StockTickerBlockEntity stockTicker))
					continue;
				if (result != null && result != stockTicker)
					return null;
				result = stockTicker;
			}
		}
		return result;
	}

	public static boolean isKeeperFor(Level level, BlockPos keeperPos, BlockPos stockTickerPos) {
		if (!CBBlocks.ENDERMAN_STOCK_KEEPER.get().equals(level.getBlockState(keeperPos).getBlock()))
			return false;
		StockTickerBlockEntity stockTicker = findStockTicker(level, keeperPos);
		return stockTicker != null && stockTicker.getBlockPos().equals(stockTickerPos);
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return rotate(state, mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FACING);
	}
}
