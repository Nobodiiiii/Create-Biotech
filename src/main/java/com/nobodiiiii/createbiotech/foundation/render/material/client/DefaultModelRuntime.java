package com.nobodiiiii.createbiotech.foundation.render.material.client;

import com.nobodiiiii.createbiotech.foundation.render.material.client.MaterialTextures.SourceSprite;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.*;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.TargetDefinition.*;
import com.nobodiiiii.createbiotech.foundation.render.material.palette.MaterialPalette;
import com.nobodiiiii.createbiotech.foundation.render.material.palette.MaterialPaletteSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** UV-only runtime. Resource interpretation and geometry capture happen once, never per frame. */
public final class DefaultModelRuntime {
    private static final Logger LOGGER = Logger.getLogger(DefaultModelRuntime.class.getName());
    private static final int DEFAULT_CACHE_CAPACITY = 128;
    private final Supplier<CastedResources.Snapshot> snapshots;
    private final Supplier<MaterialPalette> palettes;
    private final SourceResolver resolver;
    private final BiFunction<ResourceLocation, IntSize, Optional<FixedOverlay>> imageLookup;
    private final Function<ResourceLocation, BakedModel> modelLookup;
    private final Map<ModelKey, CastedModelHandle> models;
    private final Map<BindingKey, Optional<Map<String, SourceSprite>>> bindings;
    private final Map<LayerKey, Layers> layers;
    private final Map<EyePoolKey, List<FixedOverlay>> eyePools;
    private final Set<DecisionKey> logged = new HashSet<>();
    private long generation = Long.MIN_VALUE;

