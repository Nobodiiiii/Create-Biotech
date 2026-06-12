package com.nobodiiiii.createbiotech.content.experience;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class LegacyExperienceCompat {
	private LegacyExperienceCompat() {
	}

	public static void migrateTankNbt(CompoundTag compound) {
		if (!isLegacyExperienceTank(compound))
			return;

		if (!compound.contains("Size", Tag.TAG_INT))
			compound.putInt("Size", legacyTankWidth(compound));
		if (!compound.contains("Height", Tag.TAG_INT))
			compound.putInt("Height", 1);
		if (!compound.contains("Window", Tag.TAG_BYTE))
			compound.putBoolean("Window", true);

		if (!compound.contains("TankContent", Tag.TAG_COMPOUND)
			&& compound.contains("StoredExperience", Tag.TAG_INT)) {
			int amount = ExperienceFluidHelper.xpToFluidAmount(compound.getInt("StoredExperience"));
			if (amount > 0)
				compound.put("TankContent", ExperienceFluidHelper.experienceStack(amount)
					.writeToNBT(new CompoundTag()));
		}

		compound.remove("StoredExperience");
		compound.remove("Width");
	}

	private static boolean isLegacyExperienceTank(CompoundTag compound) {
		return compound.contains("StoredExperience", Tag.TAG_INT) || compound.contains("Width", Tag.TAG_INT);
	}

	private static int legacyTankWidth(CompoundTag compound) {
		if (compound.contains("Width", Tag.TAG_INT))
			return Math.max(1, compound.getInt("Width"));
		return Math.max(1, compound.getInt("Size"));
	}
}
