package com.nobodiiiii.createbiotech.foundation.gui;

/** Screen-local pan/zoom transform. It owns no content or persisted positions. */
public final class CanvasViewport {
    private int zoom;
    private double x, y;

    public double scale() { return Math.pow(2, zoom / 8.0); }
    public double screenX(double worldX) { finite(worldX); return worldX * scale() + x; }
    public double screenY(double worldY) { finite(worldY); return worldY * scale() + y; }
    public double worldX(double screenX) { finite(screenX); return (screenX - x) / scale(); }
    public double worldY(double screenY) { finite(screenY); return (screenY - y) / scale(); }

    public void pan(double screenDx, double screenDy) {
        finite(screenDx); finite(screenDy);
        double nextX = x + screenDx, nextY = y + screenDy;
        finite(nextX); finite(nextY);
        x = nextX; y = nextY;
    }

    public void zoomAt(double screenX, double screenY, int steps) {
        double anchorX = worldX(screenX), anchorY = worldY(screenY);
        int next = Math.clamp((long) zoom + steps, -16, 16);
        double factor = Math.pow(2, next / 8.0);
        double nextX = screenX - anchorX * factor, nextY = screenY - anchorY * factor;
        finite(nextX); finite(nextY);
        zoom = next; x = nextX; y = nextY;
    }

    public void reset() { zoom = 0; x = y = 0; }
    private static void finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite canvas coordinate");
    }
}