    public static DefaultModelRuntime create() {
        return new DefaultModelRuntime(CastedResources.INSTANCE::current, MaterialPaletteSync::clientPalette,
                (target, material, global, required) -> SourceSlotResolver.resolveSlots(target, material, global,
                        MaterialTextures::findDefault, MaterialTextures::findDirect, required), DEFAULT_CACHE_CAPACITY,
                (sprite, grid) -> MaterialTextures.loadImage(Minecraft.getInstance().getResourceManager(), sprite, grid)
                        .map(source -> new FixedOverlay(sprite, source)),
                id -> {
                    var manager = Minecraft.getInstance().getModelManager();
                    var model = manager.getModel(ModelResourceLocation.standalone(id));
                    return model == manager.getMissingModel() ? null : model;
                });
    }
    DefaultModelRuntime(Supplier<CastedResources.Snapshot> snapshots, Supplier<MaterialPalette> palettes,
                        SourceResolver resolver, int capacity,
                        BiFunction<ResourceLocation, IntSize, Optional<FixedOverlay>> imageLookup) {
        this(snapshots, palettes, resolver, capacity, imageLookup, id -> null);
    }
    DefaultModelRuntime(Supplier<CastedResources.Snapshot> snapshots, Supplier<MaterialPalette> palettes,
                        SourceResolver resolver, int capacity,
                        BiFunction<ResourceLocation, IntSize, Optional<FixedOverlay>> imageLookup,
                        Function<ResourceLocation, BakedModel> modelLookup) {
        if (capacity < 1) throw new IllegalArgumentException("cache capacity must be positive");
        this.snapshots = Objects.requireNonNull(snapshots); this.palettes = Objects.requireNonNull(palettes);
        this.resolver = Objects.requireNonNull(resolver); this.imageLookup = Objects.requireNonNull(imageLookup);
        this.modelLookup = Objects.requireNonNull(modelLookup);
        models = lru(capacity); bindings = lru(capacity); layers = lru(capacity); eyePools = lru(capacity);
    }
    public CastedModelHandle resolve(ModelPart root, ResourceLocation targetId, ResourceLocation material,
                                     ResourceLocation fallback) {
        Objects.requireNonNull(root); Objects.requireNonNull(fallback);
        try {
            CastedResources.Snapshot snapshot = snapshots.get();
            if (generation != snapshot.generation()) {
                models.clear(); bindings.clear(); layers.clear(); eyePools.clear(); logged.clear();
                generation = snapshot.generation();
            }
            TargetDefinition target = snapshot.targets().get(targetId);
            int materialIndex = material == null ? -1 : palettes.get().indexOf(material).orElse(-1);
            if (materialIndex < 0 || target == null)
                return CastedModelHandle.fallback(root, fallback, generation, "NO_TARGET_OR_MATERIAL");
            var key = new ModelKey(root, targetId, material, fallback, materialIndex);
            return models.computeIfAbsent(key, ignored -> prepare(root, target, material, fallback, snapshot, materialIndex));
        } catch (RuntimeException error) {
            var handle = CastedModelHandle.fallback(root, fallback, generation, "MODEL_RESOLUTION_FAILED: " + error.getMessage());
            log(targetId, material, handle);
            return handle;
        }
    }
    private CastedModelHandle prepare(ModelPart root, TargetDefinition target, ResourceLocation material,
                                      ResourceLocation fallback, CastedResources.Snapshot snapshot, int materialIndex) {
        try {
            Layers stack = layers.computeIfAbsent(new LayerKey(target.id(), material, materialIndex),
                    ignored -> resolveLayers(target, material, snapshot, materialIndex));
            if (!stack.rejection.isEmpty()) return fallback(root, target, material, fallback, stack.rejection);
            CapturedModel geometry = CapturedModel.capture(root);
            UvPlan plan = UvPlanCompiler.compile(stack.mapping, geometry.quads(target.size()), target.renderPolicy());
            if (!plan.eligible()) return fallback(root, target, material, fallback, plan.rejectionReason());
            Set<String> visible = new TreeSet<>();
            plan.faces().forEach(face -> face.forEach(fragment -> visible.add(fragment.sourceSlot())));
            Set<String> required = new TreeSet<>(visible);
            required.removeAll(stack.textures.keySet());
            stack.models.forEach(layer -> layer.sourceSlot().ifPresent(required::add));
            Set<String> requested = Set.copyOf(required);
            var bindingKey = new BindingKey(target.id(), material, requested);
            var resolved = bindings.computeIfAbsent(bindingKey, ignored -> {
                try {
                    return resolver.resolve(target, material, Optional.ofNullable(snapshot.materials().get(material)), requested)
                            .filter(map -> requested.stream().allMatch(slot -> map.get(slot) != null
                                    && map.get(slot).grid().equals(target.sourceGrids().get(slot))))
                            .map(map -> {
                                Map<String, SourceSprite> used = new HashMap<>();
                                requested.forEach(slot -> used.put(slot, map.get(slot)));
                                return Map.copyOf(used);
                            });
                } catch (RuntimeException error) { return Optional.empty(); }
            });
            if (resolved.isEmpty()) return fallback(root, target, material, fallback, "SOURCE_SPRITE_UNAVAILABLE");
            var attachments = MaterialAttachments.prepare(root, stack.models, modelLookup, resolved.get());
            if ((long) plan.additionalQuads() + attachments.quadCount() > target.renderPolicy().maxAdditionalQuads())
                return fallback(root, target, material, fallback, "ATTACHMENT_QUAD_LIMIT");
            Map<String, SourceSprite> bodySources = new HashMap<>();
            for (String slot : visible) bodySources.put(slot, stack.textures.containsKey(slot)
                    ? stack.textures.get(slot) : resolved.get().get(slot));
            Set<ResourceLocation> textures = new HashSet<>(attachments.textures());
            bodySources.values().forEach(sprite -> textures.add(sprite.texture()));
            if (textures.size() > target.renderPolicy().maxTextureBatches())
                return fallback(root, target, material, fallback, "TEXTURE_BATCH_LIMIT");
            var mesh = geometry.bind(plan, bodySources, stack.emissive);
            var handle = CastedModelHandle.direct(root, mesh, plan, generation, attachments);
            log(target.id(), material, handle);
            return handle;
        } catch (RuntimeException error) {
            return fallback(root, target, material, fallback, "UNSUPPORTED_UV_MAPPING: " + error.getMessage());
        }
    }
    private Layers resolveLayers(TargetDefinition target, ResourceLocation material,
                                  CastedResources.Snapshot snapshot, int materialIndex) {
        try {
            UvMapping mapping = UvMapping.compile(target);
            Map<String, SourceSprite> textures = new HashMap<>();
            Set<String> emissive = new HashSet<>();
            // Body naming is independent of eye layers; it replaces the base, never joins a numbered pool.
            var body = target.dedicatedBodyLayer();
            if (body.isPresent()) {
                var layer = body.get();
                String slot = uniqueSlot(mapping, "_body");
                for (var candidate : layer.materialTextureCandidates(material)) {
                    try {
                        var image = imageLookup.apply(candidate, layer.grid());
                        if (image.isEmpty()) continue;
                        mapping = mapping.withCutoutBody(image.get().image(), layer, slot);
                        textures.put(slot, new SourceSprite(MaterialTextures.textureId(image.get().sprite()), layer.grid(),0,0,1,1));
                        break;
                    } catch (IllegalArgumentException error) {
                        LOGGER.warning("Skipping body candidate " + candidate + ": " + error.getMessage());
                    }
                }
            }
            // Explicit overlays and ordered layers have priority over the named body.
            var override = target.materialOverrides().get(material);
            var legacy = SourceSlotResolver.resolveOverlay(override, target, sprite -> imageLookup.apply(sprite, target.size()));
            if (legacy.isEmpty() && (target.overlay().isPresent() || override != null && override.overlay().isPresent()))
                return Layers.rejected("OVERLAY_UNAVAILABLE");
            if (legacy.isPresent()) {
                var fixed = legacy.get();
                var rect = new IntRect(0,0,target.size().width(),target.size().height());
                var layer = new TextureLayer(Integer.MIN_VALUE, fixed.sprite(), target.size(), rect, rect, false);
                String slot = uniqueSlot(mapping, "_fixed_overlay");
                mapping = mapping.withCutoutLayer(fixed.image(), layer, slot);
                textures.put(slot, new SourceSprite(MaterialTextures.textureId(fixed.sprite()), target.size(),0,0,1,1));
            }
            List<ModelLayer> attachments = new ArrayList<>();
            int serial = 0;
            for (var layer : target.layersFor(material)) {
                if (layer instanceof ModelLayer model) { attachments.add(model); continue; }
                var texture = (TextureLayer) layer;
                Optional<FixedOverlay> image = layerImage(target, texture, material, snapshot, materialIndex);
                if (image.isEmpty()) return Layers.rejected("LAYER_TEXTURE_UNAVAILABLE: " + texture.texture());
                String slot = uniqueSlot(mapping, "_layer_" + serial++);
                mapping = mapping.withCutoutLayer(image.get().image(), texture, slot);
                textures.put(slot, new SourceSprite(MaterialTextures.textureId(image.get().sprite()), texture.grid(),0,0,1,1));
                if (texture.emissive()) emissive.add(slot);
            }
            return new Layers(mapping, Map.copyOf(textures), Set.copyOf(emissive), List.copyOf(attachments), "");
        } catch (RuntimeException error) {
            return Layers.rejected("UNSUPPORTED_UV_MAPPING: " + error.getMessage());
        }
    }
    private Optional<FixedOverlay> layerImage(TargetDefinition target, TextureLayer layer, ResourceLocation material,
                                               CastedResources.Snapshot snapshot, int materialIndex) {
        for (var candidate : layer.materialTextureCandidates(material)) {
            var image = imageLookup.apply(candidate, layer.grid());
            if (image.isPresent()) return image;
        }
        if (layer.numberedEyeDirectory().isPresent()) {
            var pool = eyePools.computeIfAbsent(new EyePoolKey(target.id(), layer), ignored -> {
                List<FixedOverlay> valid = new ArrayList<>();
                var base = UvMapping.compile(target);
                for (var candidate : layer.numberedEyeCandidates(snapshot.numberedEyes())) {
                    try {
                        var image = imageLookup.apply(candidate, layer.grid());
                        if (image.isEmpty()) continue;
                        // Reuse the UV interpreter: size, cutout alpha and resampling constraints must all fit.
                        base.withCutoutLayer(image.get().image(), layer, uniqueSlot(base, "_eye_candidate"));
                        valid.add(image.get());
                    } catch (IllegalArgumentException error) {
                        LOGGER.warning("Skipping eye candidate " + candidate + ": " + error.getMessage());
                    }
                }
                return List.copyOf(valid);
            });
            if (!pool.isEmpty()) return Optional.of(pool.get(materialIndex % pool.size()));
        }
        return imageLookup.apply(layer.texture(), layer.grid());
    }

