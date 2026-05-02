package com.nobodiiiii.createbiotech.content.slimebelt.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltBlock;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltBlockEntity;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper.LoopSection;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper.Track;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.BlockHelper;
import com.simibubi.create.foundation.utility.ServerSpeedProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SlimeBeltInventory {

	public final SlimeBeltBlockEntity belt;
	private final List<TransportedItemStack> items;
	final List<TransportedItemStack> toInsert;
	final List<TransportedItemStack> toRemove;
	boolean beltMovementPositive;
	final float SEGMENT_WINDOW = .75f;
	private static final float WRAP_ENTRY_OFFSET = 1 / 512f;
	private static final float TRANSFER_WAIT_MARGIN = .25f;
	private static final float SEAM_EPSILON = 1 / 1024f;

	TransportedItemStack lazyClientItem;

	private enum SeamAction {
		NONE,
		TRANSFER,
		WAIT,
		INSERT,
		BLOCKED
	}

	private enum Connector {
		END,
		START
	}

	public SlimeBeltInventory(SlimeBeltBlockEntity be) {
		this.belt = be;
		items = new LinkedList<>();
		toInsert = new LinkedList<>();
		toRemove = new LinkedList<>();
	}

	public void tick() {

		// Residual item for "smooth" transitions
		if (lazyClientItem != null) {
			if (lazyClientItem.locked)
				lazyClientItem = null;
			else
				lazyClientItem.locked = true;
		}

		// Added/Removed items from previous cycle
		if (!toInsert.isEmpty() || !toRemove.isEmpty()) {
			toInsert.forEach(this::insert);
			toInsert.clear();
			items.removeAll(toRemove);
			toRemove.clear();
			belt.notifyUpdate();
		}

		if (belt.getSpeed() == 0)
			return;

		boolean movingPositive = belt.getDirectionAwareBeltMovementSpeed() > 0;
		if (beltMovementPositive != movingPositive) {
			beltMovementPositive = movingPositive;
			belt.notifyUpdate();
		}

		float trackSpeed = Math.abs(belt.getDirectionAwareBeltMovementSpeed());
		boolean horizontalProcessing = belt.getBlockState().getValue(SlimeBeltBlock.SLOPE) == com.simibubi.create.content.kinetics.belt.BeltSlope.HORIZONTAL;
		Level world = belt.getLevel();
		boolean onClient = world.isClientSide && !belt.isVirtual();
		Ending[] ending = new Ending[] { Ending.UNRESOLVED };
		Set<TransportedItemStack> transferredThisTick = Collections.newSetFromMap(new IdentityHashMap<>());

		processTrack(Track.FRONT, trackSpeed, onClient, world, horizontalProcessing, ending, transferredThisTick);
		processConnector(beltMovementPositive ? Connector.END : Connector.START, trackSpeed, onClient, transferredThisTick);
		processTrack(Track.BACK, trackSpeed, onClient, world, false, ending, transferredThisTick);
		processConnector(beltMovementPositive ? Connector.START : Connector.END, trackSpeed, onClient, transferredThisTick);
	}

	private void processTrack(Track track, float trackSpeed, boolean onClient, Level world, boolean horizontalProcessing,
		Ending[] ending, Set<TransportedItemStack> transferredThisTick) {
		TransportedItemStack stackInFront = null;
		float spacing = 1;
		Direction movementFacing = belt.getMovementFacing();

		for (TransportedItemStack currentItem : getItemsOnTrackOrdered(track)) {
			if (transferredThisTick.contains(currentItem))
				continue;

			currentItem.prevBeltPosition = currentItem.beltPosition;
			currentItem.prevSideOffset = currentItem.sideOffset;

			if (currentItem.stack.isEmpty()) {
				items.remove(currentItem);
				continue;
			}

			float movement = trackSpeed;
			if (onClient)
				movement *= ServerSpeedProvider.get();

			if (world.isClientSide && currentItem.locked)
				continue;

			if (currentItem.lockedExternally) {
				currentItem.lockedExternally = false;
				continue;
			}

			float currentProgress = getTrackProgress(track, currentItem.beltPosition);
			boolean noMovement = false;

			if (stackInFront != null) {
				float diff = getTrackProgress(track, stackInFront.beltPosition) - currentProgress;
				if (Math.abs(diff) <= spacing)
					noMovement = true;
				movement = Math.min(movement, diff - spacing);
			}

			Track targetTrack = track == Track.FRONT ? Track.BACK : Track.FRONT;
			Connector exitConnector = getExitConnector(track);
			float diffToEnd = belt.beltLength - currentProgress;
			boolean approachingSeam = Math.abs(diffToEnd) < Math.abs(movement) + 1;
			boolean crossingSeam = movement > 0 && currentProgress + movement >= belt.beltLength;
			SeamAction seamAction = SeamAction.NONE;
			float limitedMovement = movement;
			if (approachingSeam) {
				if (track == Track.FRONT) {
					if (ending[0] == Ending.UNRESOLVED)
						ending[0] = resolveEnding();
					if (ending[0] == Ending.INSERT) {
						seamAction = SeamAction.INSERT;
					} else if (ending[0] == Ending.BLOCKED) {
						seamAction = SeamAction.BLOCKED;
					} else {
						seamAction = isConnectorEntryClear(exitConnector, currentItem) ? SeamAction.TRANSFER : SeamAction.WAIT;
					}
				} else {
					seamAction = isConnectorEntryClear(exitConnector, currentItem) ? SeamAction.TRANSFER : SeamAction.WAIT;
				}

				float margin = seamAction == SeamAction.TRANSFER ? 0
					: seamAction == SeamAction.BLOCKED ? Ending.BLOCKED.margin
					: seamAction == SeamAction.INSERT ? Ending.INSERT.margin : TRANSFER_WAIT_MARGIN;
				limitedMovement = Math.min(limitedMovement, diffToEnd - margin);
			}

			float nextProgress = currentProgress + limitedMovement;
			float nextFrontOffset = getFrontOffsetForTrackProgress(track, nextProgress);

			if (!onClient && horizontalProcessing && track == Track.FRONT && seamAction == SeamAction.NONE) {
				ItemStack item = currentItem.stack;
				if (handleBeltProcessingAndCheckIfRemoved(currentItem, nextFrontOffset, noMovement)) {
					items.remove(currentItem);
					belt.notifyUpdate();
					continue;
				}
				if (item != currentItem.stack)
					belt.notifyUpdate();
				if (currentItem.locked)
					continue;
			}

			if (noMovement) {
				stackInFront = currentItem;
				continue;
			}

			setLoopPositionFromTrackProgress(currentItem, track, nextProgress);
			float diffToMiddle = currentItem.getTargetSideOffset() - currentItem.sideOffset;
			currentItem.sideOffset += Mth.clamp(diffToMiddle * Math.abs(limitedMovement) * 6f, -Math.abs(diffToMiddle),
				Math.abs(diffToMiddle));

			boolean reachedTransferSeam = seamAction == SeamAction.TRANSFER && crossingSeam
				&& currentProgress + limitedMovement >= belt.beltLength - SEAM_EPSILON;

			if (reachedTransferSeam) {
				float overshoot = Math.max(0, currentProgress + limitedMovement - belt.beltLength);
				moveIntoConnector(currentItem, exitConnector, overshoot);
				transferredThisTick.add(currentItem);
				continue;
			}

			if (!onClient && seamAction == SeamAction.INSERT && approachingSeam && limitedMovement != movement) {
				BlockPos nextPosition = SlimeBeltHelper.getPositionForOffset(belt, beltMovementPositive ? belt.beltLength : -1);
				DirectBeltInputBehaviour inputBehaviour =
					BlockEntityBehaviour.get(world, nextPosition, DirectBeltInputBehaviour.TYPE);
				if (inputBehaviour != null && inputBehaviour.canInsertFromSide(movementFacing)) {
					ItemStack remainder = inputBehaviour.handleInsertion(currentItem, movementFacing, false);
					if (!remainder.equals(currentItem.stack, false)) {
						currentItem.stack = remainder;
						if (remainder.isEmpty()) {
							lazyClientItem = currentItem;
							lazyClientItem.locked = false;
							items.remove(currentItem);
						}
						belt.notifyUpdate();
						continue;
					}
				}
			}

			stackInFront = currentItem;
		}
	}

	private void processConnector(Connector connector, float trackSpeed, boolean onClient,
		Set<TransportedItemStack> transferredThisTick) {
		TransportedItemStack stackInFront = null;
		float spacing = 1;
		float connectorLength = getConnectorLength();
		Track targetTrack = getTargetTrack(connector);

		for (TransportedItemStack currentItem : getItemsOnConnectorOrdered(connector)) {
			if (transferredThisTick.contains(currentItem))
				continue;

			currentItem.prevBeltPosition = currentItem.beltPosition;
			currentItem.prevSideOffset = currentItem.sideOffset;

			if (currentItem.stack.isEmpty()) {
				items.remove(currentItem);
				continue;
			}

			float movement = trackSpeed;
			if (onClient)
				movement *= ServerSpeedProvider.get();

			if (belt.getLevel().isClientSide && currentItem.locked)
				continue;

			if (currentItem.lockedExternally) {
				currentItem.lockedExternally = false;
				continue;
			}

			float currentProgress = getConnectorProgress(connector, currentItem.beltPosition);
			boolean noMovement = false;

			if (stackInFront != null) {
				float diff = getConnectorProgress(connector, stackInFront.beltPosition) - currentProgress;
				if (Math.abs(diff) <= spacing)
					noMovement = true;
				movement = Math.min(movement, diff - spacing);
			}

			float diffToEnd = connectorLength - currentProgress;
			boolean crossingConnectorEnd = movement > 0 && currentProgress + movement >= connectorLength;
			boolean targetEntryClear = isTransferEntryClear(targetTrack, currentItem);
			if (Math.abs(diffToEnd) < Math.abs(movement) + 1 && !targetEntryClear)
				movement = Math.min(movement, diffToEnd - TRANSFER_WAIT_MARGIN);

			if (noMovement) {
				stackInFront = currentItem;
				continue;
			}

			float nextProgress = currentProgress + movement;
			currentItem.beltPosition = getLoopPositionForConnectorProgress(connector, nextProgress);
			float diffToMiddle = currentItem.getTargetSideOffset() - currentItem.sideOffset;
			currentItem.sideOffset += Mth.clamp(diffToMiddle * Math.abs(movement) * 6f, -Math.abs(diffToMiddle),
				Math.abs(diffToMiddle));

			if (targetEntryClear && crossingConnectorEnd
				&& currentProgress + movement >= connectorLength - SEAM_EPSILON) {
				float overshoot = Math.max(0, currentProgress + movement - connectorLength);
				transferToTrack(currentItem, targetTrack, overshoot);
				transferredThisTick.add(currentItem);
				continue;
			}

			stackInFront = currentItem;
		}
	}

	protected boolean handleBeltProcessingAndCheckIfRemoved(TransportedItemStack currentItem, float nextOffset,
															boolean noMovement) {
		int currentSegment = (int) SlimeBeltHelper.getFrontOffsetForLoopPosition(belt, currentItem.beltPosition);

		if (currentItem.locked) {
			BeltProcessingBehaviour processingBehaviour = getBeltProcessingAtSegment(currentSegment);
			TransportedItemStackHandlerBehaviour stackHandlerBehaviour =
				getTransportedItemStackHandlerAtSegment(currentSegment);

			if (stackHandlerBehaviour == null)
				return false;
			if (processingBehaviour == null) {
				currentItem.locked = false;
				belt.notifyUpdate();
				return false;
			}

			ProcessingResult result = processingBehaviour.handleHeldItem(currentItem, stackHandlerBehaviour);
			if (result == ProcessingResult.REMOVE)
				return true;
			if (result == ProcessingResult.HOLD)
				return false;

			currentItem.locked = false;
			belt.notifyUpdate();
			return false;
		}

		if (noMovement)
			return false;

		float currentFrontOffset = SlimeBeltHelper.getFrontOffsetForLoopPosition(belt, currentItem.beltPosition);
		if (currentFrontOffset > .5f || beltMovementPositive) {
			int firstUpcomingSegment = (int) (currentFrontOffset + (beltMovementPositive ? .5f : -.5f));
			int step = beltMovementPositive ? 1 : -1;

			for (int segment = firstUpcomingSegment; beltMovementPositive ? segment + .5f <= nextOffset
				: segment + .5f >= nextOffset; segment += step) {

				BeltProcessingBehaviour processingBehaviour = getBeltProcessingAtSegment(segment);
				TransportedItemStackHandlerBehaviour stackHandlerBehaviour =
					getTransportedItemStackHandlerAtSegment(segment);

				if (processingBehaviour == null)
					continue;
				if (stackHandlerBehaviour == null)
					continue;
				if (BeltProcessingBehaviour.isBlocked(belt.getLevel(), SlimeBeltHelper.getPositionForOffset(belt, segment)))
					continue;

				ProcessingResult result = processingBehaviour.handleReceivedItem(currentItem, stackHandlerBehaviour);
				if (result == ProcessingResult.REMOVE)
					return true;

				if (result == ProcessingResult.HOLD) {
					currentItem.beltPosition = segment + .5f + (beltMovementPositive ? 1 / 512f : -1 / 512f);
					currentItem.locked = true;
					belt.notifyUpdate();
					return false;
				}
			}
		}

		return false;
	}

	protected BeltProcessingBehaviour getBeltProcessingAtSegment(int segment) {
		return BlockEntityBehaviour.get(belt.getLevel(), SlimeBeltHelper.getPositionForOffset(belt, segment)
			.above(2), BeltProcessingBehaviour.TYPE);
	}

	protected TransportedItemStackHandlerBehaviour getTransportedItemStackHandlerAtSegment(int segment) {
		return BlockEntityBehaviour.get(belt.getLevel(), SlimeBeltHelper.getPositionForOffset(belt, segment),
			TransportedItemStackHandlerBehaviour.TYPE);
	}

	private enum Ending {
		UNRESOLVED(0), WRAP(0), INSERT(.25f), BLOCKED(.45f);

		private float margin;

		Ending(float f) {
			this.margin = f;
		}
	}

	private Ending resolveEnding() {
		Level world = belt.getLevel();
		BlockPos nextPosition = SlimeBeltHelper.getPositionForOffset(belt, beltMovementPositive ? belt.beltLength : -1);
		Direction movementFacing = belt.getMovementFacing();

		DirectBeltInputBehaviour inputBehaviour =
			BlockEntityBehaviour.get(world, nextPosition, DirectBeltInputBehaviour.TYPE);
		if (inputBehaviour != null && inputBehaviour.canInsertFromSide(movementFacing))
			return Ending.INSERT;

		if (BlockHelper.hasBlockSolidSide(world.getBlockState(nextPosition), world, nextPosition,
			movementFacing
				.getOpposite()))
			return Ending.BLOCKED;

		return Ending.WRAP;
	}

	public boolean canInsertAt(int segment) {
		return canInsertAtFromSide(segment, Direction.UP);
	}

	public boolean canInsertAtFromSide(int segment, Direction side) {
		if (side != null && belt.getMovementFacing() == side.getOpposite())
			return false;
		Track track = SlimeBeltHelper.resolveInputTrack(belt.getBlockState(), side);
		float insertPos = getInsertionPosition(segment, side);
		for (TransportedItemStack stack : items)
			if (isBlocking(track, insertPos, stack))
				return false;
		for (TransportedItemStack stack : toInsert)
			if (isBlocking(track, insertPos, stack))
				return false;
		return true;
	}

	public boolean canInsertAtOnTrack(int segment, Track track) {
		float insertPos = getInsertionPositionForTrack(segment, track);
		for (TransportedItemStack stack : items)
			if (isBlocking(track, insertPos, stack))
				return false;
		for (TransportedItemStack stack : toInsert)
			if (isBlocking(track, insertPos, stack))
				return false;
		return true;
	}

	public float getInsertionPosition(int segment, Direction side) {
		Track track = SlimeBeltHelper.resolveInputTrack(belt.getBlockState(), side);
		float trackProgress;
		if (side != null && side == belt.getMovementFacing() && track == Track.FRONT) {
			trackProgress = beltMovementPositive ? segment : belt.beltLength - (segment + 1f);
		} else {
			float halfSegment = segment + .5f;
			float offset = beltMovementPositive ? -1 / 16f : 1 / 16f;
			float frontOffset = halfSegment + offset;
			if (track == Track.FRONT)
				trackProgress = beltMovementPositive ? frontOffset : belt.beltLength - frontOffset;
			else
				trackProgress = beltMovementPositive ? belt.beltLength - frontOffset : frontOffset;
		}
		return getLoopPositionForTrackProgress(track, trackProgress);
	}

	public float getInsertionPositionForTrack(int segment, Track track) {
		float halfSegment = segment + .5f;
		float offset = beltMovementPositive ? -1 / 16f : 1 / 16f;
		float frontOffset = halfSegment + offset;
		float trackProgress = track == Track.FRONT
			? beltMovementPositive ? frontOffset : belt.beltLength - frontOffset
			: beltMovementPositive ? belt.beltLength - frontOffset : frontOffset;
		return getLoopPositionForTrackProgress(track, trackProgress);
	}

	public float getSmoothInsertionPosition(int segment, Direction side, float extraOffset) {
		Track track = SlimeBeltHelper.resolveInputTrack(belt.getBlockState(), side);
		if (track != Track.FRONT || side != belt.getMovementFacing())
			return getInsertionPosition(segment, side);
		float trackProgress = beltMovementPositive ? segment - extraOffset
			: belt.beltLength - (segment + 1f + extraOffset);
		return getLoopPositionForTrackProgress(Track.FRONT, trackProgress);
	}

	public void prepareInsertedItem(TransportedItemStack transported, int segment, Direction side) {
		float insertionPosition = getInsertionPosition(segment, side);
		transported.beltPosition = insertionPosition;
		transported.prevBeltPosition = insertionPosition;
		transported.insertedAt = segment;
		transported.insertedFrom = side;
		transported.prevSideOffset = transported.sideOffset;
	}

	public void prepareInsertedItemOnTrack(TransportedItemStack transported, int segment, Track track) {
		float insertionPosition = getInsertionPositionForTrack(segment, track);
		transported.beltPosition = insertionPosition;
		transported.prevBeltPosition = insertionPosition;
		transported.insertedAt = segment;
		transported.insertedFrom = getRepresentativeSideForTrack(segment, track);
		transported.prevSideOffset = transported.sideOffset;
	}

	private boolean isBlocking(Track track, float insertPos, TransportedItemStack stack) {
		if (getTrackForLoopPosition(stack.beltPosition) != track)
			return false;
		float insertProgress = getTrackProgress(track, insertPos);
		float currentProgress = getTrackProgress(track, stack.beltPosition);
		float distanceAhead = currentProgress - insertProgress;
		return distanceAhead >= 0 && distanceAhead <= 1f;
	}

	public void addItem(TransportedItemStack newStack) {
		toInsert.add(newStack);
	}

	private void insert(TransportedItemStack newStack) {
		if (items.isEmpty())
			items.add(newStack);
		else {
			int index = 0;
			for (TransportedItemStack stack : items) {
				if (stack.compareTo(newStack) > 0 == beltMovementPositive)
					break;
				index++;
			}
			items.add(index, newStack);
		}
	}

	private List<TransportedItemStack> getItemsOnTrackOrdered(Track track) {
		List<TransportedItemStack> ordered = new ArrayList<>();
		for (TransportedItemStack stack : items)
			if (getTrackForLoopPosition(stack.beltPosition) == track)
				ordered.add(stack);
		ordered.sort((first, second) -> Float.compare(getTrackProgress(track, second.beltPosition),
			getTrackProgress(track, first.beltPosition)));
		return ordered;
	}

	private List<TransportedItemStack> getItemsOnConnectorOrdered(Connector connector) {
		List<TransportedItemStack> ordered = new ArrayList<>();
		LoopSection section = getSectionForConnector(connector);
		for (TransportedItemStack stack : items)
			if (SlimeBeltHelper.getLoopSection(belt, stack.beltPosition) == section)
				ordered.add(stack);
		ordered.sort((first, second) -> Float.compare(getConnectorProgress(connector, second.beltPosition),
			getConnectorProgress(connector, first.beltPosition)));
		return ordered;
	}

	private float getTrackProgress(Track track, float loopPosition) {
		float normalized = SlimeBeltHelper.normalizeLoopPosition(belt, loopPosition);
		float length = belt.beltLength;
		float connectorLength = getConnectorLength();
		float backTrackStart = length + connectorLength;
		float backTrackEnd = backTrackStart + length;
		if (beltMovementPositive)
			return track == Track.FRONT ? normalized : normalized - backTrackStart;
		return track == Track.FRONT ? length - normalized : backTrackEnd - normalized;
	}

	private float getLoopPositionForTrackProgress(Track track, float progress) {
		float clamped = Mth.clamp(progress, 0, belt.beltLength);
		float length = belt.beltLength;
		float connectorLength = getConnectorLength();
		float backTrackStart = length + connectorLength;
		float backTrackEnd = backTrackStart + length;
		float loopPos = beltMovementPositive ? (track == Track.FRONT ? clamped : backTrackStart + clamped)
			: (track == Track.FRONT ? length - clamped : backTrackEnd - clamped);
		return SlimeBeltHelper.normalizeLoopPosition(belt, loopPos);
	}

	private float getFrontOffsetForTrackProgress(Track track, float progress) {
		float clamped = Mth.clamp(progress, 0, belt.beltLength);
		return beltMovementPositive ? (track == Track.FRONT ? clamped : belt.beltLength - clamped)
			: (track == Track.FRONT ? belt.beltLength - clamped : clamped);
	}

	private void setLoopPositionFromTrackProgress(TransportedItemStack currentItem, Track track, float progress) {
		currentItem.beltPosition = getLoopPositionForTrackProgress(track, progress);
	}

	private float getConnectorProgress(Connector connector, float loopPosition) {
		float normalized = SlimeBeltHelper.normalizeLoopPosition(belt, loopPosition);
		float connectorStart = getConnectorStart(connector);
		float connectorLength = getConnectorLength();
		if (beltMovementPositive)
			return normalized - connectorStart;
		if (connector == Connector.END)
			return connectorStart + connectorLength - normalized;
		return SlimeBeltHelper.getLoopLength(belt) - normalized;
	}

	private float getLoopPositionForConnectorProgress(Connector connector, float progress) {
		float clamped = Mth.clamp(progress, 0, getConnectorLength());
		float connectorStart = getConnectorStart(connector);
		if (beltMovementPositive)
			return SlimeBeltHelper.normalizeLoopPosition(belt, connectorStart + clamped);
		if (connector == Connector.END)
			return SlimeBeltHelper.normalizeLoopPosition(belt, connectorStart + getConnectorLength() - clamped);
		return SlimeBeltHelper.normalizeLoopPosition(belt, SlimeBeltHelper.getLoopLength(belt) - clamped);
	}

	private boolean isTransferEntryClear(Track targetTrack, TransportedItemStack currentItem) {
		float entryPosition = getTrackEntryPosition(targetTrack);
		for (TransportedItemStack stack : items)
			if (stack != currentItem && !toRemove.contains(stack) && isBlocking(targetTrack, entryPosition, stack))
				return false;
		for (TransportedItemStack stack : toInsert)
			if (isBlocking(targetTrack, entryPosition, stack))
				return false;
		return true;
	}

	private float getTrackEntryPosition(Track targetTrack) {
		return getLoopPositionForTrackProgress(targetTrack, WRAP_ENTRY_OFFSET);
	}

	private void transferToTrack(TransportedItemStack currentItem, Track targetTrack, float overshoot) {
		currentItem.prevBeltPosition = currentItem.beltPosition;
		currentItem.beltPosition = getLoopPositionForTrackProgress(targetTrack, WRAP_ENTRY_OFFSET + overshoot);
	}

	private boolean isConnectorEntryClear(Connector connector, TransportedItemStack currentItem) {
		LoopSection section = getSectionForConnector(connector);
		for (TransportedItemStack stack : items)
			if (stack != currentItem && !toRemove.contains(stack)
				&& SlimeBeltHelper.getLoopSection(belt, stack.beltPosition) == section)
				return false;
		for (TransportedItemStack stack : toInsert)
			if (SlimeBeltHelper.getLoopSection(belt, stack.beltPosition) == section)
				return false;
		return true;
	}

	private void moveIntoConnector(TransportedItemStack currentItem, Connector connector, float overshoot) {
		currentItem.prevBeltPosition = currentItem.beltPosition;
		float connectorProgress = Mth.clamp(Math.max(WRAP_ENTRY_OFFSET, overshoot), WRAP_ENTRY_OFFSET,
			Math.max(WRAP_ENTRY_OFFSET, getConnectorLength() - SEAM_EPSILON));
		currentItem.beltPosition = getLoopPositionForConnectorProgress(connector, connectorProgress);
	}

	private Connector getExitConnector(Track track) {
		if (track == Track.FRONT)
			return beltMovementPositive ? Connector.END : Connector.START;
		return beltMovementPositive ? Connector.START : Connector.END;
	}

	private Track getTargetTrack(Connector connector) {
		if (connector == Connector.END)
			return beltMovementPositive ? Track.BACK : Track.FRONT;
		return beltMovementPositive ? Track.FRONT : Track.BACK;
	}

	private LoopSection getSectionForConnector(Connector connector) {
		return connector == Connector.END ? LoopSection.END_TURN : LoopSection.START_TURN;
	}

	private Track getTrackForLoopPosition(float loopPosition) {
		LoopSection section = SlimeBeltHelper.getLoopSection(belt, loopPosition);
		if (section == LoopSection.FRONT)
			return Track.FRONT;
		if (section == LoopSection.BACK)
			return Track.BACK;
		return null;
	}

	private Direction getRepresentativeSideForTrack(int segment, Track track) {
		Direction frontInputSide = SlimeBeltHelper.getFrontInputSide(belt.getBlockState());
		Direction primary = track == Track.FRONT ? Direction.UP : Direction.DOWN;
		Direction alternate = track == Track.FRONT ? frontInputSide : frontInputSide.getOpposite();
		Vec3 trackNormal = SlimeBeltHelper.getTrackNormal(belt, getInsertionPositionForTrack(segment, track));
		return getAlignment(trackNormal, alternate) > getAlignment(trackNormal, primary) ? alternate : primary;
	}

	private double getAlignment(Vec3 normal, Direction side) {
		return normal.dot(Vec3.atLowerCornerOf(side.getNormal()));
	}

	private float getConnectorStart(Connector connector) {
		return connector == Connector.END ? belt.beltLength : belt.beltLength + getConnectorLength() + belt.beltLength;
	}

	private float getConnectorLength() {
		return SlimeBeltHelper.getConnectorLength(belt);
	}

	public TransportedItemStack getStackAtOffset(int offset, Direction side) {
		Track track = SlimeBeltHelper.resolveInputTrack(belt.getBlockState(), side);
		float min = offset;
		float max = offset + 1;
		for (TransportedItemStack stack : items) {
			if (toRemove.contains(stack))
				continue;
			if (SlimeBeltHelper.resolveInputTrack(belt.getBlockState(), stack.insertedFrom) != track
				&& getTrackForLoopPosition(stack.beltPosition) != track)
				continue;
			float frontOffset = SlimeBeltHelper.getFrontOffsetForLoopPosition(belt, stack.beltPosition);
			if (frontOffset > max)
				continue;
			if (frontOffset > min)
				return stack;
		}
		return null;
	}

	public void read(CompoundTag nbt) {
		items.clear();
		nbt.getList("Items", Tag.TAG_COMPOUND)
			.forEach(inbt -> items.add(TransportedItemStack.read((CompoundTag) inbt)));
		if (nbt.contains("LazyItem"))
			lazyClientItem = TransportedItemStack.read(nbt.getCompound("LazyItem"));
		beltMovementPositive = nbt.getBoolean("PositiveOrder");
	}

	public CompoundTag write() {
		CompoundTag nbt = new CompoundTag();
		ListTag itemsNBT = new ListTag();
		items.forEach(stack -> itemsNBT.add(stack.serializeNBT()));
		nbt.put("Items", itemsNBT);
		if (lazyClientItem != null)
			nbt.put("LazyItem", lazyClientItem.serializeNBT());
		nbt.putBoolean("PositiveOrder", beltMovementPositive);
		return nbt;
	}

	public void eject(TransportedItemStack stack) {
		ItemStack ejected = stack.stack;
		Vec3 outPos = SlimeBeltHelper.getVectorForOffset(belt, stack.beltPosition);
		float movementSpeed = Math.max(Math.abs(belt.getBeltMovementSpeed()), 1 / 8f);
		Vec3 outMotion = Vec3.atLowerCornerOf(belt.getBeltChainDirection())
			.scale(movementSpeed)
			.add(0, 1 / 8f, 0);
		outPos = outPos.add(outMotion.normalize()
			.scale(0.001));
		ItemEntity entity = new ItemEntity(belt.getLevel(), outPos.x, outPos.y + 6 / 16f, outPos.z, ejected);
		entity.setDeltaMovement(outMotion);
		entity.setDefaultPickUpDelay();
		entity.hurtMarked = true;
		belt.getLevel()
			.addFreshEntity(entity);
	}

	public void ejectAll() {
		items.forEach(this::eject);
		items.clear();
	}

	public void applyToEachWithin(float position, float maxDistanceToPosition,
								  Function<TransportedItemStack, TransportedResult> processFunction) {
		boolean dirty = false;
		for (TransportedItemStack transported : items) {
			if (toRemove.contains(transported))
				continue;
			if (SlimeBeltHelper.getLoopSection(belt, transported.beltPosition) != LoopSection.FRONT)
				continue;
			ItemStack stackBefore = transported.stack.copy();
			float frontOffset = SlimeBeltHelper.getFrontOffsetForLoopPosition(belt, transported.beltPosition);
			if (Math.abs(position - frontOffset) >= maxDistanceToPosition)
				continue;
			TransportedResult result = processFunction.apply(transported);
			if (result == null || result.didntChangeFrom(stackBefore))
				continue;

			dirty = true;
			if (result.hasHeldOutput()) {
				TransportedItemStack held = result.getHeldOutput();
				held.beltPosition = ((int) position) + .5f - (beltMovementPositive ? 1 / 512f : -1 / 512f);
				toInsert.add(held);
			}
			toInsert.addAll(result.getOutputs());
			toRemove.add(transported);
		}
		if (dirty) {
			belt.notifyUpdate();
		}
	}

	public List<TransportedItemStack> getTransportedItems() {
		return items;
	}

	@Nullable
	public TransportedItemStack getLazyClientItem() {
		return lazyClientItem;
	}

}

