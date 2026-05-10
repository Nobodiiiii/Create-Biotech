package com.nobodiiiii.createbiotech.client;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class CreeperBlastChamberClientSoundHandler {

	private CreeperBlastChamberClientSoundHandler() {
	}

	public static void stopManagedPrimedSound() {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.getSoundManager()
			.stop(SoundEvents.CREEPER_PRIMED.getLocation(), SoundSource.BLOCKS);
	}
}
