package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

import com.nobodiiiii.createbiotech.network.CBPackets;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Model geometry is captured by the client; membership, transforms, fit and contact are checked by the server. */
public record SurgicalTableCubeRotationPacket(BlockPos pos, UUID requestId, InteractionHand hand, UUID subjectId,
	int cubeId, SurgicalCubeRotation groupRotation, SurgicalCubeRotation selectedRotation,
	Map<Integer, BitSet> group,
	List<SurgicalWrenchRotation.Body> bodies, Vec3 groupTranslation, Vec3 selectedTranslation) {
	public SurgicalTableCubeRotationPacket(FriendlyByteBuf buffer) {
		this(buffer.readBlockPos(), buffer.readUUID(), buffer.readEnum(InteractionHand.class), buffer.readUUID(),
			buffer.readVarInt(), SurgicalCubeRotation.read(buffer), SurgicalCubeRotation.read(buffer),
			readGroup(buffer), readBodies(buffer), readVector(buffer), readVector(buffer));
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

	private static Map<Integer, BitSet> readGroup(FriendlyByteBuf buffer) {
		int count = count(buffer, SurgicalAssembly.MAX_SOURCES);
		Map<Integer, BitSet> group = new HashMap<>();
		for (int i = 0; i < count; i++) {
			int id = buffer.readVarInt();
			int size = count(buffer, SurgicalAssembly.MAX_CUBES);
			BitSet cubes = new BitSet();
			for (int j = 0; j < size; j++) {
				int cube = buffer.readVarInt();
				if (cube < 0 || cube >= SurgicalAssembly.MAX_CUBES || cubes.get(cube))
					throw new IllegalArgumentException("Invalid surgical rotation group");
				cubes.set(cube);
			}
			if (group.putIfAbsent(id, cubes) != null)
				throw new IllegalArgumentException("Duplicate surgical rotation body");
		}
		return Map.copyOf(group);
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
		buffer.writeUUID(requestId);
		buffer.writeEnum(hand);
		buffer.writeUUID(subjectId);
		buffer.writeVarInt(cubeId);
		groupRotation.write(buffer);
		selectedRotation.write(buffer);
		buffer.writeVarInt(group.size());
		for (Map.Entry<Integer, BitSet> entry : group.entrySet()) {
			buffer.writeVarInt(entry.getKey());
			buffer.writeVarInt(entry.getValue().cardinality());
			for (int cube = entry.getValue().nextSetBit(0); cube >= 0; cube = entry.getValue().nextSetBit(cube + 1))
				buffer.writeVarInt(cube);
		}
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
		writeVector(buffer, groupTranslation);
		writeVector(buffer, selectedTranslation);
	}

	public void handle(ServerPlayer player) {
		if (player != null)
			CBPackets.sendToPlayer(new Result(requestId, apply(player)), player);
	}

	private boolean apply(ServerPlayer player) {
		if (player == null || player.isSpectator() || !player.mayBuild()
			|| !player.level().isLoaded(pos) || !SurgicalKitItem.isWrench(player.getItemInHand(hand)))
			return false;
		SurgicalTablePlane.Plane plane = SurgicalTablePlane.scan(player.level(), pos);
		double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1.0d;
		if (!plane.valid() || !pos.equals(plane.source()) || plane.tiles().stream()
			.noneMatch(tile -> player.distanceToSqr(Vec3.atCenterOf(tile)) <= range * range))
			return false;
		SurgicalTableBlockEntity table = SurgicalTableBlockEntity.controller(player.level(), plane);
		if (table == null)
			return false;
		SurgicalWrenchRotation.Plan plan = table.planWrenchRotation(subjectId, cubeId, groupRotation,
			selectedRotation, group, bodies, plane, groupTranslation, selectedTranslation);
		if (plan.valid())
			table.applyWrenchRotation(plan);
		else
			player.displayClientMessage(Component.translatable(plan.failure() == SurgicalWrenchRotation.Failure.JOINT_DISCONNECTED
				? "message.create_biotech.surgical_table.rotation_breaks_joint"
				: plan.failure() == SurgicalWrenchRotation.Failure.DISCONNECTED
				? "message.create_biotech.surgical_table.rotation_disconnects"
				: "message.create_biotech.surgical_table.no_space"), true);
		return plan.valid();
	}

	public record Result(UUID requestId, boolean accepted) {
		public Result(FriendlyByteBuf buffer) { this(buffer.readUUID(), buffer.readBoolean()); }
		public void write(FriendlyByteBuf buffer) {
			buffer.writeUUID(requestId);
			buffer.writeBoolean(accepted);
		}
	}

}
