package com.nobodiiiii.createbiotech.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.nobodiiiii.createbiotech.content.dingdongchicken.EntityRedstoneIndex;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedStoneWireBlock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Adds the entity source after the normal wire evaluator, preserving its original result. */
@Mixin(value = RedStoneWireBlock.class, priority = 500)
public abstract class RedStoneWireEntityRedstoneMixin {

	@ModifyReturnValue(
		method = "calculateTargetStrength(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)I",
		at = @At("RETURN")
	)
	private int createBiotech$addEntityPower(int original, Level level, BlockPos pos) {
		if (original >= 15 || !(level instanceof ServerLevel serverLevel))
			return original;
		return Math.max(original, EntityRedstoneIndex.get(serverLevel).getExternalPowerForWire(pos));
	}
}
