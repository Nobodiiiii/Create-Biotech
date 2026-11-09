package com.nobodiiiii.createbiotech.entity.trait;

import java.util.BitSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;

/** Shared back-part carriers for acquiring the presence trait and installing its saddles. */
public final class BionicSaddleCarriers {
	private static final Pattern MODDED_BACK = Pattern.compile(
		"^(?:(?:upper|lower|middle|main|front|rear|central)[_-]?)?"
			+ "(?:body|torso|back|trunk|chest)"
			+ "(?:[_-]?(?:upper|lower|middle|main|front|rear|[0-9]+))?$",
		Pattern.CASE_INSENSITIVE);

	private BionicSaddleCarriers() {}

	public static BitSet select(SurgicalAssembly.Source source) {
		return select(source.profile().entityTypeId().getNamespace().equals("minecraft"),
			source.anatomy().parts(), source.presentCubes());
	}

	private static BitSet select(boolean vanilla, Map<Integer, Set<String>> parts, BitSet present) {
		BitSet backs = new BitSet();
		parts.forEach((cube, names) -> {
			if (names.stream().anyMatch(name -> matches(name, vanilla))) backs.set(cube);
		});
		// Inspect the original labels before intersecting: cutting off a known back must not
		// turn a remaining head or leg into a fallback saddle carrier.
		if (!vanilla && backs.isEmpty()) return (BitSet) present.clone();
		backs.and(present);
		return backs;
	}

	private static boolean matches(String name, boolean vanilla) {
		if (vanilla) return name.equals("body");
		// Parent path names must not qualify their head, leg or saddle children as backs.
		String leaf = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('.')) + 1);
		return MODDED_BACK.matcher(leaf).matches();
	}
}
