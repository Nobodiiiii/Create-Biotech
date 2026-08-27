package com.nobodiiiii.createbiotech.content.slimemimic;

import java.util.ArrayList;
import java.util.List;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Reports renderer-resolved cuboids for a dying mimic to its authoritative server. */
public record SlimeMimicDeathGeometryPacket(int entityId, List<SlimeMimicCubeGeometry> cubes) {
	public SlimeMimicDeathGeometryPacket {
		cubes = List.copyOf(cubes);
	}

	public SlimeMimicDeathGeometryPacket(FriendlyByteBuf buffer) {
		this(buffer.readVarInt(), readCubes(buffer));
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeVarInt(entityId);
		buffer.writeVarInt(cubes.size());
		for (SlimeMimicCubeGeometry cube : cubes) {
			buffer.writeVarInt(cube.source());
			buffer.writeVarInt(cube.cube());
			for (Vec3 corner : cube.corners()) {
				buffer.writeDouble(corner.x);
				buffer.writeDouble(corner.y);
				buffer.writeDouble(corner.z);
			}
		}
	}

	public void handle(ServerPlayer player) {
		SlimeMimicDeathHandler.acceptGeometry(player, entityId, cubes);
	}

	private static List<SlimeMimicCubeGeometry> readCubes(FriendlyByteBuf buffer) {
		int count = buffer.readVarInt();
		if (count <= 0 || count > SurgicalAssembly.MAX_CUBES)
			throw new IllegalArgumentException("Invalid slime-mimic death cube count " + count);
		List<SlimeMimicCubeGeometry> cubes = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			int source = buffer.readVarInt();
			int cube = buffer.readVarInt();
			List<Vec3> corners = new ArrayList<>(8);
			for (int corner = 0; corner < 8; corner++)
				corners.add(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
			cubes.add(new SlimeMimicCubeGeometry(source, cube, corners));
		}
		return cubes;
	}
}
