package com.nobodiiiii.createbiotech.content.beltsurface;

import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryTrackerBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Adapter passed to {@link FunnelInteractionCore#check}. Encapsulates everything the core needs to know about
 * "the belt surface the item is currently travelling on" — in surface-local terms.
 * <p>
 * Implementations exist for vanilla belts and slime belts (one per track). The core itself is identical for all.
 */
public interface FunnelTickContext {

	Level level();

	int beltLength();

	/**
	 * Direction of iteration along the integer segment axis. Items moving "forward" on the surface (in the surface
	 * forward direction) scan segments in increasing order; items moving the other way scan decreasing.
	 */
	boolean movingTowardHigherSegments();

	/** Belt motion direction at this surface, in <em>world</em> frame. */
	Direction movementFacing();

	VersionedInventoryTrackerBehaviour invVersionTracker();

	/** Item's current position along the segment axis of this surface (the "frontOffset" in slime-belt parlance). */
	float currentSegmentPosition(TransportedItemStack item);

	/** Block position where the funnel attached to this surface sits at the given segment. */
	BlockPos funnelPosFor(int segment);

	/** Convert the funnel block's stored (surface-local) {@code HORIZONTAL_FACING} to world frame for this surface. */
	Direction worldizeFunnelFacing(Direction localFacing);

	/** When a blocking funnel halts an item, freeze the item at the given segment-axis offset. */
	void lockItemAtEntry(TransportedItemStack item, float entryOnSegmentAxis);

	void notifyUpdate();
}
