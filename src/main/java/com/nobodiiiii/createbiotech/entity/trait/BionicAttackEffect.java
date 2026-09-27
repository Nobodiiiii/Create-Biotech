package com.nobodiiiii.createbiotech.entity.trait;

import net.minecraft.resources.ResourceLocation;

/** One configured melee effect; each effect retains its own carrier and hit requirements. */
public record BionicAttackEffect(ResourceLocation effect, int durationTicks, int amplifier,
	double chance, boolean unarmedOnly, BionicTrait.Carrier carrier) {}
