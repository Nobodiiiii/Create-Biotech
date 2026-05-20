package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberBlockEntity;
import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberBlock;
import com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

@Mixin(GoggleOverlayRenderer.class)
public abstract class GoggleOverlayRendererMixin {

	@Inject(
		method = "proxiedOverlayPosition(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;",
		at = @At("HEAD"), cancellable = true, remap = false)
	private static void createBiotech$proxyCreeperBlastChamberStructure(Level level, BlockPos pos,
		CallbackInfoReturnable<BlockPos> cir) {
		BlockPos controllerPos = CreeperBlastChamberBlockEntity.findGoggleInformationSource(level, pos);
		if (controllerPos != null) {
			cir.setReturnValue(controllerPos);
			return;
		}

		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof EvokerEnchantingChamberBlock
			&& state.getValue(EvokerEnchantingChamberBlock.HALF) == DoubleBlockHalf.UPPER) {
			cir.setReturnValue(pos.below());
		}
	}
}
