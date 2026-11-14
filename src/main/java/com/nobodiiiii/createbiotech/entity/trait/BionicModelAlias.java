package com.nobodiiiii.createbiotech.entity.trait;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;

/** Data-owned aliases for otherwise unnamed cuboids within a model node. */
public record BionicModelAlias(String part, @Nullable Set<Integer> cubes, Set<String> names) {

	public BionicModelAlias {
		if (!BionicAnatomySnapshot.validPartName(part) || names.isEmpty() || names.size() > 16
			|| names.stream().anyMatch(name -> !BionicAnatomySnapshot.validPartName(name)))
			throw new IllegalArgumentException("Invalid model alias");
		if (cubes != null && (cubes.size() > SurgicalAssembly.MAX_CUBES
			|| cubes.stream().anyMatch(cube -> cube == null || cube < 0 || cube >= SurgicalAssembly.MAX_CUBES)))
			throw new IllegalArgumentException("Invalid model alias cube index");
		cubes = cubes == null ? null : Set.copyOf(cubes);
		names = Set.copyOf(names);
	}

	public boolean matches(Set<String> nodeNames, int cube) {
		return nodeNames.contains(part) && (cubes == null || cubes.contains(cube));
	}

	static List<BionicModelAlias> decode(@Nullable JsonElement json) {
		if (json == null) return List.of();
		if (!json.isJsonArray() || json.getAsJsonArray().size() > 256)
			throw new IllegalArgumentException("Expected at most 256 model aliases");
		List<BionicModelAlias> result = new ArrayList<>();
		for (JsonElement value : json.getAsJsonArray()) {
			var rule = value.getAsJsonObject();
			if (!rule.get("part").isJsonPrimitive() || !rule.getAsJsonPrimitive("part").isString())
				throw new IllegalArgumentException("Expected model node name");
			Set<Integer> cubes = null;
			if (rule.has("cubes")) {
				cubes = new HashSet<>();
				for (JsonElement cube : rule.getAsJsonArray("cubes")) {
					if (!cube.isJsonPrimitive() || !cube.getAsJsonPrimitive().isNumber()
						|| !cubes.add(cube.getAsBigDecimal().intValueExact()))
						throw new IllegalArgumentException("Invalid or duplicate model cube index");
				}
			}
			Set<String> names = new HashSet<>();
			for (JsonElement name : rule.getAsJsonArray("names")) {
				if (!name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString() || !names.add(name.getAsString()))
					throw new IllegalArgumentException("Invalid or duplicate model alias name");
			}
			result.add(new BionicModelAlias(rule.get("part").getAsString(), cubes, names));
		}
		return List.copyOf(result);
	}
}
