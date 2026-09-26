package com.nobodiiiii.createbiotech.entity.trait;

/** How a trait is inherited, independently of its tissue scope or anatomical slot. */
public enum BionicTraitType {
	/** 存在型：只要有效范围内保留了载体，就获得完整特性，与占比无关。 */
	PRESENCE,
	/** 门槛型：占比达标，并满足该特性可选的附加条件后，获得完整特性。 */
	COVERAGE_THRESHOLD,
	/** 比例型：特性的强度、概率或数值贡献随载体占比变化。 */
	COVERAGE_SCALED
}
