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
import net.minecraft.world.phys.Vec3;

/** Server-validated second click for gluing two surgical components at exact hit points. */
public record SurgicalTableGluePacket(BlockPos pos, InteractionHand hand, Endpoint first, Endpoint second,
	SurgicalLayPose targetPose, List<Move> moves, List<AnchorMove> anchorMoves,
	SurgicalGlueTransform replayTransform) {
	public SurgicalTableGluePacket {
		targetPose = targetPose == null ? SurgicalLayPose.IDENTITY : targetPose;
		moves = moves == null ? List.of() : List.copyOf(moves);
		anchorMoves = anchorMoves == null ? List.of() : List.copyOf(anchorMoves);
		replayTransform = replayTransform == null ? SurgicalGlueTransform.IDENTITY : replayTransform;
		if (moves.size() > SurgicalAssembly.MAX_SOURCES || anchorMoves.size() > SurgicalAssembly.MAX_SOURCES)
			throw new IllegalArgumentException("Too many surgical glue moves");
	}

	public SurgicalTableGluePacket(FriendlyByteBuf buffer) {
		this(buffer.readBlockPos(), buffer.readEnum(InteractionHand.class), Endpoint.read(buffer), Endpoint.read(buffer),
			SurgicalLayPose.read(buffer), readMoves(buffer), readAnchorMoves(buffer),
			SurgicalGlueTransform.read(buffer));
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeBlockPos(pos);
		buffer.writeEnum(hand);
		first.write(buffer);
		second.write(buffer);
		targetPose.write(buffer);
		buffer.writeVarInt(moves.size());
		for (Move move : moves)
			move.write(buffer);
		buffer.writeVarInt(anchorMoves.size());
		for (AnchorMove move : anchorMoves)
			move.write(buffer);
		replayTransform.write(buffer);
	}

	public void handle(ServerPlayer player) {
		if (player == null || player.isSpectator() || !player.mayBuild() || !player.level().isLoaded(pos)
			|| !first.valid() || !second.valid())
			return;
		ItemStack held = player.getItemInHand(hand);
		if (!SurgicalKitItem.isGlue(held))
			return;

		double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1.0d;
		SurgicalTablePlane.Plane plane = SurgicalTablePlane.scan(player.level(), pos);
		if (!plane.valid() || !pos.equals(plane.source()) || plane.tiles().stream()
			.noneMatch(tile -> player.distanceToSqr(Vec3.atCenterOf(tile)) <= range * range)
			|| !hitOnPlane(pos, first.hit, plane) || !hitOnPlane(pos, second.hit, plane))
			return;
		SurgicalTableBlockEntity table = SurgicalTableBlockEntity.controller(player.level(), plane);
		if (table == null)
			return;
		SurgicalSubject firstSubject = table.getSubject(first.subjectId);
		SurgicalSubject secondSubject = table.getSubject(second.subjectId);
		if (firstSubject == null || secondSubject == null || !targetPose.equals(secondSubject.layPose())
			|| !firstSubject.initializeOrMatchTopology(first.observedCubeCount, first.seams)
			|| !secondSubject.initializeOrMatchTopology(second.observedCubeCount, second.seams))
			return;
		if (table.glueComponents(player, held, hand, first.subjectId, first.cubeId,
			second.subjectId, second.cubeId, targetPose, moves, anchorMoves, replayTransform,
			second.contact, plane,
			first.layout, second.layout))
			player.displayClientMessage(Component.translatable(
				"message.create_biotech.surgical_table.glue_success"), true);
	}

	static List<Move> readMoves(FriendlyByteBuf buffer) {
		int count = buffer.readVarInt();
		if (count < 0 || count > SurgicalAssembly.MAX_SOURCES)
			throw new IllegalArgumentException("Invalid surgical glue move count " + count);
		List<Move> moves = new ArrayList<>(count);
		int translations = 0;
		for (int index = 0; index < count; index++) {
			Move move = Move.read(buffer);
			translations += move.translations.size();
			if (translations > SurgicalAssembly.MAX_CUBES)
				throw new IllegalArgumentException("Oversized surgical glue move");
			moves.add(move);
		}
		return List.copyOf(moves);
	}

	static List<AnchorMove> readAnchorMoves(FriendlyByteBuf buffer) {
		int count = buffer.readVarInt();
		if (count < 0 || count > SurgicalAssembly.MAX_SOURCES)
			throw new IllegalArgumentException("Invalid surgical glue anchor move count " + count);
		List<AnchorMove> moves = new ArrayList<>(count);
		int translations = 0;
		for (int index = 0; index < count; index++) {
			AnchorMove move = AnchorMove.read(buffer);
			translations += move.translations.size();
			if (translations > SurgicalAssembly.MAX_CUBES)
				throw new IllegalArgumentException("Oversized surgical glue anchor move");
			moves.add(move);
		}
		return List.copyOf(moves);
	}

	public record Move(int subjectId, List<CubeTranslation> translations,
		SurgicalTableLayout.Proposal layout) {
		public Move {
			translations = List.copyOf(translations);
			layout = layout == null ? SurgicalTableLayout.Proposal.EMPTY : layout;
		}

		void write(FriendlyByteBuf buffer) {
			buffer.writeVarInt(subjectId);
			buffer.writeVarInt(translations.size());
			for (CubeTranslation translation : translations) {
				buffer.writeVarInt(translation.cubeId);
				buffer.writeDouble(translation.offset.x);
				buffer.writeDouble(translation.offset.y);
				buffer.writeDouble(translation.offset.z);
				translation.rotation.write(buffer);
			}
			Endpoint.writeLayout(buffer, layout);
		}

		static Move read(FriendlyByteBuf buffer) {
			int subjectId = buffer.readVarInt();
			int count = buffer.readVarInt();
			if (count < 0 || count > SurgicalAssembly.MAX_CUBES)
				throw new IllegalArgumentException("Invalid surgical glue translation count " + count);
			List<CubeTranslation> translations = new ArrayList<>(count);
			for (int index = 0; index < count; index++)
				translations.add(new CubeTranslation(buffer.readVarInt(),
					new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
					SurgicalCubeRotation.read(buffer)));
			return new Move(subjectId, translations, Endpoint.readLayout(buffer));
		}
	}

	public record AnchorMove(int subjectId, List<CubeTranslation> translations) {
		public AnchorMove {
			translations = List.copyOf(translations);
		}

		void write(FriendlyByteBuf buffer) {
			buffer.writeVarInt(subjectId);
			buffer.writeVarInt(translations.size());
			for (CubeTranslation translation : translations) {
				buffer.writeVarInt(translation.cubeId);
				buffer.writeDouble(translation.offset.x);
				buffer.writeDouble(translation.offset.y);
				buffer.writeDouble(translation.offset.z);
				translation.rotation.write(buffer);
			}
		}

		static AnchorMove read(FriendlyByteBuf buffer) {
			int subjectId = buffer.readVarInt();
			int count = buffer.readVarInt();
			if (count < 0 || count > SurgicalAssembly.MAX_CUBES)
				throw new IllegalArgumentException("Invalid surgical glue anchor translation count " + count);
			List<CubeTranslation> translations = new ArrayList<>(count);
			for (int index = 0; index < count; index++)
				translations.add(new CubeTranslation(buffer.readVarInt(),
					new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
					SurgicalCubeRotation.read(buffer)));
			return new AnchorMove(subjectId, translations);
		}
	}

	public record CubeTranslation(int cubeId, Vec3 offset, SurgicalCubeRotation rotation) {
		public CubeTranslation {
			rotation = rotation == null ? SurgicalCubeRotation.IDENTITY : rotation;
		}

		public CubeTranslation(int cubeId, Vec3 offset) {
			this(cubeId, offset, SurgicalCubeRotation.IDENTITY);
		}

		public boolean valid() {
			double bound = SurgicalTablePlane.MAX_TILES + 2.0d;
			return cubeId >= 0 && offset != null && Double.isFinite(offset.x)
				&& Double.isFinite(offset.y) && Double.isFinite(offset.z)
				&& Math.abs(offset.x) <= bound && Math.abs(offset.y) <= bound
				&& Math.abs(offset.z) <= bound && rotation != null;
		}
	}

	static boolean hitOnPlane(BlockPos pos, Vec3 localHit, SurgicalTablePlane.Plane plane) {
		double worldX = pos.getX() + localHit.x;
		double worldZ = pos.getZ() + localHit.z;
		return localHit.y >= -64.0d && localHit.y <= 64.0d
			&& plane.workArea().contains(worldX, worldZ, worldX, worldZ, 1.0e-6d);
	}

	public record Endpoint(int subjectId, int cubeId, int observedCubeCount,
		List<SurgicalAssembly.Seam> seams, Vec3 hit, SurgicalTableLayout.Proposal layout,
		SurgicalGlueContact contact) {
		public Endpoint {
			seams = List.copyOf(seams);
			layout = layout == null ? SurgicalTableLayout.Proposal.EMPTY : layout;
		}

		boolean valid() {
			double bound = SurgicalTablePlane.MAX_TILES + 2.0d;
			return subjectId >= 0 && cubeId >= 0 && cubeId < observedCubeCount
				&& SurgicalAssembly.validTopology(observedCubeCount, seams)
				&& contact != null
				&& hit != null && Double.isFinite(hit.x) && Double.isFinite(hit.y) && Double.isFinite(hit.z)
				&& Math.abs(hit.x) <= bound && Math.abs(hit.y) <= bound && Math.abs(hit.z) <= bound;
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
			buffer.writeDouble(hit.x);
			buffer.writeDouble(hit.y);
			buffer.writeDouble(hit.z);
			writeLayout(buffer, layout);
			contact.write(buffer);
		}

		static Endpoint read(FriendlyByteBuf buffer) {
			int subjectId = buffer.readVarInt();
			int cubeId = buffer.readVarInt();
			int cubeCount = buffer.readVarInt();
			int seamCount = buffer.readVarInt();
			if (seamCount < 0 || seamCount > SurgicalAssembly.MAX_SEAMS)
				throw new IllegalArgumentException("Invalid surgical seam count " + seamCount);
			List<SurgicalAssembly.Seam> seams = new ArrayList<>(seamCount);
			for (int index = 0; index < seamCount; index++)
				seams.add(SurgicalAssembly.Seam.of(buffer.readVarInt(), buffer.readVarInt()));
			return new Endpoint(subjectId, cubeId, cubeCount, seams,
				new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()), readLayout(buffer),
				SurgicalGlueContact.read(buffer));
		}

		static void writeLayout(FriendlyByteBuf buffer, SurgicalTableLayout.Proposal layout) {
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

		static SurgicalTableLayout.Proposal readLayout(FriendlyByteBuf buffer) {
			int offsetCount = buffer.readVarInt();
			if (offsetCount < 0 || offsetCount > SurgicalAssembly.MAX_CUBES)
				throw new IllegalArgumentException("Invalid surgical offset count " + offsetCount);
			List<SurgicalTableLayout.CubeOffset> offsets = new ArrayList<>(offsetCount);
			for (int index = 0; index < offsetCount; index++)
				offsets.add(new SurgicalTableLayout.CubeOffset(buffer.readVarInt(), buffer.readDouble(),
					buffer.readDouble(), buffer.readDouble()));
			int footprintCount = buffer.readVarInt();
			if (footprintCount < 0 || footprintCount > SurgicalAssembly.MAX_CUBES)
				throw new IllegalArgumentException("Invalid surgical footprint count " + footprintCount);
			List<SurgicalTableLayout.Footprint> footprints = new ArrayList<>(footprintCount);
			for (int index = 0; index < footprintCount; index++)
				footprints.add(new SurgicalTableLayout.Footprint(buffer.readVarInt(), buffer.readDouble(),
					buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readInt(), buffer.readInt()));
			return new SurgicalTableLayout.Proposal(offsets, footprints);
		}
	}
}
