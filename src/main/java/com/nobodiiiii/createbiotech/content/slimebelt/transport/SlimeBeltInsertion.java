package com.nobodiiiii.createbiotech.content.slimebelt.transport;

import javax.annotation.Nullable;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltInsertionPlanner;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltInsertionPlanner.InsertionPlan;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltLoopGeometry.LoopSection;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltLoopGeometry.Track;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import net.minecraft.core.Direction;

/** SlimeBeltInsertion algorithms over the canonical SlimeBeltInventory state. */
final class SlimeBeltInsertion {
	private SlimeBeltInsertion() {}


	static boolean canInsertAt(SlimeBeltInventory operationOwner, int segment) {
		return SlimeBeltInsertion.canInsertAtFromSide(operationOwner, segment, Direction.UP);
	}

	static boolean canInsertAtFromSide(SlimeBeltInventory operationOwner, int segment, Direction side) {
		return SlimeBeltInsertion.canInsertAtFromSide(operationOwner, segment, side, false);
	}

	static boolean canInsertAtFromSide(SlimeBeltInventory operationOwner, int segment, Direction side, boolean verticalHorizontalBeltInput) {
		return SlimeBeltInsertion.canInsert(operationOwner, SlimeBeltInsertion.planInsertion(operationOwner, segment, side, verticalHorizontalBeltInput, null));
	}

	/**
	 * Resolve where an insert from the given side would land. The stack, when available,
	 * only decides belt-to-belt continuation biases; occupancy gating stays
	 * stack-independent so occupancy queries and real inserts agree.
	 */
	static @Nullable
	InsertionPlan planInsertion(SlimeBeltInventory operationOwner, int segment, Direction side, boolean verticalHorizontalBeltInput,
		@Nullable TransportedItemStack transported) {
		SlimeBeltInsertion.refreshMovementDirection(operationOwner);
		boolean cameFromBelt = transported != null && transported.prevBeltPosition != 0;
		return SlimeBeltInsertionPlanner.plan(operationOwner.belt, operationOwner.frame(), segment, side, verticalHorizontalBeltInput, cameFromBelt);
	}

	/** Occupancy gate for a resolved plan; probes the same landing the item would take. */
	static boolean canInsert(SlimeBeltInventory operationOwner, @Nullable InsertionPlan plan) {
		if (plan == null)
			return false;
		// Landings within one spacing of the track exit additionally require the exit
		// turn to be clear, so the arriving item cannot overlap one already wrapping.
		if (plan.occupancyConnector() != null && !SlimeBeltInsertion.isTurnClear(operationOwner, plan.occupancyConnector(), null))
			return false;
		for (TransportedItemStack stack : operationOwner.items)
			if (SlimeBeltInsertion.isBlocking(operationOwner, plan.track(), plan.landingLoopPosition(), stack))
				return false;
		for (TransportedItemStack stack : operationOwner.toInsert)
			if (SlimeBeltInsertion.isBlocking(operationOwner, plan.track(), plan.landingLoopPosition(), stack))
				return false;
		return true;
	}

	static boolean canInsertAtOnTrack(SlimeBeltInventory operationOwner, int segment, Track track) {
		return SlimeBeltInsertion.canInsert(operationOwner, SlimeBeltInsertion.planTrackInsertion(operationOwner, segment, track));
	}

	static void prepareInsertedItem(SlimeBeltInventory operationOwner, TransportedItemStack transported, int segment, Direction side) {
		SlimeBeltInsertion.prepareInsertedItem(operationOwner, transported, segment, side, false);
	}

	static void prepareInsertedItem(SlimeBeltInventory operationOwner, TransportedItemStack transported, int segment, Direction side,
		boolean verticalHorizontalBeltInput) {
		SlimeBeltInsertion.applyInsertion(operationOwner, transported, SlimeBeltInsertion.planInsertion(operationOwner, segment, side, verticalHorizontalBeltInput, transported));
	}

	static void prepareInsertedItemOnTrack(SlimeBeltInventory operationOwner, TransportedItemStack transported, int segment, Track track) {
		SlimeBeltInsertion.applyInsertion(operationOwner, transported, SlimeBeltInsertion.planTrackInsertion(operationOwner, segment, track));
	}

	static InsertionPlan planTrackInsertion(SlimeBeltInventory operationOwner, int segment, Track track) {
		// Entity capture can be the first inventory interaction after loading. Resolve
		// against the live kinetic direction instead of the persisted/default order.
		SlimeBeltInsertion.refreshMovementDirection(operationOwner);
		return SlimeBeltInsertionPlanner.planTrackInsertion(operationOwner.belt, operationOwner.frame(), segment, track);
	}

	/** Land a stack according to a plan; gate and landing consume the same resolution. */
	static void applyInsertion(SlimeBeltInventory operationOwner, TransportedItemStack transported, @Nullable InsertionPlan plan) {
		if (plan == null)
			return;
		transported.beltPosition = plan.landingLoopPosition();
		if (plan.sideOffset() != null)
			transported.sideOffset = plan.sideOffset();
		transported.prevBeltPosition = transported.beltPosition;
		transported.prevSideOffset = transported.sideOffset;
		transported.insertedAt = plan.insertedAt();
		transported.insertedFrom = plan.insertedFrom();
	}

	static boolean refreshMovementDirection(SlimeBeltInventory operationOwner) {
		if (operationOwner.belt.getSpeed() == 0)
			return false;
		// Direct insertion may run before this controller's tick after loading or
		// a kinetic reversal. Coordinate conversion must use the live direction,
		// not the previous tick's persisted PositiveOrder value.
		boolean movingPositive = operationOwner.belt.getDirectionAwareBeltMovementSpeed() > 0;
		if (operationOwner.beltMovementPositive == movingPositive)
			return false;
		operationOwner.beltMovementPositive = movingPositive;
		return true;
	}

	/** True when no live or queued item occupies the given turn, {@code ignoring} excepted. */
	static boolean isTurnClear(SlimeBeltInventory operationOwner, LoopSection turn, @Nullable TransportedItemStack ignoring) {
		for (TransportedItemStack stack : operationOwner.items)
			if (stack != ignoring && !operationOwner.toRemove.contains(stack)
				&& SlimeBeltHelper.getLoopSection(operationOwner.belt, stack.beltPosition) == turn)
				return false;
		for (TransportedItemStack stack : operationOwner.toInsert)
			if (SlimeBeltHelper.getLoopSection(operationOwner.belt, stack.beltPosition) == turn)
				return false;
		return true;
	}

	static boolean isBlocking(SlimeBeltInventory operationOwner, Track track, float insertPos, TransportedItemStack stack) {
		if (operationOwner.getTrackForLoopPosition(stack.beltPosition) != track)
			return false;
		float insertProgress = operationOwner.getTrackProgress(track, insertPos);
		float currentProgress = operationOwner.getTrackProgress(track, stack.beltPosition);
		float distanceAhead = currentProgress - insertProgress;
		return distanceAhead >= 0 && distanceAhead <= 1f;
	}

	static void addItem(SlimeBeltInventory operationOwner, TransportedItemStack newStack) {
		operationOwner.toInsert.add(newStack);
	}

	static void insert(SlimeBeltInventory operationOwner, TransportedItemStack newStack) {
		if (operationOwner.items.isEmpty())
			operationOwner.items.add(newStack);
		else {
			int index = 0;
			for (TransportedItemStack stack : operationOwner.items) {
				if (stack.compareTo(newStack) > 0 == operationOwner.beltMovementPositive)
					break;
				index++;
			}
			operationOwner.items.add(index, newStack);
		}
	}
}
