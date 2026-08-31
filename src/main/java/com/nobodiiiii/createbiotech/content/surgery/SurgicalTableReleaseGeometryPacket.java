package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalTableClientHandler;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Batched renderer-geometry handshake for cuboids released by destroyed surgical tables. */
public record SurgicalTableReleaseGeometryPacket(UUID transaction, List<CubeGeometry> cubes) {
	public static final int MAX_TRANSACTION_CUBES = 512;

	public SurgicalTableReleaseGeometryPacket {
		cubes = List.copyOf(cubes);
		if (cubes.isEmpty() || cubes.size() > MAX_TRANSACTION_CUBES)
			throw new IllegalArgumentException("Invalid released surgical cube count");
	}

	public SurgicalTableReleaseGeometryPacket(FriendlyByteBuf buffer) {
		this(buffer.readUUID(), readGeometry(buffer));
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeUUID(transaction);
		buffer.writeVarInt(cubes.size());
		for (CubeGeometry cube : cubes) {
			buffer.writeUUID(cube.subjectKey());
			buffer.writeVarInt(cube.cube());
			for (Vec3 corner : cube.corners()) {
				buffer.writeDouble(corner.x);
				buffer.writeDouble(corner.y);
				buffer.writeDouble(corner.z);
			}
		}
	}

	public void handle(ServerPlayer player) {
		SurgicalTableSupportManager.acceptGeometry(player, transaction, cubes);
	}

	private static List<CubeGeometry> readGeometry(FriendlyByteBuf buffer) {
		int count = buffer.readVarInt();
		if (count <= 0 || count > MAX_TRANSACTION_CUBES)
			throw new IllegalArgumentException("Invalid released surgical cube count " + count);
		List<CubeGeometry> geometry = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			UUID subjectKey = buffer.readUUID();
			int cube = buffer.readVarInt();
			List<Vec3> corners = new ArrayList<>(8);
			for (int corner = 0; corner < 8; corner++)
				corners.add(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
			geometry.add(new CubeGeometry(subjectKey, cube, corners));
		}
		return List.copyOf(geometry);
	}

	public record ClientBoundRequest(UUID transaction, BlockPos origin, List<SubjectCubes> subjects) {
		public ClientBoundRequest {
			origin = origin.immutable();
			subjects = List.copyOf(subjects);
			int total = subjects.stream().mapToInt(subject -> subject.cubes().size()).sum();
			if (subjects.isEmpty() || subjects.size() > MAX_TRANSACTION_CUBES
				|| total <= 0 || total > MAX_TRANSACTION_CUBES)
				throw new IllegalArgumentException("Invalid released surgical geometry request");
		}

		public ClientBoundRequest(FriendlyByteBuf buffer) {
			this(buffer.readUUID(), buffer.readBlockPos(), readSubjects(buffer));
		}

		public void write(FriendlyByteBuf buffer) {
			buffer.writeUUID(transaction);
			buffer.writeBlockPos(origin);
			buffer.writeVarInt(subjects.size());
			for (SubjectCubes subject : subjects) {
				buffer.writeUUID(subject.subjectKey());
				buffer.writeVarInt(subject.cubes().size());
				for (int cube : subject.cubes())
					buffer.writeVarInt(cube);
			}
		}

		public void handle(LocalPlayer player) {
			SurgicalTableClientHandler.reportReleasedGeometry(this);
		}

		private static List<SubjectCubes> readSubjects(FriendlyByteBuf buffer) {
			int count = buffer.readVarInt();
			if (count <= 0 || count > MAX_TRANSACTION_CUBES)
				throw new IllegalArgumentException("Invalid released surgical subject count " + count);
			List<SubjectCubes> subjects = new ArrayList<>(count);
			int total = 0;
			for (int index = 0; index < count; index++) {
				UUID subjectKey = buffer.readUUID();
				int cubeCount = buffer.readVarInt();
				if (cubeCount <= 0 || total + cubeCount > MAX_TRANSACTION_CUBES)
					throw new IllegalArgumentException("Invalid released surgical subject cube count " + cubeCount);
				List<Integer> cubes = new ArrayList<>(cubeCount);
				for (int cube = 0; cube < cubeCount; cube++)
					cubes.add(buffer.readVarInt());
				total += cubeCount;
				subjects.add(new SubjectCubes(subjectKey, cubes));
			}
			return List.copyOf(subjects);
		}
	}

	public record SubjectCubes(UUID subjectKey, List<Integer> cubes) {
		public SubjectCubes {
			cubes = List.copyOf(cubes);
			if (cubes.isEmpty() || cubes.size() > MAX_TRANSACTION_CUBES
				|| cubes.stream().anyMatch(cube -> cube == null || cube < 0))
				throw new IllegalArgumentException("Invalid released surgical subject cubes");
		}
	}

	public record CubeGeometry(UUID subjectKey, int cube, List<Vec3> corners) {
		public CubeGeometry {
			corners = List.copyOf(corners);
			if (subjectKey == null || cube < 0 || corners.size() != 8)
				throw new IllegalArgumentException("Invalid released surgical cube geometry");
		}
	}
}
