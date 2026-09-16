package com.nobodiiiii.createbiotech.foundation.render.material;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.foundation.render.material.palette.MaterialPaletteSync;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Native Biotech module; legacy data IDs are not a separate mod registration. */
public final class MaterialRenderingModule {
    public static final String OWNER_MOD_ID = CreateBiotech.MOD_ID;
    public static final String DATA_NAMESPACE = "casted_materials";
    private static final ResourceLocation CREATE_CASING =
            ResourceLocation.fromNamespaceAndPath("create", "casing");
    public static final TagKey<Item> CASING_ITEMS = TagKey.create(Registries.ITEM, CREATE_CASING);
    public static final TagKey<Block> CASING_BLOCKS = TagKey.create(Registries.BLOCK, CREATE_CASING);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, DATA_NAMESPACE);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> MATERIAL =
            COMPONENTS.registerComponentType("material", builder -> builder
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC));

    private MaterialRenderingModule() { }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        modBus.addListener(MaterialPaletteSync::register);
        NeoForge.EVENT_BUS.addListener(MaterialPaletteSync::onDatapackSync);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(DATA_NAMESPACE, path);
    }
}
