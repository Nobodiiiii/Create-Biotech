package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Hooks Create's Haunting air current so that a slime mimic caught in a soul-fire
 * fan stream (Encased Fan blowing through a lit Soul Campfire / Soul Fire) and
 * carrying Regeneration gradually turns back into the real creature it imitates.
 *
 * <p>Create itself uses this exact entry point to convert a Horse into a Skeleton
 * Horse, accumulating ticks in the entity's persistent data until a cycle completes.
 */
@Mixin(value = AllFanProcessingTypes.HauntingType.class, priority = 1001)
public abstract class HauntingTypeSlimeMimicMixin {

	@Inject(method = "affectEntity", at = @At("HEAD"), cancellable = true, remap = false)
	private void createBiotech$hauntSlimeMimicIntoReality(Entity entity, Level level, CallbackInfo ci) {
		if (!(entity instanceof LivingEntity living) || !SlimeMimicHandler.isSlimeMimic(living))
			return;

		// Take over haunting for mimics: drive our own progress and skip Create's
		// default blindness/slowness and Horse -> Skeleton Horse conversion.
		SlimeMimicHandler.advanceHaunting(living, level);
		ci.cancel();
	}
}
