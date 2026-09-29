package com.nobodiiiii.createbiotech.entity.trait;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** An independently acquired variant with its own carriers and runtime parameters. */
public record BionicSubtrait(ResourceLocation id, BionicTrait.Carrier carrier, Map<String, Double> parameters) {
	public BionicSubtrait { parameters = Map.copyOf(parameters); }
	public double parameter(String key, double fallback) { return Math.min(key.endsWith("_ticks") ? 24000 : 100, parameters.getOrDefault(key, fallback)); }
	public String descriptionId() { return "create_biotech.trait.variant." + id.getPath(); }
}
