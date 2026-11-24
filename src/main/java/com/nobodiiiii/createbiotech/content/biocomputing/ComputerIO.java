package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;

/** World-agnostic I/O boundary connecting the Scratch runtime to native Redstone and Create Display Links. */
public interface ComputerIO {
	int PORT_COUNT = Port.VALUES.length;
	int INPUT_SLOTS = 8;
	int OUTPUT_LINES = 8;

	int readRedstone(Port port);

	void writeRedstone(Port port, int power);

	String readInputSlot(int slotIndex);

	void setOutputLine(int lineIndex, String text);

	void appendOutput(String text);

	void clearOutput();

	void updateDisplay();

	long gameTick();

	ComputerIO NOOP = new ComputerIO() {
		@Override public int readRedstone(Port port) { return 0; }
		@Override public void writeRedstone(Port port, int power) {}
		@Override public String readInputSlot(int slotIndex) { return ""; }
		@Override public void setOutputLine(int lineIndex, String text) {}
		@Override public void appendOutput(String text) {}
		@Override public void clearOutput() {}
		@Override public void updateDisplay() {}
		@Override public long gameTick() { return 0L; }
	};

	/** Relative redstone sides, defined from the viewpoint of a player facing the computer's front screen. */
	enum Port {
		LEFT, RIGHT, BACK, TOP;

		public static final Port[] VALUES = values();

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		public Port next() {
			return VALUES[(ordinal() + 1) % VALUES.length];
		}

		public Direction worldDirection(Direction facing) {
			return switch (this) {
				case LEFT -> facing.getClockWise();
				case RIGHT -> facing.getCounterClockWise();
				case BACK -> facing.getOpposite();
				case TOP -> Direction.UP;
			};
		}

		@Nullable
		public static Port parse(String value) {
			if (value == null)
				return null;
			return switch (value.toLowerCase(Locale.ROOT)) {
				case "left" -> LEFT;
				case "right" -> RIGHT;
				case "back" -> BACK;
				case "top" -> TOP;
				default -> null;
			};
		}

		@Nullable
		public static Port fromWorldDirection(Direction facing, Direction worldFace) {
			if (worldFace == Direction.UP)
				return TOP;
			if (worldFace == facing.getClockWise())
				return LEFT;
			if (worldFace == facing.getCounterClockWise())
				return RIGHT;
			if (worldFace == facing.getOpposite())
				return BACK;
			return null;
		}
	}
}
