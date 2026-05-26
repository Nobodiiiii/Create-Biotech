package com.nobodiiiii.createbiotech.content.experience.pipe;

import java.util.Optional;

import javax.annotation.Nullable;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;

public class ExperienceConnection {
	public final Direction side;

	Couple<Float> pressure;
	Optional<ExperienceFlowSource> source;
	Optional<ExperienceFlowSource> previousSource;
	Optional<Flow> flow;
	Optional<ExperienceNetwork> network;

	public ExperienceConnection(Direction side) {
		this.side = side;
		this.pressure = Couple.create(() -> 0f);
		this.source = Optional.empty();
		this.previousSource = Optional.empty();
		this.flow = Optional.empty();
		this.network = Optional.empty();
	}

	public boolean getProvidedExperience() {
		if (!hasFlow())
			return false;
		Flow currentFlow = flow.get();
		return currentFlow.inbound && currentFlow.complete;
	}

	public boolean flipFlowsIfPressureReversed() {
		if (!hasFlow())
			return false;
		boolean singlePressure = comparePressure() != 0 && (getInboundPressure() == 0 || getOutwardPressure() == 0);
		Flow currentFlow = flow.get();
		if (!singlePressure || comparePressure() < 0 == currentFlow.inbound)
			return false;
		currentFlow.inbound = !currentFlow.inbound;
		if (!currentFlow.complete)
			flow = Optional.empty();
		return true;
	}

	public void manageSource(Level world, BlockPos pos) {
		if (source.isEmpty() && !determineSource(world, pos))
			return;
		source.ifPresent(currentSource -> currentSource.manageSource(world));
	}

	public boolean manageFlows(Level world, BlockPos pos, boolean internalFlowAvailable) {
		Optional<ExperienceNetwork> retainedNetwork = network;
		network = Optional.empty();

		if (source.isEmpty() && !determineSource(world, pos))
			return false;
		ExperienceFlowSource flowSource = source.get();

		if (!hasFlow()) {
			if (!hasPressure())
				return false;
			boolean prioritizeInbound = comparePressure() < 0;
			for (boolean trueFalse : Iterate.trueAndFalse) {
				boolean inbound = prioritizeInbound == trueFalse;
				if (pressure.get(inbound) == 0)
					continue;
				boolean canFlow = inbound ? flowSource.canProvideExperience() : internalFlowAvailable;
				if (tryStartingNewFlow(inbound, canFlow))
					return true;
			}
			return false;
		}

		Flow currentFlow = flow.get();
		boolean sourceAvailable = currentFlow.inbound ? flowSource.canProvideExperience() : internalFlowAvailable;
		if (!hasPressure() || !sourceAvailable) {
			flow = Optional.empty();
			return true;
		}

		if (currentFlow.inbound != comparePressure() < 0) {
			boolean inbound = !currentFlow.inbound;
			boolean available = inbound ? flowSource.canProvideExperience() : internalFlowAvailable;
			if (available) {
				ExperiencePropagator.resetAffectedExperienceNetworks(world, pos, side);
				tryStartingNewFlow(inbound, true);
				return true;
			}
		}

		if (!flowSource.isEndpoint() || !currentFlow.inbound)
			return false;

		network = retainedNetwork;
		if (network.isEmpty())
			network = Optional.of(new ExperienceNetwork(world, new BlockFace(pos, side), flowSource::provideEndpoint));
		network.get()
			.tick();
		return false;
	}

	private boolean tryStartingNewFlow(boolean inbound, boolean available) {
		if (!available)
			return false;
		flow = Optional.of(new Flow(inbound));
		return true;
	}

	public boolean determineSource(Level world, BlockPos pos) {
		BlockPos relative = pos.relative(side);
		if (world.getChunk(relative.getX() >> 4, relative.getZ() >> 4, ChunkStatus.FULL, false) == null)
			return false;

		BlockFace location = new BlockFace(pos, side);
		if (ExperiencePropagator.isOpenEnd(world, pos, side)) {
			if (previousSource.orElse(null) instanceof ExperienceFlowSource.OpenEnded)
				source = previousSource;
			else
				source = Optional.of(new ExperienceFlowSource.OpenEnded(location));
			return true;
		}

		if (ExperienceTransport.findEndpoint(world, location) != null) {
			source = Optional.of(new ExperienceFlowSource.Endpoint(location));
			return true;
		}

		ExperienceTransportBehaviour behaviour =
			BlockEntityBehaviour.get(world, relative, ExperienceTransportBehaviour.TYPE);
		source = Optional.of(behaviour == null ? new ExperienceFlowSource.Blocked(location)
			: new ExperienceFlowSource.OtherPipe(location));
		return true;
	}

