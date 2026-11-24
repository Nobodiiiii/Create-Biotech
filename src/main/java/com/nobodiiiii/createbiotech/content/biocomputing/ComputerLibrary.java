package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.Locale;

import com.nobodiiiii.createbiotech.content.biocomputing.ComputerProgram.Op;

/** Each villager provides Core and at most one extension library. */
public enum ComputerLibrary {
	CORE, RANDOMNESS, MATH, TEXT, SIGNAL, LOGISTICS, TIMING, ALL;

	public boolean allows(Op op) {
		return this == ALL || op.library() == CORE || op.library() == this;
	}

	public boolean includesLogistics() {
		return this == LOGISTICS || this == ALL;
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	static ComputerLibrary forProfession(String id) {
		return switch (id) {
			case "minecraft:nitwit" -> RANDOMNESS;
			case "minecraft:armorer", "minecraft:weaponsmith", "minecraft:toolsmith" -> MATH;
			case "minecraft:librarian", "minecraft:cartographer" -> TEXT;
			case "minecraft:cleric" -> SIGNAL;
			case "minecraft:butcher", "minecraft:leatherworker", "minecraft:mason", "minecraft:fletcher" -> LOGISTICS;
			case "minecraft:farmer", "minecraft:fisherman", "minecraft:shepherd" -> TIMING;
			default -> CORE;
		};
	}
}
