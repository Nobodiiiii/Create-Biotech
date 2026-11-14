package com.nobodiiiii.createbiotech.content.slimebelt.transport;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.content.beltsurface.FunnelInteractionCore;
import com.nobodiiiii.createbiotech.content.beltsurface.TransportedItemStackSync;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltBlock;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltBlockEntity;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltInsertionPlanner.InsertionPlan;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltLoopGeometry;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltLoopGeometry.LoopSection;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltLoopGeometry.Track;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltNeighbor;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.ServerSpeedProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SlimeBeltInventory {

	public final SlimeBeltBlockEntity belt;
	final List<TransportedItemStack> items;
	final List<TransportedItemStack> toInsert;
	final List<TransportedItemStack> toRemove;
	boolean beltMovementPositive;
	/** Loop-boundary entry offset; see {@link SlimeBeltLoopGeometry#WRAP_ENTRY_OFFSET}. */
	private static final float WRAP_ENTRY_OFFSET = SlimeBeltLoopGeometry.WRAP_ENTRY_OFFSET;
	/** Items waiting for a busy turn or track entry hold this far before the seam. */
	private static final float TRANSFER_WAIT_MARGIN = .25f;
	/** Float tolerance when deciding whether an item has actually reached a seam. */
	private static final float SEAM_EPSILON = 1 / 1024f;
	/** Maximum distance between the loop surface and a corner-transfer contact point. */
	private static final float SIDE_TRANSFER_DISTANCE = 3 / 16f;
	static final double SIDE_TRANSFER_DISTANCE_SQR = SIDE_TRANSFER_DISTANCE * SIDE_TRANSFER_DISTANCE;
	/** Minimum gap between two items on the same track or turn, in belt segments. */
	private static final float SPACING = 1;

	TransportedItemStack lazyClientItem;
	private final TransportedItemStackSync itemSync;

	/**
	 * {@code insertSide} is the side handed to the target's input behaviour (from
	 * {@link SlimeBeltNeighbor#handoffInsertSide}); the corner side that resolved the
	 * geometry is already folded into the contact fields.
	 */
	record SideTransferCandidate(BlockPos targetPos, Direction insertSide,
		float contactProgress, float contactParam, double distanceSqr) {
	}

	public SlimeBeltInventory(SlimeBeltBlockEntity be) {
		this.belt = be;
		items = new LinkedList<>();
		toInsert = new LinkedList<>();
		toRemove = new LinkedList<>();
		itemSync = new TransportedItemStackSync();
	}

	public void tick() {

		// Residual item for "smooth" transitions
		if (lazyClientItem != null) {
			if (lazyClientItem.locked)
				lazyClientItem = null;
			else
				lazyClientItem.locked = true;
		}

		boolean movementDirectionChanged = SlimeBeltInsertion.refreshMovementDirection(this);

		// Added/Removed items from previous cycle
		if (flushPendingChanges()) {
			belt.notifyUpdate();
		}

		if (belt.getSpeed() == 0)
			return;

		if (movementDirectionChanged)
			belt.notifyUpdate();

		float trackSpeed = Math.abs(belt.getDirectionAwareBeltMovementSpeed());
		boolean horizontalProcessing = belt.getBlockState().getValue(SlimeBeltBlock.SLOPE) == BeltSlope.HORIZONTAL;
		Level world = belt.getLevel();
		boolean onClient = world.isClientSide && !belt.isVirtual();

		new AdvancePass(trackSpeed, onClient, world, horizontalProcessing).run();
	}

	/**
	 * One pass over every transported item in motion order: sections visited FRONT →
	 * exit turn → BACK → entry turn, items within a section frontmost-first. Spacing
	 * chains reset at section boundaries — the turns act as single-occupancy airlocks
	 * between the tracks (see the seam gates), so gap spacing never spans a seam.
	 *
	 * <p>Two gate families limit movement. Track exits resolve an {@link Ending} against
	 * the world (INSERT into a neighbour, BLOCKED by it, or WRAP through the turn); turn
	 * entries and exits require the destination to be clear, else the item waits
	 * {@link #TRANSFER_WAIT_MARGIN} before the seam. Seam clamps may move an item
	 * backwards (e.g. a margin upgrade from INSERT to BLOCKED); spacing clamps never do
	 * — blocked followers simply hold position.
	 */
	private final class AdvancePass {

		private final SlimeBeltLoopGeometry.MotionFrame frame;
		private final float trackSpeed;
		private final boolean onClient;
		private final Level world;
		private final boolean horizontalProcessing;
		private final Ending[] endings = new Ending[] { Ending.UNRESOLVED, Ending.UNRESOLVED };

		private TransportedItemStack stackInFront;

		private AdvancePass(float trackSpeed, boolean onClient, Level world, boolean horizontalProcessing) {
			this.frame = frame();
			this.trackSpeed = trackSpeed;
			this.onClient = onClient;
			this.world = world;
			this.horizontalProcessing = horizontalProcessing;
		}

		private record OrderedItem(TransportedItemStack item, LoopSection section, int sectionRank, float progress) {
		}

		private void run() {
			LoopSection lastSection = null;
			for (OrderedItem entry : snapshotInMotionOrder()) {
				TransportedItemStack currentItem = entry.item();
				LoopSection section = entry.section();
				if (section != lastSection) {
					stackInFront = null;
					lastSection = section;
				}

				currentItem.prevBeltPosition = currentItem.beltPosition;
				currentItem.prevSideOffset = currentItem.sideOffset;

				if (currentItem.stack.isEmpty()) {
					items.remove(currentItem);
					continue;
				}

				float movement = trackSpeed;
				if (onClient)
					movement *= ServerSpeedProvider.get();

				if (world.isClientSide && currentItem.locked) {
					stackInFront = currentItem;
					continue;
				}

				if (currentItem.lockedExternally) {
					currentItem.lockedExternally = false;
					stackInFront = currentItem;
					continue;
				}

				if (section == LoopSection.FRONT || section == LoopSection.BACK)
					advanceTrackItem(currentItem, section == LoopSection.FRONT ? Track.FRONT : Track.BACK, movement);
				else
					advanceTurnItem(currentItem, section, movement);
			}
		}

		/** Snapshot classification: items crossing a seam this tick are still visited once. */
		private List<OrderedItem> snapshotInMotionOrder() {
			List<OrderedItem> ordered = new ArrayList<>(items.size());
			for (TransportedItemStack stack : items) {
				LoopSection section = SlimeBeltHelper.getLoopSection(belt, stack.beltPosition);
				ordered.add(new OrderedItem(stack, section, sectionRank(section), progressWithin(section, stack)));
			}
			ordered.sort((first, second) -> first.sectionRank() != second.sectionRank()
				? Integer.compare(first.sectionRank(), second.sectionRank())
				: Float.compare(second.progress(), first.progress()));
			return ordered;
		}

		private int sectionRank(LoopSection section) {
			if (section == LoopSection.FRONT)
				return 0;
			if (section == frame.exitTurn())
				return 1;
			if (section == LoopSection.BACK)
				return 2;
			return 3;
		}

		private float progressWithin(LoopSection section, TransportedItemStack stack) {
			if (section == LoopSection.FRONT)
				return frame.trackProgress(Track.FRONT, stack.beltPosition);
			if (section == LoopSection.BACK)
				return frame.trackProgress(Track.BACK, stack.beltPosition);
			return frame.turnProgressInMotion(section, stack.beltPosition);
		}

		private void advanceTrackItem(TransportedItemStack currentItem, Track track, float movement) {
			float currentProgress = frame.trackProgress(track, currentItem.beltPosition);
			boolean noMovement = false;

			if (stackInFront != null) {
				float diff = frame.trackProgress(track, stackInFront.beltPosition) - currentProgress;
				if (Math.abs(diff) <= SPACING)
					noMovement = true;
				movement = Math.min(movement, diff - SPACING);
			}

			LoopSection exitTurn = track == Track.FRONT ? frame.exitTurn() : frame.entryTurn();
			float diffToEnd = belt.beltLength - currentProgress;
			boolean approachingSeam = Math.abs(diffToEnd) < Math.abs(movement) + 1;
			boolean crossingSeam = movement > 0 && currentProgress + movement >= belt.beltLength;
			boolean transferAtSeam = false;
			Ending trackEnding = Ending.WRAP;
			float limitedMovement = movement;
			if (approachingSeam) {
				if (endings[track.ordinal()] == Ending.UNRESOLVED)
					endings[track.ordinal()] = SlimeBeltSideTransfer.resolveEnding(SlimeBeltInventory.this, track);
				trackEnding = endings[track.ordinal()];
				float margin;
				if (trackEnding == Ending.INSERT || trackEnding == Ending.BLOCKED) {
					margin = trackEnding.margin;
				} else {
					transferAtSeam = SlimeBeltInsertion.isTurnClear(SlimeBeltInventory.this, exitTurn, currentItem);
					margin = transferAtSeam ? 0 : TRANSFER_WAIT_MARGIN;
				}
				limitedMovement = Math.min(limitedMovement, diffToEnd - margin);
			}

			float nextProgress = currentProgress + limitedMovement;
			float nextFrontOffset = frame.frontOffsetOfTrackProgress(track, nextProgress);

			if (!onClient && horizontalProcessing && track == Track.FRONT) {
				ItemStack item = currentItem.stack;
				if (SlimeBeltItemProcessing.handleBeltProcessingAndCheckIfRemoved(SlimeBeltInventory.this, currentItem, nextFrontOffset, noMovement)) {
					items.remove(currentItem);
					belt.notifyUpdate();
					return;
				}
				if (item != currentItem.stack)
					belt.notifyUpdate();
				if (currentItem.locked) {
					stackInFront = currentItem;
					return;
				}
			}

			if (FunnelInteractionCore.check(new SlimeBeltSurfaceTickContext(SlimeBeltInventory.this, track),
				currentItem, nextFrontOffset)) {
				stackInFront = currentItem;
				return;
			}

			if (SlimeBeltSideTransfer.tryTransferToAdjacentBelt(SlimeBeltInventory.this, currentItem, track, currentProgress, nextProgress, limitedMovement,
				onClient)) {
				stackInFront = currentItem;
				return;
			}

			if (noMovement) {
				stackInFront = currentItem;
				return;
			}

			if (horizontalProcessing && track == Track.FRONT) {
				if (SlimeBeltTunnelInteractionHandler.flapTunnelsAndCheckIfStuck(SlimeBeltInventory.this,
					currentItem, nextFrontOffset)) {
					stackInFront = currentItem;
					return;
				}
				if (SlimeBeltCrusherInteractionHandler.checkForCrushers(SlimeBeltInventory.this, currentItem,
					nextFrontOffset)) {
					stackInFront = currentItem;
					return;
				}
			}

			setLoopPositionFromTrackProgress(currentItem, track, nextProgress);
			float diffToMiddle = currentItem.getTargetSideOffset() - currentItem.sideOffset;
			currentItem.sideOffset += Mth.clamp(diffToMiddle * Math.abs(limitedMovement) * 6f,
				-Math.abs(diffToMiddle), Math.abs(diffToMiddle));

			boolean reachedTransferSeam = transferAtSeam && crossingSeam
				&& currentProgress + limitedMovement >= belt.beltLength - SEAM_EPSILON;

			if (reachedTransferSeam) {
				float overshoot = Math.max(0, currentProgress + limitedMovement - belt.beltLength);
				moveIntoTurn(currentItem, exitTurn, overshoot);
				// The item left this section; followers keep spacing against the one before it.
				return;
			}

			if (!onClient && trackEnding == Ending.INSERT && approachingSeam && limitedMovement != movement) {
				Direction insertSide = SlimeBeltHelper.getMovementFacingForTrack(belt, track);
				DirectBeltInputBehaviour inputBehaviour =
					BlockEntityBehaviour.get(world, SlimeBeltSideTransfer.getTrackOutputPosition(SlimeBeltInventory.this, track), DirectBeltInputBehaviour.TYPE);
				if (inputBehaviour != null && inputBehaviour.canInsertFromSide(insertSide)) {
					ItemStack remainder = inputBehaviour.handleInsertion(currentItem, insertSide, false);
					if (!ItemStack.matches(remainder, currentItem.stack)) {
						currentItem.stack = remainder;
						if (remainder.isEmpty()) {
							lazyClientItem = currentItem;
							lazyClientItem.locked = false;
							items.remove(currentItem);
						}
						belt.notifyUpdate();
						stackInFront = currentItem;
						return;
					}
				}
			}

			stackInFront = currentItem;
		}

		private void advanceTurnItem(TransportedItemStack currentItem, LoopSection turn, float movement) {
			float connectorLength = getConnectorLength();
			Track targetTrack = turn == frame.exitTurn() ? Track.BACK : Track.FRONT;
			float currentProgress = frame.turnProgressInMotion(turn, currentItem.beltPosition);
			boolean noMovement = false;

			if (stackInFront != null) {
				float diff = frame.turnProgressInMotion(turn, stackInFront.beltPosition) - currentProgress;
				if (Math.abs(diff) <= SPACING)
					noMovement = true;
				movement = Math.min(movement, diff - SPACING);
			}

			float diffToEnd = connectorLength - currentProgress;
			boolean crossingConnectorEnd = movement > 0 && currentProgress + movement >= connectorLength;
			boolean targetEntryClear = isTrackEntryClear(targetTrack, currentItem);
			if (Math.abs(diffToEnd) < Math.abs(movement) + 1 && !targetEntryClear)
				movement = Math.min(movement, diffToEnd - TRANSFER_WAIT_MARGIN);

			if (noMovement) {
				stackInFront = currentItem;
				return;
			}

			float nextProgress = currentProgress + movement;
			currentItem.beltPosition = frame.loopPositionOfTurnProgress(turn, nextProgress);
			float diffToMiddle = currentItem.getTargetSideOffset() - currentItem.sideOffset;
			currentItem.sideOffset += Mth.clamp(diffToMiddle * Math.abs(movement) * 6f, -Math.abs(diffToMiddle),
				Math.abs(diffToMiddle));

			if (targetEntryClear && crossingConnectorEnd
				&& currentProgress + movement >= connectorLength - SEAM_EPSILON) {
				float overshoot = Math.max(0, currentProgress + movement - connectorLength);
				moveOntoTrack(currentItem, targetTrack, overshoot);
				return;
			}

			stackInFront = currentItem;
		}

		private boolean isTrackEntryClear(Track targetTrack, TransportedItemStack currentItem) {
			float entryPosition = frame.loopPositionOfTrackProgress(targetTrack, WRAP_ENTRY_OFFSET);
			for (TransportedItemStack stack : items)
				if (stack != currentItem && !toRemove.contains(stack) && SlimeBeltInsertion.isBlocking(SlimeBeltInventory.this, targetTrack, entryPosition, stack))
					return false;
			for (TransportedItemStack stack : toInsert)
				if (SlimeBeltInsertion.isBlocking(SlimeBeltInventory.this, targetTrack, entryPosition, stack))
					return false;
			return true;
		}

		private void moveIntoTurn(TransportedItemStack currentItem, LoopSection turn, float overshoot) {
			currentItem.prevBeltPosition = currentItem.beltPosition;
			float turnProgress = Mth.clamp(Math.max(WRAP_ENTRY_OFFSET, overshoot), WRAP_ENTRY_OFFSET,
				Math.max(WRAP_ENTRY_OFFSET, getConnectorLength() - SEAM_EPSILON));
			currentItem.beltPosition = frame.loopPositionOfTurnProgress(turn, turnProgress);
		}

		private void moveOntoTrack(TransportedItemStack currentItem, Track targetTrack, float overshoot) {
			currentItem.prevBeltPosition = currentItem.beltPosition;
			currentItem.beltPosition = frame.loopPositionOfTrackProgress(targetTrack, WRAP_ENTRY_OFFSET + overshoot);
		}
	}

	protected boolean handleBeltProcessingAndCheckIfRemoved(TransportedItemStack currentItem, float nextOffset,
															boolean noMovement) {
		return SlimeBeltItemProcessing.handleBeltProcessingAndCheckIfRemoved(this, currentItem, nextOffset, noMovement);
	}

	protected BeltProcessingBehaviour getBeltProcessingAtSegment(int segment) {
		return SlimeBeltItemProcessing.getBeltProcessingAtSegment(this, segment);
	}

	protected TransportedItemStackHandlerBehaviour getTransportedItemStackHandlerAtSegment(int segment) {
		return SlimeBeltItemProcessing.getTransportedItemStackHandlerAtSegment(this, segment);
	}

	/** How the world past a track exit treats arriving items; margin = stop distance before the seam. */
	enum Ending {
		UNRESOLVED(0), WRAP(0), INSERT(.25f), BLOCKED(.45f);

		private float margin;

		Ending(float f) {
			this.margin = f;
		}
	}

	public boolean canInsertAt(int segment) {
		return SlimeBeltInsertion.canInsertAt(this, segment);
	}

	public boolean canInsertAtFromSide(int segment, Direction side) {
		return SlimeBeltInsertion.canInsertAtFromSide(this, segment, side);
	}

	public boolean canInsertAtFromSide(int segment, Direction side, boolean verticalHorizontalBeltInput) {
		return SlimeBeltInsertion.canInsertAtFromSide(this, segment, side, verticalHorizontalBeltInput);
	}

	/**
	 * Resolve where an insert from the given side would land. The stack, when available,
	 * only decides belt-to-belt continuation biases; occupancy gating stays
	 * stack-independent so occupancy queries and real inserts agree.
	 */
	@Nullable
	public InsertionPlan planInsertion(int segment, Direction side, boolean verticalHorizontalBeltInput,
		@Nullable TransportedItemStack transported) {
		return SlimeBeltInsertion.planInsertion(this, segment, side, verticalHorizontalBeltInput, transported);
	}

	/** Occupancy gate for a resolved plan; probes the same landing the item would take. */
	public boolean canInsert(@Nullable InsertionPlan plan) {
		return SlimeBeltInsertion.canInsert(this, plan);
	}

	public boolean canInsertAtOnTrack(int segment, Track track) {
		return SlimeBeltInsertion.canInsertAtOnTrack(this, segment, track);
	}

	public void prepareInsertedItem(TransportedItemStack transported, int segment, Direction side) {
		SlimeBeltInsertion.prepareInsertedItem(this, transported, segment, side);
	}

	public void prepareInsertedItem(TransportedItemStack transported, int segment, Direction side,
		boolean verticalHorizontalBeltInput) {
		SlimeBeltInsertion.prepareInsertedItem(this, transported, segment, side, verticalHorizontalBeltInput);
	}

	public void prepareInsertedItemOnTrack(TransportedItemStack transported, int segment, Track track) {
		SlimeBeltInsertion.prepareInsertedItemOnTrack(this, transported, segment, track);
	}

	/** Land a stack according to a plan; gate and landing consume the same resolution. */
	public void applyInsertion(TransportedItemStack transported, @Nullable InsertionPlan plan) {
		SlimeBeltInsertion.applyInsertion(this, transported, plan);
	}

	public void addItem(TransportedItemStack newStack) {
		SlimeBeltInsertion.addItem(this, newStack);
	}

	private void insert(TransportedItemStack newStack) {
		SlimeBeltInsertion.insert(this, newStack);
	}

	/**
	 * Direction-aware coordinate view for the current tick. All loop↔track/turn
	 * conversions below delegate here; the frame is the single home of the
	 * {@code beltMovementPositive}/track branching.
	 */
	SlimeBeltLoopGeometry.MotionFrame frame() {
		return belt.getLoop()
			.motion(beltMovementPositive);
	}

	float getTrackProgress(Track track, float loopPosition) {
		return frame().trackProgress(track, loopPosition);
	}

	float getLoopPositionForTrackProgress(Track track, float progress) {
		return frame().loopPositionOfTrackProgress(track, progress);
	}

	float getFrontOffsetForTrackProgress(Track track, float progress) {
		return frame().frontOffsetOfTrackProgress(track, progress);
	}

	float getTrackProgressForFrontOffset(Track track, float frontOffset) {
		return frame().trackProgressOfFrontOffset(track, frontOffset);
	}

	void setLoopPositionFromTrackProgress(TransportedItemStack currentItem, Track track, float progress) {
		currentItem.beltPosition = getLoopPositionForTrackProgress(track, progress);
	}

	Track getTrackForLoopPosition(float loopPosition) {
		LoopSection section = SlimeBeltHelper.getLoopSection(belt, loopPosition);
		if (section == LoopSection.FRONT)
			return Track.FRONT;
		if (section == LoopSection.BACK)
			return Track.BACK;
		return null;
	}

	private float getConnectorLength() {
		return SlimeBeltHelper.getConnectorLength(belt);
	}

	public TransportedItemStack getStackAtOffset(int offset, Direction side) {
		Track track = SlimeBeltHelper.resolveIOTrack(belt, offset, side);
		if (track == null)
			return null;
		float min = offset;
		float max = offset + 1;
		for (TransportedItemStack stack : items) {
			if (toRemove.contains(stack))
				continue;
			if (getTrackForLoopPosition(stack.beltPosition) != track)
				continue;
			float frontOffset = SlimeBeltHelper.getFrontOffsetForLoopPosition(belt, stack.beltPosition);
			if (frontOffset > max)
				continue;
			if (frontOffset > min)
				return stack;
		}
		return null;
	}

	public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
		toInsert.clear();
		toRemove.clear();
		float predictionTolerance = Math.max(.125f, Math.abs(belt.getDirectionAwareBeltMovementSpeed()) * 1.5f);
		lazyClientItem = itemSync.read(nbt, registries, items, lazyClientItem, clientPacket,
			(first, second) -> (float) belt.getLoop().worldPos(first).distanceTo(belt.getLoop().worldPos(second)),
			predictionTolerance);
		beltMovementPositive = nbt.getBoolean("PositiveOrder");
	}

	public CompoundTag write(HolderLookup.Provider registries) {
		// sendData() can run immediately after addItem(), before the next inventory tick.
		// Serialize one coherent snapshot so the client never replaces its live list with
		// the stale pre-insertion state. This mirrors Create's BeltInventory#write.
		SlimeBeltInsertion.refreshMovementDirection(this);
		flushPendingChanges();
		CompoundTag nbt = new CompoundTag();
		itemSync.write(nbt, registries, items, lazyClientItem);
		nbt.putBoolean("PositiveOrder", beltMovementPositive);
		return nbt;
	}

	private boolean flushPendingChanges() {
		if (toInsert.isEmpty() && toRemove.isEmpty())
			return false;
		toInsert.forEach(this::insert);
		toInsert.clear();
		items.removeAll(toRemove);
		toRemove.clear();
		return true;
	}

	public void eject(TransportedItemStack stack) {
		ItemStack ejected = stack.stack;
		Vec3 outPos = SlimeBeltHelper.getVectorForOffset(belt, stack.beltPosition);
		float movementSpeed = Math.max(Math.abs(belt.getBeltMovementSpeed()), 1 / 8f);
		float tangentStep = beltMovementPositive ? .001f : -.001f;
		Vec3 tangent = belt.getLoop().worldPos(stack.beltPosition + tangentStep)
			.subtract(outPos);
		if (tangent.lengthSqr() < 1.0E-8d)
			tangent = Vec3.atLowerCornerOf(belt.getBeltChainDirection());
		else
			tangent = tangent.normalize();
		Vec3 outMotion = tangent.scale(movementSpeed)
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
		applyToEachWithin(position, maxDistanceToPosition, Track.FRONT, processFunction);
	}

	public void applyToEachWithin(float position, float maxDistanceToPosition, Track track,
								  Function<TransportedItemStack, TransportedResult> processFunction) {
		boolean dirty = false;
		for (TransportedItemStack transported : items) {
			if (toRemove.contains(transported))
				continue;
			if (getTrackForLoopPosition(transported.beltPosition) != track)
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
