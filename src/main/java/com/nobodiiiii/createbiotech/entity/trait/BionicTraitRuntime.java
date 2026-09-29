package com.nobodiiiii.createbiotech.entity.trait;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import net.minecraft.world.phys.Vec3;

/** Shared donor selection and baked carrier positions; no preview entities are spawned at runtime. */
public final class BionicTraitRuntime {
	private BionicTraitRuntime() {}

	@Nullable
	public static SurgicalAssembly.Source dominant(SlimeBionicEntity entity, BionicTrait trait) {
		return dominant(entity, entity.getBionicTraits().members(trait));
	}

	@Nullable
	public static SurgicalAssembly.Source dominant(SlimeBionicEntity entity,
		Collection<SurgicalAssembly.CombinationMember> members) {
		SurgicalAssembly assembly = entity.getAssembly();
		if (assembly == null) return null;
		Map<Integer, Double> volumes = new HashMap<>();
		for (var member : members)
			volumes.merge(member.source(), assembly.sources().get(member.source()).anatomy().volume(member.cube()), Double::sum);
		return volumes.entrySet().stream().sorted(Map.Entry.<Integer, Double>comparingByValue().reversed()
			.thenComparing(Map.Entry.comparingByKey())).findFirst().map(entry -> assembly.sources().get(entry.getKey())).orElse(null);
	}

	/** Select one physical carrier group, so disjoint heads never fire from empty space between them. */
	public static Set<SurgicalAssembly.CombinationMember> firstGroup(SlimeBionicEntity entity,
		Collection<SurgicalAssembly.CombinationMember> members) {
		SurgicalAssembly assembly = entity.getAssembly();
		if (assembly == null || members.isEmpty()) return Set.of();
		var first = members.stream().min(Comparator.comparingInt(SurgicalAssembly.CombinationMember::source)
			.thenComparingInt(SurgicalAssembly.CombinationMember::cube)).orElseThrow();
		var limb = assembly.primaryLimbOf(first.source(), first.cube());
		return members.stream().filter(member -> java.util.Objects.equals(limb, assembly.primaryLimbOf(member.source(), member.cube()))).collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	public static Vec3 origin(SlimeBionicEntity entity, Collection<SurgicalAssembly.CombinationMember> members) {
		SurgicalAssembly assembly = entity.getAssembly();
		if (assembly == null || assembly.attackGeometry() == null) return entity.getEyePosition();
		Vec3 sum = Vec3.ZERO;
		double weight = 0;
		for (var member : members) {
			Vec3 point = assembly.attackGeometry().carrierOrigins().get(member);
			if (point == null) continue;
			double volume = assembly.sources().get(member.source()).anatomy().volume(member.cube());
			sum = sum.add(point.scale(volume));
			weight += volume;
		}
		return weight == 0 ? entity.getEyePosition()
			: entity.position().add(sum.scale(1 / weight).yRot(-entity.yBodyRot * (float) Math.PI / 180));
	}
}
