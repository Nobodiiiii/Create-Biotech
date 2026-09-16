package com.nobodiiiii.createbiotech.foundation.render.material.mapping;

import com.nobodiiiii.createbiotech.foundation.render.material.mapping.TargetDefinition.IntRect;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.TargetDefinition.PixelMapping;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.TargetDefinition.RegionMapping;

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

    /** Replaces opaque overlay cells in this interpretation, without adding a second geometry pass. */
    public UvMapping withCutoutOverlay(PixelImage overlay, String sourceSlot) {
        Objects.requireNonNull(overlay, "overlay");
        Objects.requireNonNull(sourceSlot, "sourceSlot");
        if (overlay.width() != size.width() || overlay.height() != size.height()) {
            throw new IllegalArgumentException("overlay dimensions " + overlay.width() + "x" + overlay.height()
                    + " must match target " + size.width() + "x" + size.height());
        }
        if (sourceGrids.containsKey(sourceSlot)) {
            throw new IllegalArgumentException("overlay source slot already exists: " + sourceSlot);
        }
        Affine[] replaced = cells.clone();
        Affine overlayAffine = new Affine(sourceSlot, 1, 0, 0, 1, 0, 0);
        Set<String> visibleSlots = new LinkedHashSet<>();
        for (int y = 0; y < size.height(); y++) {
            for (int x = 0; x < size.width(); x++) {
                int alpha = overlay.get(x, y) >>> 24;
                if (alpha != 0 && alpha != 255) {
                    throw new IllegalArgumentException("cutout overlay alpha at [" + x + "," + y + "] is " + alpha
                            + "; only alpha 0 or 255 is supported");
                }
                int index = y * size.width() + x;
                if (alpha == 255) replaced[index] = overlayAffine;
                if (replaced[index] != null) visibleSlots.add(replaced[index].slot());
            }
        }
        // Keep the original validation order for surviving sources, then validate the new overlay source.
        Set<String> required = new LinkedHashSet<>(requiredSlots);
        required.add(sourceSlot);
        required.retainAll(visibleSlots);
        Map<String, IntSize> grids = new HashMap<>(sourceGrids);
        grids.put(sourceSlot, size);
        return new UvMapping(size, grids, replaced, required);
    }

    public Affine at(int x, int y) {
        Objects.checkIndex(x, size().width());
        Objects.checkIndex(y, size().height());
        return cells[y * size().width() + x];
    }

    private static void write(Affine[] cells, int index, Affine affine) {
        if (cells[index] != null) throw new IllegalArgumentException("mapping destinations overlap");
        cells[index] = affine;
    }

    /** Inverse edge transform; long translations avoid overflow for large logical source grids. */
    public record Affine(String slot, int a, int b, int c, int d, long tx, long ty) {
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
