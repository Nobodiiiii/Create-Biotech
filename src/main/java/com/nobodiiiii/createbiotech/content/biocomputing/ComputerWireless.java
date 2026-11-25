package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerIO.Channel;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerIO.FrequencyPair;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerIO.RedstoneFrequency;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;

import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

final class ComputerWireless {
	private static final FrequencyPair EMPTY_PAIR = new FrequencyPair("", "");
	private final ComputerBlockEntity computer;
	private final Radio[] legacyRadios = new Radio[Channel.VALUES.length];
	private final Map<Couple<Frequency>, Radio> transmitters = new LinkedHashMap<>();

	ComputerWireless(ComputerBlockEntity computer) {
		this.computer = computer;
		for (Channel channel : Channel.VALUES)
			legacyRadios[channel.ordinal()] = new Radio();
	}

	int read(RedstoneFrequency address) {
		if (address instanceof Channel channel)
			return legacyRadios[channel.ordinal()].input;
		Couple<Frequency> key = networkKey((FrequencyPair) address);
		if (key == null)
			return 0;
		Radio transmitter = transmitters.get(key);
		if (transmitter != null)
			return transmitter.sample(computer.getLevel());
		Radio receiver = new Radio();
		receiver.key = key;
		return receiver.sample(computer.getLevel());
	}

	WriteResult write(RedstoneFrequency address, int power, int pulseTicks, long now) {
		long until = power == 0 || pulseTicks == 0 ? 0 : now + pulseTicks;
		if (address instanceof Channel channel) {
			Radio radio = legacyRadios[channel.ordinal()];
			if (radio.key == null)
				return WriteResult.UNCHANGED;
			if (radio.output == 0 && power > 0 && atTransmitterLimit())
				return WriteResult.FULL;
			return radio.setOutput(power, until) ? WriteResult.CHANGED : WriteResult.UNCHANGED;
		}
		FrequencyPair pair = (FrequencyPair) address;
		Couple<Frequency> key = networkKey(pair);
		if (key == null)
			return WriteResult.UNCHANGED;
		Radio radio = transmitters.get(key);
		if (power == 0) {
			if (radio == null)
				return WriteResult.UNCHANGED;
			if (computer.getLevel() instanceof ServerLevel level)
				radio.disconnect(level);
			transmitters.remove(key);
			return WriteResult.CHANGED;
		}
		if (radio == null) {
			if (atTransmitterLimit())
				return WriteResult.FULL;
			radio = new Radio();
			radio.key = key;
			radio.frequency = pair;
			transmitters.put(key, radio);
		}
		return radio.setOutput(power, until) ? WriteResult.CHANGED : WriteResult.UNCHANGED;
	}

	private boolean atTransmitterLimit() {
		int count = transmitters.size();
		for (Radio radio : legacyRadios)
			if (radio.output > 0)
				count++;
		return count >= ComputerIO.MAX_WIRELESS_TRANSMITTERS;
	}

	boolean tune(Channel channel, FrequencyPair requested) {
		Radio radio = legacyRadios[channel.ordinal()];
		FrequencyPair pair = new FrequencyPair(requested.first().strip(), requested.second().strip());
		if (pair.equals(radio.frequency))
			return false;
		Couple<Frequency> key = networkKey(pair);
		if (key == null && !pair.isEmpty())
			return false;

		radio.frequency = pair;
		radio.key = key;
		if (key == null)
			radio.setOutput(0, 0);
		radio.input = radio.sample(computer.getLevel());
		return true;
	}

	boolean tickInputs(ServerLevel level, long now) {
		boolean changed = false;
		for (Radio radio : legacyRadios)
			changed |= radio.expirePulse(level, now);
		changed |= transmitters.values().removeIf(radio -> radio.expirePulse(level, now));
		for (Radio radio : legacyRadios)
			changed |= radio.updateInput(level);
		for (Radio radio : transmitters.values())
			changed |= radio.updateInput(level);
		return changed;
	}

	boolean resetOutputs() {
		boolean changed = false;
		for (Radio radio : legacyRadios)
			changed |= radio.setOutput(0, 0);
		for (Radio radio : transmitters.values())
			changed |= radio.setOutput(0, 0);
		flushOutputs();
		transmitters.clear();
		return changed;
	}

	void flushOutputs() {
		if (computer.getLevel() instanceof ServerLevel level) {
			for (Radio radio : legacyRadios)
				radio.publish(level);
			for (Radio radio : transmitters.values())
				radio.publish(level);
		}
	}

	void disconnect() {
		if (computer.getLevel() instanceof ServerLevel level) {
			for (Radio radio : legacyRadios)
				radio.disconnect(level);
			for (Radio radio : transmitters.values())
				radio.disconnect(level);
		}
	}

	List<ComputerRuntime.ChannelState> snapshot() {
		List<ComputerRuntime.ChannelState> states = new ArrayList<>(legacyRadios.length + transmitters.size());
		for (Radio radio : legacyRadios)
			if (radio.key != null)
				states.add(radio.state());
		for (Radio radio : transmitters.values())
			states.add(radio.state());
		return states;
	}

