package com.nobodiiiii.createbiotech.entity.trait;

import java.util.BitSet;

/** Tissue contributing to both the numerator and denominator, independent of inheritance type. */
public enum BionicBodyTraitScope {
	/** 躯干特性：只计算安装在躯干部位的保留体块。 */
	TORSO,
	/** 全身特性：所有保留体块都参与，不限制器官角色或安装部位。 */
	WHOLE_BODY;

	/** Returns a separate selection; cut-away cubes never contribute in either scope. */
	public BitSet selectCubes(BitSet retained, BitSet torso) {
		BitSet selected = (BitSet) retained.clone();
		if (this == TORSO)
			selected.and(torso);
		return selected;
	}

	public String descriptionId() {
		return this == TORSO ? BionicTraitSlot.BODY.descriptionId()
			: "create_biotech.trait.scope.whole_body";
	}
}
