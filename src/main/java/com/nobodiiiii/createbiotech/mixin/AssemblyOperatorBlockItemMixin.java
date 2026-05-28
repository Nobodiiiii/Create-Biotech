package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltBlock;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.processing.AssemblyOperatorBlockItem;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(AssemblyOperatorBlockItem.class)
public abstract class AssemblyOperatorBlockItemMixin {

	@Inject(method = "operatesOn", at = @At("HEAD"), cancellable = true, remap = false)
	private void createBiotech$recognizeModBelts(LevelReader world, BlockPos pos, BlockState placedOnState,
		CallbackInfoReturnable<Boolean> cir) {
		Block block = placedOnState.getBlock();
		if (block instanceof SlimeBeltBlock
			&& placedOnState.getValue(SlimeBeltBlock.SLOPE) == BeltSlope.HORIZONTAL) {
			cir.setReturnValue(true);
		} else if (block instanceof MagmaBeltBlock
			&& placedOnState.getValue(MagmaBeltBlock.SLOPE) == BeltSlope.HORIZONTAL) {
			cir.setReturnValue(true);
		}
	}
}
