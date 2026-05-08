package com.nobodiiiii.createbiotech.content.schrodingerscat;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

public class SchrodingersCatBlockEntity extends BlockEntity {

	private static final Random RANDOM = new Random();
	private static final int TICK_INTERVAL = 20;
	private static final int PULSE_DURATION = 2;

	private int signalStrength = 15;
	private int tickCounter = 0;
	private int leftPulseTicks = 0;
	private int rightPulseTicks = 0;

	public SchrodingersCatBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.SCHRODINGERS_CAT.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, SchrodingersCatBlockEntity be) {
		if (level.isClientSide())
			return;

		boolean needsUpdate = false;

		if (be.leftPulseTicks > 0) {
			be.leftPulseTicks--;
			if (be.leftPulseTicks == 0)
				needsUpdate = true;
		}
		if (be.rightPulseTicks > 0) {
			be.rightPulseTicks--;
			if (be.rightPulseTicks == 0)
				needsUpdate = true;
		}

		be.tickCounter++;
		if (be.tickCounter >= TICK_INTERVAL) {
			be.tickCounter = 0;
			int prevSignal = be.signalStrength;
			be.signalStrength = RANDOM.nextBoolean() ? 15 : 0;

			if (prevSignal == 0 && be.signalStrength == 15) {
				be.leftPulseTicks = PULSE_DURATION;
				needsUpdate = true;
			} else if (prevSignal == 15 && be.signalStrength == 0) {
				be.rightPulseTicks = PULSE_DURATION;
				needsUpdate = true;
			}

			if (prevSignal != be.signalStrength) {
				be.setChanged();
				needsUpdate = true;
			}
		}

		if (needsUpdate)
			level.updateNeighborsAt(pos, state.getBlock());
	}

	public int getSignalStrength() {
		return signalStrength;
	}

	public boolean getLeftPulse() {
		return leftPulseTicks > 0;
	}

	public boolean getRightPulse() {
		return rightPulseTicks > 0;
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putInt("SignalStrength", signalStrength);
		tag.putInt("TickCounter", tickCounter);
		tag.putInt("LeftPulseTicks", leftPulseTicks);
		tag.putInt("RightPulseTicks", rightPulseTicks);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		signalStrength = tag.getInt("SignalStrength");
		tickCounter = tag.getInt("TickCounter");
		leftPulseTicks = tag.getInt("LeftPulseTicks");
		rightPulseTicks = tag.getInt("RightPulseTicks");
	}
}
