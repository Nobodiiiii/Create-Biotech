package com.nobodiiiii.createbiotech.entity;

import com.nobodiiiii.createbiotech.api.bionic.BionicStat;
import com.nobodiiiii.createbiotech.content.surgery.stats.BionicStats;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitSet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** BionicAttributeRefresh algorithms over the canonical SlimeBionicEntity state. */
final class BionicAttributeRefresh {
	private BionicAttributeRefresh() {}


	static void refreshBaseStats(SlimeBionicEntity operationOwner, SurgicalAssembly assembly) {
		if (operationOwner.level().isClientSide) return;
		operationOwner.baseStatsRevision = BionicStats.revision(operationOwner.level());
		BionicAttributeRefresh.refreshMaximumHealth(operationOwner, assembly, operationOwner.getBionicTraits());
		BionicLocomotionPolicy.refreshMovementSpeed(operationOwner);
	}

	static double anatomicalBaseStat(SlimeBionicEntity operationOwner, BionicStat stat) {
		int size = operationOwner.wildSlimeSize();
		if (size > 0 && stat == BionicStat.FOLLOW_RANGE)
			return net.minecraft.world.entity.ai.attributes.DefaultAttributes.getSupplier(net.minecraft.world.entity.EntityType.SLIME).getBaseValue(Attributes.FOLLOW_RANGE);
		SurgicalAssembly assembly = operationOwner.getAssembly();
		return BionicStats.evaluate(stat, operationOwner.level(),
			java.util.Map.of("volume", assembly != null && assembly.hasBodyVolume() ? assembly.bodyVolume() : 0.0d));
	}

	/** Applies the packed union-volume calibration without healing an already damaged body. */
	static void refreshMaximumHealth(SlimeBionicEntity operationOwner, SurgicalAssembly assembly, BionicTraitSet traits) {
		if (operationOwner.level().isClientSide)
			return;
		var maximumHealth = operationOwner.getAttribute(Attributes.MAX_HEALTH);
		if (maximumHealth == null)
			return;
		double calibrated = operationOwner.baseMaximumHealth(assembly, traits);
		if (maximumHealth.getBaseValue() != calibrated)
			maximumHealth.setBaseValue(calibrated);
		if (operationOwner.getHealth() > operationOwner.getMaxHealth())
			operationOwner.setHealth(operationOwner.getMaxHealth());
	}

	static void refreshNaturalArmor(SlimeBionicEntity operationOwner, BionicTraitSet traits) {
		var armor = operationOwner.getAttribute(Attributes.ARMOR);
		if (armor != null && armor.getBaseValue() != traits.value(BionicTrait.NATURAL_ARMOR))
			armor.setBaseValue(traits.value(BionicTrait.NATURAL_ARMOR));
	}

	static void refreshKnockbackResistance(SlimeBionicEntity operationOwner, BionicTraitSet traits) {
		var resistance = operationOwner.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		double value = Mth.clamp(BionicAttributeRefresh.anatomicalBaseStat(operationOwner, BionicStat.KNOCKBACK_RESISTANCE)
			+ traits.value(BionicTrait.KNOCKBACK_RESISTANCE), 0.0d, 1.0d);
		if (resistance != null && resistance.getBaseValue() != value)
			resistance.setBaseValue(value);
	}
}
