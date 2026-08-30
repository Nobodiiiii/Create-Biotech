package com.nobodiiiii.createbiotech.entity;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Lets the measuring client finalize the authoritative bounds of legacy and newly packed bodies. */
public record SlimeBionicBodyBoundsPacket(int entityId, SurgicalAssembly.BodyBounds bounds,
	SurgicalAssembly.HitboxGeometry hitboxGeometry) {
	private static final double MAX_REPORT_DISTANCE_SQR = 128.0d * 128.0d;

	public SlimeBionicBodyBoundsPacket(FriendlyByteBuf buffer) {
		this(buffer.readVarInt(), readBounds(buffer), SurgicalAssembly.HitboxGeometry.read(buffer));
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeVarInt(entityId);
		buffer.writeFloat(bounds.width());
		buffer.writeFloat(bounds.height());
		buffer.writeFloat(bounds.depth());
		buffer.writeFloat(bounds.centerX());
		buffer.writeFloat(bounds.minY());
		buffer.writeFloat(bounds.centerZ());
		buffer.writeFloat(bounds.legLength());
		buffer.writeVarInt(bounds.groundedLegCount());
		buffer.writeVarInt(bounds.groundedKneeCount());
		buffer.writeFloat(bounds.legVolumeRatio());
		hitboxGeometry.write(buffer);
	}

	public void handle(ServerPlayer player) {
		if (player == null)
			return;
		Entity found = player.level().getEntity(entityId);
		if (!(found instanceof SlimeBionicEntity bionic)
			|| player.distanceToSqr(bionic) > MAX_REPORT_DISTANCE_SQR)
			return;
		SurgicalAssembly assembly = bionic.getAssembly();
		if (assembly == null || !reasonableCorrection(assembly.bodyBounds(), bounds)
			|| !validMobilityMeasurements(assembly, bounds)
			|| !reasonableCorrection(assembly.hitboxGeometry(), hitboxGeometry))
			return;
		bionic.setAssembly(assembly.withBodyGeometry(bounds, hitboxGeometry));
	}

	private static SurgicalAssembly.BodyBounds readBounds(FriendlyByteBuf buffer) {
		SurgicalAssembly.BodyBounds bounds = SurgicalAssembly.BodyBounds.create(
			buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
			buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readVarInt(),
			buffer.readVarInt(), buffer.readFloat());
		if (bounds == null)
			throw new IllegalArgumentException("Invalid bionic body bounds");
		return bounds;
	}

	private static boolean reasonableCorrection(SurgicalAssembly.BodyBounds existing,
		SurgicalAssembly.BodyBounds measured) {
		return existing == null || close(existing.width(), measured.width())
			&& close(existing.height(), measured.height()) && close(existing.depth(), measured.depth())
			&& closeOffset(existing.centerX(), measured.centerX())
			&& closeOffset(existing.minY(), measured.minY())
			&& closeOffset(existing.centerZ(), measured.centerZ())
			&& (existing.groundedLegCount() == 0
				|| reasonableLegLength(existing.legLength(), measured.legLength()))
			&& reasonableMobility(existing, measured);
	}

	private static boolean close(float expected, float measured) {
		return Math.abs(expected - measured) <= Math.max(0.5f, expected * 0.25f);
	}

	private static boolean closeOffset(float expected, float measured) {
		return Math.abs(expected - measured) <= 0.5f;
	}

	private static boolean reasonableLegLength(float expected, float measured) {
		return expected == 0.0f || measured > 0.0f && close(expected, measured);
	}

	private static boolean reasonableMobility(SurgicalAssembly.BodyBounds expected,
		SurgicalAssembly.BodyBounds measured) {
		return expected.groundedLegCount() == 0
			|| expected.groundedLegCount() == measured.groundedLegCount()
				&& expected.groundedKneeCount() == measured.groundedKneeCount()
				&& Math.abs(expected.legVolumeRatio() - measured.legVolumeRatio()) <= 0.1f;
	}

	private static boolean validMobilityMeasurements(SurgicalAssembly assembly,
		SurgicalAssembly.BodyBounds measured) {
		long installedHips = assembly.effectiveLimbs().stream()
			.filter(limb -> limb.type() == SurgicalLimbType.HIP).count();
		long installedKnees = assembly.effectiveLimbs().stream()
			.filter(limb -> limb.type() == SurgicalLimbType.KNEE).count();
		return measured.groundedLegCount() <= installedHips
			&& measured.groundedKneeCount() <= installedKnees;
	}

	private static boolean reasonableCorrection(SurgicalAssembly.HitboxGeometry existing,
		SurgicalAssembly.HitboxGeometry measured) {
		if (measured == null)
			return false;
		if (existing == null)
			return true;
		if (existing.limbs().size() != measured.limbs().size()
			|| !close(existing.overall(), measured.overall())
			|| !close(existing.body(), measured.body()))
			return false;
		for (int index = 0; index < existing.limbs().size(); index++)
			if (!close(existing.limbs().get(index), measured.limbs().get(index)))
				return false;
		return true;
	}

	private static boolean close(SurgicalAssembly.VisualBounds expected,
		SurgicalAssembly.VisualBounds measured) {
		return closeCoordinate(expected.minX(), measured.minX())
			&& closeCoordinate(expected.minY(), measured.minY())
			&& closeCoordinate(expected.minZ(), measured.minZ())
			&& closeCoordinate(expected.maxX(), measured.maxX())
			&& closeCoordinate(expected.maxY(), measured.maxY())
			&& closeCoordinate(expected.maxZ(), measured.maxZ());
	}

	private static boolean closeCoordinate(float expected, float measured) {
		return Math.abs(expected - measured) <= 0.75f;
	}
}
