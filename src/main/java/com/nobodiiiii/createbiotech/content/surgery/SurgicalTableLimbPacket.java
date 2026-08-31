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

/**
 * Installs one anatomical joint after the player right-clicked two connected cubes in order.
 *
 * <p>The first target is the rotating child and the second is the pivot. Both carry the topology
 * the client observed so an untouched subject can still be initialized server-side, exactly like
 * {@link SurgicalTableInteractionPacket} does for single-cube operations.</p>
 */
public record SurgicalTableLimbPacket(BlockPos pos, InteractionHand hand, SurgicalLimbType type,
	Target child, Target parent) {

	public SurgicalTableLimbPacket(FriendlyByteBuf buffer) {
		this(buffer.readBlockPos(), buffer.readEnum(InteractionHand.class),
			buffer.readEnum(SurgicalLimbType.class), Target.read(buffer), Target.read(buffer));
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeBlockPos(pos);
		buffer.writeEnum(hand);
		buffer.writeEnum(type);
		child.write(buffer);
		parent.write(buffer);
	}

	public void handle(ServerPlayer player) {
		if (player == null || player.isSpectator() || !player.mayBuild() || !player.level().isLoaded(pos)
			|| type == null || !child.valid() || !parent.valid())
			return;

		double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1.0d;
		SurgicalTablePlane.Plane plane = SurgicalTablePlane.scan(player.level(), pos);
		if (!plane.valid() || !pos.equals(plane.source())
			|| plane.tiles().stream()
			.noneMatch(tile -> player.distanceToSqr(Vec3.atCenterOf(tile)) <= range * range))
			return;
		SurgicalTableBlockEntity table = SurgicalTableBlockEntity.controller(player.level(), plane);
		if (table == null)
			return;

		ItemStack held = player.getItemInHand(hand);
		if (SurgicalKitItem.limbType(held) != type)
			return;
		table.attachLimb(player, held, hand, type, child.subjectId, child.cubeId, parent.subjectId,
			parent.cubeId, child.observedCubeCount, child.seams, parent.observedCubeCount, parent.seams);
	}

	public record Target(int subjectId, int cubeId, int observedCubeCount,
		List<SurgicalAssembly.Seam> seams) {
		public Target {
			seams = List.copyOf(seams);
		}

		private boolean valid() {
			return subjectId >= 0 && cubeId >= 0 && cubeId < observedCubeCount
				&& SurgicalAssembly.validTopology(observedCubeCount, seams);
		}

		private void write(FriendlyByteBuf buffer) {
			buffer.writeVarInt(subjectId);
			buffer.writeVarInt(cubeId);
			buffer.writeVarInt(observedCubeCount);
			buffer.writeVarInt(seams.size());
			for (SurgicalAssembly.Seam seam : seams) {
				buffer.writeVarInt(seam.first());
				buffer.writeVarInt(seam.second());
			}
		}

		private static Target read(FriendlyByteBuf buffer) {
			int subjectId = buffer.readVarInt();
			int cubeId = buffer.readVarInt();
			int observedCubeCount = buffer.readVarInt();
			int size = buffer.readVarInt();
			if (size < 0 || size > SurgicalAssembly.MAX_SEAMS)
				throw new IllegalArgumentException("Invalid surgical seam count " + size);
			List<SurgicalAssembly.Seam> seams = new ArrayList<>(size);
			for (int index = 0; index < size; index++)
				seams.add(SurgicalAssembly.Seam.of(buffer.readVarInt(), buffer.readVarInt()));
			return new Target(subjectId, cubeId, observedCubeCount, seams);
		}
	}
}
