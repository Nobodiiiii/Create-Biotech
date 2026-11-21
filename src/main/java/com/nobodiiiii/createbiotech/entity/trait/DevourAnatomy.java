package com.nobodiiiii.createbiotech.entity.trait;

import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.CombinationMember;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult.InactiveReason;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;

import net.minecraft.world.phys.Vec3;

final class DevourAnatomy {
	private DevourAnatomy() {}

	static BionicTraitResult evaluate(SurgicalAssembly assembly, BionicTraitData data, BionicTissue tissue,
		List<BionicTraitDonors.Facts> donors, List<BitSet> bindings) {
		var geometry = assembly.attackGeometry();
		Map<CombinationMember, List<Vec3>> cubes = geometry == null ? Map.of() : geometry.carrierCubes();
		double ground = cubes.values().stream().flatMap(List::stream).mapToDouble(Vec3::y).min().orElse(Double.NaN);
		Set<CombinationMember> inner = new HashSet<>(), outer = new HashSet<>(), grounded = new HashSet<>();
		double size = 0;
		for (int source = 0; source < tissue.sourceCount(); source++) {
			if (bindings.get(source).isEmpty()) continue;
			var core = BionicTraitCarriers.select(BionicTrait.Carrier.of(BionicAnatomyRole.DEVOUR_INNER), data, tissue, source);
			var shell = BionicTraitCarriers.select(BionicTrait.Carrier.of(BionicAnatomyRole.DEVOUR_OUTER), data, tissue, source);
			for (int cube = bindings.get(source).nextSetBit(0); cube >= 0; cube = bindings.get(source).nextSetBit(cube + 1)) {
				var member = new CombinationMember(source, cube);
				if (core.get(cube)) inner.add(member);
				if (!shell.get(cube)) continue;
				outer.add(member);
				var points = cubes.get(member);
				if (points != null && points.stream().anyMatch(p -> p.y - ground < SurgicalGait.GROUND_CONTACT_TOLERANCE)) {
					grounded.add(member);
					size = Math.max(size, donors.get(source).value(BionicTrait.DEVOUR));
				}
			}
		}
		if (inner.isEmpty() && outer.isEmpty()) return BionicTraitResult.ABSENT;
		Set<CombinationMember> all = new HashSet<>(inner);
		all.addAll(outer);
		var result = new BionicTraitResult(1, 1, size, Set.of(), all, null);
		if (inner.isEmpty() || outer.isEmpty())
			return result.missingRequiredParts(List.of(List.of(new BionicTraitResult.MissingPart(CBEntityTypes.DEVOURER.getId(),
				inner.isEmpty() ? BionicAnatomyRole.DEVOUR_INNER : BionicAnatomyRole.DEVOUR_OUTER))));
		if (grounded.isEmpty()) return result.disabled(cubes.isEmpty() ? InactiveReason.BODY_MEASUREMENT_UNAVAILABLE : InactiveReason.NOT_GROUNDED);
		grounded.addAll(inner);
		return new BionicTraitResult(1, 1, size, Set.of(), grounded, null);
	}
}
