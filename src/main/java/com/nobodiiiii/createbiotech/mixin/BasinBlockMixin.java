package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessing;
import com.simibubi.create.content.processing.basin.BasinBlock;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

@Mixin(BasinBlock.class)
public abstract class BasinBlockMixin {

	@Inject(method = "updateEntityAfterFallOn(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;)V",
		at = @At("TAIL"))
	private void createBiotech$disableSmallSlimeAi(BlockGetter world, Entity entity, CallbackInfo ci) {
		if (!(world instanceof Level level))
			return;
		if (entity instanceof Slime slime)
			BasinEntityProcessing.handleSmallSlimeInBasin(slime);
	}
}
