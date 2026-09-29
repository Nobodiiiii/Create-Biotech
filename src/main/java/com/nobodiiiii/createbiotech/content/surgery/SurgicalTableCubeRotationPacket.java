package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Model geometry is captured by the client; membership, transforms, fit and contact are checked by the server. */
public record SurgicalTableCubeRotationPacket(BlockPos pos, InteractionHand hand, UUID subjectId,
	int cubeId, Vec3 axis, double degrees, boolean straighten, boolean single,
	List<SurgicalWrenchRotation.Body> bodies) {
	public SurgicalTableCubeRotationPacket(FriendlyByteBuf buffer) {
		this(buffer.readBlockPos(), buffer.readEnum(InteractionHand.class), buffer.readUUID(),
			buffer.readVarInt(), readVector(buffer), buffer.readDouble(), buffer.readBoolean(), buffer.readBoolean(),
			readBodies(buffer));
	}

	private static Vec3 readVector(FriendlyByteBuf buffer) {
		return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
	}

	private static void writeVector(FriendlyByteBuf buffer, Vec3 vector) {
		buffer.writeDouble(vector.x);
		buffer.writeDouble(vector.y);
		buffer.writeDouble(vector.z);
	}

	private static int count(FriendlyByteBuf buffer, int maximum) {
		int count = buffer.readVarInt();
		if (count < 0 || count > maximum)
			throw new IllegalArgumentException("Invalid surgical rotation payload size");
		return count;
	}

	private static List<SurgicalWrenchRotation.Body> readBodies(FriendlyByteBuf buffer) {
		int count = count(buffer, SurgicalAssembly.MAX_SOURCES);
		List<SurgicalWrenchRotation.Body> bodies = new ArrayList<>(count);
		int total = 0;
		for (int i = 0; i < count; i++) {
			int id = buffer.readVarInt();
			UUID key = buffer.readUUID();
			int cubeCount = buffer.readVarInt();
			int seamCount = count(buffer, SurgicalAssembly.MAX_SEAMS);
			List<SurgicalAssembly.Seam> seams = new ArrayList<>(seamCount);
			for (int j = 0; j < seamCount; j++)
				seams.add(SurgicalAssembly.Seam.of(buffer.readVarInt(), buffer.readVarInt()));
			int size = count(buffer, SurgicalAssembly.MAX_CUBES - total);
			total += size;
			List<SurgicalWrenchRotation.Cube> cubes = new ArrayList<>(size);
			for (int j = 0; j < size; j++) {
				int cubeId = buffer.readVarInt();
				Vec3 offset = readVector(buffer);
				SurgicalCubeRotation rotation = SurgicalCubeRotation.read(buffer);
				List<Vec3> corners = new ArrayList<>(8);
				for (int corner = 0; corner < 8; corner++)
					corners.add(readVector(buffer));
				cubes.add(new SurgicalWrenchRotation.Cube(cubeId, corners, offset, rotation));
			}
			bodies.add(new SurgicalWrenchRotation.Body(id, key, cubeCount, seams, cubes));
		}
		return List.copyOf(bodies);
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeBlockPos(pos);
		buffer.writeEnum(hand);
		buffer.writeUUID(subjectId);
		buffer.writeVarInt(cubeId);
		writeVector(buffer, axis);
		buffer.writeDouble(degrees);
		buffer.writeBoolean(straighten);
		buffer.writeBoolean(single);
		buffer.writeVarInt(bodies.size());
		for (SurgicalWrenchRotation.Body body : bodies) {
			buffer.writeVarInt(body.id());
			buffer.writeUUID(body.key());
			buffer.writeVarInt(body.cubeCount());
			buffer.writeVarInt(body.seams().size());
			for (SurgicalAssembly.Seam seam : body.seams()) {
				buffer.writeVarInt(seam.first());
				buffer.writeVarInt(seam.second());
			}
			buffer.writeVarInt(body.cubes().size());
			for (SurgicalWrenchRotation.Cube cube : body.cubes()) {
				buffer.writeVarInt(cube.id());
				writeVector(buffer, cube.offset());
				cube.rotation().write(buffer);
				for (Vec3 corner : cube.corners())
					writeVector(buffer, corner);
			}
		}
	}

	public void handle(ServerPlayer player) {
		if (player == null || player.isSpectator() || !player.mayBuild()
			|| !player.level().isLoaded(pos) || !SurgicalKitItem.isWrench(player.getItemInHand(hand)))
			return;
		SurgicalTablePlane.Plane plane = SurgicalTablePlane.scan(player.level(), pos);
		double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1.0d;
		if (!plane.valid() || !pos.equals(plane.source()) || plane.tiles().stream()
			.noneMatch(tile -> player.distanceToSqr(Vec3.atCenterOf(tile)) <= range * range))
			return;
		SurgicalTableBlockEntity table = SurgicalTableBlockEntity.controller(player.level(), plane);
		if (table == null)
			return;
		SurgicalWrenchRotation.Plan plan = table.planWrenchRotation(subjectId, cubeId, axis, degrees,
			straighten, single, bodies, plane);
		if (plan.valid())
			table.applyWrenchRotation(plan);
		else
			player.displayClientMessage(Component.translatable(plan.failure() == SurgicalWrenchRotation.Failure.DISCONNECTED
				? "message.create_biotech.surgical_table.rotation_disconnects"
				: "message.create_biotech.surgical_table.no_space"), true);
	}
}
