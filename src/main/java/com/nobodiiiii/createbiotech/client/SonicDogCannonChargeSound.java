package com.nobodiiiii.createbiotech.client;

import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicDogCannonItem;
import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicDogCannonUpgrade;

import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SonicDogCannonChargeSound extends EntityBoundSoundInstance {
	private static final int USE_STATE_GRACE_TICKS = 3;

	private final Player player;
	private final boolean expectsVoicePack;
	private int age;

	public SonicDogCannonChargeSound(Player player, SoundEvent sound, boolean looping, boolean expectsVoicePack) {
		super(sound, SoundSource.PLAYERS,
			1.0f, 1.0f, player, player.getRandom().nextLong());
		this.player = player;
		this.looping = looping;
		this.expectsVoicePack = expectsVoicePack;
	}

	@Override
	public void tick() {
		super.tick();
		age++;
		if (isStopped() || age <= USE_STATE_GRACE_TICKS)
			return;

		if (!player.isUsingItem()
			|| !(player.getUseItem().getItem() instanceof SonicDogCannonItem)
			|| SonicDogCannonUpgrade.VOICE_PACK.isInstalled(player.getUseItem()) != expectsVoicePack)
			stopSound();
	}

	public void stopSound() {
		stop();
	}
}
