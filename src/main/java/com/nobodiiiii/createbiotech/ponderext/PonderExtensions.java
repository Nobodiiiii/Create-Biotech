package com.nobodiiiii.createbiotech.ponderext;

import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;

/** Handwritten extension point. Created once; never overwritten by the exporter. */
public final class PonderExtensions {
    private PonderExtensions() {}
    public static void install(RuntimeEngine engine) {
        SceneBounds.install(engine);
        CreeperBlastChamberEffects.install(engine);
        ExperiencePumpEffects.install(engine);
        PowerBeltConnectorEffects.install(engine);
        ShulkerPackagerEffects.install(engine);
        PetriDishEffects.install(engine);
        AllayPortEffects.install(engine);
    }
}
