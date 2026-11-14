package com.nobodiiiii.createbiotech.api.bionic;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult;

/** Pure acquisition refinement. May disable a candidate or narrow its carriers, never create carriers. */
@FunctionalInterface
public interface BionicAnatomyEvaluator {
	BionicTraitResult evaluate(SurgicalAssembly assembly, BionicTrait trait, BionicTraitResult candidate);
}
