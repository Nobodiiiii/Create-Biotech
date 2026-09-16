package com.nobodiiiii.createbiotech.foundation.render.material;

import com.nobodiiiii.createbiotech.foundation.render.material.CastedMaterialsApi.MaterialSetResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

public final class CastedMaterialState {
    public static final String NBT_KEY = "CastedMaterial";

    private ResourceLocation material;

    public Optional<ResourceLocation> get() {
        return Optional.ofNullable(material);
    }

    public MaterialSetResult set(ResourceLocation next) {
        Objects.requireNonNull(next, "next");
        if (next.equals(material)) {
            return MaterialSetResult.UNCHANGED;
        }
        material = next;
        return MaterialSetResult.CHANGED;
    }

    public MaterialSetResult clear() {
        if (material == null) {
            return MaterialSetResult.UNCHANGED;
        }
        material = null;
        return MaterialSetResult.CHANGED;
    }

    public void save(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (material == null) {
            tag.remove(NBT_KEY);
        } else {
            tag.putString(NBT_KEY, material.toString());
        }
    }

    /** Applies a full saved/synced snapshot; an absent key represents a removed material. */
    public void load(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (!tag.contains(NBT_KEY)) {
            material = null;
            return;
        }
        if (!tag.contains(NBT_KEY, Tag.TAG_STRING)) {
            return;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(tag.getString(NBT_KEY));
        if (parsed != null) {
            material = parsed;
        }
    }
}
