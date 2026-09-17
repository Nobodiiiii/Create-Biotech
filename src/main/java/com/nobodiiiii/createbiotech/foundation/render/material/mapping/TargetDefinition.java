package com.nobodiiiii.createbiotech.foundation.render.material.mapping;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

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
        RenderPolicy renderPolicy,
        List<Layer> layers,
        Optional<ResourceLocation> bodyTextures) {
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
        layers = sortedLayers(layers);
        bodyTextures = bodyTextures == null ? Optional.empty() : bodyTextures;
    }

    public TargetDefinition(ResourceLocation id, IntSize size, Map<String, IntSize> sourceGrids,
                            List<RegionMapping> regions, List<PixelMapping> pixels,
                            Optional<ResourceLocation> overlay, Map<ResourceLocation, MaterialOverride> materialOverrides,
                            Optional<ComputedCost> computedCost) {
        this(id, size, sourceGrids, regions, pixels, overlay, materialOverrides, computedCost,
                RenderPolicy.DEFAULT, List.of());
    }

    public TargetDefinition(ResourceLocation id, IntSize size, Map<String, IntSize> sourceGrids,
                            List<RegionMapping> regions, List<PixelMapping> pixels,
                            Optional<ResourceLocation> overlay, Map<ResourceLocation, MaterialOverride> materialOverrides,
                            Optional<ComputedCost> computedCost, RenderPolicy renderPolicy) {
        this(id, size, sourceGrids, regions, pixels, overlay, materialOverrides, computedCost,
                renderPolicy, List.of());
    }

    public TargetDefinition(ResourceLocation id, IntSize size, Map<String, IntSize> sourceGrids,
                            List<RegionMapping> regions, List<PixelMapping> pixels,
                            Optional<ResourceLocation> overlay, Map<ResourceLocation, MaterialOverride> materialOverrides,
                            Optional<ComputedCost> computedCost, RenderPolicy renderPolicy, List<Layer> layers) {
        this(id, size, sourceGrids, regions, pixels, overlay, materialOverrides, computedCost,
                renderPolicy, layers, Optional.empty());
    }

    /** A lookup anchor, not a required default PNG. Missing dedicated bodies retain generated UVs. */
    public Optional<TextureLayer> dedicatedBodyLayer() {
        return bodyTextures.map(folder -> {
            String path = folder.getPath().replaceAll("/+$", "");
            var rect = new IntRect(0, 0, size.width(), size.height());
            return new TextureLayer(Integer.MIN_VALUE, folder.withPath(path + "/body_"),
                    size, rect, rect, false, true);
        });
    }

    public List<Layer> layersFor(ResourceLocation materialId) {
        MaterialOverride override = materialOverrides.get(materialId);
        return override == null ? layers : override.layers().orElse(layers);
    }

    private static List<Layer> sortedLayers(List<Layer> layers) {
        if (layers == null) return List.of();
        return layers.stream().sorted(java.util.Comparator.comparingInt(Layer::index)).toList();
    }

    public record RegionMapping(String sourceSlot, IntRect source, IntRect destination,
                                MappingTransform transform) {
    }

    public record PixelMapping(String sourceSlot, IntPoint source, IntPoint destination) {
    }

    public record MaterialOverride(Map<String, ResourceLocation> slots, Optional<ResourceLocation> overlay,
                                   Optional<List<Layer>> layers) {
        public MaterialOverride {
            slots = Map.copyOf(slots);
            overlay = overlay == null ? Optional.empty() : overlay;
            layers = layers == null ? Optional.empty() : layers.map(TargetDefinition::sortedLayers);
        }

        public MaterialOverride(Map<String, ResourceLocation> slots, Optional<ResourceLocation> overlay) {
            this(slots, overlay, Optional.empty());
        }
    }

    public sealed interface Layer permits TextureLayer, ModelLayer {
        int index();
    }

    public record TextureLayer(int index, ResourceLocation texture, IntSize grid, IntRect source,
                               IntRect destination, boolean emissive, boolean materialVariants) implements Layer {
        public TextureLayer(int index, ResourceLocation texture, IntSize grid, IntRect source,
                            IntRect destination, boolean emissive) {
            this(index, texture, grid, source, destination, emissive, false);
        }

        /** Dedicated replacements only; explicit textures opt out by default. */
        public List<ResourceLocation> materialTextureCandidates(ResourceLocation material) {
            if (!materialVariants || material == null) return List.of();
            String folder = textureFolder();
            String filename = texture.getPath().substring(folder.length());
            String prefix = filename.startsWith("eye_") ? "eye_" : filename.startsWith("body_") ? "body_" : "";
            // Never guess the role or try a bare casing name: eyes and bodies share an editing folder.
            if (prefix.isEmpty()) return List.of();
            String path = material.getPath();
            int nameStart = path.lastIndexOf('/') + 1;
            String variant = path.substring(0, nameStart) + prefix + path.substring(nameStart);
            return java.util.stream.Stream.of(
                    texture.withPath(folder + material.getNamespace() + "/" + variant),
                    texture.withPath(folder + variant)).distinct().toList();
        }

        /** Only eyes have a generic pool. Bodies remain UV-mapped or explicitly casing-specific. */
        public Optional<String> numberedEyeDirectory() {
            String folder = textureFolder();
            if (!materialVariants || !texture.getPath().substring(folder.length()).startsWith("eye_"))
                return Optional.empty();
            return Optional.of("textures" + (folder.isEmpty() ? "" : "/" + folder.substring(0, folder.length() - 1)));
        }

        public List<ResourceLocation> numberedEyeCandidates(java.util.Collection<ResourceLocation> sprites) {
            if (numberedEyeDirectory().isEmpty()) return List.of();
            String prefix = textureFolder() + "eye_";
            return sprites.stream().filter(sprite -> sprite.getNamespace().equals(texture.getNamespace()))
                    .filter(sprite -> sprite.getPath().startsWith(prefix)
                            && sprite.getPath().substring(prefix.length()).matches("[0-9]+"))
                    .distinct().sorted(java.util.Comparator
                            .comparing((ResourceLocation sprite) -> new java.math.BigInteger(sprite.getPath().substring(prefix.length())))
                            .thenComparing(ResourceLocation::getPath)).toList();
        }

        private String textureFolder() {
            return texture.getPath().substring(0, texture.getPath().lastIndexOf('/') + 1);
        }

    }

    public record ModelLayer(int index, ResourceLocation model, String part, Vec3 offset, Vec3 rotation,
                             Vec3 scale, Optional<String> sourceSlot, Optional<IntRect> source,
                             boolean emissive) implements Layer {
        public ModelLayer {
            sourceSlot = sourceSlot == null ? Optional.empty() : sourceSlot;
            source = source == null ? Optional.empty() : source;
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
