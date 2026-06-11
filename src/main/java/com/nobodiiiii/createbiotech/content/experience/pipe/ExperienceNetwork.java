package com.nobodiiiii.createbiotech.content.experience.pipe;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.content.experience.ExperienceConstants;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public class ExperienceNetwork {
	private static final int CYCLES_PER_TICK = 16;

	private final Level world;
	private final BlockFace start;
	private final Supplier<ExperienceEndpoint> sourceSupplier;
	private final Set<Pair<BlockFace, ExperienceConnection>> frontier;
	private final Set<BlockPos> visited;
	private final List<BlockFace> queued;
	private final List<BlockFace> targets;
	private final Map<BlockPos, WeakReference<ExperienceTransportBehaviour>> cache;

	private int pauseBeforePropagation;
	private double accumulatedTransfer;

	public ExperienceNetwork(Level world, BlockFace start, Supplier<ExperienceEndpoint> sourceSupplier) {
		this.world = world;
		this.start = start;
		this.sourceSupplier = sourceSupplier;
		this.frontier = new HashSet<>();
		this.visited = new HashSet<>();
		this.queued = new ArrayList<>();
		this.targets = new ArrayList<>();
		this.cache = new HashMap<>();
		reset();
	}

	public void tick() {
		if (pauseBeforePropagation > 0) {
			pauseBeforePropagation--;
			return;
		}

		for (int cycle = 0; cycle < CYCLES_PER_TICK; cycle++) {
			boolean shouldContinue = false;

			for (Iterator<BlockFace> iterator = queued.iterator(); iterator.hasNext();) {
				BlockFace blockFace = iterator.next();
				if (!world.isLoaded(blockFace.getPos()))
					continue;
				ExperienceConnection connection = get(blockFace);
				if (connection != null)
					frontier.add(Pair.of(blockFace, connection));
				iterator.remove();
			}

			for (Iterator<Pair<BlockFace, ExperienceConnection>> iterator = frontier.iterator(); iterator.hasNext();) {
				Pair<BlockFace, ExperienceConnection> pair = iterator.next();
				BlockFace blockFace = pair.getFirst();
				ExperienceConnection connection = pair.getSecond();

				if (!connection.hasFlow())
					continue;

				ExperienceConnection.Flow flow = connection.flow.get();
				if (!flow.inbound) {
					if (connection.comparePressure() >= 0)
						iterator.remove();
					continue;
				}
				if (!flow.complete)
					continue;

				boolean canRemove = true;
				for (Direction side : Iterate.directions) {
					if (side == blockFace.getFace())
						continue;
					BlockFace adjacentLocation = new BlockFace(blockFace.getPos(), side);
					ExperienceConnection adjacent = get(adjacentLocation);
					if (adjacent == null)
						continue;
					if (!adjacent.hasFlow()) {
						if (adjacent.hasPressure() && adjacent.getPressure().getSecond() > 0)
							canRemove = false;
						continue;
					}

					ExperienceConnection.Flow outFlow = adjacent.flow.get();
					if (outFlow.inbound) {
						if (adjacent.comparePressure() > 0)
							canRemove = false;
						continue;
					}

					if (!outFlow.complete) {
						canRemove = false;
						continue;
					}

					// Give a newly connected pipe end one chance to resolve its endpoint before
					// deciding whether this branch is a transport target or another pipe segment.
					if (adjacent.source.isEmpty() && !adjacent.determineSource(world, blockFace.getPos())) {
						canRemove = false;
						continue;
					}

					if (adjacent.source.isPresent() && adjacent.source.get().isEndpoint()) {
						targets.add(adjacentLocation);
						continue;
					}

					if (visited.add(adjacentLocation.getConnectedPos())) {
						queued.add(adjacentLocation.getOpposite());
						shouldContinue = true;
					}
				}

				if (canRemove)
					iterator.remove();
			}

			if (!shouldContinue)
				break;
		}

		ExperienceEndpoint source = sourceSupplier.get();
		if (source == null || targets.isEmpty())
			return;

		ExperienceConnection startConnection = get(start);
		if (startConnection == null)
			return;

		float pressure = startConnection.getPressure().getFirst();
		if (pressure <= 0)
			return;

		accumulatedTransfer += pressure * ExperienceConstants.pumpXpPerRpmPerSecond() / 20.0d;
		int budget = (int) Math.floor(accumulatedTransfer);
		if (budget <= 0)
			return;

		int transferred = distribute(source, budget);
		if (transferred > 0)
			accumulatedTransfer -= transferred;
	}

	private int distribute(ExperienceEndpoint source, int budget) {
		int remainingBudget = budget;
		int transferred = 0;
		List<BlockFace> availableTargets = new ArrayList<>(targets);

		while (remainingBudget > 0 && !availableTargets.isEmpty()) {
			boolean madeProgress = false;
			int dividedTransfer = Math.max(1, remainingBudget / availableTargets.size());

			for (Iterator<BlockFace> iterator = availableTargets.iterator(); iterator.hasNext() && remainingBudget > 0;) {
				BlockFace targetLocation = iterator.next();
				ExperienceEndpoint target = resolveTarget(targetLocation);
				if (target == null) {
					iterator.remove();
					continue;
				}

				int offer = Math.min(dividedTransfer, remainingBudget);
				int accepted = target.insert(offer, true);
				if (accepted <= 0) {
					iterator.remove();
					continue;
				}

				int extractable = source.extract(accepted, true);
				if (extractable <= 0)
					return transferred;

				int moved = Math.min(accepted, extractable);
				int extracted = source.extract(moved, false);
				if (extracted <= 0)
					return transferred;

				int inserted = target.insert(extracted, false);
				if (inserted <= 0) {
					iterator.remove();
					continue;
				}

				transferred += inserted;
				remainingBudget -= inserted;
				madeProgress = true;

				if (inserted < accepted)
					iterator.remove();
			}

			if (!madeProgress)
				break;
		}

		return transferred;
	}

	@Nullable
	private ExperienceEndpoint resolveTarget(BlockFace targetLocation) {
		ExperienceConnection connection = get(targetLocation);
		return connection == null ? null : connection.provideEndpoint();
	}

	public void reset() {
		frontier.clear();
		visited.clear();
		targets.clear();
		queued.clear();
		queued.add(start);
		pauseBeforePropagation = 2;
		accumulatedTransfer = 0;
	}

	@Nullable
	private ExperienceConnection get(BlockFace location) {
		ExperienceTransportBehaviour transport = getTransport(location.getPos());
		return transport == null ? null : transport.getConnection(location.getFace());
	}

	@Nullable
	private ExperienceTransportBehaviour getTransport(BlockPos pos) {
		WeakReference<ExperienceTransportBehaviour> weakReference = cache.get(pos);
		ExperienceTransportBehaviour behaviour = weakReference == null ? null : weakReference.get();
		if (behaviour != null && behaviour.blockEntity.isRemoved())
			behaviour = null;
		if (behaviour == null) {
			behaviour = BlockEntityBehaviour.get(world, pos, ExperienceTransportBehaviour.TYPE);
			if (behaviour != null)
				cache.put(pos, new WeakReference<>(behaviour));
		}
		return behaviour;
	}
}
