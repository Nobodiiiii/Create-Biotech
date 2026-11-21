package com.nobodiiiii.createbiotech.content.surgery;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.AttackGeometry;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.BodyBounds;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.CombinationMember;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.HitboxGeometry;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Measurements only. The server owns the donors, their poses, connections and trait evaluation. */
public record DevouredBodyMeasurementPacket(int entityId, UUID entityUuid, UUID latestDonor,
	List<DevouredDonorTopology> prey, BodyBounds bounds, double volume, HitboxGeometry hitboxes,
	AttackGeometry attacks, @Nullable SurgicalPackingAlignment alignment) {
	public DevouredBodyMeasurementPacket {
		prey = List.copyOf(prey);
		if (prey.isEmpty() || prey.size() > SurgicalAssembly.MAX_SOURCES || bounds == null || attacks == null
			|| !SurgicalHealthCalibration.validMeasuredVolume(volume, hitboxes))
			throw new IllegalArgumentException("Invalid devoured body measurements");
	}

	public DevouredBodyMeasurementPacket(FriendlyByteBuf buffer) {
		this(buffer.readVarInt(), buffer.readUUID(), buffer.readUUID(), SurgicalTablePlacementPacket.readPreyModels(buffer),
			SurgicalTableInteractionPacket.readBodyBounds(buffer), buffer.readDouble(), HitboxGeometry.read(buffer),
			AttackGeometry.read(buffer), buffer.readBoolean() ? SurgicalPackingAlignment.read(buffer) : null);
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeVarInt(entityId);
		buffer.writeUUID(entityUuid);
		buffer.writeUUID(latestDonor);
		SurgicalTablePlacementPacket.writePreyModels(buffer, prey);
		SurgicalTableInteractionPacket.writeBodyBounds(buffer, bounds);
		buffer.writeDouble(volume);
		hitboxes.write(buffer);
		attacks.write(buffer);
		buffer.writeBoolean(alignment != null);
		if (alignment != null) alignment.write(buffer);
	}

	public void handle(ServerPlayer player) {
		var level = player.serverLevel();
		if (!(level.getEntity(entityId) instanceof SlimeBionicEntity body) || !body.isAlive()
			|| !entityUuid.equals(body.getUUID()) || body.growthObserver() != player.getId()
			|| !level.getChunkSource().chunkMap.getPlayersWatching(body).contains(player)) return;
		var pending = body.getPendingGrowth();
		if (pending != null && latestDonor.equals(pending.sources().getLast().donorId()))
			DevouredBodyInstallQueue.submit(body, level, pending, this);
	}

	@Nullable
	SurgicalPackingOperations.MeasuredBody prepare(SurgicalAssembly pending) {
		var expanded = DevouredDonorTopology.expand(pending, prey);
		if (expanded == null || !matchesCubes(expanded)) return null;
		return SurgicalPackingOperations.prepareMeasuredAssembly(expanded, bounds, volume, hitboxes, attacks, alignment);
	}

	private boolean matchesCubes(SurgicalAssembly assembly) {
		int count = 0;
		double sumVolume = 0;
		for (int index = 0; index < assembly.sources().size(); index++) {
			var cubes = assembly.sources().get(index).presentCubes();
			count += cubes.cardinality();
			for (int cube = cubes.nextSetBit(0); cube >= 0; cube = cubes.nextSetBit(cube + 1)) {
				var member = new CombinationMember(index, cube);
				var corners = attacks.carrierCubes().get(member);
				if (corners == null) return false;
				Vec3 center = Vec3.ZERO;
				for (var corner : corners) center = center.add(corner);
				if (center.scale(0.125d).distanceToSqr(attacks.carrierOrigins().get(member)) > 1e-8d) return false;
				sumVolume += SurgicalVolumeSampler.cubeVolume(corners);
			}
		}
		return volume <= sumVolume * 1.0001d + 1e-6d
			&& attacks.carrierCubes().size() == count && attacks.carrierOrigins().size() == count
			&& attacks.armCount() == SurgicalPackingOperations.limbCount(assembly, SurgicalLimbType.SHOULDER);
	}
}
