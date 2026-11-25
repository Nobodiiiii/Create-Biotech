package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerIO.Channel;
import com.nobodiiiii.createbiotech.content.biocomputing.ComputerIO.FrequencyPair;
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
	private final Radio[] radios = new Radio[Channel.VALUES.length];

	ComputerWireless(ComputerBlockEntity computer) {
		this.computer = computer;
		for (Channel channel : Channel.VALUES)
			radios[channel.ordinal()] = new Radio();
	}

	int read(Channel channel) {
		return radios[channel.ordinal()].input;
	}

	boolean write(Channel channel, int power, int pulseTicks, long now) {
		Radio radio = radios[channel.ordinal()];
		return radio.key != null && radio.setOutput(power, power == 0 || pulseTicks == 0 ? 0 : now + pulseTicks);
	}

	boolean tune(Channel channel, FrequencyPair requested) {
		Radio radio = radios[channel.ordinal()];
		FrequencyPair pair = new FrequencyPair(requested.first().strip(), requested.second().strip());
		if (pair.first().length() > ComputerRuntime.MAX_VALUE_LENGTH || pair.second().length() > ComputerRuntime.MAX_VALUE_LENGTH
			|| pair.equals(radio.frequency))
			return false;
		Frequency left = frequency(pair.first());
		Frequency right = frequency(pair.second());
		if (left == null || right == null)
			return false;

		radio.frequency = pair;
		radio.key = left == Frequency.EMPTY && right == Frequency.EMPTY ? null : Couple.create(left, right);
		if (radio.key == null)
			radio.setOutput(0, 0);
		radio.input = radio.sample(computer.getLevel());
		return true;
	}

	boolean tickInputs(ServerLevel level, long now) {
		boolean changed = false;
		for (Radio radio : radios) {
			if (radio.pulseUntil != 0 && now >= radio.pulseUntil) {
				radio.setOutput(0, 0);
				radio.publish(level);
				changed = true;
			}
		}
		for (Radio radio : radios) {
			int input = radio.sample(level);
			if (input != radio.input) {
				radio.input = input;
				changed = true;
			}
		}
		return changed;
	}

	boolean resetOutputs() {
		boolean changed = false;
		for (Radio radio : radios)
			changed |= radio.setOutput(0, 0);
		flushOutputs();
		return changed;
	}

	void flushOutputs() {
		if (computer.getLevel() instanceof ServerLevel level)
			for (Radio radio : radios)
				radio.publish(level);
	}

	void disconnect() {
		if (computer.getLevel() instanceof ServerLevel level)
			for (Radio radio : radios)
				radio.disconnect(level);
	}

	List<ComputerRuntime.ChannelState> snapshot() {
		List<ComputerRuntime.ChannelState> states = new ArrayList<>(radios.length);
		for (Radio radio : radios)
			states.add(new ComputerRuntime.ChannelState(radio.frequency.first(), radio.frequency.second(), radio.input, radio.output));
		return states;
	}

	ListTag save() {
		ListTag list = new ListTag();
		for (Radio radio : radios) {
			CompoundTag tag = new CompoundTag();
			tag.putString("First", radio.frequency.first());
			tag.putString("Second", radio.frequency.second());
			// Pulses expire when unloaded. Steady outputs are restored on the next server tick.
			tag.putInt("Power", radio.pulseUntil == 0 ? radio.output : 0);
			list.add(tag);
		}
		return list;
	}

	void load(ListTag list) {
		disconnect();
		for (Channel channel : Channel.VALUES) {
			Radio radio = radios[channel.ordinal()];
			radio.frequency = EMPTY_PAIR;
			radio.key = null;
			radio.input = 0;
			radio.setOutput(0, 0);
			CompoundTag tag = list.getCompound(channel.ordinal());
			tune(channel, new FrequencyPair(tag.getString("First"), tag.getString("Second")));
			if (radio.key != null)
				radio.setOutput(Math.clamp(tag.getInt("Power"), 0, 15), 0);
		}
	}

	@Nullable
	private static Frequency frequency(String text) {
		if (text.isEmpty())
			return Frequency.EMPTY;
		ItemStack stack = ComputerItemReference.read(text, true);
		return stack.isEmpty() ? null : Frequency.of(stack);
	}

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
