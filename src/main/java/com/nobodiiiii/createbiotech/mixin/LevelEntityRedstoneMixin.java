package com.nobodiiiii.createbiotech.mixin;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.content.dingdongchicken.EntityRedstoneIndex;
import com.nobodiiiii.createbiotech.content.dingdongchicken.EntityRedstoneLevelAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Lets entity-backed redstone sources participate in ordinary signal queries.
 *
 * <p>Mixin 0.8.5 (Forge 1.20.1) rejects injectors inside interfaces, so the four
 * {@link SignalGetter} default methods are overridden on {@link Level} instead. Each override
 * reproduces the vanilla default and then folds in the entity contribution, so hooks placed on
 * {@code BlockState#getSignal} by other mods keep applying.
 */
@Mixin(Level.class)
public abstract class LevelEntityRedstoneMixin implements SignalGetter {

	@Override
	public int getSignal(BlockPos pos, Direction direction) {
		BlockState state = getBlockState(pos);
		int signal = state.getSignal(this, pos, direction);
		int weakOrDirect = state.shouldCheckWeakPower(this, pos, direction)
			? Math.max(signal, getDirectSignalTo(pos))
			: signal;
		return createBiotech$addEntitySignal(weakOrDirect, pos);
	}

	@Override
	public int getControlInputSignal(BlockPos pos, Direction direction, boolean diodesOnly) {
		BlockState state = getBlockState(pos);
		if (diodesOnly)
			return DiodeBlock.isDiode(state) ? getDirectSignal(pos, direction) : 0;

		int original;
		if (state.is(Blocks.REDSTONE_BLOCK))
			original = 15;
		else if (state.is(Blocks.REDSTONE_WIRE))
			original = state.getValue(RedStoneWireBlock.POWER);
		else
			original = state.isSignalSource() ? getDirectSignal(pos, direction) : 0;
		return createBiotech$addEntitySignal(original, pos);
	}

	@Override
	public int getBestNeighborSignal(BlockPos pos) {
		int best = 0;
		for (Direction direction : DIRECTIONS) {
			int signal = getSignal(pos.relative(direction), direction);
			if (signal >= 15) {
				best = 15;
				break;
			}
			if (signal > best)
				best = signal;
		}
		return createBiotech$addEntitySignal(best, pos);
	}

	@Override
	public boolean hasNeighborSignal(BlockPos pos) {
		for (Direction direction : DIRECTIONS) {
			if (getSignal(pos.relative(direction), direction) > 0)
				return true;
		}

		EntityRedstoneIndex index = createBiotech$entityRedstoneIndex();
		return index != null && index.hasAnySources() && index.isSource(pos);
	}

	@Unique
	private int createBiotech$addEntitySignal(int original, BlockPos pos) {
		if (original >= 15)
			return original;
		EntityRedstoneIndex index = createBiotech$entityRedstoneIndex();
		if (index == null || !index.hasAnySources())
			return original;
		return index.isSource(pos) ? 15 : original;
	}

	@Unique
	@Nullable
	private EntityRedstoneIndex createBiotech$entityRedstoneIndex() {
		return (Object) this instanceof EntityRedstoneLevelAccess access
			? access.createBiotech$getEntityRedstoneIndex()
			: null;
	}
}
