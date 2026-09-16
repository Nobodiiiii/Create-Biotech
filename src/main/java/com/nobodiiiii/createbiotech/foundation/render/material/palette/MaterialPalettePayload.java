package com.nobodiiiii.createbiotech.foundation.render.material.palette;

import com.nobodiiiii.createbiotech.foundation.render.material.MaterialRenderingModule;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record MaterialPalettePayload(List<ResourceLocation> ids, byte[] fingerprint)
        implements CustomPacketPayload {
    public static final int PROTOCOL_VERSION = 1;
    public static final int FINGERPRINT_BYTES = 32;
    public static final int MAX_MATERIALS = 16_384;

    public static final Type<MaterialPalettePayload> TYPE =
            new Type<>(MaterialRenderingModule.id("material_palette"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaterialPalettePayload> STREAM_CODEC =
            StreamCodec.of(MaterialPalettePayload::encode, MaterialPalettePayload::decode);

    public MaterialPalettePayload {
        ids = List.copyOf(ids);
        fingerprint = fingerprint.clone();
        if (fingerprint.length != FINGERPRINT_BYTES) {
            throw new IllegalArgumentException("Palette fingerprint must be 32 bytes");
        }
        if (ids.size() > MAX_MATERIALS) {
            throw new IllegalArgumentException("Palette exceeds " + MAX_MATERIALS + " materials");
        }
    }

    @Override
    public byte[] fingerprint() {
        return fingerprint.clone();
    }

    public static MaterialPalettePayload from(MaterialPalette palette) {
        return new MaterialPalettePayload(palette.ids(), palette.fingerprint());
    }

    public MaterialPalette toPalette() {
        MaterialPalette palette = MaterialPalette.from(ids);
        if (!palette.ids().equals(ids) || !Arrays.equals(palette.fingerprint(), fingerprint)) {
            throw new IllegalArgumentException("Palette order or fingerprint is invalid");
        }
        return palette;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MaterialPalettePayload payload) {
        buffer.writeVarInt(PROTOCOL_VERSION);
        buffer.writeBytes(payload.fingerprint);
        buffer.writeVarInt(payload.ids.size());
        for (ResourceLocation id : payload.ids) {
            ResourceLocation.STREAM_CODEC.encode(buffer, id);
        }
    }

    private static MaterialPalettePayload decode(RegistryFriendlyByteBuf buffer) {
        int protocol = buffer.readVarInt();
        if (protocol != PROTOCOL_VERSION) {
            throw new IllegalArgumentException("Unsupported Casted Materials protocol " + protocol);
        }
        byte[] fingerprint = new byte[FINGERPRINT_BYTES];
        buffer.readBytes(fingerprint);
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_MATERIALS) {
            throw new IllegalArgumentException("Invalid material count " + count);
        }
        List<ResourceLocation> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(ResourceLocation.STREAM_CODEC.decode(buffer));
        }
        MaterialPalettePayload payload = new MaterialPalettePayload(ids, fingerprint);
        payload.toPalette();
        return payload;
    }
}
