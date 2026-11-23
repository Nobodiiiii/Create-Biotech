package com.nobodiiiii.createbiotech.content.slimeculturechamber;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.BlockSnapshot;

import static com.nobodiiiii.createbiotech.content.slimeculturechamber.SlimeCultureChamberBlock.ANCHOR_PART;
import static com.nobodiiiii.createbiotech.content.slimeculturechamber.SlimeCultureChamberBlock.FACING;
import static com.nobodiiiii.createbiotech.content.slimeculturechamber.SlimeCultureChamberBlock.PART;
import static com.nobodiiiii.createbiotech.content.slimeculturechamber.SlimeCultureChamberBlock.PART_COUNT;

public class SlimeCultureChamberItem extends BlockItem {

	public SlimeCultureChamberItem(SlimeCultureChamberBlock block, Properties properties) {
		super(block, properties);
	}

	@Override
	protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		BlockPos anchor = context.getClickedPos();
		Direction facing = state.getValue(FACING);
		BlockPos[] positions = new BlockPos[PART_COUNT];
		for (int part = 0; part < PART_COUNT; part++) {
			BlockPos pos = SlimeCultureChamberBlock.partPos(anchor, facing, part);
			if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
				|| !level.isLoaded(pos) || (player != null && !level.mayInteract(player, pos)))
				return false;
			positions[part] = pos;
			if (part == ANCHOR_PART)
				continue;
			BlockPlaceContext partContext = BlockPlaceContext.at(context, pos, context.getClickedFace());
			if (!partContext.replacingClickedOnBlock() || !canPlace(partContext, state.setValue(PART, part)))
				return false;
		}

		// Place during BlockItem's placement transaction so NeoForge protects all 36 positions.
		int flags = Block.UPDATE_ALL | Block.UPDATE_IMMEDIATE;
		BlockSnapshot[] previous = new BlockSnapshot[PART_COUNT];
		for (int part = 0; part < PART_COUNT; part++) {
			if (part == ANCHOR_PART)
				continue;
			BlockPos pos = positions[part];
			BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos, flags);
			if (!level.setBlock(pos, state.setValue(PART, part), flags)) {
				restoreParts(previous);
				return false;
			}
			previous[part] = snapshot;
		}
		if (super.placeBlock(context, state))
			return true;
		restoreParts(previous);
		return false;
	}

	private static void restoreParts(BlockSnapshot[] previous) {
		for (int part = previous.length - 1; part >= 0; part--) {
			if (previous[part] != null)
				previous[part].restore();
		}
	}
}
