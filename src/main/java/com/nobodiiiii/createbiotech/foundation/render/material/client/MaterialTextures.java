package com.nobodiiiii.createbiotech.foundation.render.material.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.decoration.encasing.CasingConnectivity;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.IntSize;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.PixelImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Client source texture discovery and validation. */
@OnlyIn(Dist.CLIENT)
public final class MaterialTextures {
    private MaterialTextures() {
    }

    /** Called on a binding-cache miss, on the render thread after client models have been baked. */
    public static Optional<ResourceLocation> findDefault(ResourceLocation materialId) {
        return BuiltInRegistries.BLOCK.getOptional(materialId).flatMap(block ->
                findDefault(block.defaultBlockState(), CreateClient.CASING_CONNECTIVITY,
                        state -> Minecraft.getInstance().getBlockRenderer().getBlockModel(state)
                                .getParticleIcon(ModelData.EMPTY)));
    }

    static Optional<ResourceLocation> findDefault(BlockState state, CasingConnectivity connectivity,
            Function<BlockState, TextureAtlasSprite> particleLookup) {
        var entry = connectivity.get(state);
        // Match upstream: the registered shift is authoritative; only unregistered blocks use particles.
        var sprite = entry != null ? entry.getCasing().getTarget() : particleLookup.apply(state);
        return isMissing(sprite) ? Optional.empty() : Optional.of(sprite.contents().name());
    }

    private static boolean isMissing(TextureAtlasSprite sprite) {
        return sprite == null || MissingTextureAtlasSprite.getLocation().equals(sprite.contents().name());
    }

    public static Optional<SourceSprite> findDirect(ResourceLocation id, IntSize grid) {
        var minecraft = Minecraft.getInstance();
        return findDirect(id, grid, minecraft.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS),
                png -> minecraft.getResourceManager().getResource(png).isPresent(),
                (sprite, logicalGrid) -> loadImage(minecraft.getResourceManager(), sprite, logicalGrid)
                        .map(source -> new SourceSprite(textureId(sprite), logicalGrid, 0, 0, 1, 1, sprite, hasAnimation(minecraft.getResourceManager(), sprite))));
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
                sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), id, sprite.contents().getUniqueFrames().limit(2).count() > 1,
                () -> toPixelImage(sprite.contents().getOriginalImage())));
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

    static boolean hasAnimation(ResourceManager resources, ResourceLocation sprite) {
        try {
            var resource = resources.getResource(textureId(sprite));
            return resource.isPresent() && resource.get().metadata().getSection(
                    net.minecraft.client.resources.metadata.animation.AnimationMetadataSection.SERIALIZER).isPresent();
        } catch (IOException error) {
            return true; // Unknown metadata must not freeze an animated source into a static skin.
        }
    }

    private static ResourceLocation sourceId(ResourceLocation texture) {
        String path = texture.getPath();
        return path.startsWith("textures/") && path.endsWith(".png") && !path.startsWith("textures/atlas/")
                ? texture.withPath(path.substring(9, path.length() - 4)) : null;
    }

    /** An existing texture binding; owns no pixels, GPU texture, or animation ticker. */
    public record SourceSprite(ResourceLocation texture, IntSize grid, float u0, float v0, float u1, float v1,
                               ResourceLocation source, boolean animated, Supplier<PixelImage> pixels) {
        public SourceSprite(ResourceLocation texture, IntSize grid, float u0, float v0, float u1, float v1,
                            ResourceLocation source, boolean animated) {
            this(texture, grid, u0, v0, u1, v1, source, animated, null);
        }
        public SourceSprite(ResourceLocation texture, IntSize grid, float u0, float v0, float u1, float v1) {
            this(texture, grid, u0, v0, u1, v1, sourceId(texture), false);
        }
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
