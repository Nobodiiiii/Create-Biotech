package com.nobodiiiii.createbiotech.content.experience.pipe;

public interface ExperienceEndpoint {
	int extract(int maxAmount, boolean simulate);

	int insert(int amount, boolean simulate);

	default boolean canExtract() {
		return extract(1, true) > 0;
	}
}
