package com.nobodiiiii.createbiotech.foundation.render.material.mapping;

import java.util.Arrays;

public final class PixelImage {
    private final int width;
    private final int height;
    private final int[] pixels;

    public PixelImage(int width, int height) {
        this(width, height, new int[Math.multiplyExact(width, height)]);
    }

    public PixelImage(int width, int height, int[] pixels) {
        if (width <= 0 || height <= 0 || pixels.length != Math.multiplyExact(width, height)) {
            throw new IllegalArgumentException("Invalid image dimensions or pixel count");
        }
        this.width = width;
        this.height = height;
        this.pixels = pixels.clone();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int get(int x, int y) {
        return pixels[index(x, y)];
    }

    public void set(int x, int y, int argb) {
        pixels[index(x, y)] = argb;
    }

    public int[] pixels() {
        return pixels.clone();
    }

    private int index(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw new IndexOutOfBoundsException(x + "," + y + " outside " + width + "x" + height);
        }
        return y * width + x;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PixelImage image && width == image.width && height == image.height
                && Arrays.equals(pixels, image.pixels);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * width + height) + Arrays.hashCode(pixels);
    }
}
