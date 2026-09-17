package com.nobodiiiii.createbiotech.foundation.render.material.mapping;

import com.nobodiiiii.createbiotech.foundation.render.material.mapping.TargetDefinition.IntRect;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.TargetDefinition.PixelMapping;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.TargetDefinition.RegionMapping;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.TargetDefinition.TextureLayer;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable destination-to-source mapping used by geometry compilation and test-only pixel sampling.
 * Cells describe UV edges; pixel sampling evaluates the same transform at destination pixel centers.
 * Unmapped cells remain null (transparent), not an implicit identity mapping.
 */
public final class UvMapping {
    private static final int MAX_TARGET_DIMENSION = 256;
    private final IntSize size;
    private final Map<String, IntSize> sourceGrids;
    private final Affine[] cells;
    private final Set<String> requiredSlots;

    private UvMapping(IntSize size, Map<String, IntSize> sourceGrids, Affine[] cells, Set<String> requiredSlots) {
        this.size = size;
        this.sourceGrids = Map.copyOf(sourceGrids);
        this.cells = cells;
        this.requiredSlots = Collections.unmodifiableSet(new LinkedHashSet<>(requiredSlots));
    }

    public static UvMapping compile(TargetDefinition target) {
        Objects.requireNonNull(target, "target");
        IntSize size = target.size();
        if (size.width() > MAX_TARGET_DIMENSION || size.height() > MAX_TARGET_DIMENSION) {
            throw new IllegalArgumentException("target dimensions " + size.width() + "x" + size.height()
                    + " exceed schema limit 1.." + MAX_TARGET_DIMENSION);
        }
        if (target.regions().isEmpty() && target.pixels().isEmpty()) {
            throw new IllegalArgumentException("mapping is empty");
        }
        Affine[] cells = new Affine[size.width() * size.height()];
        for (RegionMapping region : target.regions()) {
            IntSize sourceGrid = target.sourceGrids().get(region.sourceSlot());
            IntRect source = region.source(), destination = region.destination();
            if (sourceGrid == null || source == null || destination == null || region.transform() == null
                    || !source.fits(sourceGrid) || !destination.fits(size)) {
                throw new IllegalArgumentException("region has invalid source, destination or transform");
            }
            int width = region.transform().swapsAxes() ? source.height() : source.width();
            int height = region.transform().swapsAxes() ? source.width() : source.height();
            if (destination.width() != width || destination.height() != height) {
                throw new IllegalArgumentException("region dimensions do not match transform");
            }
            Affine affine = Affine.of(region);
            for (int y = destination.y(); y < destination.y() + height; y++) {
                for (int x = destination.x(); x < destination.x() + width; x++) {
                    write(cells, y * size.width() + x, affine);
                }
            }
        }
        for (PixelMapping pixel : target.pixels()) {
            IntSize sourceGrid = target.sourceGrids().get(pixel.sourceSlot());
            if (sourceGrid == null || pixel.source() == null || pixel.destination() == null
                    || pixel.source().x() >= sourceGrid.width() || pixel.source().y() >= sourceGrid.height()
                    || pixel.destination().x() >= size.width() || pixel.destination().y() >= size.height()) {
                throw new IllegalArgumentException("pixel has invalid source or destination");
            }
            Affine affine = new Affine(pixel.sourceSlot(), 1, 0, 0, 1,
                    (long) pixel.source().x() - pixel.destination().x(),
                    (long) pixel.source().y() - pixel.destination().y());
            write(cells, pixel.destination().y() * size.width() + pixel.destination().x(), affine);
        }
        return new UvMapping(size, target.sourceGrids(), cells, target.requiredSlots());
    }

    public IntSize size() {
        return size;
    }

    public Map<String, IntSize> sourceGrids() {
        return sourceGrids;
    }

    public Set<String> requiredSlots() {
        return requiredSlots;
    }

    /** A dedicated body replaces the generated base, including its transparent holes. */
    public UvMapping withCutoutBody(PixelImage image, TextureLayer layer, String sourceSlot) {
        return new UvMapping(size, sourceGrids, new Affine[cells.length], Set.of())
                .withCutoutLayer(image, layer, sourceSlot);
    }

