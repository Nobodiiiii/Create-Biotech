package com.nobodiiiii.createbiotech.foundation.render.material.mapping;

import com.nobodiiiii.createbiotech.foundation.render.material.mapping.TargetDefinition.MaterialOverride;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class SourceSlotResolver {
    private SourceSlotResolver() {
    }

    /** Source precedence for UV bindings and offline verification. Lookup validates its own resource type. */
    public static <T> Optional<Map<String, T>> resolveSlots(
            TargetDefinition target, ResourceLocation materialId, Optional<MaterialDefinition> globalDefinition,
            Function<ResourceLocation, Optional<ResourceLocation>> defaultSpriteLookup,
            BiFunction<ResourceLocation, IntSize, Optional<T>> lookup) {
        return resolveSlots(target, materialId, globalDefinition, defaultSpriteLookup, lookup, target.requiredSlots());
    }

    /** Only bindings sampled by the compiled model are necessary on the direct path. */
    public static <T> Optional<Map<String, T>> resolveSlots(
            TargetDefinition target, ResourceLocation materialId, Optional<MaterialDefinition> globalDefinition,
            Function<ResourceLocation, Optional<ResourceLocation>> defaultSpriteLookup,
            BiFunction<ResourceLocation, IntSize, Optional<T>> lookup, Set<String> required) {
        MaterialOverride override = target.materialOverrides().get(materialId);
        Map<String, ResourceLocation> targetSlots = override == null ? Map.of() : override.slots();
        Map<String, ResourceLocation> globalSlots = globalDefinition.map(MaterialDefinition::slots).orElse(Map.of());
        Map<String, T> resolved = new LinkedHashMap<>();

        for (String slot : required.stream().sorted().toList()) {
            IntSize logicalGrid = target.sourceGrids().get(slot);
            if (logicalGrid == null) throw new IllegalArgumentException("undeclared source slot " + slot);
            Optional<T> image = resolveSlot(slot, logicalGrid, targetSlots, globalSlots,
                    materialId, defaultSpriteLookup, lookup);
            if (image.isEmpty()) {
                return Optional.empty();
            }
            resolved.put(slot, image.get());
        }

        return Optional.of(Map.copyOf(resolved));
    }

    private static <T> Optional<T> resolveSlot(
            String slot,
            IntSize logicalGrid,
            Map<String, ResourceLocation> targetSlots,
            Map<String, ResourceLocation> globalSlots,
            ResourceLocation materialId,
            Function<ResourceLocation, Optional<ResourceLocation>> defaultSpriteLookup,
            BiFunction<ResourceLocation, IntSize, Optional<T>> imageLookup) {
        Optional<T> exactTarget = load(targetSlots.get(slot), logicalGrid, imageLookup);
        if (exactTarget.isPresent()) {
            return exactTarget;
        }
        Optional<T> exactGlobal = load(globalSlots.get(slot), logicalGrid, imageLookup);
        if (exactGlobal.isPresent()) {
            return exactGlobal;
        }

        for (String fallback : fallbacks(slot)) {
            if (fallback.equals(slot)) {
                continue;
            }
            Optional<T> targetFallback = load(targetSlots.get(fallback), logicalGrid, imageLookup);
            if (targetFallback.isPresent()) {
                return targetFallback;
            }
        }
        for (String fallback : fallbacks(slot)) {
            if (fallback.equals(slot)) {
                continue;
            }
            Optional<T> globalFallback = load(globalSlots.get(fallback), logicalGrid, imageLookup);
            if (globalFallback.isPresent()) {
                return globalFallback;
            }
        }
        return defaultSpriteLookup.apply(materialId).flatMap(sprite -> {
            Optional<T> base = load(sprite, logicalGrid, imageLookup);
            if (base.isPresent() || sprite.getPath().endsWith("_connected")) {
                return base;
            }
            // A selected CT-sheet grid can be larger than the baked model's ordinary tile.
            // Keep explicit declarations and compatible base tiles ahead of this naming fallback.
            return load(sprite.withSuffix("_connected"), logicalGrid, imageLookup);
        });
    }

    private static <T> Optional<T> load(
            ResourceLocation sprite,
            IntSize logicalGrid,
            BiFunction<ResourceLocation, IntSize, Optional<T>> imageLookup) {
        return sprite == null ? Optional.empty() : imageLookup.apply(sprite, logicalGrid);
    }

    private static List<String> fallbacks(String slot) {
        return switch (slot) {
            case "side" -> List.of("side", "all", "particle", "dominant");
            case "end" -> List.of("all", "side", "particle", "dominant");
            case "all" -> List.of("all", "side", "particle", "dominant");
            default -> List.of("all", "side", "particle", "dominant");
        };
    }

    public static <T> Optional<T> resolveOverlay(
            MaterialOverride override,
            TargetDefinition target,
            Function<ResourceLocation, Optional<T>> overlayLookup) {
        if (override != null && override.overlay().isPresent()) {
            Optional<T> overrideOverlay = overlayLookup.apply(override.overlay().get());
            if (overrideOverlay.isPresent()) {
                return overrideOverlay;
            }
        }
        return target.overlay().flatMap(overlayLookup);
    }
}
