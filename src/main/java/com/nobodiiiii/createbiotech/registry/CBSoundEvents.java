package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;

import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class CBSoundEvents {
	private static final DeferredRegister<SoundEvent> SOUND_EVENTS =
		DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, CreateBiotech.MOD_ID);

	public static final RegistryObject<SoundEvent> DING_DONG_CHICKEN_VOICE_PACK =
		register("ding_dong_chicken.voice_pack");

	private CBSoundEvents() {}

	private static RegistryObject<SoundEvent> register(String name) {
		return SOUND_EVENTS.register(name,
			() -> SoundEvent.createVariableRangeEvent(CreateBiotech.asResource(name)));
	}

	public static void register(IEventBus modEventBus) {
		SOUND_EVENTS.register(modEventBus);
	}
}
