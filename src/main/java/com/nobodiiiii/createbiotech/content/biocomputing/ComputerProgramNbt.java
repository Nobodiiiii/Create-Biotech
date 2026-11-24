package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.ArrayList;
import java.util.List;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Block;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Input;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Literal;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Op;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Single-version bounded NBT persistence for Scratch programs, keyed by stable Op names. */
final class ComputerProgramNbt {
	static final int FORMAT_VERSION = 1;

	private static final String KEY_VERSION = "Version";
	private static final String KEY_COMMANDS = "Commands";
	private static final String KEY_OP = "Op";
	private static final String KEY_INPUTS = "Inputs";
	private static final String KEY_BODIES = "Bodies";
	private static final String KEY_LITERAL = "Literal";
	private static final String KEY_BLOCK = "Block";

	private ComputerProgramNbt() {}

	static CompoundTag write(ComputerProgram program) {
		CompoundTag tag = new CompoundTag();
		tag.putInt(KEY_VERSION, FORMAT_VERSION);
		tag.put(KEY_COMMANDS, writeCommands(program.commands()));
		return tag;
	}

	static ComputerProgram read(CompoundTag tag) {
		if (tag == null || tag.getInt(KEY_VERSION) != FORMAT_VERSION || !tag.contains(KEY_COMMANDS, Tag.TAG_LIST))
			return ComputerProgram.EMPTY;
		try {
			Reader reader = new Reader();
			return new ComputerProgram(reader.commands(tag.getList(KEY_COMMANDS, Tag.TAG_COMPOUND), 1));
		} catch (IllegalArgumentException ignored) {
			return ComputerProgram.EMPTY;
		}
	}

	private static ListTag writeCommands(List<Block> commands) {
		ListTag list = new ListTag();
		for (Block command : commands)
			list.add(writeBlock(command));
		return list;
	}

	private static CompoundTag writeBlock(Block block) {
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY_OP, block.op().key());
		if (!block.inputs().isEmpty()) {
			ListTag inputs = new ListTag();
			for (Input input : block.inputs()) {
				CompoundTag entry = new CompoundTag();
				switch (input) {
					case Literal literal -> entry.putString(KEY_LITERAL, literal.text());
					case Block reporter -> entry.put(KEY_BLOCK, writeBlock(reporter));
				}
				inputs.add(entry);
			}
			tag.put(KEY_INPUTS, inputs);
		}
		if (!block.bodies().isEmpty()) {
			ListTag bodies = new ListTag();
			for (List<Block> body : block.bodies())
				bodies.add(writeCommands(body));
			tag.put(KEY_BODIES, bodies);
		}
		return tag;
	}

	private static final class Reader {
		private int remaining = ComputerProgram.MAX_BLOCKS;

		private List<Block> commands(ListTag list, int depth) {
			if (list.size() > remaining)
				throw new IllegalArgumentException("Too many commands");
			List<Block> commands = new ArrayList<>(list.size());
			for (int i = 0; i < list.size(); i++)
				commands.add(block(list.getCompound(i), depth));
			return commands;
		}

		private Block block(CompoundTag tag, int depth) {
			if (depth > ComputerProgram.MAX_DEPTH || remaining-- <= 0)
				throw new IllegalArgumentException("Program exceeds block or depth limit");
			Op op = Op.byKey(tag.getString(KEY_OP));
			if (op == null)
				throw new IllegalArgumentException("Unknown computer op");

			ListTag inputTags = tag.getList(KEY_INPUTS, Tag.TAG_COMPOUND);
			if (inputTags.size() != op.slots().size())
				throw new IllegalArgumentException("Mismatched input count for " + op);
			List<Input> inputs = new ArrayList<>(inputTags.size());
			for (int i = 0; i < inputTags.size(); i++) {
				CompoundTag entry = inputTags.getCompound(i);
				if (entry.contains(KEY_BLOCK, Tag.TAG_COMPOUND)) {
					inputs.add(block(entry.getCompound(KEY_BLOCK), depth + 1));
				} else {
					String literal = entry.getString(KEY_LITERAL);
					if (literal.length() > ComputerProgram.MAX_LITERAL_LENGTH)
						throw new IllegalArgumentException("Literal exceeds max length");
					inputs.add(new Literal(literal));
				}
			}

			ListTag bodyTags = tag.getList(KEY_BODIES, Tag.TAG_LIST);
			if (bodyTags.size() != op.bodyCount())
				throw new IllegalArgumentException("Mismatched body count for " + op);
			List<List<Block>> bodies = new ArrayList<>(bodyTags.size());
			for (int i = 0; i < bodyTags.size(); i++)
				bodies.add(commands(bodyTags.getList(i), depth + 1));
			return new Block(op, inputs, bodies);
		}
	}
}
