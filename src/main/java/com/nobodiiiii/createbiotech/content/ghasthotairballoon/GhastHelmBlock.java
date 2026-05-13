package com.nobodiiiii.createbiotech.content.ghasthotairballoon;

import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;

import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public class GhastHelmBlock extends ControlsBlock {

	public GhastHelmBlock(Properties properties) {
		super(properties);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		if (state.getValue(OPEN))
			return RenderShape.INVISIBLE;
		return super.getRenderShape(state);
	}
}
