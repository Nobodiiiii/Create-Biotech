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
		buffer.writeFloat(bounds.eyeHeight());
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
		if (assembly == null)
			return;
		SurgicalAssembly.BodyBounds existingBounds = assembly.bodyBounds();
		SurgicalAssembly.HitboxGeometry existingGeometry = assembly.hitboxGeometry();
		// A body packed by a current build already carries its measurement, taken once by the packing
		// client. Only fill in what a save from before that geometry existed is missing; never
		// re-measure. Accepting corrections lets two clients running different entity models overwrite
		// each other indefinitely, re-encoding and re-broadcasting the whole assembly every round.
		if (existingBounds != null && existingGeometry != null)
			return;
		if (existingBounds == null && !validMobilityMeasurements(assembly, bounds))
			return;
		SurgicalAssembly.BodyBounds resolvedBounds = existingBounds != null ? existingBounds : bounds;
		SurgicalAssembly.HitboxGeometry resolvedGeometry =
			existingGeometry != null ? existingGeometry : hitboxGeometry;
		if (resolvedBounds == null || resolvedGeometry == null)
			return;
		bionic.setAssembly(assembly.withBodyGeometry(resolvedBounds, resolvedGeometry));
	}

	private static SurgicalAssembly.BodyBounds readBounds(FriendlyByteBuf buffer) {
		SurgicalAssembly.BodyBounds bounds = SurgicalAssembly.BodyBounds.create(
			buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
			buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readVarInt(),
			buffer.readVarInt(), buffer.readFloat());
		if (bounds == null)
			throw new IllegalArgumentException("Invalid bionic body bounds");
		return bounds;
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
}
