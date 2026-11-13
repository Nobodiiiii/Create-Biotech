package com.nobodiiiii.createbiotech.entity.trait;

import java.util.Objects;

import net.minecraft.resources.ResourceLocation;

/** An ID reference, not a fixed taxonomy. Definitions are owned by the loaded data-pack catalog. */
public record BionicAnatomyRole(ResourceLocation id) implements Comparable<BionicAnatomyRole> {
	// Built-in traits refer to these IDs; names and matching rules live in bionic_parts JSON.
	public static final BionicAnatomyRole HEAD = builtin("head");
	public static final BionicAnatomyRole GILL = builtin("gill");
	public static final BionicAnatomyRole TORSO = builtin("torso");
	public static final BionicAnatomyRole BACK = builtin("back");
	public static final BionicAnatomyRole LEG = builtin("leg");
	public static final BionicAnatomyRole WING = builtin("wing");
	public static final BionicAnatomyRole TENTACLE = builtin("tentacle");
	public static final BionicAnatomyRole SHELL = builtin("shell");
	public static final BionicAnatomyRole BASE = builtin("base");
	public static final BionicAnatomyRole ATTACK_HAND = builtin("attack_hand");
	public static final BionicAnatomyRole EYE = builtin("eye");
	public static final BionicAnatomyRole WOOL = builtin("wool");
	public static final BionicAnatomyRole UDDER = builtin("udder");
	public static final BionicAnatomyRole LEFT_RIBCAGE = builtin("left_ribcage");
	public static final BionicAnatomyRole RIGHT_RIBCAGE = builtin("right_ribcage");

	public BionicAnatomyRole {
		Objects.requireNonNull(id);
		if (id.toString().length() > 256) throw new IllegalArgumentException("Bionic part ID is too long: " + id);
	}

	private static BionicAnatomyRole builtin(String path) {
		return new BionicAnatomyRole(ResourceLocation.fromNamespaceAndPath("create_biotech", path));
	}

	public static BionicAnatomyRole parse(String name) {
		if (!name.contains(":")) throw new IllegalArgumentException("Expected a namespaced bionic part ID: " + name);
		return new BionicAnatomyRole(ResourceLocation.parse(name));
	}

	@Override public int compareTo(BionicAnatomyRole other) { return id.compareTo(other.id); }
	@Override public String toString() { return id.toString(); }
}
