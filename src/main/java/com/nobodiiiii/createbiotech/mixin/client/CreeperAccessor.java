package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.monster.Creeper;

@Mixin(Creeper.class)
public interface CreeperAccessor {

	@Accessor("oldSwell")
	int createBiotech$getOldSwell();

	@Accessor("oldSwell")
	void createBiotech$setOldSwell(int oldSwell);

	@Accessor("swell")
	int createBiotech$getSwell();

	@Accessor("swell")
	void createBiotech$setSwell(int swell);
}
