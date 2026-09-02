package com.nobodiiiii.createbiotech.entity.ai;

import java.util.Map;

import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;

import net.minecraft.resources.ResourceLocation;

/**
 * Independent data-pack-backed cognitive tiers. Missing entity ids fall back to
 * {@link BionicIntelligence#SIMPLE}.
 */
public final class BionicIntelligenceRegistry {
	private static volatile Map<ResourceLocation, BionicIntelligence> DATA = Map.of();

	private BionicIntelligenceRegistry() {}

	static void replaceData(Map<ResourceLocation, BionicIntelligence> data) {
		DATA = Map.copyOf(data);
	}

	public static BionicIntelligence get(MimicProfile profile) {
		return get(profile == null ? null : profile.entityTypeId());
	}

	public static BionicIntelligence get(ResourceLocation entityTypeId) {
		if (entityTypeId == null)
			return BionicIntelligence.SIMPLE;
		return DATA.getOrDefault(entityTypeId, BionicIntelligence.SIMPLE);
	}
}
