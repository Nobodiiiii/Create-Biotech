package com.nobodiiiii.createbiotech.client;

import java.util.HashMap;
import java.util.Map;

import com.nobodiiiii.createbiotech.content.dingdongchicken.DingDongChickenEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DingDongChickenVoiceSoundHandler {
	private static final Map<Integer, DingDongChickenVoiceSound> ACTIVE_SOUNDS = new HashMap<>();

	private DingDongChickenVoiceSoundHandler() {}

	public static void play(int entityId) {
		stop(entityId);
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null)
			return;

		Entity entity = level.getEntity(entityId);
		if (!(entity instanceof DingDongChickenEntity chicken))
			return;

		DingDongChickenVoiceSound sound = new DingDongChickenVoiceSound(chicken);
		ACTIVE_SOUNDS.put(entityId, sound);
		Minecraft.getInstance().getSoundManager().play(sound);
	}

	static void finished(int entityId, DingDongChickenVoiceSound sound) {
		ACTIVE_SOUNDS.remove(entityId, sound);
	}

	private static void stop(int entityId) {
		DingDongChickenVoiceSound sound = ACTIVE_SOUNDS.remove(entityId);
		if (sound == null)
			return;
		sound.stopSound();
		Minecraft.getInstance().getSoundManager().stop(sound);
	}
}
