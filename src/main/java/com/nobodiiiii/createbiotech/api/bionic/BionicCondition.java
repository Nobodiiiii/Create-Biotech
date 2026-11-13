package com.nobodiiiii.createbiotech.api.bionic;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitSet;
import net.minecraft.world.level.Level;

/** Pure acquisition condition. World/time-dependent checks belong in behavior callbacks. */
@FunctionalInterface
public interface BionicCondition {
	boolean matches(Context context);
	record Context(SurgicalAssembly assembly, Level level, BionicTrait trait,
		BionicTraitSet covered, BionicTraitResult result) {}
}
