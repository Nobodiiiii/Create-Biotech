package com.nobodiiiii.createbiotech.content.creeperblastchamber;

import javax.annotation.Nullable;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.chainDrive.ChainDriveBlock;
import com.simibubi.create.foundation.blockEntity.SyncedBlockEntity;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class CreeperBlastChamberBlockEntity extends SyncedBlockEntity {

	private static final int MIN_SIZE = 3;
	private static final int MAX_SIZE = 5;

	public LerpedFloat gauge = LerpedFloat.linear();
	public LerpedFloat displayGauge = LerpedFloat.linear();
	private boolean structureValid;
	private int structureSize;
	private BlockPos structureOrigin;
	private BlockPos bottomCenter;
	private int recheckTimer;

	public CreeperBlastChamberBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.CREEPER_BLAST_CHAMBER.get(), pos, state);
		gauge.startWithValue(0);
		displayGauge.startWithValue(0);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, CreeperBlastChamberBlockEntity be) {
		if (level.isClientSide) {
			float target = be.structureValid ? (float) be.structureSize / MAX_SIZE : 0f;
			be.gauge.chase(target, 0.125f, Chaser.EXP);
			be.gauge.tickChaser();
			float displayTarget = be.structureValid ? (be.structureSize - MIN_SIZE + 1f) / (MAX_SIZE - MIN_SIZE + 1f) : 0f;
			be.displayGauge.chase(displayTarget, 0.125f, Chaser.EXP);
			be.displayGauge.tickChaser();
			return;
		}

		if (be.recheckTimer > 0) {
			be.recheckTimer--;
			return;
		}
		be.recheckTimer = 20;

		be.tryDetectStructure();
	}

	private void tryDetectStructure() {
		Level level = getLevel();
		if (level == null)
			return;
		BlockPos pos = getBlockPos();

		for (int s = MIN_SIZE; s <= MAX_SIZE; s++) {
			BlockPos origin = findOrigin(level, pos, s);
			if (origin != null) {
				boolean wasValid = structureValid;
				int oldSize = structureSize;
				setStructure(level, true, s, origin);
				if (!wasValid || oldSize != s)
					onStructureFormed(level, s, origin);
				return;
			}
		}
		boolean wasValid = structureValid;
		int oldSize = structureSize;
		BlockPos oldOrigin = structureOrigin;
		setStructure(level, false, 0, null);
		if (wasValid)
			onStructureBroken(level, oldSize, oldOrigin);
	}

	@Nullable
	private static BlockPos findOrigin(Level level, BlockPos controllerPos, int size) {
		for (int dx = 0; dx < size; dx++) {
			for (int dz = 0; dz < size; dz++) {
				BlockPos origin = controllerPos.offset(-dx, 0, -dz);
				if (verifyStructure(level, origin, size))
					return origin;
			}
		}
		return null;
	}

	private static boolean verifyStructure(Level level, BlockPos origin, int size) {
		int innerMin = 1;
		int innerMax = size - 2;

		for (int y = 0; y < 4; y++) {
			for (int x = 0; x < size; x++) {
				for (int z = 0; z < size; z++) {
					BlockPos pos = origin.offset(x, y, z);
					BlockState state = level.getBlockState(pos);
					boolean isInnerX = (x >= innerMin && x <= innerMax);
					boolean isInnerZ = (z >= innerMin && z <= innerMax);
					boolean isCenter = isInnerX && isInnerZ;

					if (isCenter && y == 3) {
						if (!AllBlocks.MECHANICAL_PRESS.has(state))
							return false;
					} else if (isCenter && (y == 1 || y == 2)) {
						if (!state.isAir())
							return false;
					} else if (isCenter && y == 0) {
						if (!AllBlocks.PACKAGER.has(state))
							return false;
					} else {
						boolean isController = state.getBlock() instanceof CreeperBlastChamberBlock;
						boolean isCasing = state.is(CBBlocks.EXPLOSION_PROOF_CASING.get());
						boolean isChainDrive = state.is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get());
						boolean isVerticalEdge = !isInnerX && !isInnerZ;
						boolean isTopOrBottom = (y == 0 || y == 3);

						if (isTopOrBottom || isVerticalEdge) {
							if (!isController && !isCasing && !isChainDrive)
								return false;
						} else {
							boolean isGlass = state.is(CBBlocks.BLAST_PROOF_GLASS.get())
								|| state.is(CBBlocks.BLAST_PROOF_FRAMED_GLASS.get());
							if (!isController && !isCasing && !isGlass)
								return false;
						}
					}
				}
			}
		}
		return true;
	}

	private void setStructure(Level level, boolean valid, int size, @Nullable BlockPos origin) {
		structureValid = valid;
		structureSize = size;
		structureOrigin = origin;
		bottomCenter = valid && origin != null
			? origin.offset(size / 2, 0, size / 2)
			: null;
		notifyUpdate();
	}

	private void onStructureFormed(Level level, int size, BlockPos origin) {
		BlockPos pressPos = origin.offset(size / 2, 3, size / 2);
		BlockState pressState = level.getBlockState(pressPos);
		Direction facing = pressState.getValue(BlockStateProperties.HORIZONTAL_FACING);
		// Chain drives go on the two edges parallel to the press facing
		// Axis is horizontal, perpendicular to the edge (= aligned with facing)
		Axis axis = facing.getAxis();
		int perSide = size - 2;
		Direction alongEdge = facing.getClockWise();
		Axis alongEdgeAxis = alongEdge.getAxis();
		boolean alongFirst = axis == Axis.Z && alongEdgeAxis == Axis.X;
		BlockState chainState = CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get().defaultBlockState()
			.setValue(BlockStateProperties.AXIS, axis)
			.setValue(ChainDriveBlock.CONNECTED_ALONG_FIRST_COORDINATE, alongFirst);

		// Edge 1: the edge in the facing direction from press
		// Edge 2: the edge in the opposite direction from press
		Direction towardEdge1 = facing;
		Direction towardEdge2 = facing.getOpposite();
		int distFromPressToEdge = size / 2;
		for (int i = -perSide / 2; i <= perSide / 2; i++) {
			BlockPos edge1Pos = pressPos.relative(towardEdge1, distFromPressToEdge)
				.relative(alongEdge, i);
			BlockPos edge2Pos = pressPos.relative(towardEdge2, distFromPressToEdge)
				.relative(alongEdge, i);
			if (!level.getBlockState(edge1Pos).is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get()))
				level.setBlock(edge1Pos, chainState, 3);
			if (!level.getBlockState(edge2Pos).is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get()))
				level.setBlock(edge2Pos, chainState, 3);
		}

		for (int i = -perSide / 2; i <= perSide / 2; i++) {
			BlockPos edge1Pos = pressPos.relative(towardEdge1, distFromPressToEdge)
				.relative(alongEdge, i);
			BlockPos edge2Pos = pressPos.relative(towardEdge2, distFromPressToEdge)
				.relative(alongEdge, i);
			updateChainDriveState(level, edge1Pos, axis, alongEdgeAxis);
			updateChainDriveState(level, edge2Pos, axis, alongEdgeAxis);
		}
	}

	private void onStructureBroken(Level level, int size, BlockPos origin) {
		BlockPos pressPos = origin.offset(size / 2, 3, size / 2);
		BlockState pressState = level.getBlockState(pressPos);
		Direction facing = pressState.getValue(BlockStateProperties.HORIZONTAL_FACING);
		Direction towardEdge1 = facing;
		Direction towardEdge2 = facing.getOpposite();
		Direction alongEdge = facing.getClockWise();
		int perSide = size - 2;
		int distFromPressToEdge = size / 2;

		for (int i = -perSide / 2; i <= perSide / 2; i++) {
			revertToCasing(level, pressPos.relative(towardEdge1, distFromPressToEdge)
				.relative(alongEdge, i));
			revertToCasing(level, pressPos.relative(towardEdge2, distFromPressToEdge)
				.relative(alongEdge, i));
		}
	}

	private void revertToCasing(Level level, BlockPos pos) {
		if (level.getBlockState(pos).is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get()))
			level.setBlock(pos, CBBlocks.EXPLOSION_PROOF_CASING.get().defaultBlockState(), 3);
	}

	private void updateChainDriveState(Level level, BlockPos pos, Axis axis, Axis alongEdgeAxis) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get()))
			return;
		ChainDriveBlock chainDrive = (ChainDriveBlock) CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get();
		boolean alongFirst = axis == Axis.Z && alongEdgeAxis == Axis.X;
		BlockState updated = state.setValue(BlockStateProperties.AXIS, axis)
			.setValue(ChainDriveBlock.CONNECTED_ALONG_FIRST_COORDINATE, alongFirst);
		for (Direction facing : Iterate.directions) {
			if (facing.getAxis() == axis)
				continue;
			BlockPos offset = pos.relative(facing);
			updated = chainDrive.updateShape(updated, facing, level.getBlockState(offset), level, pos, offset);
		}
		if (updated != state)
			level.setBlock(pos, updated, 3);
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putBoolean("StructureValid", structureValid);
		tag.putInt("StructureSize", structureSize);
		if (structureOrigin != null) {
			tag.putInt("OriginX", structureOrigin.getX());
			tag.putInt("OriginY", structureOrigin.getY());
			tag.putInt("OriginZ", structureOrigin.getZ());
		}
		if (bottomCenter != null) {
			tag.putInt("BottomX", bottomCenter.getX());
			tag.putInt("BottomY", bottomCenter.getY());
			tag.putInt("BottomZ", bottomCenter.getZ());
		}
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		structureValid = tag.getBoolean("StructureValid");
		structureSize = tag.getInt("StructureSize");
		if (tag.contains("OriginX")) {
			structureOrigin = new BlockPos(
				tag.getInt("OriginX"), tag.getInt("OriginY"), tag.getInt("OriginZ"));
		} else {
			structureOrigin = null;
		}
		if (tag.contains("BottomX")) {
			bottomCenter = new BlockPos(
				tag.getInt("BottomX"), tag.getInt("BottomY"), tag.getInt("BottomZ"));
		} else {
			bottomCenter = null;
		}
	}

	public void forceStructureCheck() {
		recheckTimer = 0;
		tryDetectStructure();
	}

	public boolean isStructureValid() {
		return structureValid;
	}

	public int getStructureSize() {
		return structureSize;
	}

	@Nullable
	public BlockPos getStructureOrigin() {
		return structureOrigin;
	}

	@Nullable
	public BlockPos getBottomCenter() {
		return bottomCenter;
	}
}
