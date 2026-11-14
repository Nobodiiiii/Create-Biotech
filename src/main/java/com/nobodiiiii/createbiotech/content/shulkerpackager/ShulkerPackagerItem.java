package com.nobodiiiii.createbiotech.content.shulkerpackager;

import com.nobodiiiii.createbiotech.network.CBPackets;
import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import com.nobodiiiii.createbiotech.mixin.ServerGamePacketListenerAccessor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class ShulkerPackagerItem extends BlockItem {

	public ShulkerPackagerItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Level world = ctx.getLevel();
		BlockPos pos = ctx.getClickedPos();
		if (ShulkerPackagerArmInteractions.isSelectable(world, pos, world.getBlockState(pos)))
			return InteractionResult.SUCCESS;
		return super.useOn(ctx);
	}

	@Override
	public InteractionResult place(BlockPlaceContext context) {
		BlockPos placementPos = context.getClickedPos();
		ItemStack placedStack = context.getItemInHand();
		InteractionResult result = super.place(context);
		Level level = context.getLevel();
		if (result.consumesAction() && level.getBlockState(placementPos).is(getBlock())
			&& level instanceof ShulkerPackagerPlacementCapture capture)
			capture.createBiotech$capturePlacementSelection(context.getHand(), placedStack);
		return result;
	}

	@Override
	protected boolean updateCustomBlockEntityTag(BlockPos pos, Level world, Player player, ItemStack stack,
		BlockState state) {
		if (!world.isClientSide && player instanceof ServerPlayer sp
			&& world.getBlockEntity(pos) instanceof ShulkerPackagerBlockEntity packager) {
			var nonce = packager.beginPlacementConfiguration(sp);
			int placementSequence = ((ServerGamePacketListenerAccessor) sp.connection)
				.createBiotech$getAckBlockChangesUpTo();
			CBPackets.sendToPlayer(new ShulkerPackagerPlacementPacket.ClientBoundRequest(pos,
				SubLevelCompat.getSpaceId(world, pos), nonce, placementSequence), sp);
		}
		return super.updateCustomBlockEntityTag(pos, world, player, stack, state);
	}

	@Override
	public boolean canAttackBlock(BlockState state, Level world, BlockPos pos, Player player) {
		return !ShulkerPackagerArmInteractions.isSelectable(world, pos, state);
	}
}
