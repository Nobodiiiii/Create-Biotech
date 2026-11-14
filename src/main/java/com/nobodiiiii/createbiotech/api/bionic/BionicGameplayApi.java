package com.nobodiiiii.createbiotech.api.bionic;

import com.nobodiiiii.createbiotech.entity.trait.BionicGameplayRules;
import net.minecraft.resources.ResourceLocation;

/** Register immutable JSON factories during common setup, before the first catalog load. */
public final class BionicGameplayApi {
	public static final int API_VERSION = 1;
	private BionicGameplayApi() {}
	public static void registerAnatomy(ResourceLocation id, BionicTraitApi.Factory<BionicAnatomyEvaluator> factory) { BionicGameplayRules.registerAnatomy(id, factory); }
	public static void registerAttack(ResourceLocation id, BionicTraitApi.Factory<BionicSpecialAttack> factory) { BionicGameplayRules.registerAttack(id, factory); }
	public static void registerDonorDetector(ResourceLocation id, BionicTraitApi.Factory<BionicDonorDetector> factory) { BionicGameplayRules.registerDetector(id, factory); }
	public static void registerRelationCondition(ResourceLocation id, BionicTraitApi.Factory<BionicRelationCondition> factory) { BionicGameplayRules.registerRelation(id, factory); }
}
