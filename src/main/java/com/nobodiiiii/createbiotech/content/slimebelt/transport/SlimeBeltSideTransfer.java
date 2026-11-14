package com.nobodiiiii.createbiotech.content.slimebelt.transport;

import javax.annotation.Nullable;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltBlockEntity;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltLoopGeometry.Track;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltNeighbor;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.BlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** SlimeBeltSideTransfer algorithms over the canonical SlimeBeltInventory state. */
final class SlimeBeltSideTransfer {
	private SlimeBeltSideTransfer() {}


	static boolean tryTransferToAdjacentBelt(SlimeBeltInventory operationOwner, TransportedItemStack currentItem, Track track, float currentProgress,
		float nextProgress, float movement, boolean onClient) {
		if (movement <= 0)
			return false;
		SlimeBeltInventory.SideTransferCandidate candidate = SlimeBeltSideTransfer.findSideTransferCandidate(operationOwner, track, currentProgress, nextProgress);
		if (candidate == null)
			return false;

		DirectBeltInputBehaviour inputBehaviour =
			BlockEntityBehaviour.get(operationOwner.belt.getLevel(), candidate.targetPos(), DirectBeltInputBehaviour.TYPE);
		if (inputBehaviour == null || !inputBehaviour.canInsertFromSide(candidate.insertSide()))
			return SlimeBeltSideTransfer.waitAtSideTransferPoint(operationOwner, currentItem, track, movement, candidate);

		ItemStack simulatedRemainder = inputBehaviour.handleInsertion(currentItem.copy(), candidate.insertSide(), true);
		if (ItemStack.matches(simulatedRemainder, currentItem.stack))
			return SlimeBeltSideTransfer.waitAtSideTransferPoint(operationOwner, currentItem, track, movement, candidate);

		if (onClient)
			SlimeBeltSideTransfer.moveItemToSideTransferPoint(operationOwner, currentItem, track, candidate.contactProgress(), movement, candidate.contactParam());
		if (onClient)
			return true;

		ItemStack remainder = inputBehaviour.handleInsertion(currentItem, candidate.insertSide(), false);
		if (ItemStack.matches(remainder, currentItem.stack))
			return SlimeBeltSideTransfer.waitAtSideTransferPoint(operationOwner, currentItem, track, movement, candidate);

		SlimeBeltSideTransfer.moveItemToSideTransferPoint(operationOwner, currentItem, track, candidate.contactProgress(), movement, candidate.contactParam());
		currentItem.stack = remainder;
		if (remainder.isEmpty()) {
			operationOwner.lazyClientItem = currentItem;
			operationOwner.lazyClientItem.locked = false;
			operationOwner.items.remove(currentItem);
		}
		operationOwner.belt.notifyUpdate();
		return true;
	}

	static boolean waitAtSideTransferPoint(SlimeBeltInventory operationOwner, TransportedItemStack currentItem, Track track, float movement,
		SlimeBeltInventory.SideTransferCandidate candidate) {
		SlimeBeltSideTransfer.moveItemToSideTransferPoint(operationOwner, currentItem, track, candidate.contactProgress(), movement, candidate.contactParam());
		return true;
	}

	static @Nullable
	SlimeBeltInventory.SideTransferCandidate findSideTransferCandidate(SlimeBeltInventory operationOwner, Track track, float currentProgress, float nextProgress) {
		Vec3 start = SlimeBeltHelper.getVectorForOffset(operationOwner.belt, operationOwner.getLoopPositionForTrackProgress(track, currentProgress));
		Vec3 end = SlimeBeltHelper.getVectorForOffset(operationOwner.belt, operationOwner.getLoopPositionForTrackProgress(track, nextProgress));
		if (start.distanceToSqr(end) < 1.0E-6)
			return null;

		float currentFrontOffset = operationOwner.getFrontOffsetForTrackProgress(track, currentProgress);
		float nextFrontOffset = operationOwner.getFrontOffsetForTrackProgress(track, nextProgress);
		int minSegment = Mth.clamp(Mth.floor(Math.min(currentFrontOffset, nextFrontOffset)) - 1, 0, operationOwner.belt.beltLength - 1);
		int maxSegment = Mth.clamp(Mth.floor(Math.max(currentFrontOffset, nextFrontOffset)) + 1, 0, operationOwner.belt.beltLength - 1);

		SlimeBeltInventory.SideTransferCandidate bestCandidate = null;
		for (int segment = minSegment; segment <= maxSegment; segment++) {
			Vec3 trackNormal = SlimeBeltHelper.getTrackNormal(operationOwner.belt, segment, track);
			Direction outgoingSide = Direction.getNearest(trackNormal.x, trackNormal.y, trackNormal.z);

			BlockPos sourceSegmentPos = SlimeBeltHelper.getPositionForOffset(operationOwner.belt, segment);
			BlockPos targetPos = sourceSegmentPos.relative(outgoingSide);
			Direction incomingFace = outgoingSide.getOpposite();
			SlimeBeltNeighbor neighbor = SlimeBeltNeighbor.at(operationOwner.belt.getLevel(), targetPos);
			if (neighbor == null || neighbor.sameLoopAs(operationOwner.belt))
				continue;

			for (Direction cornerSide : neighbor.sideTransferInsertSides(incomingFace)) {
				SlimeBeltInventory.SideTransferCandidate candidate = SlimeBeltSideTransfer.createSideTransferCandidate(operationOwner, targetPos, incomingFace, cornerSide,
					neighbor.handoffInsertSide(incomingFace, cornerSide), currentProgress, nextProgress, start, end);
				if (candidate != null && (bestCandidate == null || candidate.distanceSqr() < bestCandidate.distanceSqr()))
					bestCandidate = candidate;
			}
		}

		return bestCandidate;
	}

