package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.Mob;

@Mixin(Mob.class)
public interface MobAccessor {

	@Accessor("persistenceRequired")
	void createBiotech$setPersistenceRequired(boolean persistenceRequired);
}