    private static String uniqueSlot(UvMapping mapping, String slot) {
        while (mapping.sourceGrids().containsKey(slot)) slot = "_" + slot;
        return slot;
    }
    private CastedModelHandle fallback(ModelPart root, TargetDefinition target, ResourceLocation material,
                                       ResourceLocation fallback, String reason) {
        var handle = CastedModelHandle.fallback(root, fallback, generation, reason);
        log(target.id(), material, handle);
        return handle;
    }
    private void log(ResourceLocation target, ResourceLocation material, CastedModelHandle handle) {
        if ((Boolean.getBoolean("castedMaterials.logBackends") || handle.backend() == CastedModelHandle.Backend.FALLBACK)
                && logged.add(new DecisionKey(target, material, handle.backend(), handle.reason()))) {
            LOGGER.info(() -> "Casted Materials backend=" + handle.backend() + " target=" + target + " material=" + material
                    + " generation=" + generation + " reason=" + handle.reason()
                    + handle.plan().map(p -> " originalQuads=" + p.originalQuads() + " outputQuads=" + p.outputQuads()
                            + " additionalQuads=" + p.additionalQuads()).orElse("")
                    + " textureBatches=" + handle.textureBatches());
        }
    }
    private static <K,V> Map<K,V> lru(int capacity) {
        return new LinkedHashMap<>(16,.75f,true) {
            @Override protected boolean removeEldestEntry(Map.Entry<K,V> eldest) { return size() > capacity; }
        };
    }
    @FunctionalInterface interface SourceResolver {
        Optional<Map<String,SourceSprite>> resolve(TargetDefinition target, ResourceLocation material,
                Optional<MaterialDefinition> global, Set<String> required);
    }
    record FixedOverlay(ResourceLocation sprite, PixelImage image) {
        FixedOverlay { Objects.requireNonNull(sprite); Objects.requireNonNull(image); }
    }
    private record ModelKey(ModelPart root, ResourceLocation target, ResourceLocation material, ResourceLocation fallback, int materialIndex) { }
    private record BindingKey(ResourceLocation target, ResourceLocation material, Set<String> required) { }
    private record LayerKey(ResourceLocation target, ResourceLocation material, int materialIndex) { }
    private record EyePoolKey(ResourceLocation target, TextureLayer layer) { }
    private record DecisionKey(ResourceLocation target, ResourceLocation material, CastedModelHandle.Backend backend, String reason) { }
    private record Layers(UvMapping mapping, Map<String,SourceSprite> textures, Set<String> emissive,
                          List<ModelLayer> models, String rejection) {
        static Layers rejected(String reason) { return new Layers(null,Map.of(),Set.of(),List.of(),reason); }
    }
}
