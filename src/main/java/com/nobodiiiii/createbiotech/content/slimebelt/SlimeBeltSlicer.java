package com.nobodiiiii.createbiotech.content.slimebelt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class SlimeBeltSlicer {

	private static final float PROJECTION_COARSE_STEP = 1 / 8f;
	private static final float PROJECTION_FINE_STEP = 1 / 128f;
	private static final float PROJECTION_FINE_RADIUS = 1 / 8f;
	private static final float POSITION_EPSILON = 1 / 256f;

	public static class Feedback {}

	private record TransportedSnapshot(TransportedItemStack transported, Vec3 worldPosition) {}

	private record Projection(float loopPosition, double distanceSqr) {}

	private record Placement(SlimeBeltBlockEntity controller, TransportedItemStack transported, float loopPosition,
		double distanceSqr) {}

	private SlimeBeltSlicer() {}

	public static InteractionResult useWrench(BlockState state, Level world, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit, Feedback feedback) {
		SlimeBeltBlockEntity controllerBE = SlimeBeltHelper.getControllerBE(world, pos);
		if (controllerBE == null)
			return InteractionResult.PASS;
		if (state.getValue(SlimeBeltBlock.PART) == BeltPart.PULLEY && hit.getDirection().getAxis() != Axis.Y)
			return InteractionResult.PASS;

		int beltLength = controllerBE.beltLength;
		if (beltLength == 2)
			return InteractionResult.FAIL;

		BlockPos beltVector = BlockPos.containing(SlimeBeltHelper.getBeltVector(state));
		BeltPart part = state.getValue(SlimeBeltBlock.PART);
		List<BlockPos> beltChain = SlimeBeltBlock.getBeltChain(world, controllerBE.getBlockPos());
		boolean creative = player != null && player.isCreative();

		if (hoveringEnd(state, hit)) {
			if (world.isClientSide)
				return InteractionResult.SUCCESS;

			List<TransportedSnapshot> snapshots = captureSnapshots(controllerBE);
			resetChain(world, beltChain);

			BlockPos next = part == BeltPart.END ? pos.subtract(beltVector) : pos.offset(beltVector);
			BlockState nextState = world.getBlockState(next);
			if (!nextState.is(CBBlocks.SLIME_BELT.get()))
				return InteractionResult.FAIL;

			KineticBlockEntity.switchToBlockState(world, next, nextState.setValue(SlimeBeltBlock.PART, part));
			world.setBlock(pos, ProperWaterloggedBlock.withWater(world, Blocks.AIR.defaultBlockState(), pos),
				Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
			world.removeBlockEntity(pos);
			world.levelEvent(2001, pos, Block.getId(state));
			if (!creative && nextState.getValue(SlimeBeltBlock.PART) == BeltPart.PULLEY && player != null)
				player.getInventory().placeItemBackInInventory(com.simibubi.create.AllBlocks.SHAFT.asStack());
			SlimeBeltBlock.initBelt(world, next);
			restoreSnapshots(world, snapshots, next);
			return InteractionResult.SUCCESS;
		}

		SlimeBeltBlockEntity segmentBE = SlimeBeltHelper.getSegmentBE(world, pos);
		if (segmentBE == null)
			return InteractionResult.PASS;

		int hitSegment = segmentBE.index;
		Vec3 centerOf = VecHelper.getCenterOf(hit.getBlockPos());
		boolean towardPositive = hit.getLocation().subtract(centerOf).dot(Vec3.atLowerCornerOf(beltVector)) > 0;
		BlockPos next = !towardPositive ? pos.subtract(beltVector) : pos.offset(beltVector);

		if (hitSegment == 0 || hitSegment == 1 && !towardPositive)
			return InteractionResult.FAIL;
		if (hitSegment == beltLength - 1 || hitSegment == beltLength - 2 && towardPositive)
			return InteractionResult.FAIL;

		if (!creative) {
			int requiredShafts = 0;
			if (!segmentBE.hasPulley())
				requiredShafts++;
			BlockState other = world.getBlockState(next);
			if (other.is(CBBlocks.SLIME_BELT.get()) && other.getValue(SlimeBeltBlock.PART) == BeltPart.MIDDLE)
				requiredShafts++;
			if (!hasSplitMaterials(player, requiredShafts))
				return InteractionResult.FAIL;
		}
		if (world.isClientSide)
			return InteractionResult.SUCCESS;

		if (!creative)
			consumeSplitMaterials(player, segmentBE.hasPulley() ? 0 : 1,
				world.getBlockState(next).is(CBBlocks.SLIME_BELT.get())
					&& world.getBlockState(next).getValue(SlimeBeltBlock.PART) == BeltPart.MIDDLE ? 1 : 0);

		List<TransportedSnapshot> snapshots = captureSnapshots(controllerBE);
		resetChain(world, beltChain);
		KineticBlockEntity.switchToBlockState(world, pos,
			state.setValue(SlimeBeltBlock.PART, towardPositive ? BeltPart.END : BeltPart.START));
		KineticBlockEntity.switchToBlockState(world, next, world.getBlockState(next)
			.setValue(SlimeBeltBlock.PART, towardPositive ? BeltPart.START : BeltPart.END));
		world.playSound(null, pos, SoundEvents.WOOL_HIT,
			player == null ? SoundSource.BLOCKS : SoundSource.PLAYERS, 0.5F, 2.3F);
		SlimeBeltBlock.initBelt(world, pos);
		SlimeBeltBlock.initBelt(world, next);
		restoreSnapshots(world, snapshots, pos, next);
		return InteractionResult.SUCCESS;
	}

	public static InteractionResult useConnector(BlockState state, Level world, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit, Feedback feedback) {
		SlimeBeltBlockEntity controllerBE = SlimeBeltHelper.getControllerBE(world, pos);
		if (controllerBE == null)
			return InteractionResult.PASS;

		int beltLength = controllerBE.beltLength;
		if (beltLength == SlimeBeltConnectorItem.maxLength())
			return InteractionResult.FAIL;

		BlockPos beltVector = BlockPos.containing(SlimeBeltHelper.getBeltVector(state));
		BeltPart part = state.getValue(SlimeBeltBlock.PART);
		Direction facing = state.getValue(SlimeBeltBlock.HORIZONTAL_FACING);
		List<BlockPos> beltChain = SlimeBeltBlock.getBeltChain(world, controllerBE.getBlockPos());
		boolean creative = player != null && player.isCreative();

		if (!hoveringEnd(state, hit))
			return InteractionResult.PASS;

		BlockPos next = part == BeltPart.START ? pos.subtract(beltVector) : pos.offset(beltVector);
		SlimeBeltBlockEntity mergedController = null;
		List<BlockPos> mergedChain = List.of();
		boolean flipMergedBelt = false;
		BlockState nextState = world.getBlockState(next);

		if (!nextState.canBeReplaced()) {
			if (!nextState.is(CBBlocks.SLIME_BELT.get()))
				return InteractionResult.FAIL;
			if (!beltStatesCompatible(state, nextState))
				return InteractionResult.FAIL;

			mergedController = SlimeBeltHelper.getControllerBE(world, next);
			if (mergedController == null || mergedController.getController().equals(controllerBE.getController()))
				return InteractionResult.FAIL;
			if (mergedController.beltLength + beltLength > SlimeBeltConnectorItem.maxLength())
				return InteractionResult.FAIL;

			mergedChain = SlimeBeltBlock.getBeltChain(world, mergedController.getBlockPos());
			flipMergedBelt = facing != nextState.getValue(SlimeBeltBlock.HORIZONTAL_FACING);
		}

		if (world.isClientSide)
			return InteractionResult.SUCCESS;

		List<TransportedSnapshot> snapshots = captureSnapshots(controllerBE, mergedController);
		if (flipMergedBelt)
			for (BlockPos blockPos : mergedChain) {
				BlockState mergedState = world.getBlockState(blockPos);
				if (mergedState.is(CBBlocks.SLIME_BELT.get()))
					world.setBlock(blockPos, flipBelt(mergedState), Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
			}

		resetChain(world, beltChain);
		if (mergedController != null)
			resetChain(world, mergedChain);
		KineticBlockEntity.switchToBlockState(world, pos, state.setValue(SlimeBeltBlock.PART, BeltPart.MIDDLE));

		if (mergedController == null) {
			world.setBlock(next, ProperWaterloggedBlock.withWater(world, state, next),
				Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
			world.playSound(null, pos, SoundEvents.WOOL_PLACE,
				player == null ? SoundSource.BLOCKS : SoundSource.PLAYERS, 0.5F, 1F);
		} else {
			world.playSound(null, pos, SoundEvents.WOOL_HIT,
				player == null ? SoundSource.BLOCKS : SoundSource.PLAYERS, 0.5F, 1.3F);
			KineticBlockEntity.switchToBlockState(world, next, state.setValue(SlimeBeltBlock.PART, BeltPart.MIDDLE));
			if (!creative && player != null) {
				player.getInventory().placeItemBackInInventory(com.simibubi.create.AllBlocks.SHAFT.asStack(2));
				player.getInventory().placeItemBackInInventory(new ItemStack(CBItems.SLIME_BELT_CONNECTOR.get()));
			}
		}

		SlimeBeltBlock.initBelt(world, pos);
		SlimeBeltBlock.initBelt(world, next);
		restoreSnapshots(world, snapshots, pos, next);
		return InteractionResult.SUCCESS;
	}

	private static void resetChain(Level world, List<BlockPos> beltChain) {
		for (BlockPos chainPos : beltChain) {
			SlimeBeltBlockEntity belt = SlimeBeltHelper.getSegmentBE(world, chainPos);
			if (belt == null)
				continue;
			belt.detachKinetics();
			belt.invalidateItemHandlers();
			belt.inventory = null;
			belt.passengers = null;
			belt.controller = null;
			belt.beltLength = 0;
			belt.index = 0;
		}
	}

	private static List<TransportedSnapshot> captureSnapshots(SlimeBeltBlockEntity... candidateControllers) {
		Map<BlockPos, SlimeBeltBlockEntity> uniqueControllers = new LinkedHashMap<>();
		for (SlimeBeltBlockEntity candidate : candidateControllers) {
			if (candidate == null)
				continue;
			SlimeBeltBlockEntity controller = candidate.isController() ? candidate : candidate.getControllerBE();
			if (controller != null)
				uniqueControllers.putIfAbsent(controller.getController(), controller);
		}

		List<TransportedSnapshot> snapshots = new ArrayList<>();
		for (SlimeBeltBlockEntity controller : uniqueControllers.values())
			for (TransportedItemStack transported : new ArrayList<>(controller.getInventory().getTransportedItems()))
				snapshots.add(new TransportedSnapshot(transported.copy(),
					SlimeBeltHelper.getVectorForOffset(controller, transported.beltPosition)));
		return snapshots;
	}

	private static void restoreSnapshots(Level world, List<TransportedSnapshot> snapshots, BlockPos... anchors) {
		if (snapshots.isEmpty())
			return;

		List<SlimeBeltBlockEntity> controllers = resolveControllers(world, anchors);
		if (controllers.isEmpty()) {
			snapshots.forEach(snapshot -> spawnSnapshot(world, snapshot));
			return;
		}

		Map<BlockPos, List<Placement>> groupedPlacements = new LinkedHashMap<>();
		for (TransportedSnapshot snapshot : snapshots) {
			Placement bestPlacement = null;
			for (SlimeBeltBlockEntity controller : controllers) {
				Projection projection = projectOntoLoop(controller, snapshot.worldPosition());
				if (bestPlacement == null || projection.distanceSqr() < bestPlacement.distanceSqr())
					bestPlacement = new Placement(controller, snapshot.transported().copy(), projection.loopPosition(),
						projection.distanceSqr());
			}

			if (bestPlacement != null)
				groupedPlacements.computeIfAbsent(bestPlacement.controller().getController(), ignored -> new ArrayList<>())
					.add(bestPlacement);
		}

		for (List<Placement> placements : groupedPlacements.values()) {
			placements.sort(Comparator.comparingDouble(Placement::loopPosition));
			float previousPosition = Float.NEGATIVE_INFINITY;

			for (Placement placement : placements) {
				SlimeBeltBlockEntity controller = placement.controller();
				TransportedItemStack transported = placement.transported();
				float loopPosition = placement.loopPosition();
				if (previousPosition != Float.NEGATIVE_INFINITY && Math.abs(loopPosition - previousPosition) < POSITION_EPSILON)
					loopPosition = previousPosition + POSITION_EPSILON;
				loopPosition = SlimeBeltHelper.normalizeLoopPosition(controller, loopPosition);

				transported.beltPosition = loopPosition;
				transported.prevBeltPosition = loopPosition;
				transported.prevSideOffset = transported.sideOffset;
				updateInsertionData(controller, transported);

				controller.getInventory().addItem(transported);
				controller.setChanged();
				controller.sendData();
				previousPosition = loopPosition;
			}
		}
	}

	private static List<SlimeBeltBlockEntity> resolveControllers(Level world, BlockPos... anchors) {
		Map<BlockPos, SlimeBeltBlockEntity> uniqueControllers = new LinkedHashMap<>();
		for (BlockPos anchor : anchors) {
			if (anchor == null)
				continue;
			SlimeBeltBlockEntity segment = SlimeBeltHelper.getSegmentBE(world, anchor);
			if (segment == null)
				continue;
			SlimeBeltBlockEntity controller = segment.getControllerBE();
			if (controller != null)
				uniqueControllers.putIfAbsent(controller.getController(), controller);
		}
		return new ArrayList<>(uniqueControllers.values());
	}

	private static Projection projectOntoLoop(SlimeBeltBlockEntity controller, Vec3 worldPosition) {
		float loopLength = SlimeBeltHelper.getLoopLength(controller);
		if (loopLength <= 0)
			return new Projection(0, Double.MAX_VALUE);

		Projection best = new Projection(0, Double.MAX_VALUE);
		best = sampleProjection(controller, worldPosition, 0, loopLength, PROJECTION_COARSE_STEP, best);
		return sampleProjection(controller, worldPosition, best.loopPosition() - PROJECTION_FINE_RADIUS,
			best.loopPosition() + PROJECTION_FINE_RADIUS, PROJECTION_FINE_STEP, best);
	}

	private static Projection sampleProjection(SlimeBeltBlockEntity controller, Vec3 worldPosition, float start,
		float end, float step, Projection initialBest) {
		Projection best = initialBest;
		for (float sample = start; sample <= end + step / 2f; sample += step) {
			float loopPosition = SlimeBeltHelper.normalizeLoopPosition(controller, sample);
			double distanceSqr = SlimeBeltHelper.getVectorForOffset(controller, loopPosition).distanceToSqr(worldPosition);
			if (distanceSqr < best.distanceSqr())
				best = new Projection(loopPosition, distanceSqr);
		}
		return best;
	}

	private static void updateInsertionData(SlimeBeltBlockEntity controller, TransportedItemStack transported) {
		float normalized = SlimeBeltHelper.normalizeLoopPosition(controller, transported.beltPosition);
		float frontOffset = SlimeBeltHelper.getFrontOffsetForLoopPosition(controller, normalized);
		int segment = Mth.clamp(Mth.floor(frontOffset), 0, controller.beltLength - 1);
		SlimeBeltHelper.LoopSection section = SlimeBeltHelper.getLoopSection(controller, normalized);
		SlimeBeltHelper.Track track = switch (section) {
			case BACK -> SlimeBeltHelper.Track.BACK;
			case END_TURN -> getConnectorProgress(controller, normalized, section) < .5f
				? SlimeBeltHelper.Track.FRONT : SlimeBeltHelper.Track.BACK;
			case START_TURN -> getConnectorProgress(controller, normalized, section) < .5f
				? SlimeBeltHelper.Track.BACK : SlimeBeltHelper.Track.FRONT;
			default -> SlimeBeltHelper.Track.FRONT;
		};
		transported.insertedAt = segment;
		transported.insertedFrom = SlimeBeltHelper.getRepresentativeSideForTrack(controller, segment, track);
	}

	private static float getConnectorProgress(SlimeBeltBlockEntity controller, float loopPosition,
		SlimeBeltHelper.LoopSection section) {
		float normalized = SlimeBeltHelper.normalizeLoopPosition(controller, loopPosition);
		float connectorLength = SlimeBeltHelper.getConnectorLength(controller);
		if (connectorLength <= 0)
			return 1;
		return switch (section) {
			case END_TURN -> Mth.clamp((normalized - controller.beltLength) / connectorLength, 0, 1);
			case START_TURN -> Mth.clamp(
				(normalized - (controller.beltLength + connectorLength + controller.beltLength)) / connectorLength, 0, 1);
			default -> 0;
		};
	}

	private static void spawnSnapshot(Level world, TransportedSnapshot snapshot) {
		ItemEntity entity = new ItemEntity(world, snapshot.worldPosition().x, snapshot.worldPosition().y,
			snapshot.worldPosition().z, snapshot.transported().stack);
		entity.setDeltaMovement(Vec3.ZERO);
		entity.setDefaultPickUpDelay();
		entity.hurtMarked = true;
		world.addFreshEntity(entity);
	}

	private static boolean hasSplitMaterials(Player player, int requiredShafts) {
		if (player == null)
			return false;
		int connectors = 0;
		int shafts = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.isEmpty())
				continue;
			if (CBItems.isSlimeBeltConnector(stack))
				connectors += stack.getCount();
			if (com.simibubi.create.AllBlocks.SHAFT.isIn(stack))
				shafts += stack.getCount();
		}
		return connectors >= 1 && shafts >= requiredShafts;
	}

	private static void consumeSplitMaterials(Player player, int firstSegmentShafts, int secondSegmentShafts) {
		consumeInventoryItem(player, true, 1);
		consumeInventoryItem(player, false, firstSegmentShafts + secondSegmentShafts);
	}

	private static void consumeInventoryItem(Player player, boolean connector, int amount) {
		int remaining = amount;
		for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.isEmpty())
				continue;
			boolean matches = connector ? CBItems.isSlimeBeltConnector(stack) : com.simibubi.create.AllBlocks.SHAFT.isIn(stack);
			if (!matches)
				continue;
			int taken = Math.min(stack.getCount(), remaining);
			stack.shrink(taken);
			remaining -= taken;
		}
	}

	static boolean beltStatesCompatible(BlockState state, BlockState nextState) {
		Direction facing1 = state.getValue(SlimeBeltBlock.HORIZONTAL_FACING);
		BeltSlope slope1 = state.getValue(SlimeBeltBlock.SLOPE);
		Direction facing2 = nextState.getValue(SlimeBeltBlock.HORIZONTAL_FACING);
		BeltSlope slope2 = nextState.getValue(SlimeBeltBlock.SLOPE);

		switch (slope1) {
			case UPWARD:
				if (slope2 == BeltSlope.DOWNWARD)
					return facing1 == facing2.getOpposite();
				return slope2 == slope1 && facing1 == facing2;
			case DOWNWARD:
				if (slope2 == BeltSlope.UPWARD)
					return facing1 == facing2.getOpposite();
				return slope2 == slope1 && facing1 == facing2;
			default:
				return slope2 == slope1 && facing2.getAxis() == facing1.getAxis();
		}
	}

	static BlockState flipBelt(BlockState state) {
		Direction facing = state.getValue(SlimeBeltBlock.HORIZONTAL_FACING);
		BeltSlope slope = state.getValue(SlimeBeltBlock.SLOPE);
		BeltPart part = state.getValue(SlimeBeltBlock.PART);

		if (slope == BeltSlope.UPWARD)
			state = state.setValue(SlimeBeltBlock.SLOPE, BeltSlope.DOWNWARD);
		else if (slope == BeltSlope.DOWNWARD)
			state = state.setValue(SlimeBeltBlock.SLOPE, BeltSlope.UPWARD);

		if (part == BeltPart.END)
			state = state.setValue(SlimeBeltBlock.PART, BeltPart.START);
		else if (part == BeltPart.START)
			state = state.setValue(SlimeBeltBlock.PART, BeltPart.END);

		return state.setValue(SlimeBeltBlock.HORIZONTAL_FACING, facing.getOpposite());
	}

	static boolean hoveringEnd(BlockState state, BlockHitResult hit) {
		BeltPart part = state.getValue(SlimeBeltBlock.PART);
		if (part == BeltPart.MIDDLE || part == BeltPart.PULLEY)
			return false;

		Vec3 beltVector = SlimeBeltHelper.getBeltVector(state);
		Vec3 centerOf = VecHelper.getCenterOf(hit.getBlockPos());
		Vec3 subtract = hit.getLocation().subtract(centerOf);
		return subtract.dot(beltVector) > 0 == (part == BeltPart.END);
	}
}
