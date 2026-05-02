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
		processTrack(Track.BACK, trackSpeed, onClient, world, false, ending, transferredThisTick);
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
						seamAction = isTransferEntryClear(targetTrack, currentItem) ? SeamAction.TRANSFER : SeamAction.WAIT;
					}
				} else {
					seamAction = isTransferEntryClear(targetTrack, currentItem) ? SeamAction.TRANSFER : SeamAction.WAIT;
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
				transferToTrack(currentItem, targetTrack);
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

	public float getInsertionPosition(int segment, Direction side) {
		Track track = SlimeBeltHelper.resolveInputTrack(belt.getBlockState(), side);
		float basePosition;
		if (side != null && side == belt.getMovementFacing() && track == Track.FRONT) {
			basePosition = beltMovementPositive ? segment : segment + 1f;
		} else {
			float halfSegment = segment + .5f;
			float offset = beltMovementPositive ? -1 / 16f : 1 / 16f;
			basePosition = track == Track.FRONT ? halfSegment + offset
				: SlimeBeltHelper.getLoopLength(belt) - halfSegment + offset;
		}
		float position = basePosition;
		return SlimeBeltHelper.normalizeLoopPosition(belt, position);
	}

	public float getSmoothInsertionPosition(int segment, Direction side, float extraOffset) {
		Track track = SlimeBeltHelper.resolveInputTrack(belt.getBlockState(), side);
		if (track != Track.FRONT || side != belt.getMovementFacing())
			return getInsertionPosition(segment, side);
		float position = beltMovementPositive ? segment - extraOffset : segment + 1f + extraOffset;
		return SlimeBeltHelper.normalizeLoopPosition(belt, position);
	}

	public void prepareInsertedItem(TransportedItemStack transported, int segment, Direction side) {
		float insertionPosition = getInsertionPosition(segment, side);
		transported.beltPosition = insertionPosition;
		transported.prevBeltPosition = insertionPosition;
		transported.insertedAt = segment;
		transported.insertedFrom = side;
		transported.prevSideOffset = transported.sideOffset;
	}

	private boolean isBlocking(Track track, float insertPos, TransportedItemStack stack) {
		if (SlimeBeltHelper.isBackTrack(belt, stack.beltPosition) != (track == Track.BACK))
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
			if (SlimeBeltHelper.isBackTrack(belt, stack.beltPosition) == (track == Track.BACK))
				ordered.add(stack);
		ordered.sort((first, second) -> Float.compare(getTrackProgress(track, second.beltPosition),
			getTrackProgress(track, first.beltPosition)));
		return ordered;
	}

	private float getTrackProgress(Track track, float loopPosition) {
		float normalized = SlimeBeltHelper.normalizeLoopPosition(belt, loopPosition);
		float length = belt.beltLength;
		if (beltMovementPositive)
			return track == Track.FRONT ? normalized : normalized - length;
		return track == Track.FRONT ? length - normalized : 2 * length - normalized;
	}

	private float getLoopPositionForTrackProgress(Track track, float progress) {
		float clamped = Mth.clamp(progress, 0, belt.beltLength);
		float length = belt.beltLength;
		float loopPos = beltMovementPositive ? (track == Track.FRONT ? clamped : length + clamped)
			: (track == Track.FRONT ? length - clamped : 2 * length - clamped);
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

	private void transferToTrack(TransportedItemStack currentItem, Track targetTrack) {
		currentItem.prevBeltPosition = currentItem.beltPosition;
		currentItem.beltPosition = getTrackEntryPosition(targetTrack);
	}

	public TransportedItemStack getStackAtOffset(int offset, Direction side) {
		Track track = SlimeBeltHelper.resolveInputTrack(belt.getBlockState(), side);
		float min = offset;
		float max = offset + 1;
		for (TransportedItemStack stack : items) {
			if (toRemove.contains(stack))
				continue;
			if (SlimeBeltHelper.resolveInputTrack(belt.getBlockState(), stack.insertedFrom) != track
				&& SlimeBeltHelper.isBackTrack(belt, stack.beltPosition) != (track == Track.BACK))
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
			if (SlimeBeltHelper.isBackTrack(belt, transported.beltPosition))
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

