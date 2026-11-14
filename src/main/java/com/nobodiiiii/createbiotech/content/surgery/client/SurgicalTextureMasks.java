package com.nobodiiiii.createbiotech.content.surgery.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/** Internal SurgicalTextureMasks implementation; public entry points remain on SurgicalCapturedRenderPlan. */
final class SurgicalTextureMasks {
	private SurgicalTextureMasks() {}


	static final Map<ResourceLocation, CachedAlphaMask> ALPHA_MASKS = new ConcurrentHashMap<>();

	static AlphaMask alphaMask(ResourceLocation texture) {
		int generation = SurgicalCapturedRenderPlan.resourceGeneration;
		CachedAlphaMask cached = ALPHA_MASKS.get(texture);
		if (cached != null && cached.generation == generation)
			return cached.mask;
		AlphaMask loaded = loadAlphaMask(texture);
		if (generation != SurgicalCapturedRenderPlan.resourceGeneration)
			return loaded;
		CachedAlphaMask candidate = new CachedAlphaMask(generation, loaded);
		CachedAlphaMask stored = ALPHA_MASKS.compute(texture, (ignored, existing) -> {
			if (generation != SurgicalCapturedRenderPlan.resourceGeneration)
				return existing;
			return existing != null && existing.generation == generation ? existing : candidate;
		});
		if (generation != SurgicalCapturedRenderPlan.resourceGeneration) {
			ALPHA_MASKS.remove(texture, candidate);
			return loaded;
		}
		return stored == null ? loaded : stored.mask;
	}

	static AlphaMask loadAlphaMask(ResourceLocation texture) {
		Resource resource = Minecraft.getInstance().getResourceManager().getResource(texture).orElse(null);
		if (resource == null)
			return AlphaMask.OPAQUE;
		try (InputStream stream = resource.open(); NativeImage image = NativeImage.read(stream)) {
			int width = image.getWidth();
			int height = image.getHeight();
			if (!image.format().hasAlpha())
				return new AlphaMask(width, height, new BitSet(), true);
			BitSet pixels = new BitSet(width * height);
			for (int y = 0; y < height; y++)
				for (int x = 0; x < width; x++)
					if ((image.getPixelRGBA(x, y) >>> 24 & 0xff) != 0)
						pixels.set(y * width + x);
			return new AlphaMask(width, height, pixels, false);
		} catch (IOException | RuntimeException ignored) {
			return AlphaMask.OPAQUE;
		}
	}

	record CachedAlphaMask(int generation, AlphaMask mask) {}

	record AlphaMask(int width, int height, BitSet pixels, boolean opaque) {
		private static final AlphaMask OPAQUE = new AlphaMask(0, 0, new BitSet(), true);

		boolean hasVisiblePixels(float minU, float minV, float maxU, float maxV) {
			if (opaque)
				return true;
			int minX = clamp((int) Math.floor(Math.min(minU, maxU) * width), width);
			int minY = clamp((int) Math.floor(Math.min(minV, maxV) * height), height);
			int maxX = clamp((int) Math.ceil(Math.max(minU, maxU) * width) - 1, width);
			int maxY = clamp((int) Math.ceil(Math.max(minV, maxV) * height) - 1, height);
			for (int y = minY; y <= maxY; y++) {
				int set = pixels.nextSetBit(y * width + minX);
				if (set >= 0 && set <= y * width + maxX)
					return true;
			}
			return false;
		}

		private static int clamp(int value, int maxExclusive) {
			return maxExclusive <= 0 ? 0 : Math.max(0, Math.min(value, maxExclusive - 1));
		}
	}
}
