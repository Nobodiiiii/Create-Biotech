package com.nobodiiiii.createbiotech.content.ghasthotairballoon;

import org.apache.commons.lang3.tuple.Pair;

import com.nobodiiiii.createbiotech.registry.CBContraptionTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.TranslatingContraption;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

public class GhastHotAirBalloonContraption extends TranslatingContraption {

	private int initialOffset;

	public GhastHotAirBalloonContraption() {}

	public GhastHotAirBalloonContraption(int initialOffset) {
		this.initialOffset = initialOffset;
	}

	public int getInitialOffset() {
		return initialOffset;
	}

	@Override
	public ContraptionType getType() {
		return CBContraptionTypes.GHAST_HOT_AIR_BALLOON.value();
	}

	@Override
	public boolean assemble(Level world, BlockPos pos) throws AssemblyException {
		if (!searchMovedStructure(world, pos, null))
			return false;

		BlockPos magnetPos = pos.above();
		addBlock(world, magnetPos, Pair.of(new StructureBlockInfo(magnetPos,
			AllBlocks.PULLEY_MAGNET.getDefaultState(), null), null));

		for (int i = 2; i <= initialOffset; i++) {
			BlockPos ropePos = pos.above(i);
			addBlock(world, ropePos, Pair.of(new StructureBlockInfo(ropePos,
				AllBlocks.ROPE.getDefaultState(), null), null));
		}

		startMoving(world);
		return true;
	}

	@Override
	protected Pair<StructureBlockInfo, BlockEntity> capture(Level world, BlockPos pos) {
		Pair<StructureBlockInfo, BlockEntity> captured = super.capture(world, pos);
		StructureBlockInfo info = captured.getLeft();
		if (!info.state().is(com.nobodiiiii.createbiotech.registry.CBBlocks.GHAST_HELM.get()))
			return captured;

		BlockState openState = info.state()
			.setValue(ControlsBlock.OPEN, true)
			.setValue(ControlsBlock.VIRTUAL, false);
		StructureBlockInfo openInfo = new StructureBlockInfo(info.pos(), openState, info.nbt());
		return Pair.of(openInfo, captured.getRight());
	}

	@Override
	protected boolean isAnchoringBlockAt(BlockPos pos) {
		if (pos.getX() != anchor.getX() || pos.getZ() != anchor.getZ())
			return false;
		int y = pos.getY();
		return y > anchor.getY() && y <= anchor.getY() + initialOffset;
	}

	@Override
	protected boolean customBlockPlacement(LevelAccessor world, BlockPos pos, BlockState state) {
		return AllBlocks.PULLEY_MAGNET.has(state) || AllBlocks.ROPE.has(state);
	}

	@Override
	protected boolean customBlockRemoval(LevelAccessor world, BlockPos pos, BlockState state) {
		return AllBlocks.PULLEY_MAGNET.has(state) || AllBlocks.ROPE.has(state);
	}

	@Override
	public boolean canBeStabilized(Direction facing, BlockPos localPos) {
		return false;
	}

	@Override
	public CompoundTag writeNBT(boolean spawnPacket) {
		CompoundTag tag = super.writeNBT(spawnPacket);
		tag.putInt("InitialOffset", initialOffset);
		return tag;
	}

	@Override
	public void readNBT(Level world, CompoundTag nbt, boolean spawnData) {
		initialOffset = nbt.getInt("InitialOffset");
		super.readNBT(world, nbt, spawnData);
	}
}
