package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Slime;

@Mixin(LivingEntity.class)
public abstract class LivingEntitySlimeMimicHurtSoundMixin {

	@Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
	private void createBiotech$useSlimeHurtSoundForSlimeMimics(DamageSource damageSource,
		CallbackInfoReturnable<SoundEvent> cir) {
		LivingEntity entity = (LivingEntity) (Object) this;
		if (!SlimeMimicHandler.isSlimeMimic(entity))
			return;

		cir.setReturnValue(isLargeSlimeVoice(entity) ? SoundEvents.SLIME_HURT : SoundEvents.SLIME_HURT_SMALL);
	}

	@Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
	private void createBiotech$useSlimeDeathSoundForSlimeMimics(CallbackInfoReturnable<SoundEvent> cir) {
		LivingEntity entity = (LivingEntity) (Object) this;
		if (!SlimeMimicHandler.isSlimeMimic(entity))
			return;

		cir.setReturnValue(isLargeSlimeVoice(entity) ? SoundEvents.SLIME_DEATH : SoundEvents.SLIME_DEATH_SMALL);
	}

	private static boolean isLargeSlimeVoice(LivingEntity entity) {
		return entity.getBbWidth() > 0.75f || entity.getBbHeight() > 0.75f || entity instanceof Slime;
	}
}
