package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Block;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Input;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Literal;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Op;

/** Bounded clipboard JSON import/export for sharing Scratch programs. */
public final class ComputerProgramJson {
	private static final String FORMAT_ID = "create_biotech.scratch";
	private static final int FORMAT_VERSION = 1;
	// Blocks can contain multiple literals and JSON can escape each UTF-16 code unit as six characters.
	private static final int MAX_JSON_LENGTH = 192 * 1024;
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private ComputerProgramJson() {}

	public static String toJson(ComputerProgram program) {
		JsonObject root = new JsonObject();
		root.addProperty("format", FORMAT_ID);
		root.addProperty("version", FORMAT_VERSION);
		root.add("commands", writeCommands(program.commands()));
		return GSON.toJson(root);
	}

	public static Optional<ComputerProgram> fromJson(String json) {
		if (json == null || json.isBlank() || json.length() > MAX_JSON_LENGTH)
			return Optional.empty();
		try {
			JsonElement parsed = JsonParser.parseString(json);
			if (!parsed.isJsonObject())
				return Optional.empty();
			JsonObject root = parsed.getAsJsonObject();
			if (!FORMAT_ID.equals(stringOrNull(root.get("format"))))
				return Optional.empty();
			JsonElement version = root.get("version");
			if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()
				|| version.getAsInt() != FORMAT_VERSION)
				return Optional.empty();
			JsonElement commands = root.get("commands");
			if (commands == null || !commands.isJsonArray())
				return Optional.empty();
			Reader reader = new Reader();
			return Optional.of(new ComputerProgram(reader.commands(commands.getAsJsonArray(), 1)));
		} catch (JsonParseException | IllegalArgumentException | IllegalStateException | UnsupportedOperationException ignored) {
			return Optional.empty();
		}
	}

	private static JsonArray writeCommands(List<Block> commands) {
		JsonArray array = new JsonArray();
		for (Block command : commands)
			array.add(writeBlock(command));
		return array;
	}

	private static JsonObject writeBlock(Block block) {
		JsonObject obj = new JsonObject();
		obj.addProperty("op", block.op().key());
		if (!block.inputs().isEmpty()) {
			JsonArray inputs = new JsonArray();
			for (Input input : block.inputs()) {
				switch (input) {
					case Literal literal -> inputs.add(literal.text());
					case Block reporter -> inputs.add(writeBlock(reporter));
				}
			}
			obj.add("inputs", inputs);
		}
		if (!block.bodies().isEmpty()) {
			JsonArray bodies = new JsonArray();
			for (List<Block> body : block.bodies())
				bodies.add(writeCommands(body));
			obj.add("bodies", bodies);
		}
		return obj;
	}

	private static String stringOrNull(JsonElement element) {
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
			? element.getAsString() : null;
	}

	private static final class Reader {
		private int remaining = ComputerProgram.MAX_BLOCKS;

		private List<Block> commands(JsonArray array, int depth) {
			if (array.size() > remaining)
				throw new IllegalArgumentException("Too many commands");
			List<Block> commands = new ArrayList<>(array.size());
			for (JsonElement element : array) {
				if (!element.isJsonObject())
					throw new IllegalArgumentException("Expected command object");
				commands.add(block(element.getAsJsonObject(), depth));
			}
			return commands;
		}

		private Block block(JsonObject obj, int depth) {
			if (depth > ComputerProgram.MAX_DEPTH || remaining-- <= 0)
				throw new IllegalArgumentException("Program exceeds block or depth limit");
			Op op = Op.byKey(stringOrNull(obj.get("op")));
			if (op == null)
				throw new IllegalArgumentException("Unknown op");

			List<Input> inputs = new ArrayList<>(op.slots().size());
			JsonElement inputsEl = obj.get("inputs");
			if (op.slots().isEmpty()) {
				if (inputsEl != null && (!inputsEl.isJsonArray() || !inputsEl.getAsJsonArray().isEmpty()))
					throw new IllegalArgumentException("Unexpected inputs");
			} else {
				if (inputsEl == null || !inputsEl.isJsonArray() || inputsEl.getAsJsonArray().size() != op.slots().size())
					throw new IllegalArgumentException("Mismatched input count");
				for (JsonElement element : inputsEl.getAsJsonArray()) {
					if (element.isJsonObject()) {
						inputs.add(block(element.getAsJsonObject(), depth + 1));
					} else {
						String literal = stringOrNull(element);
						if (literal == null || literal.length() > ComputerProgram.MAX_LITERAL_LENGTH)
							throw new IllegalArgumentException("Invalid literal");
						inputs.add(new Literal(literal));
					}
				}
			}

			List<List<Block>> bodies = new ArrayList<>(op.bodyCount());
			JsonElement bodiesEl = obj.get("bodies");
			if (op.bodyCount() == 0) {
				if (bodiesEl != null && (!bodiesEl.isJsonArray() || !bodiesEl.getAsJsonArray().isEmpty()))
					throw new IllegalArgumentException("Unexpected bodies");
			} else {
				if (bodiesEl == null || !bodiesEl.isJsonArray() || bodiesEl.getAsJsonArray().size() != op.bodyCount())
					throw new IllegalArgumentException("Mismatched body count");
				for (JsonElement element : bodiesEl.getAsJsonArray()) {
					if (!element.isJsonArray())
						throw new IllegalArgumentException("Expected body array");
					bodies.add(commands(element.getAsJsonArray(), depth + 1));
				}
			}
			return new Block(op, inputs, bodies);
		}
	}
}
