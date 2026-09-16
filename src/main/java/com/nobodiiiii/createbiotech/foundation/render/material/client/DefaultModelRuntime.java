package com.nobodiiiii.createbiotech.foundation.render.material.client;

import com.nobodiiiii.createbiotech.foundation.render.material.client.CapturedModel.Bound;
import com.nobodiiiii.createbiotech.foundation.render.material.client.MaterialTextures.SourceSprite;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.IntSize;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.MaterialDefinition;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.PixelImage;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.RenderPolicy;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.SourceSlotResolver;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.TargetDefinition;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.TargetDefinition.MaterialOverride;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.UvMapping;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.UvPlan;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.UvPlanCompiler;
import com.nobodiiiii.createbiotech.foundation.render.material.palette.MaterialPalette;
import com.nobodiiiii.createbiotech.foundation.render.material.palette.MaterialPaletteSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** Render-thread UV-only runtime; cached frames only bind handles and submit live poses. */
public final class DefaultModelRuntime {
    private static final Logger LOGGER = Logger.getLogger(DefaultModelRuntime.class.getName());
    private static final int DEFAULT_CACHE_CAPACITY = 128;
    private final Supplier<CastedResources.Snapshot> snapshots;
    private final Supplier<MaterialPalette> palettes;
    private final SourceResolver resolver;
    private final BiFunction<ResourceLocation, IntSize, Optional<FixedOverlay>> overlayLookup;
    private final int capacity;
    private final Map<ModelKey, Prepared> models;
    private final Map<BindingKey, Optional<Map<String, SourceSprite>>> bindings;
    private final Map<OverlayKey, OverlayResolution> overlays;
    private final Set<DecisionKey> logged = new HashSet<>();
    private long generation = Long.MIN_VALUE;

