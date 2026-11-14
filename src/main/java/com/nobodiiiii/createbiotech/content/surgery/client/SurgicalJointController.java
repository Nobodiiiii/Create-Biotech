package com.nobodiiiii.createbiotech.content.surgery.client;

import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableLimbPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableSlimeSeamPacket;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.simibubi.create.AllSoundEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

/** Internal SurgicalJointController implementation; public entry points remain on SurgicalTableClientHandler. */
final class SurgicalJointController {
	private SurgicalJointController() {}

	static PendingLimb pendingLimb;
	@Nullable
	static PendingSlimeSeam pendingSlimeSeam;

	/**
	 * Two right-clicks make one joint. The first click records the part that will rotate, the second
	 * names the part it rotates around, and only then does the server hear about it.
	 */
	static boolean handleLimbClick(LocalPlayer player, ClientLevel level, InteractionHand hand,
		SurgicalLimbType type, @Nullable SurgicalSelectionResolver.CubeHit hit) {
		SurgicalSelectionResolver.Selection selected = SurgicalSelectionResolver.findLimbTargetSelection(hit);
		if (selected == null)
			return pendingLimb != null;
		if (pendingLimb == null || pendingLimb.hand() != hand || pendingLimb.type() != type) {
			pendingLimb = new PendingLimb(type, hand, selected);
			SurgicalSelectionResolver.limbSelection = selected;
			player.displayClientMessage(Component.translatable(
				"message.create_biotech.surgical_table.limb_first_" + type.id()), true);
			AllSoundEvents.SLIME_ADDED.playAt(level, BlockPos.containing(hit.location()), 0.5f, 1.2f, false);
			return true;
		}

		PendingLimb first = pendingLimb;
		clearPendingLimb();
		if (!first.selection().tablePos().equals(selected.tablePos())
			|| first.selection().subjectId() == selected.subjectId()
			&& first.selection().targetId() == selected.targetId()) {
			SurgicalSelectionResolver.clearSelections();
			return true;
		}
		CBPackets.sendToServer(new SurgicalTableLimbPacket(selected.tablePos(), hand, type,
			limbTarget(first.selection()), limbTarget(selected)));
		SurgicalSelectionResolver.clearSelections();
		return true;
	}

	static SurgicalTableLimbPacket.Target limbTarget(SurgicalSelectionResolver.Selection selection) {
		return new SurgicalTableLimbPacket.Target(selection.subjectId(), selection.targetId(),
			selection.observedCubeCount(), selection.seams());
	}

	/** Two right-clicks add an in-place glue edge between touching cubes without moving either one. */
	static boolean handleSlimeSeamClick(LocalPlayer player, ClientLevel level,
		InteractionHand hand, @Nullable SurgicalSelectionResolver.CubeHit hit) {
		if (pendingSlimeSeam != null && pendingSlimeSeam.hand != hand) {
			clearPendingSlimeSeam();
			return true;
		}
		SurgicalSelectionResolver.Selection selected = SurgicalSelectionResolver.findSlimeSeamTargetSelection(level, hit);
		if (selected == null)
			return pendingSlimeSeam != null;
		if (pendingSlimeSeam == null) {
			pendingSlimeSeam = new PendingSlimeSeam(hand, selected);
			SurgicalSelectionResolver.slimeSeamSelection = selected;
			player.displayClientMessage(Component.translatable(
				"message.create_biotech.surgical_table.slime_seam_first"), true);
			AllSoundEvents.SLIME_ADDED.playAt(level, BlockPos.containing(hit.location()), 0.5f, 0.9f, false);
			return true;
		}

		PendingSlimeSeam first = pendingSlimeSeam;
		clearPendingSlimeSeam();
		CBPackets.sendToServer(new SurgicalTableSlimeSeamPacket(selected.tablePos(), hand,
			slimeSeamTarget(first.selection), slimeSeamTarget(selected)));
		SurgicalSelectionResolver.clearSelections();
		return true;
	}

	static SurgicalTableSlimeSeamPacket.Target slimeSeamTarget(SurgicalSelectionResolver.Selection selection) {
		return new SurgicalTableSlimeSeamPacket.Target(selection.subjectId(), selection.targetId(),
			selection.observedCubeCount(), selection.seams());
	}

	static void clearPendingLimb() {
		pendingLimb = null;
		SurgicalSelectionResolver.limbSelection = null;
		SurgicalInteractionPresentation.CUBE_OUTLINE.clear();
		SurgicalInteractionPresentation.COMBINATION_OUTLINE.clear();
		SurgicalInteractionPresentation.GLUE_JOINT_OUTLINE.clear();
	}

	record PendingLimb(SurgicalLimbType type, InteractionHand hand, SurgicalSelectionResolver.Selection selection) {}

	record PendingSlimeSeam(InteractionHand hand, SurgicalSelectionResolver.Selection selection) {}

	static void clearPendingSlimeSeam() {
		pendingSlimeSeam = null;
		SurgicalSelectionResolver.slimeSeamSelection = null;
		SurgicalInteractionPresentation.CUBE_OUTLINE.clear();
	}
}
