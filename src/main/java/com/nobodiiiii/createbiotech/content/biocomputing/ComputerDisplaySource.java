package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.state.BlockState;

/** Exposes the Biocomputer's 8-line output buffer to Create Display Links (Display Boards, Nixie Tubes, Signs). */
public final class ComputerDisplaySource extends DisplaySource {

	@Override
	public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
		BlockPos sourcePos = context.getSourcePos();
		BlockState state = context.level().getBlockState(sourcePos);
		if (!(state.getBlock() instanceof ComputerBlock computerBlock))
			return EMPTY;
		BlockPos anchor = computerBlock.getMultiBlockAnchor(sourcePos, state);
		if (!(context.level().getBlockEntity(anchor) instanceof ComputerBlockEntity computer))
			return EMPTY;

		List<String> lines = computer.outputLinesSnapshot();
		int lastNonEmpty = lines.size() - 1;
		while (lastNonEmpty >= 0 && lines.get(lastNonEmpty).isEmpty())
			lastNonEmpty--;
		if (lastNonEmpty < 0)
			return EMPTY;

		int limit = Math.min(lastNonEmpty + 1, Math.max(1, stats.maxRows()));
		List<MutableComponent> components = new ArrayList<>(limit);
		for (int i = 0; i < limit; i++)
			components.add(Component.literal(lines.get(i)));
		return components;
	}

	@Override
	public boolean shouldPassiveReset() {
		// The computer publishes changed output and handles refresh requests from scripts.
		return false;
	}
}
