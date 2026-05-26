package com.nobodiiiii.createbiotech.content.experience.pipe;

import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.content.experience.ExperiencePumpBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.data.WorldAttached;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

public abstract class ExperienceTransportBehaviour extends BlockEntityBehaviour {
	public static final BehaviourType<ExperienceTransportBehaviour> TYPE = new BehaviourType<>();

	public enum UpdatePhase {
		WAIT_FOR_PUMPS,
		FLIP_FLOWS,
		IDLE
	}

	protected Map<Direction, ExperienceConnection> interfaces;
	protected UpdatePhase phase;

	public ExperienceTransportBehaviour(SmartBlockEntity blockEntity) {
		super(blockEntity);
		this.phase = UpdatePhase.WAIT_FOR_PUMPS;
	}

	public abstract boolean canHaveFlowToward(BlockState state, Direction direction);

	public AttachmentTypes getRenderedRimAttachment(BlockAndTintGetter world, BlockPos pos, BlockState state,
		Direction direction) {
		if (!canHaveFlowToward(state, direction))
			return AttachmentTypes.NONE;

		BlockPos offsetPos = pos.relative(direction);
		BlockState facingState = world.getBlockState(offsetPos);

		if (facingState.getBlock() instanceof ExperiencePumpBlock
			&& facingState.getValue(ExperiencePumpBlock.FACING) == direction.getOpposite())
			return AttachmentTypes.NONE;

		if (facingState.getBlock() instanceof ExperienceEncasedPipeBlock
			&& facingState.getValue(ExperienceEncasedPipeBlock.FACING_TO_PROPERTY_MAP.get(direction.getOpposite())))
			return AttachmentTypes.RIM;

		if (ExperienceTransport.hasEndpoint(world, offsetPos, direction.getOpposite()))
			return AttachmentTypes.DRAIN;

		return AttachmentTypes.RIM;
	}

	@Override
	public void initialize() {
		super.initialize();
		createConnectionData();
	}

	@Override
	public void tick() {
		super.tick();
		Level world = getWorld();
		boolean onServer = !world.isClientSide || blockEntity.isVirtual();
		if (interfaces == null)
			return;

		Collection<ExperienceConnection> connections = interfaces.values();

		if (phase == UpdatePhase.WAIT_FOR_PUMPS) {
			phase = UpdatePhase.FLIP_FLOWS;
			return;
		}

		if (onServer) {
			boolean sendUpdate = false;
			for (ExperienceConnection connection : connections) {
				sendUpdate |= connection.flipFlowsIfPressureReversed();
				connection.manageSource(world, getPos());
			}
			if (sendUpdate)
				blockEntity.notifyUpdate();
		}

		if (phase == UpdatePhase.FLIP_FLOWS) {
			phase = UpdatePhase.IDLE;
			return;
		}

		if (onServer) {
			ExperienceConnection singleSource = null;
			boolean availableFlow = false;
			for (ExperienceConnection connection : connections) {
				if (!connection.getProvidedExperience())
					continue;
				if (!availableFlow) {
					singleSource = connection;
					availableFlow = true;
					continue;
				}
				singleSource = null;
			}

			boolean sendUpdate = false;
			for (ExperienceConnection connection : connections) {
				boolean internalFlow = availableFlow && singleSource != connection;
				sendUpdate |= connection.manageFlows(world, getPos(), internalFlow);
			}

			if (sendUpdate)
				blockEntity.notifyUpdate();
		}

		for (ExperienceConnection connection : connections)
			connection.tickFlowProgress();
	}

	@Override
	public void read(CompoundTag nbt, boolean clientPacket) {
		super.read(nbt, clientPacket);
		if (interfaces == null)
			interfaces = new IdentityHashMap<>();
		for (Direction face : Iterate.directions)
			if (nbt.contains(face.getName()))
				interfaces.computeIfAbsent(face, ExperienceConnection::new);
		if (interfaces.isEmpty()) {
			interfaces = null;
			return;
		}
		interfaces.values()
			.forEach(connection -> connection.deserializeNBT(nbt, clientPacket));
	}

