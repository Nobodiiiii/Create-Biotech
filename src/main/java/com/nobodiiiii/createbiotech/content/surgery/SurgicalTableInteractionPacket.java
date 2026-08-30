package com.nobodiiiii.createbiotech.content.surgery;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

public record SurgicalTableInteractionPacket(BlockPos pos, InteractionHand hand, Action action,
	int subjectId, int targetId, int observedCubeCount, List<SurgicalAssembly.Seam> seams,
	double originOffsetX, double originOffsetZ, SurgicalTableLayout.Proposal layout,
	@Nullable SurgicalAssembly.BodyBounds bodyBounds,
	@Nullable SurgicalAssembly.HitboxGeometry hitboxGeometry,
	@Nullable SurgicalAssembly.AttackGeometry attackGeometry) {

	public SurgicalTableInteractionPacket {
		seams = List.copyOf(seams);
		layout = layout == null ? SurgicalTableLayout.Proposal.EMPTY : layout;
	}

	public SurgicalTableInteractionPacket(FriendlyByteBuf buffer) {
		this(buffer.readBlockPos(), buffer.readEnum(InteractionHand.class), buffer.readEnum(Action.class),
			buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), readSeams(buffer), buffer.readDouble(),
			buffer.readDouble(), readLayout(buffer), readBodyBounds(buffer), readHitboxGeometry(buffer),
			readAttackGeometry(buffer));
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeBlockPos(pos);
		buffer.writeEnum(hand);
		buffer.writeEnum(action);
		buffer.writeVarInt(subjectId);
		buffer.writeVarInt(targetId);
		buffer.writeVarInt(observedCubeCount);
		buffer.writeVarInt(seams.size());
		for (SurgicalAssembly.Seam seam : seams) {
			buffer.writeVarInt(seam.first());
			buffer.writeVarInt(seam.second());
		}
		buffer.writeDouble(originOffsetX);
		buffer.writeDouble(originOffsetZ);
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
		buffer.writeBoolean(bodyBounds != null);
		if (bodyBounds != null) {
			buffer.writeFloat(bodyBounds.width());
			buffer.writeFloat(bodyBounds.height());
			buffer.writeFloat(bodyBounds.depth());
			buffer.writeFloat(bodyBounds.centerX());
			buffer.writeFloat(bodyBounds.minY());
			buffer.writeFloat(bodyBounds.centerZ());
			buffer.writeFloat(bodyBounds.eyeHeight());
			buffer.writeFloat(bodyBounds.legLength());
			buffer.writeVarInt(bodyBounds.groundedLegCount());
			buffer.writeVarInt(bodyBounds.groundedKneeCount());
			buffer.writeFloat(bodyBounds.legVolumeRatio());
		}
		buffer.writeBoolean(hitboxGeometry != null);
		if (hitboxGeometry != null)
			hitboxGeometry.write(buffer);
		buffer.writeBoolean(attackGeometry != null);
		if (attackGeometry != null)
			attackGeometry.write(buffer);
	}

	public void handle(ServerPlayer player) {
		if (player == null || player.isSpectator() || !player.mayBuild() || !player.level().isLoaded(pos))
			return;

		double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1.0d;
		SurgicalTablePlane.Plane plane = SurgicalTablePlane.scan(player.level(), pos);
		boolean placement = action == Action.PLACE;
		if (!plane.valid() || !pos.equals(plane.source())
			|| plane.tiles().stream()
			.noneMatch(tile -> player.distanceToSqr(Vec3.atCenterOf(tile)) <= range * range))
			return;
		SurgicalTableBlockEntity table = SurgicalTableBlockEntity.controller(player.level(), plane);
		if (table == null)
			return;

		ItemStack held = player.getItemInHand(hand);
		if (placement)
			return;
		SurgicalSubject subject = table.getSubject(subjectId);
		if (targetId < 0 || !SurgicalAssembly.validTopology(observedCubeCount, seams)
			|| subject == null || !subject.matchesObservedTopology(observedCubeCount, seams))
			return;
		switch (action) {
		case CUT -> {
			if (targetId < seams.size() && held.is(Items.SHEARS)) {
				if (!table.cutSeam(player, held, hand, subjectId, targetId, observedCubeCount, seams, plane,
					layout, originOffsetX, originOffsetZ))
					noSpace(player);
			}
		}
		case CUT_GLUE -> {
			if (held.is(Items.SHEARS)
				&& !table.cutGlueJoint(player, held, hand, subjectId, targetId, observedCubeCount, seams,
					plane, originOffsetX, originOffsetZ))
				noSpace(player);
		}
		case PACK -> {
			if (targetId < observedCubeCount
				&& com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxItem.isBox(held)
				&& !com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxItem.hasCapturedEntity(held))
				table.packComponent(player, held, subjectId, targetId, observedCubeCount, seams, bodyBounds,
					hitboxGeometry, attackGeometry);
		}
		case CUT_CUBE_CONNECTIONS -> {
			if (targetId < observedCubeCount && held.is(Items.SHEARS)) {
				if (!table.cutCubeConnections(player, held, hand, subjectId, targetId, observedCubeCount, seams,
					plane, layout))
					noSpace(player);
			}
		}
		case COMBINE -> {
			if (targetId < observedCubeCount && held.is(Items.HONEY_BOTTLE))
				table.combineConnected(player, held, hand, subjectId, targetId, observedCubeCount, seams);
		}
		case BREAK_COMBINATION -> {
			if (targetId < observedCubeCount && held.is(Items.SHEARS))
				table.breakCombination(player, held, hand, subjectId, targetId, observedCubeCount, seams);
		}
		case DETACH_COMBINATION -> {
			if (targetId < observedCubeCount && held.is(Items.SHEARS))
				table.detachCombination(player, held, hand, subjectId, targetId, observedCubeCount, seams);
		}
		case PLACE -> {}
		}
	}

	private static void noSpace(ServerPlayer player) {
		player.displayClientMessage(Component.translatable("message.create_biotech.surgical_table.no_space"), true);
	}

	private static List<SurgicalAssembly.Seam> readSeams(FriendlyByteBuf buffer) {
		int size = buffer.readVarInt();
		if (size < 0 || size > SurgicalAssembly.MAX_SEAMS)
			throw new IllegalArgumentException("Invalid surgical seam count " + size);
		List<SurgicalAssembly.Seam> seams = new ArrayList<>(size);
		for (int i = 0; i < size; i++)
			seams.add(SurgicalAssembly.Seam.of(buffer.readVarInt(), buffer.readVarInt()));
		return seams;
	}

	private static SurgicalTableLayout.Proposal readLayout(FriendlyByteBuf buffer) {
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

	@Nullable
	private static SurgicalAssembly.BodyBounds readBodyBounds(FriendlyByteBuf buffer) {
		if (!buffer.readBoolean())
			return null;
		return SurgicalAssembly.BodyBounds.create(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
			buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
			buffer.readVarInt(), buffer.readVarInt(), buffer.readFloat());
	}

	@Nullable
	private static SurgicalAssembly.HitboxGeometry readHitboxGeometry(FriendlyByteBuf buffer) {
		return buffer.readBoolean() ? SurgicalAssembly.HitboxGeometry.read(buffer) : null;
	}

	@Nullable
	private static SurgicalAssembly.AttackGeometry readAttackGeometry(FriendlyByteBuf buffer) {
		if (!buffer.readBoolean())
			return null;
		SurgicalAssembly.AttackGeometry geometry = SurgicalAssembly.AttackGeometry.read(buffer);
		if (geometry == null)
			throw new IllegalArgumentException("Invalid surgical attack geometry");
		return geometry;
	}

	public enum Action {
		PLACE,
		CUT,
		PACK,
		CUT_CUBE_CONNECTIONS,
		CUT_GLUE,
		COMBINE,
		BREAK_COMBINATION,
		DETACH_COMBINATION
	}
}
