package com.nobodiiiii.createbiotech.foundation.render.material.palette;

import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

public final class MaterialPalette {
    private static final MaterialPalette EMPTY = create(List.of());

    private final List<ResourceLocation> ids;
    private final Map<ResourceLocation, Integer> indices;
    private final byte[] fingerprint;

    private MaterialPalette(
            List<ResourceLocation> ids,
            Map<ResourceLocation, Integer> indices,
            byte[] fingerprint) {
        this.ids = ids;
        this.indices = indices;
        this.fingerprint = fingerprint;
    }

    public static MaterialPalette empty() {
        return EMPTY;
    }

    public static MaterialPalette from(Collection<ResourceLocation> input) {
        if (input.isEmpty()) {
            return EMPTY;
        }
        List<ResourceLocation> ids = input.stream()
                .distinct()
                .sorted(MaterialDiscovery.ID_ORDER)
                .toList();
        return create(ids);
    }

    private static MaterialPalette create(List<ResourceLocation> input) {
        List<ResourceLocation> ids = List.copyOf(input);
        Map<ResourceLocation, Integer> indices = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            indices.put(ids.get(i), i);
        }
        return new MaterialPalette(ids, Map.copyOf(indices), hash(ids));
    }

    private static byte[] hash(List<ResourceLocation> ids) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (ResourceLocation id : ids) {
                digest.update(id.toString().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    public List<ResourceLocation> ids() {
        return ids;
    }

    public OptionalInt indexOf(ResourceLocation id) {
        Integer index = indices.get(id);
        return index == null ? OptionalInt.empty() : OptionalInt.of(index);
    }

    public Optional<ResourceLocation> idAt(int index) {
        return index < 0 || index >= ids.size() ? Optional.empty() : Optional.of(ids.get(index));
    }

    public byte[] fingerprint() {
        return fingerprint.clone();
    }
}
