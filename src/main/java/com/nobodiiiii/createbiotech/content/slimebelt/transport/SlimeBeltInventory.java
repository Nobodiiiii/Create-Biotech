package com.nobodiiiii.createbiotech.content.slimebelt.transport;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
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

	TransportedItemStack lazyClientItem;

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

		if (beltMovementPositive != belt.getDirectionAwareBeltMovementSpeed() > 0) {
			beltMovementPositive = !beltMovementPositive;
			items.sort((first, second) -> beltMovementPositive ? second.compareTo(first) : first.compareTo(second));
			belt.notifyUpdate();
		}

		items.sort((first, second) -> beltMovementPositive ? second.compareTo(first) : first.compareTo(second));

		TransportedItemStack seamReference = items.size() > 1 ? items.get(items.size() - 1) : null;
		TransportedItemStack stackInFront = null;
		boolean firstItem = true;
		Iterator<TransportedItemStack> iterator = items.iterator();

		float beltSpeed = belt.getDirectionAwareBeltMovementSpeed();
		Direction movementFacing = belt.getMovementFacing();
		float loopLength = SlimeBeltHelper.getLoopLength(belt);
		float frontRunLength = belt.beltLength;
		boolean horizontalProcessing = belt.getBlockState().getValue(SlimeBeltBlock.SLOPE) == com.simibubi.create.content.kinetics.belt.BeltSlope.HORIZONTAL;
		float spacing = 1;
		Level world = belt.getLevel();
		boolean onClient = world.isClientSide && !belt.isVirtual();
		Ending ending = Ending.UNRESOLVED;

		while (iterator.hasNext()) {
			TransportedItemStack currentItem = iterator.next();
			currentItem.prevBeltPosition = currentItem.beltPosition;
			currentItem.prevSideOffset = currentItem.sideOffset;

			if (currentItem.stack.isEmpty()) {
				iterator.remove();
				currentItem = null;
				continue;
			}

			float movement = beltSpeed;
			if (onClient)
				movement *= ServerSpeedProvider.get();

			// Don't move if held by processing (client)
			if (world.isClientSide && currentItem.locked)
				continue;

			// Don't move if held by external components
			if (currentItem.lockedExternally) {
				currentItem.lockedExternally = false;
				continue;
			}

			boolean noMovement = false;
			float currentPos = currentItem.beltPosition;
			float referencePos = 0;
			if (firstItem && seamReference != null) {
				referencePos = seamReference.beltPosition + (beltMovementPositive ? loopLength : -loopLength);
			} else if (stackInFront != null) {
				referencePos = stackInFront.beltPosition;
			}

			if (!firstItem && stackInFront != null || firstItem && seamReference != null) {
				float diff = referencePos - currentPos;
				if (Math.abs(diff) <= spacing)
					noMovement = true;
				movement =
					beltMovementPositive ? Math.min(movement, diff - spacing) : Math.max(movement, diff + spacing);
			}

			boolean onBackTrack = SlimeBeltHelper.isBackTrack(belt, currentPos);
			boolean crossingOutput = !onBackTrack && (beltMovementPositive ? currentPos + movement >= frontRunLength : currentPos + movement < 0);

			float limitedMovement = movement;
			if (crossingOutput) {
				if (ending == Ending.UNRESOLVED)
					ending = resolveEnding();

				if (ending != Ending.WRAP) {
					float outputBoundary = beltMovementPositive ? frontRunLength - currentPos : -currentPos;
					outputBoundary += beltMovementPositive ? -ending.margin : ending.margin;
					limitedMovement = beltMovementPositive ? Math.min(limitedMovement, outputBoundary)
						: Math.max(limitedMovement, outputBoundary);
				}
			}

			float nextLoopPos = currentItem.beltPosition + limitedMovement;
			float nextFrontOffset = SlimeBeltHelper.getFrontOffsetForLoopPosition(belt, nextLoopPos);

			if (!onClient && horizontalProcessing && !onBackTrack && !crossingOutput) {
				ItemStack item = currentItem.stack;
				if (handleBeltProcessingAndCheckIfRemoved(currentItem, nextFrontOffset, noMovement)) {
					iterator.remove();
					belt.notifyUpdate();
					continue;
				}
				if (item != currentItem.stack)
					belt.notifyUpdate();
				if (currentItem.locked)
					continue;
			}

			if (noMovement)
				continue;

			currentItem.beltPosition += limitedMovement;
			float diffToMiddle = currentItem.getTargetSideOffset() - currentItem.sideOffset;
			currentItem.sideOffset += Mth.clamp(diffToMiddle * Math.abs(limitedMovement) * 6f, -Math.abs(diffToMiddle),
				Math.abs(diffToMiddle));

			if (currentItem.beltPosition >= loopLength || currentItem.beltPosition < 0)
				currentItem.beltPosition = SlimeBeltHelper.normalizeLoopPosition(belt, currentItem.beltPosition);

			if (!onClient && crossingOutput && limitedMovement != movement && ending == Ending.INSERT) {
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
							iterator.remove();
						}
						belt.notifyUpdate();
					}
				}
			}

			stackInFront = currentItem;
			firstItem = false;
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

		DirectBeltInputBehaviour inputBehaviour =
			BlockEntityBehaviour.get(world, nextPosition, DirectBeltInputBehaviour.TYPE);
		if (inputBehaviour != null)
			return Ending.INSERT;

		if (BlockHelper.hasBlockSolidSide(world.getBlockState(nextPosition), world, nextPosition,
			belt.getMovementFacing()
				.getOpposite()))
			return Ending.BLOCKED;

		return Ending.WRAP;
	}

	public boolean canInsertAt(int segment) {
		return canInsertAtFromSide(segment, Direction.UP);
	}

	public boolean canInsertAtFromSide(int segment, Direction side) {
		float insertPos = getInsertionPosition(segment, side);
		for (TransportedItemStack stack : items)
			if (isBlocking(insertPos, stack))
				return false;
		for (TransportedItemStack stack : toInsert)
			if (isBlocking(insertPos, stack))
				return false;
		return true;
	}

	public float getInsertionPosition(int segment, Direction side) {
		float halfSegment = segment + .5f;
		float offset = beltMovementPositive ? -1 / 16f : 1 / 16f;
		Track track = SlimeBeltHelper.resolveInputTrack(belt.getBlockState(), side);
		float position = track == Track.FRONT ? halfSegment + offset
			: SlimeBeltHelper.getLoopLength(belt) - halfSegment + offset;
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

	private boolean isBlocking(float insertPos, TransportedItemStack stack) {
		float loopLength = SlimeBeltHelper.getLoopLength(belt);
		float currentPos = SlimeBeltHelper.normalizeLoopPosition(belt, stack.beltPosition);
		float distanceAhead = beltMovementPositive ? currentPos - insertPos : insertPos - currentPos;
		if (distanceAhead < 0)
			distanceAhead += loopLength;
		return distanceAhead <= 1f;
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

