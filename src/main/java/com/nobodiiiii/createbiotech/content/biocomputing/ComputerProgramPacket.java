package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerIO.Channel;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerRuntime.ChannelState;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerRuntime.Diagnostic;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerRuntime.Problem;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerRuntime.Snapshot;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerRuntime.Status;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerRuntime.Variable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public record ComputerProgramPacket(int menuId, Action action, long expectedRevision, Optional<ComputerProgram> program) {
	public enum Action { SAVE, RUN, STOP }
	public enum EditResult { SYNC, ACCEPTED, CONFLICT, REJECTED, LIBRARY_LOCKED, INCOMPLETE }

	public ComputerProgramPacket(RegistryFriendlyByteBuf buffer) {
		this(
			buffer.readVarInt(),
			buffer.readEnum(Action.class),
			buffer.readVarLong(),
			buffer.readBoolean() ? Optional.of(ComputerProgramWire.read(buffer)) : Optional.empty()
		);
	}

	public void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(menuId);
		buffer.writeEnum(action);
		buffer.writeVarLong(expectedRevision);
		buffer.writeBoolean(program.isPresent());
		program.ifPresent(value -> ComputerProgramWire.write(buffer, value));
	}

	public void handle(ServerPlayer player) {
		if (player.containerMenu instanceof ComputerProgramMenu menu && menu.containerId == menuId)
			menu.apply(player, action, expectedRevision, program);
	}

	public record ProgramState(long revision, ComputerProgram saved, @Nullable ComputerProgram execution, ComputerLibrary library) {
		ProgramState(RegistryFriendlyByteBuf buffer) {
			this(buffer.readVarLong(), ComputerProgramWire.read(buffer),
				buffer.readBoolean() ? ComputerProgramWire.read(buffer) : null, buffer.readEnum(ComputerLibrary.class));
		}

		void write(RegistryFriendlyByteBuf buffer) {
			buffer.writeVarLong(revision);
			ComputerProgramWire.write(buffer, saved);
			buffer.writeBoolean(execution != null);
			if (execution != null)
				ComputerProgramWire.write(buffer, execution);
			buffer.writeEnum(library);
		}
	}

	public record ProgramUpdate(int menuId, EditResult result, ProgramState state) {
		public ProgramUpdate(RegistryFriendlyByteBuf buffer) {
			this(buffer.readVarInt(), buffer.readEnum(EditResult.class), new ProgramState(buffer));
		}

		public void write(RegistryFriendlyByteBuf buffer) {
			buffer.writeVarInt(menuId);
			buffer.writeEnum(result);
			state.write(buffer);
		}

		public void handle(Player player) {
			if (player.containerMenu instanceof ComputerProgramMenu menu && menu.containerId == menuId)
				menu.receive(this);
		}
	}

	public record StatusUpdate(int menuId, Snapshot snapshot) {
		public StatusUpdate(RegistryFriendlyByteBuf buffer) {
			this(buffer.readVarInt(), readSnapshot(buffer));
		}

		public void write(RegistryFriendlyByteBuf buffer) {
			buffer.writeVarInt(menuId);
			buffer.writeEnum(snapshot.status());
			buffer.writeVarInt(snapshot.activeBlockIndex());
			Diagnostic diagnostic = snapshot.diagnostic();
			buffer.writeBoolean(diagnostic != null);
			if (diagnostic != null) {
				buffer.writeEnum(diagnostic.problem());
				buffer.writeVarInt(diagnostic.blockIndex());
			}

			buffer.writeVarInt(snapshot.output().size());
			for (Component line : snapshot.output())
				ComponentSerialization.STREAM_CODEC.encode(buffer, line);

			buffer.writeVarInt(snapshot.variables().size());
			for (Variable variable : snapshot.variables()) {
				buffer.writeUtf(variable.name(), ComputerRuntime.MAX_VALUE_LENGTH);
				ComponentSerialization.STREAM_CODEC.encode(buffer, variable.value());
			}

			buffer.writeVarInt(snapshot.channels().size());
			for (ChannelState channel : snapshot.channels()) {
				buffer.writeUtf(channel.first(), ComputerRuntime.MAX_VALUE_LENGTH);
				buffer.writeUtf(channel.second(), ComputerRuntime.MAX_VALUE_LENGTH);
				buffer.writeByte(channel.input());
				buffer.writeByte(channel.output());
			}
			buffer.writeBoolean(snapshot.stockLinkBound());

			List<String> inputSlots = snapshot.inputSlots();
			for (int i = 0; i < ComputerIO.INPUT_SLOTS; i++)
				buffer.writeUtf(inputSlots.get(i), ComputerRuntime.MAX_VALUE_LENGTH);
		}

		private static Snapshot readSnapshot(RegistryFriendlyByteBuf buffer) {
			Status status = buffer.readEnum(Status.class);
			int activeBlockIndex = Math.clamp(buffer.readVarInt(), -1, ComputerProgram.MAX_BLOCKS - 1);
			Diagnostic diagnostic = buffer.readBoolean()
				? new Diagnostic(buffer.readEnum(Problem.class), Math.clamp(buffer.readVarInt(), -1, ComputerProgram.MAX_BLOCKS - 1))
				: null;

			int lineCount = ComputerProgramWire.readCount(buffer, ComputerRuntime.MAX_OUTPUT_LINES);
			List<Component> lines = new ArrayList<>(lineCount);
			for (int i = 0; i < lineCount; i++)
				lines.add(ComponentSerialization.STREAM_CODEC.decode(buffer));

			int variableCount = ComputerProgramWire.readCount(buffer, ComputerRuntime.MAX_VARIABLES);
			List<Variable> variables = new ArrayList<>(variableCount);
			for (int i = 0; i < variableCount; i++)
				variables.add(new Variable(buffer.readUtf(ComputerRuntime.MAX_VALUE_LENGTH),
					ComponentSerialization.STREAM_CODEC.decode(buffer)));

			int channelCount = ComputerProgramWire.readCount(buffer, Channel.VALUES.length + ComputerIO.MAX_WIRELESS_TRANSMITTERS);
			List<ChannelState> channels = new ArrayList<>(channelCount);
			for (int i = 0; i < channelCount; i++)
				channels.add(new ChannelState(buffer.readUtf(ComputerRuntime.MAX_VALUE_LENGTH),
					buffer.readUtf(ComputerRuntime.MAX_VALUE_LENGTH), Math.clamp(buffer.readByte(), 0, 15),
					Math.clamp(buffer.readByte(), 0, 15)));
			boolean stockLinkBound = buffer.readBoolean();

			List<String> inputSlots = new ArrayList<>(ComputerIO.INPUT_SLOTS);
			for (int i = 0; i < ComputerIO.INPUT_SLOTS; i++)
				inputSlots.add(buffer.readUtf(ComputerRuntime.MAX_VALUE_LENGTH));

			return new Snapshot(status, activeBlockIndex, diagnostic, lines, variables, channels, inputSlots, stockLinkBound);
		}

		public void handle(Player player) {
			if (player.containerMenu instanceof ComputerProgramMenu menu && menu.containerId == menuId)
				menu.receive(snapshot);
		}
	}
}
