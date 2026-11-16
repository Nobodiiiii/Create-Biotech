package com.nobodiiiii.createbiotech.ponderext;

import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;

/** Handwritten extension point. Created once; never overwritten by the exporter. */
public final class PonderExtensions {
    private PonderExtensions() {}
    public static void install(RuntimeEngine engine) {
        ButterCatEngineEffects.install(engine);
        CreeperBlastChamberEffects.install(engine);
        ExperiencePumpOrbEffects.install(engine);
        PowerBeltConnectorEffects.install(engine);
        MagmaBeltConnectorEffects.install(engine);
        ShulkerPackagerEffects.install(engine);
        PetriDishEffects.install(engine);
        SquidPrinterEffects.install(engine);
        AllayPortEffects.install(engine);
        UniversalJointEffects.install(engine);
        EnchantmentLineEffects.install(engine);
    }
}
