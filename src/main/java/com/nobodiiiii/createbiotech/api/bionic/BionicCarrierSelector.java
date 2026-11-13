package com.nobodiiiii.createbiotech.api.bionic;

import java.util.BitSet;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;

/** Pure selector of original cube indices. The resolver intersects with retained tissue afterward. */
@FunctionalInterface
public interface BionicCarrierSelector {
	BitSet select(SurgicalAssembly.Source source);
}
