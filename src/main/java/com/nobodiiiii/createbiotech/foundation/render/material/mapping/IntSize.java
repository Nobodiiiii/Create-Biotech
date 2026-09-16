package com.nobodiiiii.createbiotech.foundation.render.material.mapping;

import java.util.OptionalInt;

public record IntSize(int width, int height) {
    public IntSize {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Dimensions must be positive");
        }
    }

    /** Uniform whole-number scale from this logical grid to a physical image or atlas sprite. */
    public OptionalInt integerScaleOf(int physicalWidth, int physicalHeight) {
        if (physicalWidth < width || physicalHeight < height
                || physicalWidth % width != 0 || physicalHeight % height != 0
                || physicalWidth / width != physicalHeight / height) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(physicalWidth / width);
    }
}
