package com.nobodiiiii.createbiotech.content.possession;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;

/** Player data bridge supplied by {@code PlayerPossessionDataMixin}. */
public interface PossessionAccess {

	CompoundTag createBiotech$getPossessionState();

	void createBiotech$setPossessionState(CompoundTag state);

	boolean createBiotech$isPossessionData(EntityDataAccessor<?> key);
}
