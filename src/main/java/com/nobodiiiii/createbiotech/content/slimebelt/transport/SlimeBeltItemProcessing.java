package com.nobodiiiii.createbiotech.content.slimebelt.transport;

import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

/** SlimeBeltItemProcessing algorithms over the canonical SlimeBeltInventory state. */
final class SlimeBeltItemProcessing {
	private SlimeBeltItemProcessing() {}


	static boolean handleBeltProcessingAndCheckIfRemoved(SlimeBeltInventory operationOwner, TransportedItemStack currentItem, float nextOffset,
															boolean noMovement) {
		int currentSegment = (int) SlimeBeltHelper.getFrontOffsetForLoopPosition(operationOwner.belt, currentItem.beltPosition);

		if (currentItem.locked) {
			BeltProcessingBehaviour processingBehaviour = SlimeBeltItemProcessing.getBeltProcessingAtSegment(operationOwner, currentSegment);
			TransportedItemStackHandlerBehaviour stackHandlerBehaviour =
				SlimeBeltItemProcessing.getTransportedItemStackHandlerAtSegment(operationOwner, currentSegment);

			if (stackHandlerBehaviour == null)
				return false;
			if (processingBehaviour == null) {
				currentItem.locked = false;
				operationOwner.belt.notifyUpdate();
				return false;
			}

			ProcessingResult result = processingBehaviour.handleHeldItem(currentItem, stackHandlerBehaviour);
			if (result == ProcessingResult.REMOVE)
				return true;
			if (result == ProcessingResult.HOLD)
				return false;

			currentItem.locked = false;
			operationOwner.belt.notifyUpdate();
			return false;
		}

		if (noMovement)
			return false;

		float currentFrontOffset = SlimeBeltHelper.getFrontOffsetForLoopPosition(operationOwner.belt, currentItem.beltPosition);
		if (currentFrontOffset > .5f || operationOwner.beltMovementPositive) {
			int firstUpcomingSegment = (int) (currentFrontOffset + (operationOwner.beltMovementPositive ? .5f : -.5f));
			int step = operationOwner.beltMovementPositive ? 1 : -1;

			for (int segment = firstUpcomingSegment; operationOwner.beltMovementPositive ? segment + .5f <= nextOffset
				: segment + .5f >= nextOffset; segment += step) {

				BeltProcessingBehaviour processingBehaviour = SlimeBeltItemProcessing.getBeltProcessingAtSegment(operationOwner, segment);
				TransportedItemStackHandlerBehaviour stackHandlerBehaviour =
					SlimeBeltItemProcessing.getTransportedItemStackHandlerAtSegment(operationOwner, segment);

				if (processingBehaviour == null)
					continue;
				if (stackHandlerBehaviour == null)
					continue;
				if (BeltProcessingBehaviour.isBlocked(operationOwner.belt.getLevel(), SlimeBeltHelper.getPositionForOffset(operationOwner.belt, segment)))
					continue;

				ProcessingResult result = processingBehaviour.handleReceivedItem(currentItem, stackHandlerBehaviour);
				if (result == ProcessingResult.REMOVE)
					return true;

				if (result == ProcessingResult.HOLD) {
					currentItem.beltPosition = segment + .5f + (operationOwner.beltMovementPositive ? 1 / 512f : -1 / 512f);
					currentItem.locked = true;
					operationOwner.belt.notifyUpdate();
					return false;
				}
			}
		}

		return false;
	}

	static BeltProcessingBehaviour getBeltProcessingAtSegment(SlimeBeltInventory operationOwner, int segment) {
		return BlockEntityBehaviour.get(operationOwner.belt.getLevel(), SlimeBeltHelper.getPositionForOffset(operationOwner.belt, segment)
			.above(2), BeltProcessingBehaviour.TYPE);
	}

	static TransportedItemStackHandlerBehaviour getTransportedItemStackHandlerAtSegment(SlimeBeltInventory operationOwner, int segment) {
		return BlockEntityBehaviour.get(operationOwner.belt.getLevel(), SlimeBeltHelper.getPositionForOffset(operationOwner.belt, segment),
			TransportedItemStackHandlerBehaviour.TYPE);
	}
}
