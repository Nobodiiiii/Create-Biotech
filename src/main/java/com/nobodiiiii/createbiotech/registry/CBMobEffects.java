package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.bouncing.BouncingEffect;
import com.nobodiiiii.createbiotech.content.buttercat.mob_effect.ButterRotationEffect;
import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicDogCannonStunEffect;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CBMobEffects {
	private static final String CATACLYSM_MOD_ID = "cataclysm";
	private static final ResourceKey<MobEffect> CATACLYSM_STUN = ResourceKey.create(
		Registries.MOB_EFFECT, ResourceLocation.fromNamespaceAndPath(CATACLYSM_MOD_ID, "stun"));

	private static final DeferredRegister<MobEffect> MOB_EFFECTS =
		DeferredRegister.create(Registries.MOB_EFFECT, CreateBiotech.MOD_ID);

	public static final DeferredHolder<MobEffect, MobEffect> BUTTER_ROTATION =
		MOB_EFFECTS.register("rotation", ButterRotationEffect::new);
	public static final DeferredHolder<MobEffect, MobEffect> STUN =
		MOB_EFFECTS.register("stun", SonicDogCannonStunEffect::new);
	public static final DeferredHolder<MobEffect, MobEffect> BOUNCING =
		MOB_EFFECTS.register("bouncing", BouncingEffect::new);

	private CBMobEffects() {}

	public static void register(IEventBus modEventBus) {
		MOB_EFFECTS.register(modEventBus);
	}

	public static Holder<MobEffect> sonicDogCannonStun() {
		if (ModList.get().isLoaded(CATACLYSM_MOD_ID)) {
			Holder<MobEffect> cataclysmStun = BuiltInRegistries.MOB_EFFECT.getHolder(CATACLYSM_STUN)
				.orElse(null);
			if (cataclysmStun != null)
				return cataclysmStun;
		}
		return STUN.getDelegate();
	}
}
