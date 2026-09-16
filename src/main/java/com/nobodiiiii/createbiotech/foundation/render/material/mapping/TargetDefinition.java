package com.nobodiiiii.createbiotech.foundation.render.material.mapping;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record TargetDefinition(
        ResourceLocation id,
        IntSize size,
        Map<String, IntSize> sourceGrids,
        List<RegionMapping> regions,
        List<PixelMapping> pixels,
        Optional<ResourceLocation> overlay,
        Map<ResourceLocation, MaterialOverride> materialOverrides,
        Optional<ComputedCost> computedCost,
        RenderPolicy renderPolicy) {
    /** Declared slots actually referenced by the mapping, in authoring order. */
    public Set<String> requiredSlots() {
        Set<String> required = new LinkedHashSet<>();
        regions.forEach(region -> required.add(region.sourceSlot()));
        pixels.forEach(pixel -> required.add(pixel.sourceSlot()));
        return Collections.unmodifiableSet(required);
    }

    public TargetDefinition {
        sourceGrids = Map.copyOf(sourceGrids);
        regions = List.copyOf(regions);
        pixels = List.copyOf(pixels);
        overlay = overlay == null ? Optional.empty() : overlay;
        materialOverrides = Map.copyOf(materialOverrides);
        computedCost = computedCost == null ? Optional.empty() : computedCost;
        renderPolicy = Objects.requireNonNull(renderPolicy, "renderPolicy");
    }

    public TargetDefinition(ResourceLocation id, IntSize size, Map<String, IntSize> sourceGrids,
                            List<RegionMapping> regions, List<PixelMapping> pixels,
                            Optional<ResourceLocation> overlay, Map<ResourceLocation, MaterialOverride> materialOverrides,
                            Optional<ComputedCost> computedCost) {
        this(id, size, sourceGrids, regions, pixels, overlay, materialOverrides, computedCost, RenderPolicy.DEFAULT);
    }

    public record RegionMapping(String sourceSlot, IntRect source, IntRect destination,
                                MappingTransform transform) {
    }

    public record PixelMapping(String sourceSlot, IntPoint source, IntPoint destination) {
    }

    public record MaterialOverride(Map<String, ResourceLocation> slots, Optional<ResourceLocation> overlay) {
        public MaterialOverride {
            slots = Map.copyOf(slots);
            overlay = overlay == null ? Optional.empty() : overlay;
        }
    }

    public record ComputedCost(int maxPiecesPerSourceQuad, int additionalQuads) {
        public ComputedCost {
            if (maxPiecesPerSourceQuad < 0 || additionalQuads < 0) {
                throw new IllegalArgumentException("Computed cost cannot be negative");
            }
        }
    }

    public enum MappingTransform {
        IDENTITY,
        ROTATE_90,
        ROTATE_180,
        ROTATE_270,
        MIRROR_X,
        MIRROR_Y;

        public static MappingTransform parse(String value, String path) {
            try {
                return valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException(path + ": unsupported transform " + value, error);
            }
        }

        public boolean swapsAxes() {
            return this == ROTATE_90 || this == ROTATE_270;
        }
    }

    public record IntPoint(int x, int y) {
        public IntPoint {
            if (x < 0 || y < 0) {
                throw new IllegalArgumentException("Coordinates must be non-negative");
            }
        }
    }

    public record IntRect(int x, int y, int width, int height) {
        public IntRect {
            if (x < 0 || y < 0 || width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Rectangle origin must be non-negative and size positive");
            }
        }

        public boolean fits(IntSize size) {
            return (long) x + width <= size.width() && (long) y + height <= size.height();
        }
    }
}
