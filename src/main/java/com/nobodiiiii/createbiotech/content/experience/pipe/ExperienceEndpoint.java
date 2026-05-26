package com.nobodiiiii.createbiotech.content.experience.pipe;

public interface ExperienceEndpoint {
	int extract(int maxAmount, boolean simulate);

	int insert(int amount, boolean simulate);
}
