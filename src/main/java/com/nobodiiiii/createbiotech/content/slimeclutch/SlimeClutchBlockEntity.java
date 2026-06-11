package com.nobodiiiii.createbiotech.content.slimeclutch;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SlimeClutchBlockEntity extends SplitShaftBlockEntity {

	private static final MethodHandle ROTATION_MODIFIER = resolveModifier();

	private static MethodHandle resolveModifier() {
		try {
			Method m = RotationPropagator.class.getDeclaredMethod(
				"getRotationSpeedModifier", KineticBlockEntity.class, KineticBlockEntity.class);
			m.setAccessible(true);
			return MethodHandles.lookup().unreflect(m);
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	private boolean pendingTrip;
	private boolean processingTransition;
	private int recheckCounter;

	public SlimeClutchBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.SLIME_CLUTCH.get(), pos, state);
	}

	@Override
	public float getRotationSpeedModifier(Direction face) {
		if (hasSource()) {
			if (face != getSourceFacing() && getBlockState().getValue(BlockStateProperties.POWERED))
				return 0;
		}
		return 1;
	}

	@Override
	public void updateFromNetwork(float maxStress, float currentStress, int networkSize) {
		super.updateFromNetwork(maxStress, currentStress, networkSize);
		if (level == null || level.isClientSide || processingTransition)
			return;
		if (!getBlockState().getValue(BlockStateProperties.POWERED) && isOverStressed())
			pendingTrip = true;
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide)
			return;

		boolean powered = getBlockState().getValue(BlockStateProperties.POWERED);

		if (!powered) {
			if (pendingTrip) {
				pendingTrip = false;
				performTransition(true);
				recheckCounter = getRecheckPeriod();
			}
			return;
		}

		pendingTrip = false;
		if (--recheckCounter > 0)
			return;
		recheckCounter = getRecheckPeriod();
		if (!isSoftOverloadCheckEnabled() || canSafelyMerge())
			performTransition(false);
	}

	private void performTransition(boolean toPowered) {
		BlockState state = getBlockState();
		processingTransition = true;
		try {
			if (state.getBlock() instanceof SlimeClutchBlock clutch)
				clutch.detachKinetics(level, getBlockPos(), true);
			level.setBlock(getBlockPos(), state.setValue(BlockStateProperties.POWERED, toPowered), 2 | 16);
			if (toPowered)
				CBAdvancements.awardNearby(level, getBlockPos(), 16, CBAdvancements.SLIME_CLUTCH);
		} finally {
			processingTransition = false;
		}
	}

	private boolean canSafelyMerge() {
		if (!hasSource() || level == null)
			return false;
		float sourceSpeed = getSpeed();
		if (sourceSpeed == 0)
			return false;

		Direction downstreamFace = getSourceFacing().getOpposite();
		BlockPos startPos = getBlockPos().relative(downstreamFace);
		BlockEntity firstBe = level.getBlockEntity(startPos);
		if (!(firstBe instanceof KineticBlockEntity firstKbe))
			return true;

		Map<BlockPos, Float> simSpeed = new HashMap<>();
		simSpeed.put(startPos, sourceSpeed);
		Queue<KineticBlockEntity> frontier = new ArrayDeque<>();
		frontier.add(firstKbe);

		float subnetStress = 0f;
		float subnetCapacity = 0f;
		int walked = 0;

		while (!frontier.isEmpty()) {
			if (++walked > getMaxWalk())
				return false;
			KineticBlockEntity kbe = frontier.poll();
			BlockPos kbePos = kbe.getBlockPos();
			float kbeSpeed = simSpeed.get(kbePos);

			subnetStress += kbe.calculateStressApplied() * Math.abs(kbeSpeed);
			if (kbe.isSource())
				subnetCapacity += kbe.calculateAddedStressCapacity() * Math.abs(kbe.getGeneratedSpeed());

			for (KineticBlockEntity neighbour : connectedKinetics(kbe)) {
				BlockPos np = neighbour.getBlockPos();
				if (np.equals(getBlockPos()) || simSpeed.containsKey(np))
					continue;
				float modifier = rotationModifier(kbe, neighbour);
				if (modifier == 0f)
					continue;
				simSpeed.put(np, kbeSpeed * modifier);
				frontier.add(neighbour);
			}
		}

		float mergedCapacity = capacity + subnetCapacity;
		float mergedStress = stress + subnetStress;
		return mergedCapacity >= mergedStress;
	}

	private static float rotationModifier(KineticBlockEntity from, KineticBlockEntity to) {
		if (ROTATION_MODIFIER == null)
			return RotationPropagator.isConnected(from, to) || RotationPropagator.isConnected(to, from) ? 1f : 0f;
		try {
			return (float) ROTATION_MODIFIER.invokeExact(from, to);
		} catch (Throwable t) {
			return RotationPropagator.isConnected(from, to) || RotationPropagator.isConnected(to, from) ? 1f : 0f;
		}
	}

	private List<KineticBlockEntity> connectedKinetics(KineticBlockEntity from) {
		List<KineticBlockEntity> result = new LinkedList<>();
		List<BlockPos> candidates = new LinkedList<>();
		BlockPos bp = from.getBlockPos();
		for (Direction d : Iterate.directions)
			candidates.add(bp.relative(d));
		BlockState s = from.getBlockState();
		if (s.getBlock() instanceof IRotate rotate)
			candidates = from.addPropagationLocations(rotate, s, candidates);

		for (BlockPos p : candidates) {
			BlockEntity be = level.getBlockEntity(p);
			if (!(be instanceof KineticBlockEntity kbe))
				continue;
			if (kbe == this)
				continue;
			if (!RotationPropagator.isConnected(from, kbe) && !RotationPropagator.isConnected(kbe, from))
				continue;
			result.add(kbe);
		}
		return result;
	}

	private static int getRecheckPeriod() {
		return CBConfigs.SERVER.slimeClutch.recheckPeriod.get();
	}

	private static int getMaxWalk() {
		return CBConfigs.SERVER.slimeClutch.maxWalk.get();
	}

	private static boolean isSoftOverloadCheckEnabled() {
		return CBConfigs.SERVER.slimeClutch.enableSoftOverloadCheck.get();
	}
}
