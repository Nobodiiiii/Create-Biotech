package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.ArrayList;
import java.util.List;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Block;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Input;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Literal;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Op;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

/** Bounded transport for the menu and run action. */
final class ComputerProgramWire {
	private ComputerProgramWire() {}

	static ComputerProgram read(FriendlyByteBuf buffer) {
		try {
			return new ComputerProgram(new Reader(buffer).commands(1));
		} catch (IllegalArgumentException invalid) {
			throw new DecoderException("Invalid computer program", invalid);
		}
	}

	static void write(FriendlyByteBuf buffer, ComputerProgram program) {
		writeCommands(buffer, program.commands());
	}

	private static void writeCommands(FriendlyByteBuf buffer, List<Block> commands) {
		buffer.writeVarInt(commands.size());
		for (Block command : commands)
			writeBlock(buffer, command);
	}

	private static void writeBlock(FriendlyByteBuf buffer, Block block) {
		buffer.writeEnum(block.op());
		for (Input input : block.inputs()) {
			if (input instanceof Block reporter) {
				buffer.writeBoolean(true);
				writeBlock(buffer, reporter);
			} else {
				buffer.writeBoolean(false);
				buffer.writeUtf(((Literal) input).text(), ComputerProgram.MAX_LITERAL_LENGTH);
			}
		}
		for (List<Block> body : block.bodies())
			writeCommands(buffer, body);
	}

	static int readCount(FriendlyByteBuf buffer, int limit) {
		int size = buffer.readVarInt();
		if (size < 0 || size > limit)
			throw new DecoderException("Invalid computer list size");
		return size;
	}

	private static final class Reader {
		private final FriendlyByteBuf buffer;
		private int remaining = ComputerProgram.MAX_BLOCKS;

		private Reader(FriendlyByteBuf buffer) {
			this.buffer = buffer;
		}

		private List<Block> commands(int depth) {
			int size = readCount(buffer, remaining);
			List<Block> commands = new ArrayList<>(size);
			for (int i = 0; i < size; i++)
				commands.add(block(depth));
			return commands;
		}

		private Block block(int depth) {
			if (depth > ComputerProgram.MAX_DEPTH || remaining-- <= 0)
				throw new DecoderException("Computer program exceeds its block or nesting limit");
			Op op = buffer.readEnum(Op.class);
			List<Input> inputs = new ArrayList<>(op.slots().size());
			for (int i = 0; i < op.slots().size(); i++) {
				inputs.add(buffer.readBoolean() ? block(depth + 1)
					: new Literal(buffer.readUtf(ComputerProgram.MAX_LITERAL_LENGTH)));
			}
			List<List<Block>> bodies = new ArrayList<>(op.bodyCount());
			for (int i = 0; i < op.bodyCount(); i++)
				bodies.add(commands(depth + 1));
			return new Block(op, inputs, bodies);
		}
	}
}
