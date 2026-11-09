package com.nobodiiiii.createbiotech.content.surgery;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Shared failure hints; an unclassified validation failure never implies a lack of space. */
public enum SurgicalTableFailure {
	OPERATION_FAILED("operation_failed"),
	NO_SPACE("no_space"),
	PREVIEW_CHANGED("preview_changed"),
	DIFFERENT_TABLE("different_table"),
	EDIT_LIMIT("edit_limit"),
	GLUE_CONTACT_REQUIRED("glue_contact_required"),
	MODEL_UNAVAILABLE("model_unavailable");

	private final String messageKey;

	SurgicalTableFailure(String message) {
		messageKey = "message.create_biotech.surgical_table." + message;
	}

	public void display(@Nullable Player player) {
		if (player != null)
			player.displayClientMessage(Component.translatable(messageKey), true);
	}
}
