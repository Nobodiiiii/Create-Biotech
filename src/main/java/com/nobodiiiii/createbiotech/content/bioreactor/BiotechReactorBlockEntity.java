package com.nobodiiiii.createbiotech.content.bioreactor;

import com.simibubi.create.AllBlocks;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BiotechReactorBlockEntity extends BlockEntity {

	public LerpedFloat gauge = LerpedFloat.linear();
	private boolean structureValid;
	private int recheckTimer;

	public BiotechReactorBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.BIOTECH_REACTOR.get(), pos, state);
		gauge.startWithValue(0);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, BiotechReactorBlockEntity be) {
		if (level.isClientSide) {
			float target = be.structureValid ? 1f : 0f;
			be.gauge.chase(target, 0.125f, Chaser.EXP);
			be.gauge.tickChaser();
			return;
		}

		if (be.recheckTimer > 0) {
			be.recheckTimer--;
			return;
		}
		be.recheckTimer = 20;

		boolean valid = checkStructure(level, pos);
		if (valid != be.structureValid) {
			be.structureValid = valid;
			be.setChanged();
			level.sendBlockUpdated(pos, state, state, 3);
		}
	}

	public static boolean checkStructure(Level level, BlockPos controllerPos) {
		for (int y = 0; y < 4; y++) {
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					BlockPos pos = controllerPos.offset(x, y, z);
					BlockState state = level.getBlockState(pos);
					boolean isCenter = (x == 0 && z == 0);

					if (isCenter) {
						if (y == 3) {
							if (!AllBlocks.MECHANICAL_PRESS.has(state))
								return false;
						} else if (y == 1 || y == 2) {
							if (!state.isAir())
								return false;
						}
						// y == 0 is self (controller), skip
					} else {
						if (!AllBlocks.BRASS_CASING.has(state))
							return false;
					}
				}
			}
		}
		return true;
	}

	public void forceStructureCheck() {
		recheckTimer = 0;
		Level level = getLevel();
		if (level != null && !level.isClientSide) {
			boolean valid = checkStructure(level, getBlockPos());
			if (valid != structureValid) {
				structureValid = valid;
				setChanged();
				level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
			}
		}
	}

	public boolean isStructureValid() {
		return structureValid;
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putBoolean("StructureValid", structureValid);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		structureValid = tag.getBoolean("StructureValid");
	}
}
