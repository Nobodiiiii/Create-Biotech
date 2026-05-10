package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberBlockEntity;
import com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

@Mixin(GoggleOverlayRenderer.class)
public abstract class GoggleOverlayRendererMixin {

	@Inject(
		method = "proxiedOverlayPosition(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;",
		at = @At("HEAD"), cancellable = true, remap = false)
	private static void createBiotech$proxyCreeperBlastChamberStructure(Level level, BlockPos pos,
		CallbackInfoReturnable<BlockPos> cir) {
		BlockPos controllerPos = CreeperBlastChamberBlockEntity.findGoggleInformationSource(level, pos);
		if (controllerPos != null)
			cir.setReturnValue(controllerPos);
	}
}
