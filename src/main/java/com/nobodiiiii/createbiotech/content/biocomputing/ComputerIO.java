package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.List;

/** Connects the interpreter to Create's Redstone Links, Display Links and Stock Links. */
public interface ComputerIO {
	int INPUT_SLOTS = 8;
	int OUTPUT_LINES = 8;

	int readRedstone(Channel channel);

	void writeRedstone(Channel channel, int power, int pulseTicks);

	void tuneRedstone(Channel channel, FrequencyPair frequency);

	String readInputSlot(int slotIndex);

	void setOutputLine(int lineIndex, String text);

	void appendOutput(String text);

	void clearOutput();

	void updateDisplay();

	long stockCount(List<String> filters);

	long stockTotal();

	int stockTypes();

	boolean isItem(String reference, boolean allowColor);

	boolean orderStock(String address, List<OrderEntry> items);

	long gameTick();

	record FrequencyPair(String first, String second) {}

	record OrderEntry(String item, long count) {}

	/** Numbered bindings to pairs of Create frequency items. */
	enum Channel {
		ONE, TWO, THREE, FOUR;

		public static final Channel[] VALUES = values();

		public String key() {
			return Integer.toString(ordinal() + 1);
		}
	}
}
