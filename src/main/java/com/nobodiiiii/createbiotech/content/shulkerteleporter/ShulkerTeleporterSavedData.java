package com.nobodiiiii.createbiotech.content.shulkerteleporter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public class ShulkerTeleporterSavedData extends SavedData {

	private static final String DATA_NAME = "create_biotech_shulker_teleporters";
	private static final String ENTRIES_TAG = "Teleporters";

	private final Map<Location, String> addresses = new HashMap<>();

	public static ShulkerTeleporterSavedData get(MinecraftServer server) {
		return server.overworld()
			.getDataStorage()
			.computeIfAbsent(ShulkerTeleporterSavedData::load, ShulkerTeleporterSavedData::new, DATA_NAME);
	}

	public void register(Location location, String address) {
		if (address.isBlank()) {
			unregister(location);
			return;
		}
		String previous = addresses.put(location, address);
		if (!Objects.equals(previous, address))
			setDirty();
	}

	public void unregister(Location location) {
		if (addresses.remove(location) != null)
			setDirty();
	}

	public boolean hasTarget(String address, Location source) {
		return addresses.entrySet()
			.stream()
			.anyMatch(entry -> !entry.getKey().equals(source) && entry.getValue().equals(address));
	}

	public List<Location> getTargets(String address, Location source) {
		List<Location> targets = new ArrayList<>();
		for (Map.Entry<Location, String> entry : addresses.entrySet()) {
			if (!entry.getKey().equals(source) && entry.getValue().equals(address))
				targets.add(entry.getKey());
		}
		return targets;
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		ListTag entries = new ListTag();
		for (Map.Entry<Location, String> entry : addresses.entrySet()) {
			CompoundTag entryTag = new CompoundTag();
			entryTag.putString("Address", entry.getValue());
			entryTag.putString("Dimension", entry.getKey().dimension().location().toString());
			entryTag.putLong("Pos", entry.getKey().pos().asLong());
			entries.add(entryTag);
		}
		tag.put(ENTRIES_TAG, entries);
		return tag;
	}

	private static ShulkerTeleporterSavedData load(CompoundTag tag) {
		ShulkerTeleporterSavedData data = new ShulkerTeleporterSavedData();
		ListTag entries = tag.getList(ENTRIES_TAG, Tag.TAG_COMPOUND);
		for (Tag rawEntry : entries) {
			CompoundTag entryTag = (CompoundTag) rawEntry;
			String address = entryTag.getString("Address");
			if (address.isBlank())
				continue;
			try {
				ResourceLocation dimensionId = new ResourceLocation(entryTag.getString("Dimension"));
				ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
				data.addresses.put(new Location(dimension, BlockPos.of(entryTag.getLong("Pos"))), address);
			} catch (IllegalArgumentException ignored) {}
		}
		return data;
	}

	public record Location(ResourceKey<Level> dimension, BlockPos pos) {}
}
