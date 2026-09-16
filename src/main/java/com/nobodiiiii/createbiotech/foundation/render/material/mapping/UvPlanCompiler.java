package com.nobodiiiii.createbiotech.foundation.render.material.mapping;






import com.nobodiiiii.createbiotech.foundation.render.material.mapping.UvMapping.Affine;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.UvPlan.UvFragment;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.UvPlan.UvQuad;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.UvPlan.UvVertex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure, one-time compilation of rectangular UV faces into direct-source fragments.
 * A schema-bounded affine grid is scanned into rectangles per face before any geometry is allocated.
 * Neither declared computed_cost nor texture selection participates in geometry eligibility.
 * Overlays and the texture-batch budget are resolved by the model runtime.
 */
public final class UvPlanCompiler {
    private static final double POSITION_EPSILON = 1.0e-5;

    private UvPlanCompiler() {
    }

    public static UvPlan compile(TargetDefinition target, List<UvQuad> quads, RenderPolicy policy) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(quads, "quads");
        Objects.requireNonNull(policy, "policy");
        UvMapping mapping;
        try {
            mapping = UvMapping.compile(target);
        } catch (IllegalArgumentException error) {
            return rejected(quads.size(), 0, 0, 0, "invalid mapping: " + error.getMessage());
        }
        return compile(mapping, quads, policy);
    }

    /** Compiles the supplied immutable interpretation, including any cutout replacements. */
    public static UvPlan compile(UvMapping mapping, List<UvQuad> quads, RenderPolicy policy) {
        Objects.requireNonNull(mapping, "mapping");
        Objects.requireNonNull(quads, "quads");
        Objects.requireNonNull(policy, "policy");

        List<FaceParts> compiled = new ArrayList<>(quads.size());
        int output = 0;
        int maximum = 0;
        int additional = 0;
        for (int index = 0; index < quads.size(); index++) {
            UvQuad quad = quads.get(index);
            Bounds bounds = supportedBounds(quad, mapping.size());
            if (bounds == null) {
                return rejected(quads.size(), output, maximum, additional,
                        "unsupported UV geometry at face " + index);
            }
            FaceParts parts = rectangles(mapping, bounds, policy.maxPiecesPerFace());
            compiled.add(parts);
            try {
                output = Math.addExact(output, parts.count());
                additional = Math.addExact(additional, Math.max(0, parts.count() - 1));
            } catch (ArithmeticException error) {
                return rejected(quads.size(), Integer.MAX_VALUE, Math.max(maximum, parts.count()),
                        Integer.MAX_VALUE, "geometry cost exceeds integer range");
            }
            maximum = Math.max(maximum, parts.count());
        }
        if (maximum > policy.maxPiecesPerFace()) {
            return rejected(quads.size(), output, maximum, additional,
                    "max_pieces_per_face exceeded: " + maximum + " > " + policy.maxPiecesPerFace());
        }
        if (additional > policy.maxAdditionalQuads()) {
            return rejected(quads.size(), output, maximum, additional,
                    "max_additional_quads exceeded: " + additional + " > " + policy.maxAdditionalQuads());
        }

        List<List<UvFragment>> faces = new ArrayList<>(quads.size());
        for (int index = 0; index < quads.size(); index++) {
            UvQuad original = quads.get(index);
            List<UvFragment> fragments = new ArrayList<>(compiled.get(index).count());
            for (Rectangle rectangle : compiled.get(index).rectangles()) {
                fragments.add(fragment(original, compiled.get(index).bounds(), rectangle, mapping.size()));
            }
            faces.add(fragments);
        }
        return new UvPlan(faces, quads.size(), output, maximum, additional, true, "");
    }

    private static UvPlan rejected(int original, int output, int maximum, int additional, String reason) {
        return new UvPlan(Collections.nCopies(original, List.of()), original, output, maximum, additional, false, reason);
    }

    /** Only affine planar geometry with four ordered axis-aligned UV rectangle corners is supported. */
    private static Bounds supportedBounds(UvQuad quad, IntSize target) {
        if (quad == null || !Float.isFinite(quad.nx()) || !Float.isFinite(quad.ny()) || !Float.isFinite(quad.nz())
                || (quad.nx() == 0 && quad.ny() == 0 && quad.nz() == 0)) {
            return null;
        }
        float minU = Float.POSITIVE_INFINITY, minV = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
        for (UvVertex vertex : quad.vertices()) {
            if (!Float.isFinite(vertex.x()) || !Float.isFinite(vertex.y()) || !Float.isFinite(vertex.z())
                    || !Float.isFinite(vertex.u()) || !Float.isFinite(vertex.v())) {
                return null;
            }
            minU = Math.min(minU, vertex.u());
            minV = Math.min(minV, vertex.v());
            maxU = Math.max(maxU, vertex.u());
            maxV = Math.max(maxV, vertex.v());
        }
        if (minU < 0 || minV < 0 || maxU > target.width() || maxV > target.height() || minU == maxU || minV == maxV) {
            return null;
        }
        int cornerMask = 0;
        for (int i = 0; i < 4; i++) {
            UvVertex current = quad.vertices().get(i), next = quad.vertices().get((i + 1) % 4);
            if ((current.u() != minU && current.u() != maxU) || (current.v() != minV && current.v() != maxV)
                    || (current.u() == next.u()) == (current.v() == next.v())) {
                return null;
            }
            int corner = (current.u() == maxU ? 1 : 0) | (current.v() == maxV ? 2 : 0);
            if ((cornerMask & (1 << corner)) != 0) {
                return null;
            }
            cornerMask |= 1 << corner;
        }
        UvVertex p0 = quad.vertices().get(0), p1 = quad.vertices().get(1);
        UvVertex p2 = quad.vertices().get(2), p3 = quad.vertices().get(3);
        if (!affineCoordinate(p0.x(), p1.x(), p2.x(), p3.x())
                || !affineCoordinate(p0.y(), p1.y(), p2.y(), p3.y())
                || !affineCoordinate(p0.z(), p1.z(), p2.z(), p3.z())) {
            return null;
        }
        double ax = (double) p1.x() - p0.x(), ay = (double) p1.y() - p0.y(), az = (double) p1.z() - p0.z();
        double bx = (double) p3.x() - p0.x(), by = (double) p3.y() - p0.y(), bz = (double) p3.z() - p0.z();
        double cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
        double areaSquared = cx * cx + cy * cy + cz * cz;
        if (areaSquared == 0) {
            return null;
        }
        double dot = cx * quad.nx() + cy * quad.ny() + cz * quad.nz();
        double normalSquared = (double) quad.nx() * quad.nx() + (double) quad.ny() * quad.ny() + (double) quad.nz() * quad.nz();
        if (dot * dot < areaSquared * normalSquared * (1 - POSITION_EPSILON)) {
            return null;
        }
        return new Bounds(minU, minV, maxU, maxV);
    }

    private static boolean affineCoordinate(float p0, float p1, float p2, float p3) {
        double scale = Math.max(1, Math.max(Math.abs((double) p1 - p0), Math.abs((double) p3 - p0)));
        return Math.abs((double) p0 + p2 - p1 - p3) <= POSITION_EPSILON * scale;
    }

    private static FaceParts rectangles(UvMapping mapping, Bounds bounds, int retainLimit) {
        List<Rectangle> retained = new ArrayList<>();
        Map<Span, Rectangle> active = Map.of();
        int count = 0;
        int firstX = (int) Math.floor(bounds.minU()), lastX = (int) Math.ceil(bounds.maxU());
        for (int y = (int) Math.floor(bounds.minV()); y < Math.ceil(bounds.maxV()); y++) {
            Map<Span, Rectangle> next = new LinkedHashMap<>();
            for (int x = firstX; x < lastX;) {
                Affine affine = mapping.at(x, y);
                int start = x++;
                if (affine == null) {
                    continue;
                }
                while (x < lastX && affine.equals(mapping.at(x, y))) {
                    x++;
                }
                Span span = new Span(Math.max(bounds.minU(), start), Math.min(bounds.maxU(), x), affine);
                Rectangle rectangle = active.get(span);
                if (rectangle == null) {
                    rectangle = new Rectangle(span, Math.max(bounds.minV(), y), Math.min(bounds.maxV(), y + 1));
                    count++;
                    if (count <= retainLimit) {
                        retained.add(rectangle);
                    } else if (count == (long) retainLimit + 1) {
                        // Continue measuring real costs, but never retain a pixel-quad-sized rejected plan.
                        retained.clear();
                    }
                } else {
                    rectangle.maxV = Math.min(bounds.maxV(), y + 1);
                }
                next.put(span, rectangle);
            }
            active = next;
        }
        return new FaceParts(bounds, retained, count);
    }

    private static UvFragment fragment(UvQuad original, Bounds bounds, Rectangle rectangle, IntSize targetSize) {
        List<UvVertex> vertices = new ArrayList<>(4);
        List<UvVertex> targetVertices = new ArrayList<>(4);
        for (UvVertex corner : original.vertices()) {
            float u = corner.u() == bounds.minU() ? rectangle.span.minU() : rectangle.span.maxU();
            float v = corner.v() == bounds.minV() ? rectangle.minV : rectangle.maxV;
            UvVertex mapped = interpolate(original, u, v, rectangle.span.affine());
            vertices.add(mapped);
            targetVertices.add(new UvVertex(mapped.x(), mapped.y(), mapped.z(),
                    u / targetSize.width(), v / targetSize.height()));
        }
        return new UvFragment(rectangle.span.affine().slot(),
                new UvQuad(vertices, original.nx(), original.ny(), original.nz()),
                new UvQuad(targetVertices, original.nx(), original.ny(), original.nz()));
    }

    private static UvVertex interpolate(UvQuad original, float u, float v, Affine mapping) {
        UvVertex origin = original.vertices().get(0), first = original.vertices().get(1), second = original.vertices().get(3);
        double du1 = (double) first.u() - origin.u(), dv1 = (double) first.v() - origin.v();
        double du2 = (double) second.u() - origin.u(), dv2 = (double) second.v() - origin.v();
        double du = (double) u - origin.u(), dv = (double) v - origin.v();
        double determinant = du1 * dv2 - dv1 * du2;
        double a = (du * dv2 - dv * du2) / determinant, b = (du1 * dv - dv1 * du) / determinant;
        return new UvVertex(
                (float) (origin.x() + a * ((double) first.x() - origin.x()) + b * ((double) second.x() - origin.x())),
                (float) (origin.y() + a * ((double) first.y() - origin.y()) + b * ((double) second.y() - origin.y())),
                (float) (origin.z() + a * ((double) first.z() - origin.z()) + b * ((double) second.z() - origin.z())),
                (float) mapping.mapU(u, v),
                (float) mapping.mapV(u, v));
    }

    private record Bounds(float minU, float minV, float maxU, float maxV) {
    }

    private record FaceParts(Bounds bounds, List<Rectangle> rectangles, int count) {
    }

    private record Span(float minU, float maxU, Affine affine) {
    }

    private static final class Rectangle {
        private final Span span;
        private final float minV;
        private float maxV;

        private Rectangle(Span span, float minV, float maxV) {
            this.span = span;
            this.minV = minV;
            this.maxV = maxV;
        }
    }

}
