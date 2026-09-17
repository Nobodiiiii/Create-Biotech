package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import com.nobodiiiii.createbiotech.foundation.render.material.CastedMaterialsApi;
import com.nobodiiiii.createbiotech.foundation.render.material.client.CastedMaterialsClient;
import com.nobodiiiii.createbiotech.foundation.render.material.client.CastedModelHandle;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Host model bridge; material discovery and caching belong to the internal rendering module. */
final class SpiderAssemblyMaterialRenderer {
    static final ResourceLocation TARGET = ResourceLocation.parse("create_biotech:spider_assembly_table/spider");

    private SpiderAssemblyMaterialRenderer() { }

    static CastedModelHandle resolveModel(ModelPart root, SpiderAssemblyTableBlockEntity be, ResourceLocation fallback) {
        return resolveModel(root, CastedMaterialsApi.getMaterial(be), fallback, CastedMaterialsClient::resolveModel);
    }

    static CastedModelHandle resolveModel(ModelPart root, ItemStack stack, ResourceLocation fallback) {
        return resolveModel(root, CastedMaterialsApi.getMaterial(stack), fallback, CastedMaterialsClient::resolveModel);
    }

    static CastedModelHandle resolveModel(ModelPart root, Optional<ResourceLocation> material,
                                         ResourceLocation fallback, ModelResolver resolver) {
        return material.map(id -> resolver.resolve(root, TARGET, id, fallback))
                .orElseGet(() -> CastedModelHandle.fallback(root, fallback));
    }

    @FunctionalInterface
    interface ModelResolver {
        CastedModelHandle resolve(ModelPart root, ResourceLocation target, ResourceLocation material, ResourceLocation fallback);
    }

    static void renderEyes(CastedModelHandle handle, com.mojang.blaze3d.vertex.PoseStack pose,
                           net.minecraft.client.renderer.MultiBufferSource buffers, ResourceLocation fallback,
                           int light, int overlay, int color) {
        if (handle.backend() != CastedModelHandle.Backend.FALLBACK)
            handle.renderEmissive(pose, buffers, light, overlay, color);
        else
            handle.renderLayer(pose, buffers.getBuffer(net.minecraft.client.renderer.RenderType.eyes(fallback)),
                    light, overlay, color);
    }
}
