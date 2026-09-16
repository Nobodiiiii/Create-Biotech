package com.nobodiiiii.createbiotech.foundation.render.material.client;

import com.nobodiiiii.createbiotech.foundation.render.material.mapping.DefinitionParser;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.MaterialDefinition;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.TargetDefinition;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@OnlyIn(Dist.CLIENT)
public final class CastedResources
        extends SimplePreparableReloadListener<CastedResources.Prepared> {
    public static final CastedResources INSTANCE = new CastedResources();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final String TARGETS = "casted_materials/targets";
    private static final String MATERIALS = "casted_materials/materials";

    private final AtomicReference<Snapshot> current =
            new AtomicReference<>(Snapshot.empty());

    private CastedResources() {
    }

    public Snapshot current() {
        return current.get();
    }

    @Override
    protected Prepared prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> targets = new HashMap<>();
        Map<ResourceLocation, JsonElement> materials = new HashMap<>();
        SimpleJsonResourceReloadListener.scanDirectory(manager, TARGETS, GSON, targets);
        SimpleJsonResourceReloadListener.scanDirectory(manager, MATERIALS, GSON, materials);
        return new Prepared(parseTargets(targets), parseMaterials(materials));
    }

    @Override
    protected void apply(Prepared prepared, ResourceManager manager, ProfilerFiller profiler) {
        current.updateAndGet(previous -> new Snapshot(
                previous.generation() + 1, prepared.targets(), prepared.materials()));
    }

    private static Map<ResourceLocation, TargetDefinition> parseTargets(
            Map<ResourceLocation, JsonElement> resources) {
        Map<ResourceLocation, TargetDefinition> parsed = new HashMap<>();
        resources.forEach((id, json) -> {
            try {
                parsed.put(id, DefinitionParser.parseTarget(id, json.toString()));
            } catch (RuntimeException error) {
                LOGGER.error("Skipping invalid Casted Materials target {}: {}", id, error.getMessage());
            }
        });
        return Map.copyOf(parsed);
    }

    private static Map<ResourceLocation, MaterialDefinition> parseMaterials(
            Map<ResourceLocation, JsonElement> resources) {
        Map<ResourceLocation, MaterialDefinition> parsed = new HashMap<>();
        resources.forEach((id, json) -> {
            try {
                parsed.put(id, DefinitionParser.parseMaterial(id, json.toString()));
            } catch (RuntimeException error) {
                LOGGER.error("Skipping invalid Casted Materials material {}: {}", id, error.getMessage());
            }
        });
        return Map.copyOf(parsed);
    }

    record Prepared(
            Map<ResourceLocation, TargetDefinition> targets,
            Map<ResourceLocation, MaterialDefinition> materials) {
        Prepared {
            targets = Map.copyOf(targets);
            materials = Map.copyOf(materials);
        }
    }

    public record Snapshot(
            long generation,
            Map<ResourceLocation, TargetDefinition> targets,
            Map<ResourceLocation, MaterialDefinition> materials) {
        public Snapshot {
            targets = Map.copyOf(targets);
            materials = Map.copyOf(materials);
        }

        public static Snapshot empty() {
            return new Snapshot(0, Map.of(), Map.of());
        }
    }
}
