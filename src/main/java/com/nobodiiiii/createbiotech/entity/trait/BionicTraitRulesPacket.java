package com.nobodiiiii.createbiotech.entity.trait;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.network.CBPackets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/** Bounded chunks of the full trait catalog, committed atomically by the client. No enum IDs. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public record BionicTraitRulesPacket(long generation, int offset, int total, List<Entry> entries) {
	public static final int MAX_RESOURCES = 4096;
	public static final int MAX_RESOURCE_BYTES = 131072;
	public static final int MAX_CHUNK_BYTES = 262144;
	public static final int MAX_SNAPSHOT_BYTES = 8 * 1024 * 1024;
	public record Entry(ResourceLocation id, String json) {
		public Entry {
			if (id.toString().length() > 32767) throw new IllegalArgumentException("Bionic trait resource ID is too long");
			if (json.getBytes(StandardCharsets.UTF_8).length > MAX_RESOURCE_BYTES)
				throw new IllegalArgumentException("Bionic trait resource is too large: " + id);
		}
		public int encodedSize() {
			return id.toString().getBytes(StandardCharsets.UTF_8).length
				+ json.getBytes(StandardCharsets.UTF_8).length + 10;
		}
	}
	public BionicTraitRulesPacket {
		entries = List.copyOf(entries);
		if (total < 0 || total > MAX_RESOURCES || offset < 0 || offset > total
			|| offset + entries.size() > total || entries.size() > MAX_RESOURCES)
			throw new IllegalArgumentException("Invalid bionic trait catalog chunk");
		int bytes = entries.stream().mapToInt(Entry::encodedSize).sum();
		if (bytes > MAX_CHUNK_BYTES) throw new IllegalArgumentException("Bionic trait chunk is too large");
	}
	public BionicTraitRulesPacket(RegistryFriendlyByteBuf buffer) {
		this(buffer.readLong(), buffer.readVarInt(), buffer.readVarInt(), read(buffer));
	}
	public void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeLong(generation);
		buffer.writeVarInt(offset);
		buffer.writeVarInt(total);
		buffer.writeVarInt(entries.size());
		for (Entry entry : entries) {
			buffer.writeResourceLocation(entry.id());
			buffer.writeByteArray(entry.json().getBytes(StandardCharsets.UTF_8));
		}
	}
	private static List<Entry> read(RegistryFriendlyByteBuf buffer) {
		int count = buffer.readVarInt();
		if (count < 0 || count > MAX_RESOURCES) throw new IllegalArgumentException("Invalid bionic resource count");
		List<Entry> entries = new ArrayList<>();
		int bytes = 0;
		for (int i = 0; i < count; i++) {
			ResourceLocation id = buffer.readResourceLocation();
			byte[] json = buffer.readByteArray(MAX_RESOURCE_BYTES);
			bytes += json.length + id.toString().getBytes(StandardCharsets.UTF_8).length + 10;
			if (bytes > MAX_CHUNK_BYTES) throw new IllegalArgumentException("Bionic trait chunk is too large");
			entries.add(new Entry(id, new String(json, StandardCharsets.UTF_8)));
		}
		return entries;
	}
	static void validateResources(Map<ResourceLocation, JsonElement> resources) {
		if (resources.size() > MAX_RESOURCES) throw new IllegalArgumentException("Too many bionic trait resources");
		int bytes = 0;
		for (var entry : resources.entrySet()) {
			bytes += new Entry(entry.getKey(), entry.getValue().toString()).encodedSize();
		}
		if (bytes > MAX_SNAPSHOT_BYTES) throw new IllegalArgumentException("Bionic trait catalog is too large");
	}
	@SubscribeEvent
	public static void onDatapackSync(OnDatapackSyncEvent event) {
		var snapshot = BionicTraitRegistry.snapshot();
		List<Entry> all = snapshot.resources().entrySet().stream().sorted(Map.Entry.comparingByKey())
			.map(entry -> new Entry(entry.getKey(), entry.getValue().toString())).toList();
		List<Entry> chunk = new ArrayList<>();
		int offset = 0, bytes = 0;
		for (Entry entry : all) {
			int size = entry.encodedSize();
			if (!chunk.isEmpty() && bytes + size > MAX_CHUNK_BYTES) {
				var packet = new BionicTraitRulesPacket(snapshot.generation(), offset, all.size(), chunk);
				event.getRelevantPlayers().forEach(player -> CBPackets.sendToPlayer(packet, player));
				offset += chunk.size(); chunk = new ArrayList<>(); bytes = 0;
			}
			chunk.add(entry); bytes += size;
		}
		var packet = new BionicTraitRulesPacket(snapshot.generation(), offset, all.size(), chunk);
		event.getRelevantPlayers().forEach(player -> CBPackets.sendToPlayer(packet, player));
	}
}
