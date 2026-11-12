package com.nobodiiiii.createbiotech.entity.trait;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
			EnumSet<BionicTrait> traits = EnumSet.noneOf(BionicTrait.class);
			traits.addAll(entry.traits());
			buffer.writeEnumSet(traits, BionicTrait.class);
			for (BionicTrait trait : traits) {
				EnumSet<BionicAnatomyRole> roles = EnumSet.noneOf(BionicAnatomyRole.class);
				roles.addAll(entry.carriers().get(trait));
				buffer.writeEnumSet(roles, BionicAnatomyRole.class);
			}
		}
	}

	private static List<Entry> readEntries(RegistryFriendlyByteBuf buffer) {
		int count = buffer.readVarInt();
		if (count < 0 || count > MAX_ENTRIES)
			throw new IllegalArgumentException("Too many bionic donor entries");
		List<Entry> entries = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			ResourceLocation entity = buffer.readResourceLocation();
			Map<BionicTrait, Set<BionicAnatomyRole>> carriers = new EnumMap<>(BionicTrait.class);
			for (BionicTrait trait : buffer.readEnumSet(BionicTrait.class))
				carriers.put(trait, buffer.readEnumSet(BionicAnatomyRole.class));
			entries.add(new Entry(entity, carriers));
		}
		return entries;
	}

	public record Entry(ResourceLocation entity, Map<BionicTrait, Set<BionicAnatomyRole>> carriers) {
		public Entry {
			Map<BionicTrait, Set<BionicAnatomyRole>> copy = new EnumMap<>(BionicTrait.class);
			carriers.forEach((trait, roles) -> copy.put(trait, Set.copyOf(roles)));
			carriers = Map.copyOf(copy);
		}
		public Set<BionicTrait> traits() { return carriers.keySet(); }
		public boolean provides(BionicTrait trait) {
			return carriers.containsKey(trait);
		}
	}
}
