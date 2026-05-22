package com.nobodiiiii.createbiotech.content.slimebelt.transport;

import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurface;
import com.nobodiiiii.createbiotech.content.beltsurface.FunnelTickContext;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltBlockEntity;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper.Track;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryTrackerBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Adapts a {@link SlimeBeltInventory} + {@link Track} to {@link FunnelTickContext} so the shared
 * {@link com.nobodiiiii.createbiotech.content.beltsurface.FunnelInteractionCore} can drive funnel interactions
 * on the slime belt's FRONT or BACK track without re-implementing the algorithm.
 * <p>
 * Direction discretisation is delegated to {@link BeltSurface}, which holds the {@code (outwardNormal, movementFacing)}
 * pair for this track.
 */
public final class SlimeBeltSurfaceTickContext implements FunnelTickContext {

	private final SlimeBeltInventory inv;
	private final Track track;
	private final BeltSurface surface;
	private final boolean movingFwd;

	public SlimeBeltSurfaceTickContext(SlimeBeltInventory inv, Track track) {
		this.inv = inv;
		this.track = track;
		SlimeBeltBlockEntity belt = inv.belt;
		Direction outward = SlimeBeltHelper.getRepresentativeSideForTrack(belt, 0, track);
		Direction movement = SlimeBeltHelper.getMovementFacingForTrack(belt, track);
		this.surface = BeltSurface.of(belt, belt.getBlockPos(), 0, outward, movement);
		this.movingFwd = track == Track.FRONT ? inv.beltMovementPositive : !inv.beltMovementPositive;
	}

	@Override
	public Level level() {
		return inv.belt.getLevel();
	}

	@Override
	public int beltLength() {
		return inv.belt.beltLength;
	}

	@Override
	public boolean movingTowardHigherSegments() {
		return movingFwd;
	}

	@Override
	public Direction movementFacing() {
		return surface.movementFacing();
	}

	@Override
	public VersionedInventoryTrackerBehaviour invVersionTracker() {
		return inv.belt.invVersionTracker;
	}

	@Override
	public float currentSegmentPosition(TransportedItemStack item) {
		return SlimeBeltHelper.getFrontOffsetForLoopPosition(inv.belt, item.beltPosition);
	}

	@Override
	public BlockPos funnelPosFor(int segment) {
		return SlimeBeltHelper.getPositionForOffset(inv.belt, segment).relative(surface.outwardNormal());
	}

	@Override
	public Direction worldizeFunnelFacing(Direction localFacing) {
		return surface.worldize(localFacing);
	}

	@Override
	public void lockItemAtEntry(TransportedItemStack item, float entryOnSegmentAxis) {
		inv.setLoopPositionFromTrackProgress(item, track,
			inv.getTrackProgressForFrontOffset(track, entryOnSegmentAxis));
	}

	@Override
	public void notifyUpdate() {
		inv.belt.notifyUpdate();
	}
}
