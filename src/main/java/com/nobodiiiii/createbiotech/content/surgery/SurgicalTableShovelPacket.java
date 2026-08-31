package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Shovels the complete connected group containing one client-selected model cube. */
public record SurgicalTableShovelPacket(BlockPos pos, InteractionHand hand, int subjectId, int cubeId,
	int observedCubeCount, List<SurgicalAssembly.Seam> seams, double volume) {

	public SurgicalTableShovelPacket {
		seams = List.copyOf(seams);
	}

	public SurgicalTableShovelPacket(FriendlyByteBuf buffer) {
		this(buffer.readBlockPos(), buffer.readEnum(InteractionHand.class), buffer.readVarInt(),
			buffer.readVarInt(), buffer.readVarInt(), readSeams(buffer), buffer.readDouble());
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeBlockPos(pos);
		buffer.writeEnum(hand);
		buffer.writeVarInt(subjectId);
		buffer.writeVarInt(cubeId);
		buffer.writeVarInt(observedCubeCount);
		buffer.writeVarInt(seams.size());
		for (SurgicalAssembly.Seam seam : seams) {
			buffer.writeVarInt(seam.first());
			buffer.writeVarInt(seam.second());
		}
		buffer.writeDouble(volume);
	}

	public void handle(ServerPlayer player) {
		if (player == null || player.isSpectator() || !player.mayBuild() || subjectId < 0 || cubeId < 0
			|| cubeId >= observedCubeCount || !SurgicalAssembly.validTopology(observedCubeCount, seams)
			|| !Double.isFinite(volume) || volume < 0.0d || !player.level().isLoaded(pos))
			return;

		double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1.0d;
		SurgicalTablePlane.Plane plane = SurgicalTablePlane.scan(player.level(), pos);
		if (!plane.valid() || !pos.equals(plane.source()) || plane.tiles().stream()
			.noneMatch(tile -> player.distanceToSqr(Vec3.atCenterOf(tile)) <= range * range))
			return;
		SurgicalTableBlockEntity table = SurgicalTableBlockEntity.controller(player.level(), plane);
		if (table == null)
			return;

		ItemStack held = player.getItemInHand(hand);
		if (SurgicalKitItem.isShovel(held))
			table.shovelConnectedGroup(player, held, hand, subjectId, cubeId,
				observedCubeCount, seams, volume);
	}

	private static List<SurgicalAssembly.Seam> readSeams(FriendlyByteBuf buffer) {
		int size = buffer.readVarInt();
		if (size < 0 || size > SurgicalAssembly.MAX_SEAMS)
			throw new IllegalArgumentException("Invalid surgical seam count " + size);
		List<SurgicalAssembly.Seam> seams = new ArrayList<>(size);
		for (int index = 0; index < size; index++)
			seams.add(SurgicalAssembly.Seam.of(buffer.readVarInt(), buffer.readVarInt()));
		return seams;
	}
}
