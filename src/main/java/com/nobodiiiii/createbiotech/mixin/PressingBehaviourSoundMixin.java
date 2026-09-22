package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberBlockEntity;
import com.simibubi.create.AllSoundEvents.SoundEntry;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;

@Mixin(PressingBehaviour.class)
public abstract class PressingBehaviourSoundMixin {

	@WrapWithCondition(method = "tick", at = @At(value = "INVOKE",
		target = "Lcom/simibubi/create/AllSoundEvents$SoundEntry;playOnServer(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/Vec3i;)V"),
		remap = true, require = 0)
	private boolean createBiotech$allowChamberPressActivationOnBelt(SoundEntry sound, Level level, Vec3i pos) {
		return !shouldMuteCurrentPressActivation();
	}

	@WrapWithCondition(method = "tick", at = @At(value = "INVOKE",
		target = "Lcom/simibubi/create/AllSoundEvents$SoundEntry;playOnServer(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/Vec3i;FF)V"),
		remap = true, require = 0)
	private boolean createBiotech$allowChamberPressActivation(SoundEntry sound, Level level, Vec3i pos, float volume,
		float pitch) {
		return !shouldMuteCurrentPressActivation();
	}

	private boolean shouldMuteCurrentPressActivation() {
		BlockEntityBehaviour behaviour = (BlockEntityBehaviour) (Object) this;
		if (!(behaviour.blockEntity instanceof MechanicalPressBlockEntity press))
			return false;
		return CreeperBlastChamberBlockEntity.shouldMutePressActivationSound(press);
	}
}
