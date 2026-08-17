package com.nobodiiiii.createbiotech.client;

import java.util.HashMap;
import java.util.Map;

import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicDogCannonChargeSoundPacket.Action;
import com.nobodiiiii.createbiotech.registry.CBSoundEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SonicDogCannonChargeSoundHandler {
	private static final Map<Integer, SonicDogCannonChargeSound> ACTIVE_SOUNDS = new HashMap<>();

	private SonicDogCannonChargeSoundHandler() {}

	public static void handle(int shooterId, Action action) {
		stop(shooterId);
		if (action == Action.STOP)
			return;

		ClientLevel level = Minecraft.getInstance().level;
		if (level == null)
			return;

		Entity entity = level.getEntity(shooterId);
		if (!(entity instanceof Player shooter))
			return;

		SoundEvent soundEvent = switch (action) {
			case DEFAULT_START -> CBSoundEvents.SONIC_DOG_CANNON_GROWL1.get();
			case VOICE_PACK_START -> CBSoundEvents.SONIC_DOG_CANNON_VOICE_PACK_CHARGE_START.get();
			case VOICE_PACK_LOOP -> CBSoundEvents.SONIC_DOG_CANNON_VOICE_PACK_CHARGE_LOOP.get();
			case STOP -> throw new IllegalStateException("STOP was handled before sound creation");
		};
		boolean voicePack = action != Action.DEFAULT_START;
		SonicDogCannonChargeSound sound = new SonicDogCannonChargeSound(shooter, soundEvent,
			action == Action.VOICE_PACK_LOOP, voicePack);
		ACTIVE_SOUNDS.put(shooterId, sound);
		Minecraft.getInstance().getSoundManager().play(sound);
	}

	private static void stop(int shooterId) {
		SonicDogCannonChargeSound sound = ACTIVE_SOUNDS.remove(shooterId);
		if (sound == null)
			return;
		sound.stopSound();
		Minecraft.getInstance().getSoundManager().stop(sound);
	}
}
