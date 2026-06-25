package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

@Mixin(SawBlockEntity.class)
public abstract class SawBlockEntityMixin {

	@Inject(method = "applyRecipe()V", at = @At("HEAD"), cancellable = true, remap = false)
	private void createBiotech$releaseCapturedEntityBox(CallbackInfo ci) {
		SawBlockEntity saw = (SawBlockEntity) (Object) this;
		ItemStack input = saw.inventory.getStackInSlot(0);
		if (!CapturedEntityBoxHelper.hasCapturedEntity(input))
			return;

		Level level = saw.getLevel();
		if (level == null || level.isClientSide)
			return;

		Vec3 spawnPos = VecHelper.getCenterOf(saw.getBlockPos())
			.add(0, .75f, 0);
		Vec3 motion = saw.getItemMovementVec()
			.scale(.0625)
			.add(0, .125, 0);
		CapturedEntityBoxHelper.releaseCapturedEntity(input, level, spawnPos, motion);
		saw.inventory.clear();
		ci.cancel();
	}
}
