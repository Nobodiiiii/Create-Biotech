package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Server-validated placement data for both ordinary and laid-out multi-source bodies. */
public record SurgicalTablePlacementPacket(BlockPos pos, InteractionHand hand, Direction placementFacing,
	 double originOffsetX, double originOffsetZ, SurgicalLayPose layPose,
	 SurgicalTableLayout.Proposal envelope,
	 int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
	 BitSet headCubes,
	 List<SurgicalTableLayout.Footprint> componentFootprints,
	 List<SurgicalTableLayout.Proposal> sourceLayouts) {

	public SurgicalTablePlacementPacket {
		placementFacing = placementFacing == null ? Direction.NORTH : placementFacing;
		layPose = layPose == null ? SurgicalLayPose.IDENTITY : layPose;
		envelope = envelope == null ? SurgicalTableLayout.Proposal.EMPTY : envelope;
		observedSeams = observedSeams == null ? List.of() : List.copyOf(observedSeams);
		headCubes = headCubes == null ? new BitSet() : (BitSet) headCubes.clone();
		componentFootprints = componentFootprints == null ? List.of() : List.copyOf(componentFootprints);
		sourceLayouts = sourceLayouts == null ? List.of() : List.copyOf(sourceLayouts);
		if (observedCubeCount < 0 || observedCubeCount > SurgicalAssembly.MAX_CUBES
			|| observedSeams.size() > SurgicalAssembly.MAX_SEAMS
			|| componentFootprints.size() > SurgicalAssembly.MAX_CUBES
			|| headCubes.length() > observedCubeCount)
			throw new IllegalArgumentException("Oversized discovered surgical placement topology");
		if (sourceLayouts.size() > SurgicalAssembly.MAX_SOURCES)
			throw new IllegalArgumentException("Too many surgical placement sources " + sourceLayouts.size());
	}

	@Override
	public BitSet headCubes() {
		return (BitSet) headCubes.clone();
	}

	public SurgicalTablePlacementPacket(FriendlyByteBuf buffer) {
		this(buffer.readBlockPos(), buffer.readEnum(InteractionHand.class), buffer.readEnum(Direction.class),
			buffer.readDouble(),
			buffer.readDouble(), SurgicalLayPose.read(buffer), readLayout(buffer), buffer.readVarInt(),
			readSeams(buffer), readHeadCubes(buffer), readFootprints(buffer), readSourceLayouts(buffer));
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeBlockPos(pos);
		buffer.writeEnum(hand);
		buffer.writeEnum(placementFacing);
		buffer.writeDouble(originOffsetX);
		buffer.writeDouble(originOffsetZ);
		layPose.write(buffer);
		writeLayout(buffer, envelope);
		buffer.writeVarInt(observedCubeCount);
		buffer.writeVarInt(observedSeams.size());
		for (SurgicalAssembly.Seam seam : observedSeams) {
			buffer.writeVarInt(seam.first());
			buffer.writeVarInt(seam.second());
		}
		buffer.writeVarInt(headCubes.cardinality());
		for (int cube = headCubes.nextSetBit(0); cube >= 0; cube = headCubes.nextSetBit(cube + 1))
			buffer.writeVarInt(cube);
		writeFootprints(buffer, componentFootprints);
		buffer.writeVarInt(sourceLayouts.size());
		for (SurgicalTableLayout.Proposal sourceLayout : sourceLayouts)
			writeLayout(buffer, sourceLayout);
	}

	public void handle(ServerPlayer player) {
		if (player == null || player.isSpectator() || !player.mayBuild() || !player.level().isLoaded(pos))
			return;
		double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1.0d;
		SurgicalTablePlane.Plane plane = SurgicalTablePlane.scan(player.level(), pos);
		if (!plane.valid() || !pos.equals(plane.source()) || plane.tiles().stream()
			.noneMatch(tile -> player.distanceToSqr(Vec3.atCenterOf(tile)) <= range * range))
			return;
		SurgicalTableBlockEntity table = SurgicalTableBlockEntity.controller(player.level(), plane);
		ItemStack held = player.getItemInHand(hand);
		if (table == null || (!(held.getItem() instanceof CapturedEntityBoxItem)
			&& !SurgicalKitItem.hasTemporaryCapture(held))
			|| !CapturedEntityBoxHelper.hasCapturedEntity(held))
			return;
		SurgicalTablePlacementResult result = table.tryPlaceSubject(held, plane, placementFacing, layPose,
			originOffsetX, originOffsetZ, envelope, observedCubeCount, observedSeams,
			headCubes, componentFootprints, sourceLayouts);
		result.display(player);
	}

	private static List<SurgicalTableLayout.Proposal> readSourceLayouts(FriendlyByteBuf buffer) {
		int sourceCount = buffer.readVarInt();
		if (sourceCount < 0 || sourceCount > SurgicalAssembly.MAX_SOURCES)
			throw new IllegalArgumentException("Invalid surgical placement source count " + sourceCount);
		List<SurgicalTableLayout.Proposal> layouts = new ArrayList<>(sourceCount);
		int totalOffsets = 0;
		int totalFootprints = 0;
		for (int source = 0; source < sourceCount; source++) {
			SurgicalTableLayout.Proposal layout = readLayout(buffer);
			totalOffsets += layout.offsets().size();
			totalFootprints += layout.footprints().size();
			if (totalOffsets > SurgicalAssembly.MAX_CUBES
				|| totalFootprints > SurgicalAssembly.MAX_CUBES)
				throw new IllegalArgumentException("Oversized composite surgical placement layout");
			layouts.add(layout);
		}
		return List.copyOf(layouts);
	}

	private static List<SurgicalAssembly.Seam> readSeams(FriendlyByteBuf buffer) {
		int count = buffer.readVarInt();
		if (count < 0 || count > SurgicalAssembly.MAX_SEAMS)
			throw new IllegalArgumentException("Invalid discovered surgical placement seam count " + count);
		List<SurgicalAssembly.Seam> seams = new ArrayList<>(count);
		for (int index = 0; index < count; index++)
			seams.add(SurgicalAssembly.Seam.of(buffer.readVarInt(), buffer.readVarInt()));
		return List.copyOf(seams);
	}

	private static BitSet readHeadCubes(FriendlyByteBuf buffer) {
		int count = buffer.readVarInt();
		if (count < 0 || count > SurgicalAssembly.MAX_CUBES)
			throw new IllegalArgumentException("Invalid surgical head cube count " + count);
		BitSet heads = new BitSet();
		for (int index = 0; index < count; index++) {
			int cube = buffer.readVarInt();
			if (cube < 0 || cube >= SurgicalAssembly.MAX_CUBES || heads.get(cube))
				throw new IllegalArgumentException("Invalid surgical head cube " + cube);
			heads.set(cube);
		}
		return heads;
	}

	private static void writeFootprints(FriendlyByteBuf buffer,
		List<SurgicalTableLayout.Footprint> footprints) {
		buffer.writeVarInt(footprints.size());
		for (SurgicalTableLayout.Footprint footprint : footprints) {
			buffer.writeVarInt(footprint.componentRoot());
			buffer.writeDouble(footprint.minX());
			buffer.writeDouble(footprint.minZ());
			buffer.writeDouble(footprint.maxX());
			buffer.writeDouble(footprint.maxZ());
			buffer.writeInt(footprint.gridX());
			buffer.writeInt(footprint.gridZ());
		}
	}

	private static List<SurgicalTableLayout.Footprint> readFootprints(FriendlyByteBuf buffer) {
		int count = buffer.readVarInt();
		if (count < 0 || count > SurgicalAssembly.MAX_CUBES)
			throw new IllegalArgumentException("Invalid discovered surgical placement footprint count " + count);
		List<SurgicalTableLayout.Footprint> footprints = new ArrayList<>(count);
		for (int index = 0; index < count; index++)
			footprints.add(new SurgicalTableLayout.Footprint(buffer.readVarInt(), buffer.readDouble(),
				buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readInt(), buffer.readInt()));
		return List.copyOf(footprints);
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
			throw new IllegalArgumentException("Invalid surgical placement offset count " + offsetCount);
		List<SurgicalTableLayout.CubeOffset> offsets = new ArrayList<>(offsetCount);
		for (int index = 0; index < offsetCount; index++)
			offsets.add(new SurgicalTableLayout.CubeOffset(buffer.readVarInt(), buffer.readDouble(),
				buffer.readDouble(), buffer.readDouble()));
		int footprintCount = buffer.readVarInt();
		if (footprintCount < 0 || footprintCount > SurgicalAssembly.MAX_CUBES)
			throw new IllegalArgumentException("Invalid surgical placement footprint count " + footprintCount);
		List<SurgicalTableLayout.Footprint> footprints = new ArrayList<>(footprintCount);
		for (int index = 0; index < footprintCount; index++)
			footprints.add(new SurgicalTableLayout.Footprint(buffer.readVarInt(), buffer.readDouble(),
				buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readInt(), buffer.readInt()));
		return new SurgicalTableLayout.Proposal(offsets, footprints);
	}
}
