package com.nobodiiiii.createbiotech.foundation.render.material.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.model.geom.ModelPart;


public final class CastedMaterialsClient {
    private static volatile DefaultModelRuntime modelRuntime;
    private CastedMaterialsClient() {}
    /** Host rendering entry: UV source bindings or the original base model. */
    public static CastedModelHandle resolveModel(ModelPart root, ResourceLocation targetId,
                                                  ResourceLocation materialId, ResourceLocation fallbackTexture) {
        if (materialId == null) return CastedModelHandle.fallback(root, fallbackTexture);
        try {
            DefaultModelRuntime current = modelRuntime;
            if (current == null) {
                synchronized (CastedMaterialsClient.class) {
                    if (modelRuntime == null) modelRuntime = DefaultModelRuntime.create();
                    current = modelRuntime;
                }
            }
            return current.resolve(root, targetId, materialId, fallbackTexture);
        } catch (RuntimeException error) {
            return CastedModelHandle.fallback(root, fallbackTexture);
        }
    }
}
