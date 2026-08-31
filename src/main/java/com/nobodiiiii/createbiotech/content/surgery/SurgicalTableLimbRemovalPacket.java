package com.nobodiiiii.createbiotech.content.surgery;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Removes one exact anatomical joint selected from the surgical-table model. */
public record SurgicalTableLimbRemovalPacket(BlockPos pos, InteractionHand hand, SurgicalLimbType type,
	UUID childSubject, int childCube, UUID parentSubject, int parentCube) {

	public SurgicalTableLimbRemovalPacket(FriendlyByteBuf buffer) {
		this(buffer.readBlockPos(), buffer.readEnum(InteractionHand.class),
			buffer.readEnum(SurgicalLimbType.class), buffer.readUUID(), buffer.readVarInt(),
			buffer.readUUID(), buffer.readVarInt());
	}

	public SurgicalTableLimbRemovalPacket(BlockPos pos, InteractionHand hand, SurgicalLimbJoint joint) {
		this(pos, hand, joint.type(), joint.child().subjectKey(), joint.child().cubeId(),
			joint.parent().subjectKey(), joint.parent().cubeId());
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeBlockPos(pos);
		buffer.writeEnum(hand);
		buffer.writeEnum(type);
		buffer.writeUUID(childSubject);
		buffer.writeVarInt(childCube);
		buffer.writeUUID(parentSubject);
		buffer.writeVarInt(parentCube);
	}

	public void handle(ServerPlayer player) {
		if (player == null || player.isSpectator() || !player.mayBuild() || type == null
			|| childSubject == null || parentSubject == null || childCube < 0 || parentCube < 0
			|| childSubject.equals(parentSubject) && childCube == parentCube
			|| !player.level().isLoaded(pos))
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
		if (!SurgicalKitItem.isWrench(held))
			return;
		SurgicalLimbJoint joint = new SurgicalLimbJoint(type,
			new SurgicalGlueJoint.Endpoint(childSubject, childCube),
			new SurgicalGlueJoint.Endpoint(parentSubject, parentCube));
		table.detachLimb(player, joint);
	}
}
