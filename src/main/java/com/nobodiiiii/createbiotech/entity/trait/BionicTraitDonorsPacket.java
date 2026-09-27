package com.nobodiiiii.createbiotech.entity.trait;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Incremental, bounded response; no entity NBT or client/JEI classes cross this boundary. */
public record BionicTraitDonorsPacket(long generation, int offset, int checked, int total, int failures,
	boolean complete, List<Entry> entries) {
	public static final int MAX_ENTRIES = 128;

	public BionicTraitDonorsPacket {
		entries = List.copyOf(entries);
		if (offset < 0 || checked < 0 || total < checked || failures < 0 || failures > checked
			|| entries.size() > MAX_ENTRIES)
			throw new IllegalArgumentException("Invalid bionic donor index response");
	}

	public BionicTraitDonorsPacket(RegistryFriendlyByteBuf buffer) {
		this(buffer.readLong(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
			buffer.readBoolean(), readEntries(buffer));
	}

	public void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeLong(generation);
		buffer.writeVarInt(offset);
		buffer.writeVarInt(checked);
		buffer.writeVarInt(total);
		buffer.writeVarInt(failures);
		buffer.writeBoolean(complete);
		buffer.writeVarInt(entries.size());
		for (Entry entry : entries) {
			buffer.writeResourceLocation(entry.entity());
			buffer.writeLong(entry.traits());
		}
	}

	private static List<Entry> readEntries(RegistryFriendlyByteBuf buffer) {
		int count = buffer.readVarInt();
		if (count < 0 || count > MAX_ENTRIES)
			throw new IllegalArgumentException("Too many bionic donor entries");
		List<Entry> entries = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
			entries.add(new Entry(buffer.readResourceLocation(), buffer.readLong()));
		return entries;
	}

	public record Entry(ResourceLocation entity, long traits) {
		public boolean provides(BionicTrait trait) {
			return (traits & (1L << trait.ordinal())) != 0;
		}
	}
}
