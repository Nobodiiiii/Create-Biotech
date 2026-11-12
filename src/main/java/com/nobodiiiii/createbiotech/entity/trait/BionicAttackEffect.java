package com.nobodiiiii.createbiotech.entity.trait;

import net.minecraft.resources.ResourceLocation;

/** One melee status effect using the shared attack settings and its configured carrier. */
public record BionicAttackEffect(ResourceLocation effect, int durationTicks, int amplifier,
	double chance, boolean unarmedOnly, BionicTrait.Carrier carrier) {}
