package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Receives text from any Create Display Link source into the Biocomputer's 8 input slots. */
public final class ComputerDisplayTarget extends DisplayTarget {

	@Override
	public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext context) {
		ComputerBlockEntity computer = resolveComputer(context.level(), context.getTargetPos());
		if (computer == null || line < 0 || line >= ComputerIO.INPUT_SLOTS)
			return;
		boolean singleLine = context.blockEntity().activeSource instanceof SingleLineDisplaySource;
		for (int i = 0; line + i < ComputerIO.INPUT_SLOTS; i++) {
			if (i == 0)
				reserve(line + i, computer, context);
			if (i > 0 && isReserved(line + i, computer, context))
				break;
			if (i >= text.size()) {
				if (singleLine)
					break;
				computer.setInputSlot(line + i, "");
			} else {
				computer.setInputSlot(line + i, text.get(i).getString());
			}
		}
	}

	@Override
	public DisplayTargetStats provideStats(DisplayLinkContext context) {
		return new DisplayTargetStats(ComputerIO.INPUT_SLOTS, ComputerRuntime.MAX_VALUE_LENGTH, this);
	}

	@Override
	public Component getLineOptionText(int line) {
		return Component.translatable("create_biotech.display_target.computer_input_slot", line + 1);
	}

	@Override
	public AABB getMultiblockBounds(LevelAccessor level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ComputerBlock computerBlock) {
			BlockPos lower = computerBlock.getMultiBlockAnchor(pos, state);
			return new AABB(lower.getX(), lower.getY(), lower.getZ(),
				lower.getX() + 1, lower.getY() + 2, lower.getZ() + 1);
		}
		return super.getMultiblockBounds(level, pos);
	}

	@Nullable
	private static ComputerBlockEntity resolveComputer(LevelAccessor level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof ComputerBlock computerBlock))
			return null;
		BlockPos anchor = computerBlock.getMultiBlockAnchor(pos, state);
		return level.getBlockEntity(anchor) instanceof ComputerBlockEntity computer ? computer : null;
	}
}
