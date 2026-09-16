package com.nobodiiiii.createbiotech.foundation.render.material.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.IntSize;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.PixelImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/** Client texture discovery, validation and direct binding without generated textures. */
@OnlyIn(Dist.CLIENT)
public final class MaterialTextures {
    private MaterialTextures() {
    }

    /** Must be called on the render thread after client models have been baked. */
    public static Optional<ResourceLocation> findDefault(ResourceLocation materialId) {
        return BuiltInRegistries.BLOCK.getOptional(materialId).flatMap(block -> {
            BlockState state = block.defaultBlockState();
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            TextureAtlasSprite particle = model.getParticleIcon(ModelData.EMPTY);
            if (!isMissing(particle)) {
                return Optional.of(particle.contents().name());
            }
            return largestQuadSprite(model, state);
        });
    }

    private static Optional<ResourceLocation> largestQuadSprite(BakedModel model, BlockState state) {
        Map<ResourceLocation, Double> areas = new HashMap<>();
        accumulate(areas, model.getQuads(state, null, RandomSource.create()));
        for (Direction direction : Direction.values()) {
            accumulate(areas, model.getQuads(state, direction, RandomSource.create()));
        }
        return areas.entrySet().stream()
                .min(Map.Entry.<ResourceLocation, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey);
    }

    private static void accumulate(Map<ResourceLocation, Double> areas, List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            areas.merge(quad.getSprite().contents().name(), planarArea(quad.getVertices()), Double::sum);
        }
    }

    private static boolean isMissing(TextureAtlasSprite sprite) {
        return sprite == null || MissingTextureAtlasSprite.getLocation().equals(sprite.contents().name());
    }

    private static double planarArea(int[] vertices) {
        if (vertices.length < 32) {
            return 0;
        }
        double[] first = vertex(vertices, 0);
        double[] second = vertex(vertices, 1);
        double[] third = vertex(vertices, 2);
        double[] fourth = vertex(vertices, 3);
        return triangleArea(first, second, third) + triangleArea(first, third, fourth);
    }

    private static double[] vertex(int[] vertices, int index) {
        int base = index * 8;
        return new double[]{
                Float.intBitsToFloat(vertices[base]),
                Float.intBitsToFloat(vertices[base + 1]),
                Float.intBitsToFloat(vertices[base + 2])
        };
    }

    private static double triangleArea(double[] first, double[] second, double[] third) {
        double abX = second[0] - first[0];
        double abY = second[1] - first[1];
        double abZ = second[2] - first[2];
        double acX = third[0] - first[0];
        double acY = third[1] - first[1];
        double acZ = third[2] - first[2];
        double crossX = abY * acZ - abZ * acY;
        double crossY = abZ * acX - abX * acZ;
        double crossZ = abX * acY - abY * acX;
        return Math.sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ) / 2;
    }

    public static Optional<SourceSprite> findDirect(ResourceLocation id, IntSize grid) {
        var minecraft = Minecraft.getInstance();
        return findDirect(id, grid, minecraft.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS),
                png -> minecraft.getResourceManager().getResource(png).isPresent(),
                (sprite, logicalGrid) -> loadImage(minecraft.getResourceManager(), sprite, logicalGrid)
                        .map(source -> new SourceSprite(textureId(sprite), logicalGrid, 0, 0, 1, 1)));
    }

    public static ResourceLocation textureId(ResourceLocation sprite) {
        return ResourceLocation.fromNamespaceAndPath(sprite.getNamespace(), "textures/" + sprite.getPath() + ".png");
    }

    static Optional<SourceSprite> findDirect(ResourceLocation id, IntSize grid,
            Function<ResourceLocation, TextureAtlasSprite> atlas, Predicate<ResourceLocation> resourceExists,
            BiFunction<ResourceLocation, IntSize, Optional<SourceSprite>> textureLookup) {
        var sprite = atlas.apply(id);
        if (sprite == null || sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
            ResourceLocation png = textureId(id);
            if (resourceExists.test(png)) {
                // An authoritative PNG must not be replaced by a lower-precedence material.
                // Decode/validate only on a binding-cache miss; Minecraft owns the original texture.
                return Optional.of(textureLookup.apply(id, grid).filter(source -> source.grid().equals(grid))
                        .orElseThrow(() -> new IllegalStateException("Source PNG is present but incompatible: " + id)));
            }
            return Optional.empty();
        }
        int width = sprite.contents().width(), height = sprite.contents().height();
        if (grid.integerScaleOf(width, height).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SourceSprite(sprite.atlasLocation(), grid,
                sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1()));
    }

    public static Optional<PixelImage> loadImage(ResourceManager resources, ResourceLocation spriteId,
                                                  IntSize logicalGrid) {
        ResourceLocation png = textureId(spriteId);
        Optional<Resource> resource = resources.getResource(png);
        if (resource.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream stream = resource.get().open(); NativeImage nativeImage = NativeImage.read(stream)) {
            PixelImage image = toPixelImage(nativeImage);
            return logicalGrid.integerScaleOf(image.width(), image.height()).isPresent()
                    ? Optional.of(image) : Optional.empty();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private static PixelImage toPixelImage(NativeImage nativeImage) {
        PixelImage image = new PixelImage(nativeImage.getWidth(), nativeImage.getHeight());
        for (int y = 0; y < nativeImage.getHeight(); y++) {
            for (int x = 0; x < nativeImage.getWidth(); x++) {
                image.set(x, y, abgrToArgb(nativeImage.getPixelRGBA(x, y)));
            }
        }
        return image;
    }

    private static int abgrToArgb(int abgr) {
        int alpha = abgr >>> 24;
        int blue = abgr >>> 16 & 0xff;
        int green = abgr >>> 8 & 0xff;
        int red = abgr & 0xff;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    /** An existing texture binding; owns no pixels, GPU texture, or animation ticker. */
    public record SourceSprite(ResourceLocation texture, IntSize grid, float u0, float v0, float u1, float v1) {
        public SourceSprite {
            Objects.requireNonNull(texture);
            Objects.requireNonNull(grid);
            if (!Float.isFinite(u0) || !Float.isFinite(v0) || !Float.isFinite(u1) || !Float.isFinite(v1)
                    || u0 < 0 || v0 < 0 || u1 > 1 || v1 > 1 || u1 <= u0 || v1 <= v0) {
                throw new IllegalArgumentException("invalid source sprite bounds");
            }
        }

        public float u(float logicalEdge) {
            return u0 + (u1 - u0) * logicalEdge / grid.width();
        }

        public float v(float logicalEdge) {
            return v0 + (v1 - v0) * logicalEdge / grid.height();
        }
    }
}
