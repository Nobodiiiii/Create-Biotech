package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerContraptionDamageTracker;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerContraptionDamageTracker.DamageContextScope;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

@Mixin(BlockBreakingMovementBehaviour.class)
public abstract class BlockBreakingMovementBehaviourMixin {

	@WrapMethod(
		method = "damageEntities(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;)V",
		require = 1,
		expect = 1)
	private void createBiotech$trackContraptionDamage(MovementContext context, BlockPos pos, Level world,
		Operation<Void> original) {
		AbstractContraptionEntity contraptionEntity = context.contraption.entity;
		if (contraptionEntity == null) {
			original.call(context, pos, world);
			return;
		}

		try (DamageContextScope ignored =
			BioPackagerContraptionDamageTracker.openDamageContext(contraptionEntity)) {
			original.call(context, pos, world);
		}
	}
}