    public static DefaultModelRuntime create() {
        return new DefaultModelRuntime(CastedResources.INSTANCE::current, MaterialPaletteSync::clientPalette,
                (target, material, global, required) -> SourceSlotResolver.resolveSlots(target, material, global,
                        MaterialTextures::findDefault, MaterialTextures::findDirect, required),
                DEFAULT_CACHE_CAPACITY,
                (sprite, grid) -> MaterialTextures.loadImage(Minecraft.getInstance().getResourceManager(), sprite, grid)
                        .map(source -> new FixedOverlay(sprite, source)));
    }
    DefaultModelRuntime(Supplier<CastedResources.Snapshot> snapshots, Supplier<MaterialPalette> palettes,
                        SourceResolver resolver, int capacity,
                        BiFunction<ResourceLocation, IntSize, Optional<FixedOverlay>> overlayLookup) {
        if (capacity < 1) throw new IllegalArgumentException("cache capacity must be positive");
        this.snapshots = Objects.requireNonNull(snapshots); this.palettes = Objects.requireNonNull(palettes);
        this.resolver = Objects.requireNonNull(resolver);
        this.overlayLookup = Objects.requireNonNull(overlayLookup);
        this.capacity = capacity; models = lru(capacity); bindings = lru(capacity); overlays = lru(capacity);
    }
    public CastedModelHandle resolve(ModelPart root, ResourceLocation targetId, ResourceLocation material,
                                      ResourceLocation fallback) {
        Objects.requireNonNull(root); Objects.requireNonNull(fallback);
        try {
            CastedResources.Snapshot snapshot = snapshots.get();
            if (generation != snapshot.generation()) {
                models.clear(); bindings.clear(); overlays.clear(); logged.clear();
                generation = snapshot.generation();
            }
            TargetDefinition target = snapshot.targets().get(targetId);
            if (material == null || target == null || palettes.get().indexOf(material).isEmpty()) {
                return CastedModelHandle.fallback(root, fallback, generation, "NO_TARGET_OR_MATERIAL");
            }
            RenderPolicy policy = target.renderPolicy();
            OverlayResolution fixed = overlays.computeIfAbsent(new OverlayKey(targetId, material),
                    ignored -> resolveOverlay(target, material));
            if (!fixed.rejection.isEmpty()) {
                return fallback(root, target, material, fallback, fixed.rejection);
            }
            ResourceLocation overlayId = fixed.overlay == null ? null : fixed.overlay.sprite();
            Prepared prepared = models.computeIfAbsent(new ModelKey(root, targetId, overlayId),
                    ignored -> prepare(root, target, policy, fixed.overlay));
            if (prepared.plan == null || !prepared.plan.eligible()) {
                return fallback(root, target, material, fallback, prepared.rejection);
            }
            CastedModelHandle cached = prepared.handles.get(material);
            if (cached != null) return cached;
            BindingKey bindingKey = new BindingKey(targetId, material, prepared.required);
            Optional<Map<String, SourceSprite>> source = bindings.computeIfAbsent(bindingKey, ignored -> {
                try {
                    return resolver.resolve(target, material, Optional.ofNullable(snapshot.materials().get(material)), prepared.required)
                            .filter(map -> map.keySet().containsAll(prepared.required))
                            .filter(map -> prepared.required.stream().allMatch(slot -> map.get(slot).grid().equals(target.sourceGrids().get(slot))))
                            .map(map -> {
                                Map<String, SourceSprite> used = new HashMap<>();
                                prepared.required.forEach(slot -> used.put(slot, map.get(slot)));
                                return Map.copyOf(used);
                            });
                } catch (RuntimeException error) { return Optional.empty(); }
            });
            if (source.isEmpty()) return fallback(root, target, material, fallback, "SOURCE_SPRITE_UNAVAILABLE");
            Map<String, SourceSprite> allSources = new HashMap<>(source.get());
            if (prepared.overlaySlot != null) {
                allSources.put(prepared.overlaySlot, new SourceSprite(MaterialTextures.textureId(overlayId), target.size(), 0,0,1,1));
            }
            long batches = allSources.values().stream().map(SourceSprite::texture).distinct().count();
            if (batches > policy.maxTextureBatches()) {
                return fallback(root, target, material, fallback, "TEXTURE_BATCH_LIMIT");
            }
            Bound mesh = prepared.geometry.bind(prepared.plan, allSources);
            CastedModelHandle handle = CastedModelHandle.direct(root, mesh, prepared.plan, generation);
            prepared.handles.put(material, handle);
            log(targetId, material, handle);
            return handle;
        } catch (RuntimeException error) {
            CastedModelHandle handle = CastedModelHandle.fallback(root, fallback, generation, "MODEL_RESOLUTION_FAILED");
            log(targetId, material, handle);
            return handle;
        }
    }
    private OverlayResolution resolveOverlay(TargetDefinition target, ResourceLocation material) {
        try {
            MaterialOverride override = target.materialOverrides().get(material);
            Optional<FixedOverlay> overlay = SourceSlotResolver.resolveOverlay(override, target,
                    sprite -> overlayLookup.apply(sprite, target.size()));
            boolean declared = target.overlay().isPresent() || override != null && override.overlay().isPresent();
            return new OverlayResolution(overlay.orElse(null),
                    declared && overlay.isEmpty() ? "OVERLAY_UNAVAILABLE" : "");
        } catch (RuntimeException error) {
            return new OverlayResolution(null, "OVERLAY_UNAVAILABLE");
        }
    }
    private Prepared prepare(ModelPart root, TargetDefinition target, RenderPolicy policy, FixedOverlay overlay) {
        try {
            CapturedModel geometry = CapturedModel.capture(root);
            UvMapping mapping = UvMapping.compile(target);
            String overlaySlot = null;
            if (overlay != null) {
                overlaySlot = "_fixed_overlay";
                while (mapping.sourceGrids().containsKey(overlaySlot)) overlaySlot = "_" + overlaySlot;
                mapping = mapping.withCutoutOverlay(overlay.image(), overlaySlot);
            }
            UvPlan plan = UvPlanCompiler.compile(mapping, geometry.quads(target.size()), policy);
            Set<String> required = new TreeSet<>();
            plan.faces().forEach(face -> face.forEach(fragment -> required.add(fragment.sourceSlot())));
            if (overlaySlot != null && !required.remove(overlaySlot)) overlaySlot = null;
            return new Prepared(geometry, plan, Set.copyOf(required), overlaySlot, plan.rejectionReason(), lru(capacity));
        } catch (RuntimeException error) {
            return new Prepared(null, null, Set.of(), null, "UNSUPPORTED_UV_MAPPING: " + error.getMessage(), lru(capacity));
        }
    }
    private CastedModelHandle fallback(ModelPart root, TargetDefinition target, ResourceLocation material,
                                       ResourceLocation fallback, String reason) {
        CastedModelHandle handle = CastedModelHandle.fallback(root, fallback, generation, reason);
        log(target.id(), material, handle);
        return handle;
    }
    private void log(ResourceLocation target, ResourceLocation material, CastedModelHandle handle) {
        boolean enabled = Boolean.getBoolean("castedMaterials.logBackends")
                || handle.backend() == CastedModelHandle.Backend.FALLBACK;
        if (enabled && logged.add(new DecisionKey(target, material, handle.backend(), handle.reason()))) {
            LOGGER.info(() -> "Casted Materials backend=" + handle.backend() + " target=" + target + " material=" + material
                    + " generation=" + generation + " reason=" + handle.reason()
                    + handle.plan().map(p -> " originalQuads=" + p.originalQuads() + " outputQuads=" + p.outputQuads()
                            + " additionalQuads=" + p.additionalQuads()).orElse("")
                    + " textureBatches=" + handle.textureBatches());
        }
    }
    private static <K, V> Map<K, V> lru(int capacity) {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<K, V> eldest) { return size() > capacity; }
        };
    }
    @FunctionalInterface
    interface SourceResolver {
        Optional<Map<String, SourceSprite>> resolve(TargetDefinition target, ResourceLocation material,
                Optional<MaterialDefinition> global, Set<String> required);
    }
    record FixedOverlay(ResourceLocation sprite, PixelImage image) {
        FixedOverlay { Objects.requireNonNull(sprite); Objects.requireNonNull(image); }
    }
    private record OverlayKey(ResourceLocation target, ResourceLocation material) { }
    private record OverlayResolution(FixedOverlay overlay, String rejection) { }
    private record ModelKey(ModelPart model, ResourceLocation target, ResourceLocation overlay) { }
    private record BindingKey(ResourceLocation target, ResourceLocation material, Set<String> required) { }
    private record DecisionKey(ResourceLocation target, ResourceLocation material, CastedModelHandle.Backend backend, String reason) { }
    private record Prepared(CapturedModel geometry, UvPlan plan, Set<String> required, String overlaySlot, String rejection,
                            Map<ResourceLocation, CastedModelHandle> handles) { }
}
