package com.nobodiiiii.createbiotech.foundation.render.material.client;

import com.nobodiiiii.createbiotech.foundation.render.material.MaterialRenderingModule;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.Collection;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = MaterialRenderingModule.OWNER_MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CastedClientEvents {
    private CastedClientEvents() {
    }

    @SubscribeEvent
    static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        registerReloadListener(event::registerReloadListener);
    }

    static void registerReloadListener(Consumer<PreparableReloadListener> registrar) {
        Objects.requireNonNull(registrar, "registrar").accept(CastedResources.INSTANCE);
    }

    @SubscribeEvent
    static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        registerAdditionalModels(CastedResources.modelIds(Minecraft.getInstance().getResourceManager()), event::register);
    }

    static void registerAdditionalModels(Collection<ResourceLocation> modelIds,
                                         Consumer<ModelResourceLocation> registrar) {
        Objects.requireNonNull(modelIds, "modelIds");
        Objects.requireNonNull(registrar, "registrar");
        modelIds.forEach(id -> registrar.accept(new ModelResourceLocation(id, ModelResourceLocation.STANDALONE_VARIANT)));
    }

}
