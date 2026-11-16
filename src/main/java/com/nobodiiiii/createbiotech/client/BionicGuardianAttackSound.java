package com.nobodiiiii.createbiotech.client;

import java.util.Map;
import java.util.WeakHashMap;

import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Uses GuardianAttackSoundInstance's charge curve with the bionic eye as its emitter. */
@OnlyIn(Dist.CLIENT)
public final class BionicGuardianAttackSound extends EntityBoundSoundInstance {

	private static final Map<SlimeBionicEntity, Long> STARTED_ATTACKS = new WeakHashMap<>();
	private final SlimeBionicEntity entity;
	private final long attackStarted;

	private BionicGuardianAttackSound(SlimeBionicEntity entity) {
		super(SoundEvents.GUARDIAN_ATTACK, entity.getSoundSource(), 0.0f, 0.7f,
			entity, entity.getRandom().nextLong());
		this.entity = entity;
		attackStarted = entity.getSpecialActionStartTick();
		looping = true;
		tick();
	}

	public static void update(SlimeBionicEntity entity) {
		if (!isCharging(entity)) {
			STARTED_ATTACKS.remove(entity);
			return;
		}
		long started = entity.getSpecialActionStartTick();
		Long previous = STARTED_ATTACKS.get(entity);
		if (previous != null && previous.longValue() == started)
			return;
		STARTED_ATTACKS.put(entity, started);
		Minecraft.getInstance().getSoundManager().play(new BionicGuardianAttackSound(entity));
	}

	private static boolean isCharging(SlimeBionicEntity entity) {
		return entity.level() == Minecraft.getInstance().level && entity.isAlive()
			&& !entity.isSilent() && entity.getSpecialAction() == 2;
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
		if (!isCharging(entity) || entity.getSpecialActionStartTick() != attackStarted) {
			stop();
			return;
		}
		super.tick();
		Vec3 origin = entity.getSpecialOrigin();
		x = origin.x;
		y = origin.y;
		z = origin.z;
		// ref/1.21.1/Minecraft/net/minecraft/client/resources/sounds/GuardianAttackSoundInstance.java
		float progress = Mth.clamp((float) entity.getSpecialActionTicks() / entity.getGuardianAttackDuration(), 0, 1);
		volume = progress * progress;
		pitch = 0.7f + 0.5f * progress;
	}
}
