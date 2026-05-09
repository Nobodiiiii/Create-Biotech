package com.nobodiiiii.createbiotech.client;

import com.simibubi.create.CreateClient;
import com.simibubi.create.content.decoration.encasing.CasingConnectivity;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.HorizontalCTBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

public class CasingConnectedHorizontalCTBehaviour extends HorizontalCTBehaviour {

	public CasingConnectedHorizontalCTBehaviour(CTSpriteShiftEntry layerShift, CTSpriteShiftEntry topShift) {
		super(layerShift, topShift);
	}

	@Override
	public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader, BlockPos pos,
		BlockPos otherPos, Direction face) {
		if (face.getAxis().isVertical()) {
			return super.connectsTo(state, other, reader, pos, otherPos, face);
		}

		if (isBeingBlocked(state, reader, pos, otherPos, face)) {
			return false;
		}

		CasingConnectivity cc = CreateClient.CASING_CONNECTIVITY;
		CasingConnectivity.Entry entry = cc.get(state);
		CasingConnectivity.Entry otherEntry = cc.get(other);
		if (entry == null || otherEntry == null) {
			return false;
		}
		if (!entry.isSideValid(state, face) || !otherEntry.isSideValid(other, face)) {
			return false;
		}
		return entry.getCasing() == otherEntry.getCasing();
	}
}
