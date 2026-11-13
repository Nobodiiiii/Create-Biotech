package com.nobodiiiii.createbiotech.entity.trait;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Incremental, bounded response; no entity NBT or client/JEI classes cross this boundary. */
public record BionicTraitDonorsPacket(long generation, int offset, int checked, int total, int failures,
	boolean complete, List<Entry> entries) {
	public static final int MAX_ENTRIES = 128;
	public static final int MAX_BYTES = 262144;
	public static final int MAX_CARRIER_PARTS = 256;

	public BionicTraitDonorsPacket {
		entries = List.copyOf(entries);
		if (offset < 0 || checked < 0 || total < checked || failures < 0 || failures > checked
			|| entries.size() > MAX_ENTRIES)
			throw new IllegalArgumentException("Invalid bionic donor index response");
		if (entries.stream().mapToInt(Entry::encodedSize).sum() + 64 > MAX_BYTES)
			throw new IllegalArgumentException("Bionic donor index response is too large");
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
			Set<BionicTrait> traits = new LinkedHashSet<>();
			traits.addAll(entry.traits());
			buffer.writeVarInt(traits.size());
			for (BionicTrait trait : traits.stream().sorted().toList()) {
				buffer.writeResourceLocation(trait.id());
				var roles = entry.carriers().get(trait);
				buffer.writeVarInt(roles.size());
				for (BionicAnatomyRole role : roles.stream().sorted().toList()) buffer.writeResourceLocation(role.id());
			}
		}
	}

	private static List<Entry> readEntries(RegistryFriendlyByteBuf buffer) {
		int count = buffer.readVarInt();
		if (count < 0 || count > MAX_ENTRIES)
			throw new IllegalArgumentException("Too many bionic donor entries");
		List<Entry> entries = new ArrayList<>(count);
		int bytes = 64;
		for (int i = 0; i < count; i++) {
			ResourceLocation entity = buffer.readResourceLocation();
			bytes += entity.toString().getBytes(StandardCharsets.UTF_8).length + 10;
			Map<BionicTrait, Set<BionicAnatomyRole>> carriers = new LinkedHashMap<>();
			int traitCount = buffer.readVarInt();
			if (traitCount < 0 || traitCount > BionicTraitRulesPacket.MAX_RESOURCES + BionicTrait.builtIns().size())
				throw new IllegalArgumentException("Invalid donor trait count");
			for (int j = 0; j < traitCount; j++) {
				BionicTrait trait = BionicTrait.reference(buffer.readResourceLocation());
				int partCount = buffer.readVarInt();
				if (partCount < 0 || partCount > MAX_CARRIER_PARTS) throw new IllegalArgumentException("Invalid donor part count");
				Set<BionicAnatomyRole> roles = new LinkedHashSet<>();
				for (int k = 0; k < partCount; k++)
					if (!roles.add(new BionicAnatomyRole(buffer.readResourceLocation())))
						throw new IllegalArgumentException("Duplicate donor part");
				bytes += traitSize(trait, roles);
				if (bytes > MAX_BYTES) throw new IllegalArgumentException("Bionic donor index response is too large");
				if (carriers.put(trait, roles) != null)
					throw new IllegalArgumentException("Duplicate donor trait");
			}
			Entry entry = new Entry(entity, carriers);
			entries.add(entry);
		}
		return entries;
	}

	/** Large catalogs may split one donor's traits across several index entries. */
	static List<Entry> split(ResourceLocation entity, Map<BionicTrait, Set<BionicAnatomyRole>> carriers) {
		List<Entry> result = new ArrayList<>();
		Map<BionicTrait, Set<BionicAnatomyRole>> chunk = new LinkedHashMap<>();
		int bytes = 0;
		for (BionicTrait trait : carriers.keySet().stream().sorted().toList()) {
			int size = traitSize(trait, carriers.get(trait));
			if (!chunk.isEmpty() && bytes + size > MAX_BYTES / 4) {
				result.add(new Entry(entity, chunk));
				chunk = new LinkedHashMap<>(); bytes = 0;
			}
			chunk.put(trait, carriers.get(trait)); bytes += size;
		}
		if (!chunk.isEmpty()) result.add(new Entry(entity, chunk));
		return result;
	}

	private static int traitSize(BionicTrait trait, Set<BionicAnatomyRole> roles) {
		return trait.id().toString().getBytes(StandardCharsets.UTF_8).length + 10
			+ roles.stream().mapToInt(role -> role.toString().getBytes(StandardCharsets.UTF_8).length + 5).sum();
	}

	public record Entry(ResourceLocation entity, Map<BionicTrait, Set<BionicAnatomyRole>> carriers) {
		public Entry {
			Map<BionicTrait, Set<BionicAnatomyRole>> copy = new LinkedHashMap<>();
			carriers.forEach((trait, roles) -> {
				if (roles.size() > MAX_CARRIER_PARTS) throw new IllegalArgumentException("Too many donor parts");
				copy.put(trait, Set.copyOf(roles));
			});
			carriers = Map.copyOf(copy);
		}
		public Set<BionicTrait> traits() { return carriers.keySet(); }
		int encodedSize() {
			return entity.toString().getBytes(StandardCharsets.UTF_8).length + 10
				+ carriers.entrySet().stream().mapToInt(entry -> traitSize(entry.getKey(), entry.getValue())).sum();
		}
		public boolean provides(BionicTrait trait) {
			return carriers.containsKey(trait);
		}
	}
}
