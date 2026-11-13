package com.nobodiiiii.createbiotech.content.surgery.stats;

import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public final class BionicStatReloadListener extends SimpleJsonResourceReloadListener {
	public static final BionicStatReloadListener INSTANCE = new BionicStatReloadListener();
	private BionicStatReloadListener() { super(new Gson(), "bionic_stats"); }
	@Override protected Map<ResourceLocation, JsonElement> prepare(ResourceManager manager, ProfilerFiller profiler) {
		Map<ResourceLocation, JsonElement> resources = new java.util.HashMap<>();
		var converter = net.minecraft.resources.FileToIdConverter.json("bionic_stats");
		for (var entry : converter.listMatchingResources(manager).entrySet()) {
			try (var reader = entry.getValue().openAsReader()) {
				resources.put(converter.fileToId(entry.getKey()), com.google.gson.JsonParser.parseReader(reader));
			} catch (java.io.IOException | RuntimeException exception) {
				throw new IllegalArgumentException("Cannot load bionic stat " + entry.getKey(), exception);
			}
		}
		return resources;
	}
	@Override protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
		BionicStats.reload(resources);
	}
}
