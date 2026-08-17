package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.buttercat.mob_effect.ButterRotationEffect;
import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicDogCannonStunEffect;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class CBMobEffects {
	private static final String CATACLYSM_MOD_ID = "cataclysm";
	private static final ResourceLocation CATACLYSM_STUN =
		new ResourceLocation(CATACLYSM_MOD_ID, "stun");

	private static final DeferredRegister<MobEffect> MOB_EFFECTS =
		DeferredRegister.create(Registries.MOB_EFFECT, CreateBiotech.MOD_ID);

	public static final RegistryObject<ButterRotationEffect> BUTTER_ROTATION =
		MOB_EFFECTS.register("rotation", ButterRotationEffect::new);
	public static final RegistryObject<MobEffect> STUN =
		MOB_EFFECTS.register("stun", SonicDogCannonStunEffect::new);

	private CBMobEffects() {}

	public static void register(IEventBus modEventBus) {
		MOB_EFFECTS.register(modEventBus);
	}

	/** Reuses Cataclysm's stun when that mod is present so both share one immunity model. */
	public static MobEffect sonicDogCannonStun() {
		if (ModList.get().isLoaded(CATACLYSM_MOD_ID)) {
			MobEffect cataclysmStun = ForgeRegistries.MOB_EFFECTS.getValue(CATACLYSM_STUN);
			if (cataclysmStun != null)
				return cataclysmStun;
		}
		return STUN.get();
	}
}
