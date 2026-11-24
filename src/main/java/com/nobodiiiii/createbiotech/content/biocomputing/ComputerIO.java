package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

/** Connects the interpreter to Create's Redstone Links, Display Links and Stock Links. */
public interface ComputerIO {
	int INPUT_SLOTS = 8;
	int OUTPUT_LINES = 8;

	int readRedstone(Channel channel);

	void writeRedstone(Channel channel, int power, int pulseTicks);

	void tuneRedstone(Channel channel, String first, String second);

	String readInputSlot(int slotIndex);

	void setOutputLine(int lineIndex, String text);

	void appendOutput(String text);

	void clearOutput();

	void updateDisplay();

	long stockCount(String filter);

	long stockTotal();

	int stockTypes();

	boolean orderStock(String address, String items);

	long gameTick();

	/** Numbered bindings to pairs of Create frequency items. */
	enum Channel {
		ONE, TWO, THREE, FOUR;

		public static final Channel[] VALUES = values();

		public String key() {
			return Integer.toString(ordinal() + 1);
		}

		@Nullable
		static Channel parse(String value) {
			return switch (value) {
				case "1" -> ONE;
				case "2" -> TWO;
				case "3" -> THREE;
				case "4" -> FOUR;
				default -> null;
			};
		}

		public static String next(String value) {
			Channel current = parse(value);
			return (current == null ? ONE : VALUES[(current.ordinal() + 1) % VALUES.length]).key();
		}

		static String migrate(String value) {
			return switch (value.toLowerCase(Locale.ROOT)) {
				case "left" -> "1";
				case "right" -> "2";
				case "back" -> "3";
				case "top" -> "4";
				default -> value;
			};
		}
	}
}