	ListTag save() {
		ListTag list = new ListTag();
		for (Radio radio : legacyRadios)
			list.add(radio.save());
		return list;
	}

	ListTag saveTransmitters() {
		ListTag list = new ListTag();
		for (Radio radio : transmitters.values())
			if (radio.pulseUntil == 0)
				list.add(radio.save());
		return list;
	}

	void load(ListTag list, ListTag savedTransmitters) {
		disconnect();
		transmitters.clear();
		for (Channel channel : Channel.VALUES) {
			Radio radio = legacyRadios[channel.ordinal()];
			radio.frequency = EMPTY_PAIR;
			radio.key = null;
			radio.input = 0;
			radio.setOutput(0, 0);
			CompoundTag tag = list.getCompound(channel.ordinal());
			tune(channel, savedFrequency(tag));
			if (radio.key != null)
				radio.setOutput(Math.clamp(tag.getInt("Power"), 0, 15), 0);
		}
		for (int i = 0; i < Math.min(savedTransmitters.size(), ComputerIO.MAX_WIRELESS_TRANSMITTERS); i++) {
			CompoundTag tag = savedTransmitters.getCompound(i);
			int power = Math.clamp(tag.getInt("Power"), 0, 15);
			if (power != 0)
				write(savedFrequency(tag), power, 0, 0);
		}
	}

	private static FrequencyPair savedFrequency(CompoundTag tag) {
		return new FrequencyPair(tag.getString("First"), tag.getString("Second"));
	}

	@Nullable
	private static Couple<Frequency> networkKey(FrequencyPair pair) {
		if (pair.isEmpty() || pair.first().length() > ComputerRuntime.MAX_VALUE_LENGTH
			|| pair.second().length() > ComputerRuntime.MAX_VALUE_LENGTH)
			return null;
		Frequency left = frequency(pair.first());
		Frequency right = frequency(pair.second());
		return left == null || right == null ? null : Couple.create(left, right);
	}

	@Nullable
	private static Frequency frequency(String text) {
		if (text.isEmpty())
			return Frequency.EMPTY;
		ItemStack stack = ComputerItemReference.read(text, true);
		return stack.isEmpty() ? null : Frequency.of(stack);
	}

	enum WriteResult { UNCHANGED, CHANGED, FULL }

	private final class Radio implements IRedstoneLinkable {
		private FrequencyPair frequency = EMPTY_PAIR;
		@Nullable
		private Couple<Frequency> key;
		@Nullable
		private Couple<Frequency> publishedKey;
		private int input;
		private int output;
		private int publishedPower;
		private long pulseUntil;

		ComputerRuntime.ChannelState state() {
			return new ComputerRuntime.ChannelState(frequency.first(), frequency.second(), input, output);
		}

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putString("First", frequency.first());
			tag.putString("Second", frequency.second());
			tag.putInt("Power", pulseUntil == 0 ? output : 0);
			return tag;
		}

		boolean expirePulse(ServerLevel level, long now) {
			if (pulseUntil == 0 || now < pulseUntil)
				return false;
			setOutput(0, 0);
			publish(level);
			return true;
		}

		boolean updateInput(ServerLevel level) {
			int next = sample(level);
			if (next == input)
				return false;
			input = next;
			return true;
		}

		boolean setOutput(int power, long until) {
			if (output == power && pulseUntil == until)
				return false;
			output = power;
			pulseUntil = until;
			return true;
		}

		int sample(@Nullable Level level) {
			if (key == null || !(level instanceof ServerLevel))
				return 0;
			Set<IRedstoneLinkable> network = Create.REDSTONE_LINK_NETWORK_HANDLER.networksIn(level).get(key);
			if (network == null)
				return 0;
			int max = 0;
			for (IRedstoneLinkable source : network) {
				if (!RedstoneLinkNetworkHandler.withinRange(this, source) || !source.isAlive())
					continue;
				max = Math.max(max, source.getTransmittedStrength());
				if (max >= 15)
					return 15;
			}
			return max;
		}

		void publish(ServerLevel level) {
			if (publishedKey != null && (!Objects.equals(publishedKey, key) || output == 0))
				disconnect(level);
			if (key == null || output == 0)
				return;
			if (publishedKey == null) {
				publishedKey = key;
				publishedPower = output;
				Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, this);
			} else if (publishedPower != output) {
				publishedPower = output;
				Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, this);
			}
		}

		void disconnect(ServerLevel level) {
			if (publishedKey != null) {
				Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, this);
				publishedKey = null;
				publishedPower = 0;
			}
		}

		@Override public int getTransmittedStrength() { return publishedPower; }
		@Override public void setReceivedStrength(int power) {}
		@Override public boolean isListening() { return false; }
		@Override public boolean isAlive() { return !computer.isRemoved() && computer.hasLevel(); }
		@Override public Couple<Frequency> getNetworkKey() { return Objects.requireNonNull(publishedKey); }
		@Override public BlockPos getLocation() { return computer.getBlockPos(); }
	}
}
