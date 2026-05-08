package com.nobodiiiii.createbiotech.content.creeperblastchamber;

import javax.annotation.Nullable;

import com.simibubi.create.AllBlocks;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CreeperBlastChamberBlockEntity extends BlockEntity {

	private static final int MIN_SIZE = 3;
	private static final int MAX_SIZE = 5;

	public LerpedFloat gauge = LerpedFloat.linear();
	private boolean structureValid;
	private int structureSize;
	private BlockPos structureOrigin;
	private int recheckTimer;

	public CreeperBlastChamberBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.CREEPER_BLAST_CHAMBER.get(), pos, state);
		gauge.startWithValue(0);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, CreeperBlastChamberBlockEntity be) {
		if (level.isClientSide) {
			float target = be.structureValid ? (float) be.structureSize / MAX_SIZE : 0f;
			be.gauge.chase(target, 0.125f, Chaser.EXP);
			be.gauge.tickChaser();
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
				setStructure(level, true, s, origin);
				return;
			}
		}
		setStructure(level, false, 0, null);
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
						boolean isVerticalEdge = !isInnerX && !isInnerZ;
						boolean isTopOrBottom = (y == 0 || y == 3);

						if (isTopOrBottom || isVerticalEdge) {
							if (!isController && !isCasing)
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
		if (valid == structureValid && size == structureSize)
			return;
		structureValid = valid;
		structureSize = size;
		structureOrigin = origin;
		setChanged();
		level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
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
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		structureValid = tag.getBoolean("StructureValid");
		structureSize = tag.getInt("StructureSize");
		if (tag.contains("OriginX")) {
			structureOrigin = new BlockPos(
				tag.getInt("OriginX"), tag.getInt("OriginY"), tag.getInt("OriginZ"));
		}
	}
}
