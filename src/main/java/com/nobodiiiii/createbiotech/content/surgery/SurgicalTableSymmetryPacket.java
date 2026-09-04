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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Server-validated third-stage commit after choosing a centre plane for an installed glue joint. */
public record SurgicalTableSymmetryPacket(BlockPos pos, InteractionHand hand,
	SurgicalTableGluePacket.Endpoint first, SurgicalTableGluePacket.Endpoint mirroredAnchor,
	Reference reference, SurgicalLayPose targetPose, List<SurgicalTableGluePacket.Move> moves,
	List<SurgicalTableGluePacket.AnchorMove> anchorMoves, SurgicalGlueTransform replayTransform) {
	public SurgicalTableSymmetryPacket {
		targetPose = targetPose == null ? SurgicalLayPose.IDENTITY : targetPose;
		moves = moves == null ? List.of() : List.copyOf(moves);
		anchorMoves = anchorMoves == null ? List.of() : List.copyOf(anchorMoves);
		replayTransform = replayTransform == null ? SurgicalGlueTransform.IDENTITY : replayTransform;
		if (moves.size() > SurgicalAssembly.MAX_SOURCES || anchorMoves.size() > SurgicalAssembly.MAX_SOURCES)
			throw new IllegalArgumentException("Too many surgical symmetry moves");
	}

	public SurgicalTableSymmetryPacket(FriendlyByteBuf buffer) {
		this(buffer.readBlockPos(), buffer.readEnum(InteractionHand.class),
			SurgicalTableGluePacket.Endpoint.read(buffer), SurgicalTableGluePacket.Endpoint.read(buffer),
			Reference.read(buffer), SurgicalLayPose.read(buffer), SurgicalTableGluePacket.readMoves(buffer),
			SurgicalTableGluePacket.readAnchorMoves(buffer), SurgicalGlueTransform.read(buffer));
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeBlockPos(pos);
		buffer.writeEnum(hand);
		first.write(buffer);
		mirroredAnchor.write(buffer);
		reference.write(buffer);
		targetPose.write(buffer);
		buffer.writeVarInt(moves.size());
		for (SurgicalTableGluePacket.Move move : moves)
			move.write(buffer);
		buffer.writeVarInt(anchorMoves.size());
		for (SurgicalTableGluePacket.AnchorMove move : anchorMoves)
			move.write(buffer);
		replayTransform.write(buffer);
	}

	public void handle(ServerPlayer player) {
		if (player == null || player.isSpectator() || !player.mayBuild() || !player.level().isLoaded(pos)
			|| !first.valid() || !mirroredAnchor.valid() || !reference.valid())
			return;
		ItemStack held = player.getItemInHand(hand);
		if (!SurgicalKitItem.isSymmetryWand(held))
			return;

		double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1.0d;
		SurgicalTablePlane.Plane plane = SurgicalTablePlane.scan(player.level(), pos);
		if (!plane.valid() || !pos.equals(plane.source()) || plane.tiles().stream()
			.noneMatch(tile -> player.distanceToSqr(Vec3.atCenterOf(tile)) <= range * range)
			|| !SurgicalTableGluePacket.hitOnPlane(pos, first.hit(), plane)
			|| !SurgicalTableGluePacket.hitOnPlane(pos, mirroredAnchor.hit(), plane))
			return;
		SurgicalTableBlockEntity table = SurgicalTableBlockEntity.controller(player.level(), plane);
		if (table == null)
			return;
		SurgicalSubject firstSubject = table.getSubject(first.subjectId());
		SurgicalSubject anchorSubject = table.getSubject(mirroredAnchor.subjectId());
		SurgicalSubject referenceSubject = table.getSubject(reference.subjectId());
		if (firstSubject == null || anchorSubject == null || referenceSubject == null
			|| !targetPose.equals(anchorSubject.layPose())
			|| !firstSubject.matchesObservedTopology(first.observedCubeCount(), first.seams())
			|| !anchorSubject.matchesObservedTopology(mirroredAnchor.observedCubeCount(), mirroredAnchor.seams())
			|| !referenceSubject.matchesObservedTopology(reference.observedCubeCount(), reference.seams()))
			return;

		if (table.symmetryGlueComponents(player, held, hand, first.subjectId(), first.cubeId(),
			mirroredAnchor.subjectId(), mirroredAnchor.cubeId(), reference.subjectId(), reference.cubeId(),
			reference.anchorSubjectKey(), reference.anchorCubeId(), reference.singleCube(), targetPose,
			moves, anchorMoves, replayTransform, mirroredAnchor.contact(), plane,
			first.layout(), mirroredAnchor.layout()))
			player.displayClientMessage(Component.translatable(
				"message.create_biotech.surgical_table.symmetry_success"), true);
	}

	public record Reference(int subjectId, int cubeId, int observedCubeCount,
		List<SurgicalAssembly.Seam> seams, UUID anchorSubjectKey, int anchorCubeId, boolean singleCube) {
		public Reference {
			seams = seams == null ? List.of() : List.copyOf(seams);
		}

		boolean valid() {
			return subjectId >= 0 && cubeId >= 0 && cubeId < observedCubeCount && anchorSubjectKey != null
				&& anchorCubeId >= 0 && SurgicalAssembly.validTopology(observedCubeCount, seams);
		}

		void write(FriendlyByteBuf buffer) {
			buffer.writeVarInt(subjectId);
			buffer.writeVarInt(cubeId);
			buffer.writeVarInt(observedCubeCount);
			buffer.writeVarInt(seams.size());
			for (SurgicalAssembly.Seam seam : seams) {
				buffer.writeVarInt(seam.first());
				buffer.writeVarInt(seam.second());
			}
			buffer.writeUUID(anchorSubjectKey);
			buffer.writeVarInt(anchorCubeId);
			buffer.writeBoolean(singleCube);
		}

		static Reference read(FriendlyByteBuf buffer) {
			int subjectId = buffer.readVarInt();
			int cubeId = buffer.readVarInt();
			int cubeCount = buffer.readVarInt();
			int seamCount = buffer.readVarInt();
			if (seamCount < 0 || seamCount > SurgicalAssembly.MAX_SEAMS)
				throw new IllegalArgumentException("Invalid surgical symmetry seam count " + seamCount);
			List<SurgicalAssembly.Seam> seams = new ArrayList<>(seamCount);
			for (int index = 0; index < seamCount; index++)
				seams.add(SurgicalAssembly.Seam.of(buffer.readVarInt(), buffer.readVarInt()));
			return new Reference(subjectId, cubeId, cubeCount, seams, buffer.readUUID(),
				buffer.readVarInt(), buffer.readBoolean());
		}
	}
}
