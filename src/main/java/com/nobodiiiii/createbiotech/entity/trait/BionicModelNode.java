package com.nobodiiiii.createbiotech.entity.trait;

import java.util.Set;
import org.jetbrains.annotations.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Immutable model provenance. Parent links describe geometry, never a precomputed organ role. */
public record BionicModelNode(Set<String> names, @Nullable BionicModelNode parent) {
	public static final int MAX_DEPTH = 64;
	public BionicModelNode {
		names = Set.copyOf(names);
		if (names.size() > 16 || names.stream().anyMatch(name -> !BionicAnatomySnapshot.validPartName(name)))
			throw new IllegalArgumentException("Invalid model node names");
		int depth = 1;
		for (BionicModelNode node = parent; node != null; node = node.parent)
			if (++depth > MAX_DEPTH) throw new IllegalArgumentException("Model ancestry is too deep");
	}
	public boolean matches(BionicPartDefinition part, ResourceLocation entity) {
		for (BionicModelNode node = this; node != null; node = part.includeChildren() ? node.parent : null)
			if (node.names.stream().anyMatch(name -> part.matches(entity, name))) return true;
		return false;
	}
}
