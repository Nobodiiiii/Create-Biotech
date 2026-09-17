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
import java.util.LinkedHashSet;
import java.util.Set;
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

    /** Scans the same target resources used by reload and returns every declared attachment model. */
    public static Set<ResourceLocation> modelIds(ResourceManager manager) {
        Map<ResourceLocation, JsonElement> resources = new HashMap<>();
        SimpleJsonResourceReloadListener.scanDirectory(manager, TARGETS, GSON, resources);
        LinkedHashSet<ResourceLocation> models = new LinkedHashSet<>();
        parseTargets(resources).values().forEach(target -> {
            target.layers().stream()
                    .filter(TargetDefinition.ModelLayer.class::isInstance)
                    .map(TargetDefinition.ModelLayer.class::cast)
                    .map(TargetDefinition.ModelLayer::model)
                    .forEach(models::add);
            target.materialOverrides().values().forEach(override -> override.layers().ifPresent(layers -> layers.stream()
                    .filter(TargetDefinition.ModelLayer.class::isInstance)
                    .map(TargetDefinition.ModelLayer.class::cast)
                    .map(TargetDefinition.ModelLayer::model)
                    .forEach(models::add)));
        });
        return Set.copyOf(models);
    }

    @Override
    protected Prepared prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> targets = new HashMap<>();
        Map<ResourceLocation, JsonElement> materials = new HashMap<>();
        SimpleJsonResourceReloadListener.scanDirectory(manager, TARGETS, GSON, targets);
        SimpleJsonResourceReloadListener.scanDirectory(manager, MATERIALS, GSON, materials);
        var parsed = parseTargets(targets);
        return new Prepared(parsed, parseMaterials(materials), scanNumberedEyes(manager, parsed.values()));
    }

    @Override
    protected void apply(Prepared prepared, ResourceManager manager, ProfilerFiller profiler) {
        CastedMaterialsClient.onResourceReload();
        current.updateAndGet(previous -> new Snapshot(
                previous.generation() + 1, prepared.targets(), prepared.materials(), prepared.numberedEyes()));
    }

    private static Set<ResourceLocation> scanNumberedEyes(ResourceManager manager,
                                                          java.util.Collection<TargetDefinition> targets) {
        Set<TargetDefinition.TextureLayer> layers = new LinkedHashSet<>();
        for (var target : targets) {
            java.util.stream.Stream.concat(target.layers().stream(), target.materialOverrides().values().stream()
                    .flatMap(override -> override.layers().stream()).flatMap(java.util.List::stream))
                    .filter(TargetDefinition.TextureLayer.class::isInstance)
                    .map(TargetDefinition.TextureLayer.class::cast).forEach(layers::add);
        }
        Map<String, Set<ResourceLocation>> folders = new HashMap<>();
        Set<ResourceLocation> eyes = new LinkedHashSet<>();
        for (var layer : layers) layer.numberedEyeDirectory().ifPresent(folder -> {
            var sprites = folders.computeIfAbsent(folder, path -> {
                Set<ResourceLocation> found = new LinkedHashSet<>();
                manager.listResources(path, id -> id.getPath().endsWith(".png")).keySet().forEach(id ->
                        found.add(id.withPath(id.getPath().substring("textures/".length(), id.getPath().length() - 4))));
                return found;
            });
            eyes.addAll(layer.numberedEyeCandidates(sprites));
        });
        return Set.copyOf(eyes);
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
            Map<ResourceLocation, MaterialDefinition> materials,
            Set<ResourceLocation> numberedEyes) {
        Prepared {
            targets = Map.copyOf(targets);
            materials = Map.copyOf(materials);
            numberedEyes = Set.copyOf(numberedEyes);
        }
    }

    public record Snapshot(
            long generation,
            Map<ResourceLocation, TargetDefinition> targets,
            Map<ResourceLocation, MaterialDefinition> materials,
            Set<ResourceLocation> numberedEyes) {
        public Snapshot {
            targets = Map.copyOf(targets);
            materials = Map.copyOf(materials);
            numberedEyes = Set.copyOf(numberedEyes);
        }

        public Snapshot(long generation, Map<ResourceLocation, TargetDefinition> targets,
                        Map<ResourceLocation, MaterialDefinition> materials) {
            this(generation, targets, materials, Set.of());
        }

        public static Snapshot empty() {
            return new Snapshot(0, Map.of(), Map.of());
        }
    }
}
