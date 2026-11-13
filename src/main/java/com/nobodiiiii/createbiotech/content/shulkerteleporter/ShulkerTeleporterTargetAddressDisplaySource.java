package com.nobodiiiii.createbiotech.content.shulkerteleporter;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.state.BlockState;

public class ShulkerTeleporterTargetAddressDisplaySource extends SingleLineDisplaySource {

	@Override
	protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
		BlockPos sourcePos = context.getSourcePos();
		BlockState state = context.level().getBlockState(sourcePos);
		if (!(state.getBlock() instanceof ShulkerTeleporterBlock))
			return EMPTY_LINE;
		BlockPos topPos = ShulkerTeleporterBlock.getTopPos(sourcePos, state);
		if (!(context.level().getBlockEntity(topPos) instanceof ShulkerTeleporterBlockEntity teleporter))
			return EMPTY_LINE;
		String targetAddress = teleporter.getTargetAddress();
		return targetAddress.isEmpty() ? EMPTY_LINE : Component.literal(targetAddress);
	}

	@Override
	protected boolean allowsLabeling(DisplayLinkContext context) {
		return true;
	}
}
