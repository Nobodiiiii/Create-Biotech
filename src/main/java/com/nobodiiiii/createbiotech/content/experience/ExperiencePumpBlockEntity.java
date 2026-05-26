package com.nobodiiiii.createbiotech.content.experience;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.apache.commons.lang3.mutable.MutableBoolean;

import com.nobodiiiii.createbiotech.content.experience.pipe.ExperienceConnection;
import com.nobodiiiii.createbiotech.content.experience.pipe.ExperiencePropagator;
import com.nobodiiiii.createbiotech.content.experience.pipe.ExperienceTransportBehaviour;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.foundation.advancement.PlacedByPlayerAdvancementTracker;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ExperiencePumpBlockEntity extends KineticBlockEntity {
	private final Couple<MutableBoolean> sidesToUpdate;
	public boolean pressureUpdate;

	@Nullable
	private UUID advancementOwner;

	public ExperiencePumpBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.EXPERIENCE_PUMP.get(), pos, state);
		this.sidesToUpdate = Couple.create(MutableBoolean::new);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		behaviours.add(new PumpExperienceTransportBehaviour(this));
	}

	@Override
	public void tick() {
		super.tick();
		if (level.isClientSide && !isVirtual())
			return;

		if (pressureUpdate)
			updatePressureChange();

		sidesToUpdate.forEachWithContext((update, isFront) -> {
			if (update.isFalse())
				return;
			update.setFalse();
			distributePressureTo(isFront ? getFront() : getFront().getOpposite());
		});
	}

	@Override
	public void onSpeedChanged(float previousSpeed) {
		super.onSpeedChanged(previousSpeed);
		if (Math.abs(previousSpeed) == Math.abs(getSpeed()))
			return;
		if (getSpeed() != 0)
			PlacedByPlayerAdvancementTracker.awardPlacedBy(level, advancementOwner, CBAdvancements.EXPERIENCE_PUMP);
		if (level.isClientSide && !isVirtual())
			return;
		updatePressureChange();
	}

	public void updatePressureChange() {
		pressureUpdate = false;
		Direction front = getFront();
		if (front == null)
			return;

		BlockPos frontPos = worldPosition.relative(front);
		BlockPos backPos = worldPosition.relative(front.getOpposite());
		ExperiencePropagator.propagateChangedPipe(level, frontPos, level.getBlockState(frontPos));
		ExperiencePropagator.propagateChangedPipe(level, backPos, level.getBlockState(backPos));

		ExperienceTransportBehaviour behaviour = getBehaviour(ExperienceTransportBehaviour.TYPE);
		if (behaviour != null)
			behaviour.wipePressure();
		sidesToUpdate.forEach(MutableBoolean::setTrue);
	}

	private void distributePressureTo(Direction side) {
		if (getSpeed() == 0)
			return;

		BlockFace start = new BlockFace(worldPosition, side);
		boolean pull = isPullingOnSide(isFront(side));
		Set<BlockFace> targets = new HashSet<>();
		Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph = new HashMap<>();

		if (!pull)
			ExperiencePropagator.resetAffectedExperienceNetworks(level, worldPosition, side.getOpposite());

		if (!hasReachedValidEndpoint(level, start, pull)) {
			pipeGraph.computeIfAbsent(worldPosition, $ -> Pair.of(0, new IdentityHashMap<>()))
				.getSecond()
				.put(side, pull);
			pipeGraph.computeIfAbsent(start.getConnectedPos(), $ -> Pair.of(1, new IdentityHashMap<>()))
				.getSecond()
				.put(side.getOpposite(), !pull);

			List<Pair<Integer, BlockPos>> frontier = new ArrayList<>();
			Set<BlockPos> visited = new HashSet<>();
			int maxDistance = ExperiencePropagator.getPumpRange();
			frontier.add(Pair.of(1, start.getConnectedPos()));

			while (!frontier.isEmpty()) {
				Pair<Integer, BlockPos> entry = frontier.remove(0);
				int distance = entry.getFirst();
				BlockPos currentPos = entry.getSecond();

				if (!level.isLoaded(currentPos) || visited.contains(currentPos))
					continue;
				visited.add(currentPos);
				BlockState currentState = level.getBlockState(currentPos);
				ExperienceTransportBehaviour pipe = ExperiencePropagator.getPipe(level, currentPos);
				if (pipe == null)
					continue;

				for (Direction face : ExperiencePropagator.getPipeConnections(currentState, pipe)) {
					BlockFace blockFace = new BlockFace(currentPos, face);
					BlockPos connectedPos = blockFace.getConnectedPos();

					if (!level.isLoaded(connectedPos) || blockFace.isEquivalent(start))
						continue;
					if (hasReachedValidEndpoint(level, blockFace, pull)) {
						pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>()))
							.getSecond()
							.put(face, pull);
						targets.add(blockFace);
						continue;
					}

					ExperienceTransportBehaviour pipeBehaviour = ExperiencePropagator.getPipe(level, connectedPos);
					if (pipeBehaviour == null || pipeBehaviour instanceof PumpExperienceTransportBehaviour
						|| visited.contains(connectedPos))
						continue;
					if (distance + 1 >= maxDistance) {
						pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>()))
							.getSecond()
							.put(face, pull);
						targets.add(blockFace);
						continue;
					}

					pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>()))
						.getSecond()
						.put(face, pull);
					pipeGraph.computeIfAbsent(connectedPos, $ -> Pair.of(distance + 1, new IdentityHashMap<>()))
						.getSecond()
						.put(face.getOpposite(), !pull);
					frontier.add(Pair.of(distance + 1, connectedPos));
				}
			}
		}

		Map<Integer, Set<BlockFace>> validFaces = new HashMap<>();
		searchForEndpointRecursively(pipeGraph, targets, validFaces, new BlockFace(start.getPos(), start.getOppositeFace()),
			pull);

		float pressure = Math.abs(getSpeed());
		for (Set<BlockFace> set : validFaces.values()) {
			int parallelBranches = Math.max(1, set.size() - 1);
			for (BlockFace face : set) {
				BlockPos pipePos = face.getPos();
				Direction pipeSide = face.getFace();
				if (pipePos.equals(worldPosition))
					continue;

				boolean inbound = pipeGraph.get(pipePos).getSecond().get(pipeSide);
				ExperienceTransportBehaviour pipeBehaviour = ExperiencePropagator.getPipe(level, pipePos);
				if (pipeBehaviour != null)
					pipeBehaviour.addPressure(pipeSide, inbound, pressure / parallelBranches);
			}
		}
	}

	private boolean searchForEndpointRecursively(Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph,
		Set<BlockFace> targets, Map<Integer, Set<BlockFace>> validFaces, BlockFace currentFace, boolean pull) {
		BlockPos currentPos = currentFace.getPos();
		if (!pipeGraph.containsKey(currentPos))
			return false;
		Pair<Integer, Map<Direction, Boolean>> pair = pipeGraph.get(currentPos);
		int distance = pair.getFirst();

		boolean atLeastOneBranchSuccessful = false;
		for (Direction nextFacing : Iterate.directions) {
			if (nextFacing == currentFace.getFace())
				continue;
			Map<Direction, Boolean> map = pair.getSecond();
			if (!map.containsKey(nextFacing))
				continue;

			BlockFace localTarget = new BlockFace(currentPos, nextFacing);
			if (targets.contains(localTarget)) {
				validFaces.computeIfAbsent(distance, $ -> new HashSet<>()).add(localTarget);
				atLeastOneBranchSuccessful = true;
				continue;
			}

			if (map.get(nextFacing) != pull)
				continue;
			if (!searchForEndpointRecursively(pipeGraph, targets, validFaces,
				new BlockFace(currentPos.relative(nextFacing), nextFacing.getOpposite()), pull))
				continue;

			validFaces.computeIfAbsent(distance, $ -> new HashSet<>()).add(localTarget);
			atLeastOneBranchSuccessful = true;
		}

		if (atLeastOneBranchSuccessful)
			validFaces.computeIfAbsent(distance, $ -> new HashSet<>()).add(currentFace);
		return atLeastOneBranchSuccessful;
	}

	private boolean hasReachedValidEndpoint(LevelAccessor world, BlockFace blockFace, boolean pull) {
		BlockPos connectedPos = blockFace.getConnectedPos();
		BlockState connectedState = world.getBlockState(connectedPos);
		BlockEntity blockEntity = world.getBlockEntity(connectedPos);
		Direction face = blockFace.getFace();

		if (ExperiencePumpBlock.isPump(connectedState) && connectedState.getValue(ExperiencePumpBlock.FACING).getAxis() == face.getAxis()
			&& blockEntity instanceof ExperiencePumpBlockEntity pump)
			return pump.isPullingOnSide(pump.isFront(blockFace.getOppositeFace())) != pull;

		ExperienceTransportBehaviour pipe = ExperiencePropagator.getPipe(world, connectedPos);
		if (pipe != null && pipe.canHaveFlowToward(connectedState, blockFace.getOppositeFace()))
			return false;

		if (ExperiencePropagator.hasExperienceEndpoint(world, connectedPos, face.getOpposite()))
			return true;

		return ExperiencePropagator.isOpenEnd(world, blockFace.getPos(), face);
	}

	public void updatePipesOnSide(Direction side) {
		if (!isSideAccessible(side))
			return;
		updatePipeNetwork(isFront(side));
		ExperienceTransportBehaviour behaviour = getBehaviour(ExperienceTransportBehaviour.TYPE);
		if (behaviour != null)
			behaviour.wipePressure();
	}

	private boolean isFront(Direction side) {
		BlockState state = getBlockState();
		return state.getBlock() instanceof ExperiencePumpBlock && side == state.getValue(ExperiencePumpBlock.FACING);
	}

	@Nullable
	private Direction getFront() {
		BlockState state = getBlockState();
		return state.getBlock() instanceof ExperiencePumpBlock ? state.getValue(ExperiencePumpBlock.FACING) : null;
	}

	private void updatePipeNetwork(boolean front) {
		sidesToUpdate.get(front).setTrue();
	}

	private boolean isSideAccessible(Direction side) {
		BlockState state = getBlockState();
		return state.getBlock() instanceof ExperiencePumpBlock
			&& state.getValue(ExperiencePumpBlock.FACING).getAxis() == side.getAxis();
	}

	public boolean isPullingOnSide(boolean front) {
		return !front;
	}

	public void setAdvancementOwner(@Nullable LivingEntity placer) {
		advancementOwner = PlacedByPlayerAdvancementTracker.ownerFrom(placer);
		setChanged();
	}

	@Override
	protected void write(CompoundTag compound, boolean clientPacket) {
		super.write(compound, clientPacket);
		PlacedByPlayerAdvancementTracker.writeOwner(compound, advancementOwner);
	}

	@Override
	protected void read(CompoundTag compound, boolean clientPacket) {
		super.read(compound, clientPacket);
		advancementOwner = PlacedByPlayerAdvancementTracker.readOwner(compound);
	}

	class PumpExperienceTransportBehaviour extends ExperienceTransportBehaviour {

		public PumpExperienceTransportBehaviour(SmartBlockEntity blockEntity) {
			super(blockEntity);
		}

		@Override
		public void tick() {
			super.tick();
			for (Entry<Direction, ExperienceConnection> entry : interfaces.entrySet()) {
				boolean pull = isPullingOnSide(isFront(entry.getKey()));
				Couple<Float> pressure = entry.getValue().getPressure();
				pressure.set(pull, Math.abs(getSpeed()));
				pressure.set(!pull, 0f);
			}
		}

		@Override
		public boolean canHaveFlowToward(BlockState state, Direction direction) {
			return isSideAccessible(direction);
		}

		@Override
		public AttachmentTypes getRenderedRimAttachment(BlockAndTintGetter world, BlockPos pos, BlockState state,
			Direction direction) {
			AttachmentTypes attachment = super.getRenderedRimAttachment(world, pos, state, direction);
			return attachment == AttachmentTypes.RIM ? AttachmentTypes.NONE : attachment;
		}
	}
}
