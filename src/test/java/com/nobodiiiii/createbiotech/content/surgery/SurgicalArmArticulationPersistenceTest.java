package com.nobodiiiii.createbiotech.content.surgery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

class SurgicalArmArticulationPersistenceTest {
	@Test
	void savesElbowFlagAndDefaultsLegacyGeometryToRigid() {
		SurgicalAssembly.ArmAttackGeometry articulated = SurgicalAssembly.ArmAttackGeometry.create(
			Vec3.ZERO, 1.0f, -1.0f, 1.0f, 0.25f, 0.5f, new Vec3(0.0d, 0.0d, 1.0d), true);
		assertNotNull(articulated);

		SurgicalAssembly.ArmAttackGeometry decoded =
			SurgicalAssembly.ArmAttackGeometry.load(articulated.save());
		assertNotNull(decoded);
		assertTrue(decoded.hasElbow());

		CompoundTag legacy = articulated.save();
		legacy.remove("HasElbow");
		SurgicalAssembly.ArmAttackGeometry legacyDecoded =
			SurgicalAssembly.ArmAttackGeometry.load(legacy);
		assertNotNull(legacyDecoded);
		assertFalse(legacyDecoded.hasElbow());
	}
}