    /** Applies a binary-alpha texture layer directly to this UV interpretation. */
    public UvMapping withCutoutLayer(PixelImage image, TextureLayer layer, String sourceSlot) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(sourceSlot, "sourceSlot");
        if (sourceGrids.containsKey(sourceSlot)) {
            throw new IllegalArgumentException("layer source slot already exists: " + sourceSlot);
        }
        IntSize grid = layer.grid();
        IntRect source = layer.source(), destination = layer.destination();
        if (grid == null || source == null || destination == null || !source.fits(grid) || !destination.fits(size)) {
            throw new IllegalArgumentException("layer has invalid grid, source or destination");
        }
        if (image.width() % grid.width() != 0 || image.height() % grid.height() != 0) {
            throw new IllegalArgumentException("layer image dimensions must be integer-scaled from declared grid");
        }
        int scaleX = image.width() / grid.width(), scaleY = image.height() / grid.height();
        if (scaleX != scaleY) {
            throw new IllegalArgumentException("layer image dimensions must be a uniform integer scale of declared grid");
        }
        boolean[][] opaque = new boolean[grid.height()][grid.width()];
        for (int logicalY = 0; logicalY < grid.height(); logicalY++) {
            for (int logicalX = 0; logicalX < grid.width(); logicalX++) {
                int expected = -1;
                for (int py = logicalY * scaleY; py < (logicalY + 1) * scaleY; py++) {
                    for (int px = logicalX * scaleX; px < (logicalX + 1) * scaleX; px++) {
                        int alpha = image.get(px, py) >>> 24;
                        if (alpha != 0 && alpha != 255) {
                            throw new IllegalArgumentException("cutout layer alpha at [" + px + "," + py + "] is " + alpha
                                    + "; only alpha 0 or 255 is supported");
                        }
                        if (expected < 0) expected = alpha;
                        else if (expected != alpha) {
                            throw new IllegalArgumentException("all actual alpha samples in each logical source texel must agree");
                        }
                    }
                }
                opaque[logicalY][logicalX] = expected == 255;
            }
        }
        double a = (double) source.width() / destination.width();
        double d = (double) source.height() / destination.height();
        Affine affine = new Affine(sourceSlot, a, 0, 0, d,
                source.x() - a * destination.x(), source.y() - d * destination.y());
        Affine[] replaced = cells.clone();
        Set<String> visibleSlots = new LinkedHashSet<>();
        for (int dy = 0; dy < destination.height(); dy++) {
            for (int dx = 0; dx < destination.width(); dx++) {
                int firstX = (int) Math.floor((double) dx * source.width() / destination.width());
                int lastX = (int) Math.ceil((double) (dx + 1) * source.width() / destination.width()) - 1;
                int firstY = (int) Math.floor((double) dy * source.height() / destination.height());
                int lastY = (int) Math.ceil((double) (dy + 1) * source.height() / destination.height()) - 1;
                boolean alpha = opaque[source.y() + firstY][source.x() + firstX];
                for (int sy = firstY; sy <= lastY; sy++) for (int sx = firstX; sx <= lastX; sx++) {
                    if (opaque[source.y() + sy][source.x() + sx] != alpha) {
                        throw new IllegalArgumentException("source alpha must agree where multiple texels map to one destination texel");
                    }
                }
                if (alpha) {
                    int x = destination.x() + dx, y = destination.y() + dy;
                    replaced[y * size.width() + x] = affine;
                }
            }
        }
        for (Affine cell : replaced) if (cell != null) visibleSlots.add(cell.slot());
        Set<String> required = new LinkedHashSet<>(requiredSlots);
        required.add(sourceSlot);
        required.retainAll(visibleSlots);
        Map<String, IntSize> grids = new HashMap<>(sourceGrids);
        grids.put(sourceSlot, grid);
        return new UvMapping(size, grids, replaced, required);
    }

    /** Replaces opaque overlay cells in this interpretation, without adding a second geometry pass. */
    public UvMapping withCutoutOverlay(PixelImage overlay, String sourceSlot) {
        Objects.requireNonNull(overlay, "overlay");
        if (overlay.width() != size.width() || overlay.height() != size.height()) {
            throw new IllegalArgumentException("overlay dimensions " + overlay.width() + "x" + overlay.height()
                    + " must match target " + size.width() + "x" + size.height());
        }
        TextureLayer layer = new TextureLayer(0,
                Objects.requireNonNull(ResourceLocation.tryParse("minecraft:cutout_overlay")), size,
                new IntRect(0, 0, size.width(), size.height()),
                new IntRect(0, 0, size.width(), size.height()), false);
        return withCutoutLayer(overlay, layer, sourceSlot);
    }

    public Affine at(int x, int y) {
        Objects.checkIndex(x, size().width());
        Objects.checkIndex(y, size().height());
        return cells[y * size().width() + x];
    }

    /** Samples the already interpreted mapping; omitted slots remain transparent. */
    public PixelImage rasterize(Map<String, PixelImage> images, Set<String> included) {
        PixelImage result = new PixelImage(size.width(), size.height());
        for (String slot : included) {
            IntSize grid = sourceGrids.get(slot);
            PixelImage image = images.get(slot);
            if (grid == null || image == null || image.width() != grid.width() || image.height() != grid.height())
                throw new IllegalArgumentException("composition requires native logical resolution: " + slot);
        }
        for (int y = 0; y < size.height(); y++) for (int x = 0; x < size.width(); x++) {
            Affine cell = at(x, y);
            if (cell == null || !included.contains(cell.slot())) continue;
            // Preserve direct UV sampling exactly. Resampling/high-resolution sources stay direct.
            if (Math.abs(cell.a()) + Math.abs(cell.b()) != 1
                    || Math.abs(cell.c()) + Math.abs(cell.d()) != 1
                    || cell.a() != Math.rint(cell.a()) || cell.b() != Math.rint(cell.b())
                    || cell.c() != Math.rint(cell.c()) || cell.d() != Math.rint(cell.d())
                    || cell.tx() != Math.rint(cell.tx()) || cell.ty() != Math.rint(cell.ty()))
                throw new IllegalArgumentException("composition would resample source texels");
            result.set(x, y, images.get(cell.slot()).get((int) Math.floor(cell.mapU(x + .5, y + .5)),
                    (int) Math.floor(cell.mapV(x + .5, y + .5))));
        }
        return result;
    }

    private static void write(Affine[] cells, int index, Affine affine) {
        if (cells[index] != null) throw new IllegalArgumentException("mapping destinations overlap");
        cells[index] = affine;
    }

    /** Inverse edge transform, including fractional coefficients for scaled layers. */
    public record Affine(String slot, double a, double b, double c, double d, double tx, double ty) {
        public double mapU(double u, double v) {
            return a * u + b * v + tx;
        }

        public double mapV(double u, double v) {
            return c * u + d * v + ty;
        }

        private static Affine of(RegionMapping region) {
            IntRect source = region.source(), destination = region.destination();
            long sx = source.x(), sy = source.y(), dx = destination.x(), dy = destination.y();
            return switch (region.transform()) {
                case IDENTITY -> new Affine(region.sourceSlot(), 1, 0, 0, 1, sx - dx, sy - dy);
                case ROTATE_90 -> new Affine(region.sourceSlot(), 0, 1, -1, 0, sx - dy, sy + source.height() + dx);
                case ROTATE_180 -> new Affine(region.sourceSlot(), -1, 0, 0, -1, sx + source.width() + dx, sy + source.height() + dy);
                case ROTATE_270 -> new Affine(region.sourceSlot(), 0, -1, 1, 0, sx + source.width() + dy, sy - dx);
                case MIRROR_X -> new Affine(region.sourceSlot(), -1, 0, 0, 1, sx + source.width() + dx, sy - dy);
                case MIRROR_Y -> new Affine(region.sourceSlot(), 1, 0, 0, -1, sx - dx, sy + source.height() + dy);
            };
        }
    }
}
