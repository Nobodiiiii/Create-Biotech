package com.nobodiiiii.createbiotech.content.experience;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class LegacyExperienceCompat {
	private LegacyExperienceCompat() {
	}

	public static boolean migrateTankNbt(CompoundTag compound) {
		if (!isLegacyExperienceTank(compound))
			return false;

		boolean migrated = false;

		if (!compound.contains("Size", Tag.TAG_INT)) {
			compound.putInt("Size", legacyTankWidth(compound));
			migrated = true;
		}
		if (!compound.contains("Height", Tag.TAG_INT)) {
			compound.putInt("Height", 1);
			migrated = true;
		}
		if (!compound.contains("Window", Tag.TAG_BYTE)) {
			compound.putBoolean("Window", true);
			migrated = true;
		}

		if (!compound.contains("TankContent", Tag.TAG_COMPOUND)
			&& compound.contains("StoredExperience", Tag.TAG_INT)) {
			int amount = ExperienceFluidHelper.xpToFluidAmount(compound.getInt("StoredExperience"));
			if (amount > 0) {
				compound.put("TankContent", ExperienceFluidHelper.experienceStack(amount)
					.writeToNBT(new CompoundTag()));
				migrated = true;
			}
		}

		if (compound.contains("StoredExperience", Tag.TAG_INT)) {
			compound.remove("StoredExperience");
			migrated = true;
		}
		if (compound.contains("Width", Tag.TAG_INT)) {
			compound.remove("Width");
			migrated = true;
		}

		return migrated;
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
