package com.nobodiiiii.createbiotech.foundation.render.material.mapping;

import com.nobodiiiii.createbiotech.foundation.render.material.mapping.IntSize;

import java.util.OptionalInt;

/** Test fixture input for {@link PixelReference}; never used by the runtime. */
public record SourceSlotImage(PixelImage image, IntSize logicalGrid) {
    public OptionalInt integerScale() {
        return logicalGrid.integerScaleOf(image.width(), image.height());
    }

    int sample(int logicalX, int logicalY, int scale) {
        int offset = scale / 2;
        return image.get(logicalX * scale + offset, logicalY * scale + offset);
    }
}
