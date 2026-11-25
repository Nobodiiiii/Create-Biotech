package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.List;

import net.minecraft.network.chat.Component;

/** Connects the interpreter to Create's Redstone Links, Display Links and Stock Links. */
public interface ComputerIO {
	int INPUT_SLOTS = 8;
	int OUTPUT_LINES = 8;
	int MAX_WIRELESS_TRANSMITTERS = 4;

	int readRedstone(RedstoneFrequency frequency);

	boolean writeRedstone(RedstoneFrequency frequency, int power, int pulseTicks);

	void tuneRedstone(Channel channel, FrequencyPair frequency);

	String readInputSlot(int slotIndex);

	void setOutputLine(int lineIndex, Component text);

	void appendOutput(Component text);

	void clearOutput();

	void updateDisplay();

	long stockCount(List<String> filters);

	long stockTotal();

	int stockTypes();

	boolean isItem(String reference, boolean allowColor);

	boolean orderStock(String address, List<OrderEntry> items);

	long gameTick();

	sealed interface RedstoneFrequency permits Channel, FrequencyPair {}

	record FrequencyPair(String first, String second) implements RedstoneFrequency {
		public boolean isEmpty() { return first.isEmpty() && second.isEmpty(); }
	}

	record OrderEntry(String item, long count) {}

	/** Frequency bindings from saved programs that used channel numbers. */
	enum Channel implements RedstoneFrequency {
		ONE, TWO, THREE, FOUR;

		public static final Channel[] VALUES = values();
	}
}
