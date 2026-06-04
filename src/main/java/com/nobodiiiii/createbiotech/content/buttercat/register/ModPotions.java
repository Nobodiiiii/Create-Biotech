package com.nobodiiiii.createbiotech.content.buttercat.register;

import com.nobodiiiii.createbiotech.content.buttercat.ButterCatModule;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;


public class ModPotions {

    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, ButterCatModule.MODID);

    public static final RegistryObject<Potion> ROTATION =
            POTIONS.register("rotation_potion", () -> new Potion(new MobEffectInstance(
                    ModEffects.BUTTER_ROTATION_EFFECT.get(),  // 直接 get()，不需要 getDelegate()
                    1200,
                    2
            )));

    public static final RegistryObject<Potion> SUPER_ROTATION =
            POTIONS.register("super_rotation_potion", () -> new Potion(new MobEffectInstance(
                    ModEffects.BUTTER_ROTATION_EFFECT.get(),
                    3600,
                    4
            )));

    public static void register(IEventBus modBus) {
        POTIONS.register(modBus);
    }
}
