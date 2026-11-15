package com.nobodiiiii.createbiotech.ponderext;

import com.nododiiiii.ponderer.runtime.api.RuntimeFeatures;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Client feature ownership stays outside exporter-managed sources. */
@EventBusSubscriber(modid = "create_biotech", value = Dist.CLIENT)
public final class PonderClientFeatures {
    private static AutoCloseable playerEnhancements;

    private PonderClientFeatures() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (playerEnhancements == null)
                playerEnhancements = RuntimeFeatures.requestPlayerEnhancements("create_biotech");
        });
    }
}
