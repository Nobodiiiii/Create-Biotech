package com.nobodiiiii.createbiotech.api.bionic;

import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Additional runtime condition for one donor/target relationship. Must not mutate the world. */
@FunctionalInterface
public interface BionicRelationCondition {
	boolean matches(SlimeBionicEntity entity, ResourceLocation donor, LivingEntity target);
}
