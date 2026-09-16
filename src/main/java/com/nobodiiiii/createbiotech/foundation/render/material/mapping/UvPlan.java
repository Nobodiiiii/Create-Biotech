package com.nobodiiiii.createbiotech.foundation.render.material.mapping;

import java.util.List;
import java.util.Objects;

/**
 * Immutable compile result indexed by original face. Rejected plans expose no renderable fragments.
 * Budget rejections still report measured post-merge counts. Additional quads are counted per face;
 * transparent omitted faces never cancel another face's extra geometry.
 */
public record UvPlan(List<List<UvFragment>> faces, int originalQuads, int outputQuads,
                     int maxPiecesPerFace, int additionalQuads, boolean eligible, String rejectionReason) {
    public UvPlan {
        faces = faces.stream().map(List::copyOf).toList();
        Objects.requireNonNull(rejectionReason, "rejectionReason");
    }

    /** A clipped quad plus the same geometry addressing the original target texture. */
    public record UvFragment(String sourceSlot, UvQuad quad, UvQuad targetQuad) {
        public UvFragment {
            Objects.requireNonNull(sourceSlot, "sourceSlot");
            Objects.requireNonNull(quad, "quad");
            Objects.requireNonNull(targetQuad, "targetQuad");
        }
    }

    /** Four ordered corners and their model-local normal. The compiler validates geometry support. */
    public record UvQuad(List<UvVertex> vertices, float nx, float ny, float nz) {
        public UvQuad {
            vertices = List.copyOf(vertices);
            if (vertices.size() != 4) {
                throw new IllegalArgumentException("A UV quad must contain exactly four vertices");
            }
        }
    }

    /** Immutable model-local position and UV in logical pixel-edge coordinates, never normalized UVs. */
    public record UvVertex(float x, float y, float z, float u, float v) {
    }
}
