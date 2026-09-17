package com.nobodiiiii.createbiotech.foundation.render.material.mapping;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.resources.ResourceLocation;

/** Small offline pixel oracle for checking UV mappings against authored fixtures. */
public final class PixelReference {
    private PixelReference() {}

    public static PixelImage render(UvMapping mapping, Map<String, SourceSlotImage> slots, PixelImage overlay) {
        PixelImage output = new PixelImage(mapping.size().width(), mapping.size().height());
        Map<String, Integer> scales = new HashMap<>();
        for (String required : mapping.requiredSlots()) {
            SourceSlotImage source = slots.get(required);
            if (source == null) throw new IllegalArgumentException("missing source slot: " + required);
            if (!source.logicalGrid().equals(mapping.sourceGrids().get(required))) {
                throw new IllegalArgumentException("source grid mismatch for slot " + required);
            }
            int scale = source.integerScale().orElseThrow(() ->
                    new IllegalArgumentException("source dimensions are not a uniform integer scale for slot " + required));
            scales.put(required, scale);
        }
        for (int y = 0; y < output.height(); y++) {
            for (int x = 0; x < output.width(); x++) {
                UvMapping.Affine cell = mapping.at(x, y);
                if (cell == null) continue;
                SourceSlotImage source = slots.get(cell.slot());
                int sx = (int) Math.floor(cell.mapU(x + .5, y + .5));
                int sy = (int) Math.floor(cell.mapV(x + .5, y + .5));
                output.set(x, y, source.sample(sx, sy, scales.get(cell.slot())));
            }
        }
        if (overlay != null) alphaOver(output, overlay);
        return output;
    }

    public static PixelImage render(UvMapping mapping, Map<String, SourceSlotImage> slots, PixelImage legacyOverlay,
                                    List<TargetDefinition.Layer> layers,
                                    Function<ResourceLocation, PixelImage> textures) {
        PixelImage output = render(mapping, slots, legacyOverlay);
        layers.stream().sorted(Comparator.comparingInt(TargetDefinition.Layer::index)).forEach(layer -> {
            if (layer instanceof TargetDefinition.TextureLayer textureLayer) {
                applyTextureLayer(output, textureLayer, textures.apply(textureLayer.texture()));
            }
            // Model attachments need baked geometry and a live part pose; a flat PNG cannot represent them.
        });
        return output;
    }

    private static void applyTextureLayer(PixelImage output, TargetDefinition.TextureLayer layer, PixelImage texture) {
        if (texture == null) throw new IllegalArgumentException("missing layer texture: " + layer.texture());
        int scaleX = texture.width() / layer.grid().width();
        int scaleY = texture.height() / layer.grid().height();
        if (scaleX <= 0 || scaleX != scaleY
                || texture.width() % layer.grid().width() != 0
                || texture.height() % layer.grid().height() != 0) {
            throw new IllegalArgumentException("layer texture dimensions must be a uniform integer grid scale");
        }
        for (int y = 0; y < layer.destination().height(); y++) {
            for (int x = 0; x < layer.destination().width(); x++) {
                double logicalX = layer.source().x() + (x + .5) * layer.source().width() / layer.destination().width();
                double logicalY = layer.source().y() + (y + .5) * layer.source().height() / layer.destination().height();
                int sample = texture.get((int) Math.floor(logicalX * scaleX), (int) Math.floor(logicalY * scaleX));
                int alpha = sample >>> 24;
                if (alpha != 0 && alpha != 255) {
                    throw new IllegalArgumentException("layer texture requires cutout alpha");
                }
                int destinationX = layer.destination().x() + x;
                int destinationY = layer.destination().y() + y;
                if (alpha == 255) output.set(destinationX, destinationY, sample);
            }
        }
    }

    public static void alphaOver(PixelImage destination, PixelImage overlay) {
        if (overlay.width() != destination.width() || overlay.height() != destination.height()) {
            throw new IllegalArgumentException("overlay dimensions must match target");
        }
        for (int y = 0; y < destination.height(); y++) {
            for (int x = 0; x < destination.width(); x++) {
                destination.set(x, y, alphaOver(destination.get(x, y), overlay.get(x, y)));
            }
        }
    }

    private static int alphaOver(int destination, int source) {
        int sa = source >>> 24;
        if (sa == 0) return destination;
        if (sa == 255) return source;
        int da = destination >>> 24;
        int inverse = 255 - sa;
        int outA = sa + (da * inverse + 127) / 255;
        if (outA == 0) return 0;
        int r = channel(destination, source, 16, sa, da, inverse, outA);
        int g = channel(destination, source, 8, sa, da, inverse, outA);
        int b = channel(destination, source, 0, sa, da, inverse, outA);
        return outA << 24 | r << 16 | g << 8 | b;
    }

    private static int channel(int destination, int source, int shift, int sa, int da, int inverse, int outA) {
        long sc = source >>> shift & 0xff;
        long dc = destination >>> shift & 0xff;
        long numerator = sc * sa * 255L + dc * da * inverse;
        return (int) ((numerator + outA * 127L) / (outA * 255L));
    }
}