	public void tickFlowProgress() {
		if (!hasFlow())
			return;
		Flow currentFlow = flow.get();
		float flowSpeed = 1 / 32f + Mth.clamp(pressure.get(currentFlow.inbound) / 128f, 0, 1) * 31 / 32f;
		currentFlow.progress.setValue(Math.min(currentFlow.progress.getValue() + flowSpeed, 1));
		if (currentFlow.progress.getValue() >= 1)
			currentFlow.complete = true;
	}

	public void serializeNBT(CompoundTag tag) {
		CompoundTag connectionData = new CompoundTag();
		tag.put(side.getName(), connectionData);

		if (hasPressure()) {
			ListTag pressureData = new ListTag();
			pressureData.add(FloatTag.valueOf(getInboundPressure()));
			pressureData.add(FloatTag.valueOf(getOutwardPressure()));
			connectionData.put("Pressure", pressureData);
		}

		if (hasFlow()) {
			CompoundTag flowData = new CompoundTag();
			Flow currentFlow = flow.get();
			flowData.putBoolean("In", currentFlow.inbound);
			if (!currentFlow.complete)
				flowData.put("Progress", currentFlow.progress.writeNBT());
			connectionData.put("Flow", flowData);
		}
	}

	public void deserializeNBT(CompoundTag tag, boolean clientPacket) {
		CompoundTag connectionData = tag.getCompound(side.getName());
		if (connectionData.contains("Pressure")) {
			ListTag pressureData = connectionData.getList("Pressure", Tag.TAG_FLOAT);
			pressure = Couple.create(pressureData.getFloat(0), pressureData.getFloat(1));
		} else {
			pressure.replace(f -> 0f);
		}

		source = Optional.empty();
		if (connectionData.contains("Flow")) {
			CompoundTag flowData = connectionData.getCompound("Flow");
			boolean inbound = flowData.getBoolean("In");
			if (flow.isEmpty())
				flow = Optional.of(new Flow(inbound));
			Flow currentFlow = flow.get();
			currentFlow.inbound = inbound;
			currentFlow.complete = !flowData.contains("Progress");
			if (!currentFlow.complete)
				currentFlow.progress.readNBT(flowData.getCompound("Progress"), clientPacket);
			else {
				if (currentFlow.progress.getValue() == 0)
					currentFlow.progress.startWithValue(1);
				currentFlow.progress.setValue(1);
			}
		} else {
			flow = Optional.empty();
		}
	}

	public float comparePressure() {
		return getOutwardPressure() - getInboundPressure();
	}

	public void wipePressure() {
		pressure.replace(f -> 0f);
		source.ifPresent(current -> previousSource = Optional.of(current));
		source = Optional.empty();
		resetNetwork();
	}

	public void addPressure(boolean inbound, float addedPressure) {
		pressure = pressure.mapWithContext((value, in) -> in == inbound ? value + addedPressure : value);
	}

	public Couple<Float> getPressure() {
		return pressure;
	}

	public boolean hasPressure() {
		return getInboundPressure() != 0 || getOutwardPressure() != 0;
	}

	private float getOutwardPressure() {
		return pressure.getSecond();
	}

	private float getInboundPressure() {
		return pressure.getFirst();
	}

	public boolean hasFlow() {
		return flow.isPresent();
	}

	public boolean hasNetwork() {
		return network.isPresent();
	}

	public void resetNetwork() {
		network.ifPresent(ExperienceNetwork::reset);
	}

	@Nullable
	public ExperienceEndpoint provideEndpoint() {
		return source.isPresent() ? source.get().provideEndpoint() : null;
	}

	public static class Flow {
		public boolean complete;
		public boolean inbound;
		public LerpedFloat progress;

		public Flow(boolean inbound) {
			this.complete = false;
			this.inbound = inbound;
			this.progress = LerpedFloat.linear()
				.startWithValue(0);
		}
	}
}