	static @Nullable
	SlimeBeltInventory.SideTransferCandidate createSideTransferCandidate(SlimeBeltInventory operationOwner, BlockPos targetPos, Direction incomingFace,
		Direction cornerSide, Direction insertSide, float currentProgress, float nextProgress, Vec3 start, Vec3 end) {
		Vec3 transferPoint = SlimeBeltSideTransfer.getTransferCorner(operationOwner, targetPos, incomingFace, cornerSide);
		float contactParam = SlimeBeltSideTransfer.getClosestPointParam(operationOwner, start, end, transferPoint);
		Vec3 closestPoint = start.lerp(end, contactParam);
		double distanceSqr = closestPoint.distanceToSqr(transferPoint);
		if (distanceSqr > SlimeBeltInventory.SIDE_TRANSFER_DISTANCE_SQR)
			return null;

		float contactProgress = Mth.lerp(contactParam, currentProgress, nextProgress);
		return new SlimeBeltInventory.SideTransferCandidate(targetPos, insertSide, contactProgress, contactParam, distanceSqr);
	}

	static Vec3 getTransferCorner(SlimeBeltInventory operationOwner, BlockPos targetPos, Direction incomingFace, Direction cornerSide) {
		return Vec3.atCenterOf(targetPos)
			.add(incomingFace.getStepX() * .5, incomingFace.getStepY() * .5, incomingFace.getStepZ() * .5)
			.add(cornerSide.getStepX() * .5, cornerSide.getStepY() * .5, cornerSide.getStepZ() * .5);
	}

	static float getClosestPointParam(SlimeBeltInventory operationOwner, Vec3 start, Vec3 end, Vec3 point) {
		Vec3 delta = end.subtract(start);
		double lengthSqr = delta.lengthSqr();
		if (lengthSqr < 1.0E-6)
			return 0;
		double projected = point.subtract(start)
			.dot(delta) / lengthSqr;
		return (float) Mth.clamp(projected, 0, 1);
	}

	static void moveItemToSideTransferPoint(SlimeBeltInventory operationOwner, TransportedItemStack currentItem, Track track, float contactProgress,
		float movement, float contactParam) {
		operationOwner.setLoopPositionFromTrackProgress(currentItem, track, contactProgress);
		float partialMovement = Math.abs(movement * contactParam);
		float diffToMiddle = currentItem.getTargetSideOffset() - currentItem.sideOffset;
		currentItem.sideOffset += Mth.clamp(diffToMiddle * partialMovement * 6f, -Math.abs(diffToMiddle),
			Math.abs(diffToMiddle));
	}

	static SlimeBeltInventory.Ending resolveEnding(SlimeBeltInventory operationOwner, Track track) {
		Level world = operationOwner.belt.getLevel();
		BlockPos outputPosition = SlimeBeltSideTransfer.getTrackOutputPosition(operationOwner, track);
		Direction insertSide = SlimeBeltHelper.getMovementFacingForTrack(operationOwner.belt, track);

		DirectBeltInputBehaviour inputBehaviour =
			BlockEntityBehaviour.get(world, outputPosition, DirectBeltInputBehaviour.TYPE);

		// Detect a foreign or not-yet-initialized slime belt at the seam — any
		// "I can't push into it right now" answer should become a hard BLOCK
		// rather than letting our items fall back to our own connector loop.
		SlimeBeltBlockEntity adjacentSegment = SlimeBeltHelper.getSegmentBE(world, outputPosition);
		SlimeBeltBlockEntity adjacentController =
			adjacentSegment == null ? null : adjacentSegment.getControllerBE();
		boolean adjacentIsForeignSlimeBelt = adjacentController != null
			&& !adjacentController.getBlockPos().equals(operationOwner.belt.getBlockPos());
		boolean adjacentSlimeBeltNeedsHandoff = adjacentSegment != null
			&& (adjacentController == null || adjacentIsForeignSlimeBelt);

		if (inputBehaviour != null && inputBehaviour.canInsertFromSide(insertSide)) {
			// Structurally compatible. If the downstream foreign slime belt is currently
			// occupied (entry slot full), BLOCK rather than let items camp at INSERT
			// margin retrying every tick or risk slipping into our own connector loop.
			if (adjacentIsForeignSlimeBelt && inputBehaviour.isOccupied(insertSide))
				return SlimeBeltInventory.Ending.BLOCKED;
			return SlimeBeltInventory.Ending.INSERT;
		}

		// Not structurally compatible (e.g. antiparallel rotation). Foreign slime belt
		// in front of us is still a hard obstruction; do not wrap.
		if (adjacentSlimeBeltNeedsHandoff)
			return SlimeBeltInventory.Ending.BLOCKED;

		if (BlockHelper.hasBlockSolidSide(world.getBlockState(outputPosition), world, outputPosition,
			insertSide.getOpposite()))
			return SlimeBeltInventory.Ending.BLOCKED;

		return SlimeBeltInventory.Ending.WRAP;
	}

	static BlockPos getTrackOutputPosition(SlimeBeltInventory operationOwner, Track track) {
		int outputOffset = track == Track.FRONT ? (operationOwner.beltMovementPositive ? operationOwner.belt.beltLength : -1)
			: (operationOwner.beltMovementPositive ? -1 : operationOwner.belt.beltLength);
		return SlimeBeltHelper.getPositionForOffset(operationOwner.belt, outputOffset);
	}
}
