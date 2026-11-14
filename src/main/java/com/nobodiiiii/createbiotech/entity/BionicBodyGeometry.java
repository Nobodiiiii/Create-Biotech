package com.nobodiiiii.createbiotech.entity;

import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalHealthCalibration;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** BionicBodyGeometry algorithms over the canonical SlimeBionicEntity state. */
final class BionicBodyGeometry {
	private BionicBodyGeometry() {}


	/** Applies the renderer's exact visible envelope on the client, including slime-shell inflation. */
	static void setClientBodyGeometry(SlimeBionicEntity operationOwner, SurgicalAssembly assembly, SurgicalAssembly.BodyBounds bounds,
		SurgicalAssembly.HitboxGeometry hitboxGeometry, double bodyVolume) {
		if (!operationOwner.level().isClientSide || assembly == null || bounds == null || hitboxGeometry == null
			|| operationOwner.getAssembly() != assembly
			|| !SurgicalHealthCalibration.validMeasuredVolume(bodyVolume, hitboxGeometry))
			return;
		if (operationOwner.clientBoundsAssembly == assembly && bounds.equals(operationOwner.clientBodyBounds)
			&& hitboxGeometry.equals(operationOwner.clientHitboxGeometry)
			&& Double.compare(bodyVolume, operationOwner.clientBodyVolume) == 0)
			return;
		operationOwner.clientBoundsAssembly = assembly;
		operationOwner.clientBodyBounds = bounds;
		operationOwner.clientHitboxGeometry = hitboxGeometry;
		operationOwner.clientBodyVolume = bodyVolume;
		operationOwner.refreshDimensions();
		BionicBodyGeometry.updateHitParts(operationOwner);
	}

	/**
	 * Collision, hit parts and picking must agree between the server and every client, so they read
	 * the bounds frozen in the assembly rather than whatever this client's model measures. A client
	 * running a replaced entity model therefore sees visuals and hitbox disagree, which is preferred
	 * over each client simulating its own physics for the same mob.
	 */
	static @Nullable
	SurgicalAssembly.BodyBounds authoritativeBodyBounds(SlimeBionicEntity operationOwner) {
		SurgicalAssembly assembly = operationOwner.getAssembly();
		return assembly == null ? null : assembly.bodyBounds();
	}

	/** Render-only envelope: this client's own measurement when it has one, else the shared bounds. */
	static @Nullable
	SurgicalAssembly.BodyBounds renderBodyBounds(SlimeBionicEntity operationOwner) {
		SurgicalAssembly assembly = operationOwner.getAssembly();
		return operationOwner.level().isClientSide && operationOwner.clientBoundsAssembly == assembly && operationOwner.clientBodyBounds != null
			? operationOwner.clientBodyBounds : BionicBodyGeometry.authoritativeBodyBounds(operationOwner);
	}

	static void updateHitParts(SlimeBionicEntity operationOwner) {
		if (operationOwner.hitParts == null || operationOwner.registeredHitParts.length == 0)
			return;
		SurgicalAssembly.HitboxGeometry geometry = BionicMeleeController.authoritativeHitboxGeometry(operationOwner);
		List<SurgicalAssembly.VisualBounds> bounds = geometry == null ? List.of()
			: geometry.partBounds(SlimeBionicEntity.MULTIPART_THRESHOLD);
		AABB equipmentBounds = operationOwner.equipment.interactionBounds();
		if (bounds.size() + (equipmentBounds == null ? 0 : 1) > operationOwner.registeredHitParts.length) {
			if (geometry == null) return;
			bounds = List.of(geometry.overall());
		}
		for (int index = 0; index < operationOwner.registeredHitParts.length; index++)
			operationOwner.registeredHitParts[index].setHitBounds(index < bounds.size() ? BionicBodyGeometry.worldBounds(operationOwner, bounds.get(index))
				: index == bounds.size() ? equipmentBounds : null);
	}

	/** Chooses the cached subset before level tracking starts; tracked part maps cannot grow later. */
	static void configureHitPartRegistration(SlimeBionicEntity operationOwner, SurgicalAssembly assembly) {
		if (operationOwner.isAddedToLevel())
			return;
		SurgicalAssembly.HitboxGeometry geometry = assembly == null ? null : assembly.hitboxGeometry();
		if (geometry != null)
			BionicBodyGeometry.setRegisteredHitPartCount(operationOwner, geometry.partBounds(SlimeBionicEntity.MULTIPART_THRESHOLD).size()
				+ (assembly.attackGeometry() != null && !assembly.attackGeometry().equipmentBounds().isEmpty() ? 1 : 0));
	}

	static void setRegisteredHitPartCount(SlimeBionicEntity operationOwner, int count) {
		operationOwner.registeredHitParts = count >= operationOwner.hitParts.length ? operationOwner.hitParts
			: Arrays.copyOf(operationOwner.hitParts, Mth.clamp(count, 0, operationOwner.hitParts.length));
	}

	static AABB worldBounds(SlimeBionicEntity operationOwner, SurgicalAssembly.VisualBounds bounds) {
		if (operationOwner.isBlockAttached()) {
			AABB rotated = operationOwner.blockAttachment.bounds(bounds);
			if (rotated != null) return rotated.move(operationOwner.position());
		}
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;
		for (int xSide = 0; xSide < 2; xSide++)
			for (int ySide = 0; ySide < 2; ySide++)
			for (int zSide = 0; zSide < 2; zSide++) {
				Vec3 rotated = operationOwner.bodyToWorld(new Vec3(xSide == 0 ? bounds.minX() : bounds.maxX(),
					ySide == 0 ? bounds.minY() : bounds.maxY(), zSide == 0 ? bounds.minZ() : bounds.maxZ()));
				minX = Math.min(minX, rotated.x);
				minY = Math.min(minY, rotated.y);
				minZ = Math.min(minZ, rotated.z);
				maxX = Math.max(maxX, rotated.x);
				maxY = Math.max(maxY, rotated.y);
				maxZ = Math.max(maxZ, rotated.z);
			}
		return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}
}
