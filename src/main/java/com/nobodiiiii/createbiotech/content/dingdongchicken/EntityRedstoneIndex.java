package com.nobodiiiii.createbiotech.content.dingdongchicken;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * Per-level runtime index for active entity-backed redstone sources. Nothing in this cache is
 * persisted: loaded entities rebuild it as they enter the level.
 */
public final class EntityRedstoneIndex {

	private static final Direction[] DIRECTIONS = Direction.values();

	private final Long2IntOpenHashMap sourceReferences = new Long2IntOpenHashMap();
	private final Long2IntOpenHashMap directReceiverReferences = new Long2IntOpenHashMap();

	private int activePositionCount;
	private long singleSourcePosition;

	public static EntityRedstoneIndex get(ServerLevel level) {
		return ((EntityRedstoneLevelAccess) level).createBiotech$getEntityRedstoneIndex();
	}

	public boolean hasAnySources() {
		return activePositionCount != 0;
	}

	public boolean isSource(BlockPos pos) {
		return isSource(pos.asLong());
	}

	public boolean isSource(long packedPos) {
		if (activePositionCount == 0)
			return false;
		if (activePositionCount == 1)
			return packedPos == singleSourcePosition;
		return sourceReferences.containsKey(packedPos);
	}

	/**
	 * Adds one entity reference. The return value reports whether this position changed from an
	 * inactive position into an active source position.
	 */
	public boolean addSource(long packedPos) {
		int previous = sourceReferences.addTo(packedPos, 1);
		if (previous != 0)
			return false;

		activePositionCount++;
		if (activePositionCount == 1)
			singleSourcePosition = packedPos;
		updateNearbyCaches(packedPos, 1);
		return true;
	}

	/**
	 * Removes one entity reference. The return value reports whether this position ceased to be a
	 * source after the removal.
	 */
	public boolean removeSource(long packedPos) {
		int previous = sourceReferences.get(packedPos);
		if (previous == 0)
			return false;
		if (previous > 1) {
			sourceReferences.put(packedPos, previous - 1);
			return false;
		}

		sourceReferences.remove(packedPos);
		activePositionCount--;
		updateNearbyCaches(packedPos, -1);
		if (activePositionCount == 1)
			singleSourcePosition = sourceReferences.keySet().iterator().nextLong();
		return true;
	}

	public void notifySourceChanged(ServerLevel level, long packedPos) {
		BlockPos pos = BlockPos.of(packedPos);
		// A virtual source may share its cell with a real block. That block must be invited to
		// re-evaluate its own powered state as well as the six ordinary neighboring consumers.
		level.neighborChanged(pos, Blocks.REDSTONE_BLOCK, pos);
		level.updateNeighborsAt(pos, Blocks.REDSTONE_BLOCK);
		level.updateNeighbourForOutputSignal(pos, Blocks.REDSTONE_BLOCK);

		// Revisit the consumers around the six receiving cells too. This is notification coverage,
		// not signal propagation: power queries below still accept only the source cell and its
		// immediate neighbors. The wider notification clears cached states reliably on removal.
		for (Direction direction : DIRECTIONS) {
			BlockPos receiverPos = pos.relative(direction);
			level.updateNeighborsAt(receiverPos, Blocks.REDSTONE_BLOCK);
			level.updateNeighbourForOutputSignal(receiverPos, Blocks.REDSTONE_BLOCK);
		}
	}

	/**
	 * Supplies redstone-block-style weak power to wire evaluators which bypass
	 * {@code SignalGetter#getSignal}. The occupied cell is an intentional extension; otherwise only
	 * the six immediately adjacent wire positions receive power.
	 */
	public int getExternalPowerForWire(BlockPos wirePos) {
		if (activePositionCount == 0)
			return 0;

		long packedWirePos = wirePos.asLong();
		if (isSource(packedWirePos))
			return 15;
		return directReceiverReferences.containsKey(packedWirePos) ? 15 : 0;
	}

	private void updateNearbyCaches(long packedSourcePos, int delta) {
		int x = BlockPos.getX(packedSourcePos);
		int y = BlockPos.getY(packedSourcePos);
		int z = BlockPos.getZ(packedSourcePos);

		for (Direction direction : DIRECTIONS) {
			updateReference(directReceiverReferences, BlockPos.asLong(
				x + direction.getStepX(),
				y + direction.getStepY(),
				z + direction.getStepZ()), delta);
		}
	}

	private static void updateReference(Long2IntOpenHashMap references, long packedPos, int delta) {
		if (delta > 0) {
			references.addTo(packedPos, delta);
			return;
		}

		int previous = references.get(packedPos);
		if (previous <= 1)
			references.remove(packedPos);
		else
			references.put(packedPos, previous - 1);
	}
}
