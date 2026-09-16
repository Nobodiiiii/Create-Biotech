package com.nobodiiiii.createbiotech.foundation.render.material.client;

import com.nobodiiiii.createbiotech.foundation.render.material.MaterialRenderingModule;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.util.Objects;
import java.util.function.Consumer;

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

}
