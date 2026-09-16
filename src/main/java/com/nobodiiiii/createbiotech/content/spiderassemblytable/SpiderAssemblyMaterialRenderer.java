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
    static final ResourceLocation FIXED_EYES =
            ResourceLocation.parse("create_biotech:textures/block/spider_assembly_table/casted_fixed_eyes.png");

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

    static ResourceLocation eyes(CastedModelHandle.Backend backend, ResourceLocation fallback) {
        return backend == CastedModelHandle.Backend.DIRECT_UV
                ? FIXED_EYES : fallback;
    }
}
