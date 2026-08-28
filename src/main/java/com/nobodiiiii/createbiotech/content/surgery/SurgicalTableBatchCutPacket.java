package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Server-validated, multi-subject layout for shift-shears cutting every edge around one cube. */
public record SurgicalTableBatchCutPacket(BlockPos pos, InteractionHand hand, int subjectId, int cubeId,
	int observedCubeCount, List<SurgicalAssembly.Seam> seams, SurgicalTableLayout.Proposal layout,
	List<Vec3> groupDeltas) {

	public SurgicalTableBatchCutPacket {
		seams = List.copyOf(seams);
		layout = layout == null ? SurgicalTableLayout.Proposal.EMPTY : layout;
		groupDeltas = groupDeltas == null ? List.of() : List.copyOf(groupDeltas);
		if (groupDeltas.size() > SurgicalAssembly.MAX_CUBES)
			throw new IllegalArgumentException("Too many surgical batch-cut groups");
	}

	public SurgicalTableBatchCutPacket(FriendlyByteBuf buffer) {
		this(buffer.readBlockPos(), buffer.readEnum(InteractionHand.class), buffer.readVarInt(),
			buffer.readVarInt(), buffer.readVarInt(), readSeams(buffer), readLayout(buffer), readDeltas(buffer));
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
		writeLayout(buffer, layout);
		buffer.writeVarInt(groupDeltas.size());
		for (Vec3 delta : groupDeltas) {
			buffer.writeDouble(delta.x);
			buffer.writeDouble(delta.z);
		}
	}

	public void handle(ServerPlayer player) {
		if (player == null || player.isSpectator() || !player.mayBuild() || !player.level().isLoaded(pos)
			|| subjectId < 0 || cubeId < 0 || cubeId >= observedCubeCount
			|| !SurgicalAssembly.validTopology(observedCubeCount, seams))
			return;
		ItemStack held = player.getItemInHand(hand);
		if (!held.is(Items.SHEARS))
			return;

		double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1.0d;
		SurgicalTablePlane.Plane plane = SurgicalTablePlane.scan(player.level(), pos);
		if (!plane.valid() || !pos.equals(plane.source()) || plane.tiles().stream()
			.noneMatch(tile -> player.distanceToSqr(Vec3.atCenterOf(tile)) <= range * range))
			return;
		SurgicalTableBlockEntity table = SurgicalTableBlockEntity.controller(player.level(), plane);
		if (table != null && !table.cutCubeConnections(player, held, hand, subjectId, cubeId,
			observedCubeCount, seams, plane, layout, groupDeltas))
			player.displayClientMessage(Component.translatable(
				"message.create_biotech.surgical_table.no_space"), true);
	}

	private static List<SurgicalAssembly.Seam> readSeams(FriendlyByteBuf buffer) {
		int count = buffer.readVarInt();
		if (count < 0 || count > SurgicalAssembly.MAX_SEAMS)
			throw new IllegalArgumentException("Invalid surgical batch-cut seam count " + count);
		List<SurgicalAssembly.Seam> seams = new ArrayList<>(count);
		for (int index = 0; index < count; index++)
			seams.add(SurgicalAssembly.Seam.of(buffer.readVarInt(), buffer.readVarInt()));
		return List.copyOf(seams);
	}

	private static List<Vec3> readDeltas(FriendlyByteBuf buffer) {
		int count = buffer.readVarInt();
		if (count < 0 || count > SurgicalAssembly.MAX_CUBES)
			throw new IllegalArgumentException("Invalid surgical batch-cut group count " + count);
		List<Vec3> deltas = new ArrayList<>(count);
		for (int index = 0; index < count; index++)
			deltas.add(new Vec3(buffer.readDouble(), 0.0d, buffer.readDouble()));
		return List.copyOf(deltas);
	}

	private static void writeLayout(FriendlyByteBuf buffer, SurgicalTableLayout.Proposal layout) {
		buffer.writeVarInt(layout.offsets().size());
		for (SurgicalTableLayout.CubeOffset offset : layout.offsets()) {
			buffer.writeVarInt(offset.cubeId());
			buffer.writeDouble(offset.x());
			buffer.writeDouble(offset.y());
			buffer.writeDouble(offset.z());
		}
		buffer.writeVarInt(layout.footprints().size());
		for (SurgicalTableLayout.Footprint footprint : layout.footprints()) {
			buffer.writeVarInt(footprint.componentRoot());
			buffer.writeDouble(footprint.minX());
			buffer.writeDouble(footprint.minZ());
			buffer.writeDouble(footprint.maxX());
			buffer.writeDouble(footprint.maxZ());
			buffer.writeInt(footprint.gridX());
			buffer.writeInt(footprint.gridZ());
		}
	}

	private static SurgicalTableLayout.Proposal readLayout(FriendlyByteBuf buffer) {
		int offsetCount = buffer.readVarInt();
		if (offsetCount < 0 || offsetCount > SurgicalAssembly.MAX_CUBES)
			throw new IllegalArgumentException("Invalid surgical batch-cut offset count " + offsetCount);
		List<SurgicalTableLayout.CubeOffset> offsets = new ArrayList<>(offsetCount);
		for (int index = 0; index < offsetCount; index++)
			offsets.add(new SurgicalTableLayout.CubeOffset(buffer.readVarInt(), buffer.readDouble(),
				buffer.readDouble(), buffer.readDouble()));
		int footprintCount = buffer.readVarInt();
		if (footprintCount < 0 || footprintCount > SurgicalAssembly.MAX_CUBES)
			throw new IllegalArgumentException("Invalid surgical batch-cut footprint count " + footprintCount);
		List<SurgicalTableLayout.Footprint> footprints = new ArrayList<>(footprintCount);
		for (int index = 0; index < footprintCount; index++)
			footprints.add(new SurgicalTableLayout.Footprint(buffer.readVarInt(), buffer.readDouble(),
				buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readInt(), buffer.readInt()));
		return new SurgicalTableLayout.Proposal(offsets, footprints);
	}
}
