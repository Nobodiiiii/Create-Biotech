package com.nobodiiiii.createbiotech.content.surgery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.LoadingModList;

class SurgicalFlatComponentPackingTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
		Bootstrap.bootStrap();
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 2})
	void flatComponentCanBePackedAndReloadedOnEveryAxis(int flatAxis) {
		double[] size = {0.5d, 0.5d, 0.5d};
		size[flatAxis] = 0.0d;
		List<Vec3> corners = new ArrayList<>(8);
		for (int corner = 0; corner < 8; corner++)
			corners.add(new Vec3((corner & 1) == 0 ? 0.0d : size[0],
				(corner & 2) == 0 ? 0.0d : size[1], (corner & 4) == 0 ? 0.0d : size[2]));
		List<List<Vec3>> cubes = List.of(corners);
		SurgicalBodyBounds.Envelope visible = new SurgicalBodyBounds.Envelope(
			0.0d, 0.0d, 0.0d, size[0], size[1], size[2]);
		SurgicalAssembly.BodyBounds body = SurgicalBodyBounds.measure(cubes, cubes, visible);
		assertNotNull(body);
		SurgicalAssembly assembly = assembly();
		SurgicalAssembly.HitboxGeometry hitboxes = SurgicalHitboxGeometry.measure(assembly,
			List.of(Map.of(0, corners)), visible, body);
		assertNotNull(hitboxes);
		double volume = SurgicalVolumeSampler.unionVolume(cubes);
		assertEquals(0.0d, volume);
		assertTrue(SurgicalHealthCalibration.validMeasuredVolume(volume, hitboxes));

		SurgicalAssembly packed = assembly.withBodyGeometry(body, hitboxes, volume);
		assertTrue(packed.isReadyForEntity());
		CompoundTag saved = packed.save();
		assertTrue(saved.contains("BodyVolume", Tag.TAG_DOUBLE));
		assertEquals(0.0d, saved.getDouble("BodyVolume"));
		SurgicalAssembly restored = SurgicalAssembly.load(saved);
		assertNotNull(restored);
		assertTrue(restored.isReadyForEntity());
		assertTrue(restored.hasBodyVolume());
		assertEquals(body, restored.bodyBounds());
		assertEquals(hitboxes, restored.hitboxGeometry());
		assertEquals(SurgicalHealthCalibration.MIN_HEALTH,
			SurgicalHealthCalibration.maximumHealth(restored.bodyVolume()));
	}

	@Test
	void missingVolumeRemainsUnmeasuredAfterReload() {
		SurgicalAssembly assembly = assembly();
		CompoundTag saved = assembly.save();
		assertFalse(saved.contains("BodyVolume"));
		SurgicalAssembly restored = SurgicalAssembly.load(saved);
		assertNotNull(restored);
		assertFalse(restored.hasBodyVolume());
		assertFalse(restored.isReadyForEntity());
	}

	@Test
	void invalidOrOversizedMeasurementsAreStillRejected() {
		SurgicalAssembly.VisualBounds bounds = SurgicalAssembly.VisualBounds.create(
			-0.25d, 0.0d, -0.25d, 0.25d, 0.5d, 0.25d);
		SurgicalAssembly.HitboxGeometry hitboxes = SurgicalAssembly.HitboxGeometry.create(
			bounds, bounds, List.of());
		assertNotNull(hitboxes);
		for (double volume : new double[] {-1.0d, Double.NaN, Double.NEGATIVE_INFINITY,
			Double.POSITIVE_INFINITY, 1.0d, Double.MAX_VALUE})
			assertFalse(SurgicalHealthCalibration.validMeasuredVolume(volume, hitboxes));
		assertFalse(SurgicalHealthCalibration.validMeasuredVolume(0.0d, null));
		assertTrue(SurgicalHealthCalibration.validMeasuredVolume(0.125d, hitboxes));
	}

	private static SurgicalAssembly assembly() {
		CompoundTag profileTag = new CompoundTag();
		profileTag.putInt("Version", 2);
		profileTag.putString("EntityType", "minecraft:warden");
		MimicProfile profile = MimicProfile.load(profileTag);
		assertNotNull(profile);
		BitSet present = new BitSet();
		present.set(0);
		SurgicalAssembly assembly = SurgicalAssembly.create(profile, 1, present,
			new BitSet(), List.of(), new BitSet(), List.of());
		assertNotNull(assembly);
		return assembly;
	}
}
