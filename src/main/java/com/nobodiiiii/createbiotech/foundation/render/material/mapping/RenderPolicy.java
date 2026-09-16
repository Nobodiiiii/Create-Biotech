package com.nobodiiiii.createbiotech.foundation.render.material.mapping;

/** Client rendering limits; costs are measured against captured model faces, not JSON metadata. */
public record RenderPolicy(int maxPiecesPerFace, int maxAdditionalQuads, int maxTextureBatches) {
    public static final RenderPolicy DEFAULT = new RenderPolicy(64, 2048, 4);

    public RenderPolicy {
        if (maxPiecesPerFace < 1) {
            throw new IllegalArgumentException("max_pieces_per_face must be positive");
        }
        if (maxAdditionalQuads < 0) {
            throw new IllegalArgumentException("max_additional_quads must be non-negative");
        }
        if (maxTextureBatches < 1) {
            throw new IllegalArgumentException("max_texture_batches must be positive");
        }
    }

}
