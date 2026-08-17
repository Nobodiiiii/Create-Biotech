package com.nobodiiiii.createbiotech.client;

import com.nobodiiiii.createbiotech.content.dingdongchicken.DingDongChickenEntity;
import com.nobodiiiii.createbiotech.registry.CBSoundEvents;

import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class DingDongChickenVoiceSound extends EntityBoundSoundInstance {
	private final int entityId;
	private int age;

	DingDongChickenVoiceSound(DingDongChickenEntity chicken) {
		super(CBSoundEvents.DING_DONG_CHICKEN_VOICE_PACK.get(), SoundSource.NEUTRAL,
			1.0F, 1.0F, chicken, chicken.getRandom().nextLong());
		entityId = chicken.getId();
	}

	@Override
	public void tick() {
		super.tick();
		if (isStopped()) {
			DingDongChickenVoiceSoundHandler.finished(entityId, this);
			return;
		}

		if (++age >= DingDongChickenEntity.SIGNAL_DURATION) {
			stop();
			DingDongChickenVoiceSoundHandler.finished(entityId, this);
		}
	}

	void stopSound() {
		stop();
	}
}