	@Override
	public void write(CompoundTag nbt, boolean clientPacket) {
		super.write(nbt, clientPacket);
		if (clientPacket)
			createConnectionData();
		if (interfaces == null)
			return;
		interfaces.values()
			.forEach(connection -> connection.serializeNBT(nbt));
	}

	public boolean hasProvidedOutwardExperience(Direction side) {
		createConnectionData();
		if (!interfaces.containsKey(side))
			return false;
		ExperienceConnection connection = interfaces.get(side);
		return connection.hasFlow() && connection.flow.get().complete && !connection.flow.get().inbound;
	}

	@Nullable
	public ExperienceConnection getConnection(Direction side) {
		createConnectionData();
		return interfaces.get(side);
	}

	public boolean hasAnyPressure() {
		createConnectionData();
		for (ExperienceConnection connection : interfaces.values())
			if (connection.hasPressure())
				return true;
		return false;
	}

	public void addPressure(Direction side, boolean inbound, float pressure) {
		createConnectionData();
		if (!interfaces.containsKey(side))
			return;
		interfaces.get(side)
			.addPressure(inbound, pressure);
		blockEntity.sendData();
	}

	public void wipePressure() {
		if (interfaces != null)
			for (Direction direction : Iterate.directions) {
				if (!canHaveFlowToward(blockEntity.getBlockState(), direction))
					interfaces.remove(direction);
				else
					interfaces.computeIfAbsent(direction, ExperienceConnection::new);
			}
		phase = UpdatePhase.WAIT_FOR_PUMPS;
		createConnectionData();
		interfaces.values()
			.forEach(ExperienceConnection::wipePressure);
		blockEntity.sendData();
	}

	private void createConnectionData() {
		if (interfaces != null)
			return;
		interfaces = new IdentityHashMap<>();
		for (Direction direction : Iterate.directions)
			if (canHaveFlowToward(blockEntity.getBlockState(), direction))
				interfaces.put(direction, new ExperienceConnection(direction));
	}

	@Override
	public BehaviourType<?> getType() {
		return TYPE;
	}

	public static final WorldAttached<Map<BlockPos, Map<Direction, ExperienceConnection>>> interfaceTransfer =
		new WorldAttached<>($ -> new HashMap<>());

	public enum AttachmentTypes {
		NONE,
		CONNECTION(ComponentPartials.CONNECTION),
		DETAILED_CONNECTION(ComponentPartials.RIM_CONNECTOR),
		RIM(ComponentPartials.RIM_CONNECTOR, ComponentPartials.RIM),
		PARTIAL_RIM(ComponentPartials.RIM),
		DRAIN(ComponentPartials.RIM_CONNECTOR, ComponentPartials.DRAIN),
		PARTIAL_DRAIN(ComponentPartials.DRAIN);

		public final ComponentPartials[] partials;

		AttachmentTypes(ComponentPartials... partials) {
			this.partials = partials;
		}

		public AttachmentTypes withoutConnector() {
			if (this == AttachmentTypes.RIM)
				return AttachmentTypes.PARTIAL_RIM;
			if (this == AttachmentTypes.DRAIN)
				return AttachmentTypes.PARTIAL_DRAIN;
			return this;
		}

		public enum ComponentPartials {
			CONNECTION, RIM_CONNECTOR, RIM, DRAIN;
		}
	}

	public static void cacheFlows(LevelAccessor world, BlockPos pos) {
		ExperienceTransportBehaviour pipe = BlockEntityBehaviour.get(world, pos, ExperienceTransportBehaviour.TYPE);
		if (pipe != null)
			interfaceTransfer.get(world)
				.put(pos, pipe.interfaces);
	}

	public static void loadFlows(LevelAccessor world, BlockPos pos) {
		ExperienceTransportBehaviour pipe = BlockEntityBehaviour.get(world, pos, ExperienceTransportBehaviour.TYPE);
		if (pipe != null)
			pipe.interfaces = interfaceTransfer.get(world)
				.remove(pos);
	}
}
