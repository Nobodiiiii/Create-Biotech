package com.nobodiiiii.createbiotech.content.experience.pipe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.content.experience.ExperiencePumpBlock;
import com.nobodiiiii.createbiotech.content.experience.ExperiencePumpBlockEntity;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllTags.AllBlockTags;
import com.nobodiiiii.createbiotech.content.experience.ExperienceConstants;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.BlockHelper;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class ExperiencePropagator {

	private ExperiencePropagator() {}

	public static void propagateChangedPipe(LevelAccessor world, BlockPos pipePos, BlockState pipeState) {
		List<Pair<Integer, BlockPos>> frontier = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		Set<Pair<ExperiencePumpBlockEntity, Direction>> discoveredPumps = new HashSet<>();

		frontier.add(Pair.of(0, pipePos));

		while (!frontier.isEmpty()) {
			Pair<Integer, BlockPos> pair = frontier.remove(0);
			BlockPos currentPos = pair.getSecond();
			if (visited.contains(currentPos))
				continue;
			visited.add(currentPos);
			BlockState currentState = currentPos.equals(pipePos) ? pipeState : world.getBlockState(currentPos);
			ExperienceTransportBehaviour pipe = getPipe(world, currentPos);
			if (pipe == null)
				continue;
			pipe.wipePressure();

			for (Direction direction : getPipeConnections(currentState, pipe)) {
				BlockPos target = currentPos.relative(direction);
				if (world instanceof Level level && !level.isLoaded(target))
					continue;

				BlockEntity blockEntity = world.getBlockEntity(target);
				BlockState targetState = world.getBlockState(target);
				if (blockEntity instanceof ExperiencePumpBlockEntity pump) {
					if (!ExperiencePumpBlock.isPump(targetState)
						|| targetState.getValue(ExperiencePumpBlock.FACING).getAxis() != direction.getAxis())
						continue;
					discoveredPumps.add(Pair.of(pump, direction.getOpposite()));
					continue;
				}

				if (visited.contains(target))
					continue;
				ExperienceTransportBehaviour targetPipe = getPipe(world, target);
				if (targetPipe == null)
					continue;
				int distance = pair.getFirst();
				if (distance >= getPumpRange() && !targetPipe.hasAnyPressure())
					continue;
				if (targetPipe.canHaveFlowToward(targetState, direction.getOpposite()))
					frontier.add(Pair.of(distance + 1, target));
			}
		}

		discoveredPumps.forEach(pair -> pair.getFirst().updatePipesOnSide(pair.getSecond()));
	}

	public static void resetAffectedExperienceNetworks(Level world, BlockPos start, Direction side) {
		List<BlockPos> frontier = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		frontier.add(start);

		while (!frontier.isEmpty()) {
			BlockPos pos = frontier.remove(0);
			if (visited.contains(pos))
				continue;
			visited.add(pos);
			ExperienceTransportBehaviour pipe = getPipe(world, pos);
			if (pipe == null)
				continue;

			for (Direction direction : Iterate.directions) {
				if (pos.equals(start) && direction != side)
					continue;
				BlockPos target = pos.relative(direction);
				if (visited.contains(target))
					continue;
				ExperienceConnection connection = pipe.getConnection(direction);
				if (connection == null || !connection.hasFlow())
					continue;
				ExperienceConnection.Flow flow = connection.flow.get();
				if (!flow.inbound)
					continue;
				connection.resetNetwork();
				frontier.add(target);
			}
		}
	}

	@Nullable
	public static Direction validateNeighbourChange(BlockState state, Level world, BlockPos pos, Block otherBlock,
		BlockPos neighborPos, boolean isMoving) {
		if (world.isClientSide)
			return null;
		otherBlock = world.getBlockState(neighborPos).getBlock();
		if (otherBlock instanceof ExperiencePipeBlock || otherBlock instanceof ExperienceEncasedPipeBlock
			|| otherBlock instanceof ExperiencePumpBlock)
			return null;
		if (getStraightPipeAxis(state) == null && !(state.getBlock() instanceof ExperienceEncasedPipeBlock))
			return null;
		for (Direction direction : Iterate.directions)
			if (pos.relative(direction).equals(neighborPos))
				return direction;
		return null;
	}

	@Nullable
	public static ExperienceTransportBehaviour getPipe(BlockGetter world, BlockPos pos) {
		return BlockEntityBehaviour.get(world, pos, ExperienceTransportBehaviour.TYPE);
	}

	public static boolean isOpenEnd(BlockGetter world, BlockPos pos, Direction side) {
		BlockPos connectedPos = pos.relative(side);
		BlockState connectedState = world.getBlockState(connectedPos);
		ExperienceTransportBehaviour pipe = getPipe(world, connectedPos);
		if (pipe != null && pipe.canHaveFlowToward(connectedState, side.getOpposite()))
			return false;
		if (ExperiencePumpBlock.isPump(connectedState)
			&& connectedState.getValue(ExperiencePumpBlock.FACING).getAxis() == side.getAxis())
			return false;
		if (isNozzleFacingCurrent(connectedState, side))
			return true;
		if (hasExperienceEndpoint(world, connectedPos, side.getOpposite()))
			return false;
		if (BlockHelper.hasBlockSolidSide(connectedState, world, connectedPos, side.getOpposite())
			&& !AllBlockTags.FAN_TRANSPARENT.matches(connectedState))
			return false;
		if (!(connectedState.canBeReplaced() && connectedState.getDestroySpeed(world, connectedPos) != -1)
			&& !connectedState.hasProperty(BlockStateProperties.WATERLOGGED))
			return false;
		return true;
	}

	private static boolean isNozzleFacingCurrent(BlockState state, Direction side) {
		return AllBlocks.NOZZLE.has(state) && state.hasProperty(BlockStateProperties.FACING)
			&& state.getValue(BlockStateProperties.FACING) == side.getOpposite();
	}

	public static boolean hasExperienceEndpoint(BlockGetter world, BlockPos pos, Direction side) {
		return ExperienceTransport.hasEndpoint(world, pos, side);
	}

	public static List<Direction> getPipeConnections(BlockState state, ExperienceTransportBehaviour pipe) {
		List<Direction> list = new ArrayList<>();
		for (Direction direction : Iterate.directions)
			if (pipe.canHaveFlowToward(state, direction))
				list.add(direction);
		return list;
	}

	public static int getPumpRange() {
		return ExperienceConstants.pumpRange();
	}

	@Nullable
	public static Axis getStraightPipeAxis(BlockState state) {
		if (state.getBlock() instanceof ExperiencePumpBlock)
			return state.getValue(ExperiencePumpBlock.FACING).getAxis();
		if (!(state.getBlock() instanceof ExperiencePipeBlock))
			return null;

		Axis axisFound = null;
		int connections = 0;
		for (Axis axis : Iterate.axes) {
			Direction negative = Direction.get(AxisDirection.NEGATIVE, axis);
			Direction positive = Direction.get(AxisDirection.POSITIVE, axis);
			boolean openNegative = ExperiencePipeBlock.isOpenAt(state, negative);
			boolean openPositive = ExperiencePipeBlock.isOpenAt(state, positive);
			if (openNegative)
				connections++;
			if (openPositive)
				connections++;
			if (openNegative && openPositive) {
				if (axisFound != null)
					return null;
				axisFound = axis;
			}
		}
		return connections == 2 ? axisFound : null;
	}
}
