package com.nobodiiiii.createbiotech.api.bionic;

import net.minecraft.world.entity.LivingEntity;

/** Pure donor probe, compiled once per reload. Null means no automatic fact. Pack overrides run last. */
@FunctionalInterface
public interface BionicDonorDetector {
	Double detect(LivingEntity donor);
}
