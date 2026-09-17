package com.nobodiiiii.createbiotech.foundation.render.material.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.foundation.render.material.mapping.PixelImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Render-thread cache whose bindings remain valid until resource reload or close. */
final class ComposedTextureCache implements AutoCloseable {
    private static final int DEFAULT_MAX_ENTRIES = 128;
    private static final long DEFAULT_MAX_BYTES = 16L * 1024 * 1024;
    private static final AtomicLong NEXT_TEXTURE_ID = new AtomicLong();

    record Images(PixelImage color, PixelImage emission) {
        Images {
            Objects.requireNonNull(color);
            if (emission != null && (color.width() != emission.width() || color.height() != emission.height())) {
                throw new IllegalArgumentException("Color and emission dimensions must match");
            }
        }
    }

    record Textures(ResourceLocation color, ResourceLocation emission) {}

    interface Backend {
        void upload(ResourceLocation id, PixelImage image);
        void release(ResourceLocation id);
    }

    private final int maxEntries;
    private final long maxBytes;
    private final Backend backend;
    private final Map<Object, Textures> entries = new HashMap<>();
    private long usedBytes;
    private boolean closed;

    ComposedTextureCache(int maxEntries, long maxBytes, Backend backend) {
        if (maxEntries < 0 || maxBytes < 0) {
            throw new IllegalArgumentException("Cache limits must be non-negative");
        }
        this.maxEntries = maxEntries;
        this.maxBytes = maxBytes;
        this.backend = Objects.requireNonNull(backend);
    }

    static ComposedTextureCache create() {
        return new ComposedTextureCache(DEFAULT_MAX_ENTRIES, DEFAULT_MAX_BYTES, new Backend() {
            @Override
            public void upload(ResourceLocation id, PixelImage image) {
                RenderSystem.assertOnRenderThread();
                NativeImage pixels = toNativeImage(image);
                DynamicTexture texture = null;
                try {
                    // This constructor uploads once, at the exact composed dimensions, without an atlas page.
                    texture = new DynamicTexture(pixels);
                    Minecraft.getInstance().getTextureManager().register(id, texture);
                } catch (RuntimeException | Error failure) {
                    if (texture == null) {
                        pixels.close();
                    } else {
                        texture.close();
                    }
                    throw failure;
                }
            }

            @Override
            public void release(ResourceLocation id) {
                Minecraft.getInstance().getTextureManager().release(id);
            }
        });
    }

    Optional<Textures> resolve(Object key, Supplier<Optional<Images>> factory) {
        Textures existing = entries.get(key);
        if (existing != null) {
            return Optional.of(existing);
        }
        // Never evict textures referenced by an earlier batch in the same frame.
        if (closed || entries.size() >= maxEntries || usedBytes >= maxBytes) {
            return Optional.empty();
        }
        Optional<Images> composed = factory.get();
        if (composed.isEmpty()) {
            return Optional.empty();
        }
        Images images = composed.get();
        long bytes = byteSize(images.color()) + (images.emission() == null ? 0 : byteSize(images.emission()));
        if (bytes > maxBytes - usedBytes) {
            return Optional.empty();
        }
        ResourceLocation color = nextTextureId();
        ResourceLocation emission = images.emission() == null ? null : nextTextureId();
        boolean emissionStarted = false;
        try {
            backend.upload(color, images.color());
            if (emission != null) {
                emissionStarted = true;
                backend.upload(emission, images.emission());
            }
        } catch (RuntimeException failure) {
            backend.release(color);
            if (emissionStarted) {
                backend.release(emission);
            }
            return Optional.empty();
        }
        Textures textures = new Textures(color, emission);
        entries.put(key, textures);
        usedBytes += bytes;
        return Optional.of(textures);
    }

    private static long byteSize(PixelImage image) {
        return (long) image.width() * image.height() * Integer.BYTES;
    }

    private static ResourceLocation nextTextureId() {
        return ResourceLocation.fromNamespaceAndPath(CreateBiotech.MOD_ID,
                "dynamic/composed_material/" + NEXT_TEXTURE_ID.getAndIncrement());
    }

    static NativeImage toNativeImage(PixelImage source) {
        NativeImage result = new NativeImage(source.width(), source.height(), false);
        for (int y = 0; y < source.height(); y++) {
            for (int x = 0; x < source.width(); x++) {
                int argb = source.get(x, y);
                int abgr = (argb & 0xff00ff00) | (argb >>> 16 & 0xff) | (argb & 0xff) << 16;
                result.setPixelRGBA(x, y, abgr);
            }
        }
        return result;
    }

    void clear() {
        for (Textures textures : entries.values()) {
            backend.release(textures.color());
            if (textures.emission() != null) {
                backend.release(textures.emission());
            }
        }
        entries.clear();
        usedBytes = 0;
    }

    @Override
    public void close() {
        clear();
        closed = true;
    }
}
