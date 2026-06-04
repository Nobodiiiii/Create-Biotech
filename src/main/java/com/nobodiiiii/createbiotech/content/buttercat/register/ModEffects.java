package com.nobodiiiii.createbiotech.content.buttercat.register;

import com.nobodiiiii.createbiotech.content.buttercat.ButterCatModule;
import com.nobodiiiii.createbiotech.content.buttercat.mob_effect.ButterRotationEffect;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;


public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, ButterCatModule.MODID);

    public static final RegistryObject<ButterRotationEffect> BUTTER_ROTATION_EFFECT =
            EFFECTS.register("rotation", ButterRotationEffect::new);

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }
}

