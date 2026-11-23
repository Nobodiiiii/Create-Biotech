package com.nobodiiiii.createbiotech.entity.trait;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

import com.nobodiiiii.createbiotech.api.bionic.BionicStat;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.ArmAttackGeometry;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.CombinationMember;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCombatCalibration;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalHealthCalibration;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalStatVolume;
import com.nobodiiiii.createbiotech.content.surgery.stats.BionicStats;
import com.nobodiiiii.createbiotech.entity.DevourerEntity;
import net.minecraft.world.level.Level;

/** Converts signed donor points to volume before merging bodies or mounted arm chains. */
final class BionicTissueStats {
	record Values(double health, Map<ArmAttackGeometry, Double> armMultipliers, double devourDamageOffset) {
		static final Values EMPTY = new Values(Double.NaN, Map.of(), 0);
	}

	private final SurgicalAssembly assembly;
	private final Level level;
	private final int wildSize;
	private final BionicTraitResult health, strength, devour;
	private final Map<CombinationMember, Double> strengthTissue = new HashMap<>();
	private final Map<ArmAttackGeometry, Double> multipliers = new HashMap<>();
	private final List<ArmAttackGeometry> arms;
	private double healthVolumeOffset;
	private double nativeHealthOffset;
	private int nextArm;

	BionicTissueStats(SurgicalAssembly assembly, Level level, boolean wild, Map<BionicTrait, BionicTraitResult> results) {
		this.assembly = assembly;
		this.level = level;
		wildSize = wild ? Math.clamp(assembly.sources().getFirst().profile().stableInt("Size") + 1, 1, 127) : 0;
		health = results.getOrDefault(BionicTrait.HEALTH_OFFSET, BionicTraitResult.ABSENT);
		strength = results.getOrDefault(BionicTrait.DAMAGE_OFFSET, BionicTraitResult.ABSENT);
		devour = results.getOrDefault(BionicTrait.DEVOUR, BionicTraitResult.ABSENT);
		arms = strength.active() && assembly.attackGeometry() != null ? assembly.attackGeometry().arms() : List.of();
	}

	boolean hasOffsets() { return health.active() || strength.active(); }

	void addSource(int id, BionicTissue tissue, BionicTraitDonors.Facts facts) {
		double points = facts.value(BionicTrait.HEALTH_OFFSET), armPoints = facts.value(BionicTrait.DAMAGE_OFFSET);
		if ((!health.active() || points == 0) && (!strength.active() || armPoints == 0)) return;
		var source = tissue.source(id);
		var anatomy = source.anatomy();
		double original = anatomy.originalVolume();
		if (original <= 0) return;
		double volume = anatomy.nativeUnionVolume();
		if (!Double.isFinite(volume)) {
			String type = source.profile().entityTypeId().toString();
			double edge = (source.profile().stableInt("Size") + 1) * 0.5;
			volume = switch (type) {
				case "minecraft:slime", "minecraft:magma_cube", "create_biotech:devourer" -> edge * edge * edge;
				default -> original;
			};
		}
		var present = source.presentCubes();
		double retainedPoints = 0;
		var armVolumes = strength.active() && armPoints != 0 ? tissue.originalArmVolumes(id) : Map.<Integer, BionicTissue.ArmVolume>of();
		double base = armVolumes.isEmpty() ? 0 : BionicStats.evaluate(BionicStat.ATTACK_DAMAGE, level,
			Map.of("volume", volume, "slime_size", -1.0));
		Map<BionicTissue.ArmVolume, Double> deltas = new HashMap<>();
		for (int cube = present.nextSetBit(0); cube >= 0; cube = present.nextSetBit(cube + 1)) {
			var member = new CombinationMember(id, cube);
			if (health.active()) retainedPoints += health.memberValues().getOrDefault(member, 0.0);
			var armVolume = armVolumes.get(cube);
			if (armVolume != null && strength.memberValues().containsKey(member)) {
				double delta = deltas.computeIfAbsent(armVolume,
					v -> SurgicalCombatCalibration.strengthVolumeOffset(v.union(), v.length(), armPoints, base, level));
				strengthTissue.put(member, delta * (anatomy.volume(cube) / armVolume.captured()));
			}
		}
		if (points != 0 && retainedPoints != 0) {
			if (wildSize > 0 && id == 0) nativeHealthOffset = retainedPoints;
			else healthVolumeOffset += SurgicalHealthCalibration.healthVolumeOffset(volume, level, points) * (retainedPoints / points);
		}
	}

	/** One arm per evaluation step. Combat, AI selection and box previews reuse its result. */
	boolean nextArm(BionicTissue tissue) {
		if (nextArm >= arms.size() || strengthTissue.isEmpty()) return false;
		var arm = arms.get(nextArm++);
		if (arm.sourceId() < 0) return true;
		var shoulder = assembly.primaryLimbOf(arm.sourceId(), arm.cubeId());
		var chain = shoulder == null ? assembly.rotatingGroup(arm.sourceId(), arm.cubeId()) : tissue.limbMembers(shoulder);
		int interval = SurgicalCombatCalibration.stats(arm, level).attackInterval();
		double delta = armVolumeOffset(chain);
		if (delta != 0) multipliers.put(arm, SurgicalCombatCalibration.damageMultiplier(bounded(arm.volume() + delta), arm, interval, level));
		return true;
	}

	private double armVolumeOffset(Iterable<CombinationMember> members) {
		double delta = 0;
		for (var member : members) delta += strengthTissue.getOrDefault(member, 0.0);
		return delta;
	}

	Values finish() {
		double physical = Math.max(0, assembly.bodyVolume());
		double shell = wildSize * (double) wildSize * wildSize / 8;
		double exposed = Math.max(0, physical - shell);
		// The physical volume already excludes overlap. A donor's delta stays local when another donor is attached.
		double extra = Math.max(0, exposed + healthVolumeOffset);
		double maximum;
		if (wildSize == 0) {
			maximum = SurgicalHealthCalibration.maximumHealth(bounded(extra), level);
		} else {
			double nativeHealth = Math.min(DevourerEntity.maxHealthForSize(wildSize), BionicStat.MAX_HEALTH.maximum());
			maximum = Math.clamp(nativeHealth + nativeHealthOffset, 1, BionicStat.MAX_HEALTH.maximum());
			if (extra > 0) {
				double equivalent = SurgicalHealthCalibration.volumeForHealth(shell, maximum, level);
				double gain = SurgicalHealthCalibration.maximumHealth(bounded(equivalent + extra), level)
					- SurgicalHealthCalibration.maximumHealth(equivalent, level);
				maximum = Math.clamp(maximum + gain, 1, BionicStat.MAX_HEALTH.maximum());
			}
		}
		double devourOffset = 0;
		if (devour.active() && !strengthTissue.isEmpty()) {
			DoubleUnaryOperator contact = v -> BionicStats.evaluate(BionicStat.ATTACK_DAMAGE, level,
				Map.of("volume", v, "slime_size", 2 * Math.cbrt(v)));
			double baseVolume = wildSize > 0 ? shell : physical;
			double delta = armVolumeOffset(devour.members());
			if (delta != 0) devourOffset = contact.applyAsDouble(bounded(baseVolume + delta)) - contact.applyAsDouble(baseVolume);
		}
		return new Values(maximum, Map.copyOf(multipliers), devourOffset);
	}

	private static double bounded(double volume) {
		return Math.clamp(volume, 0, SurgicalStatVolume.LIMIT);
	}
}
