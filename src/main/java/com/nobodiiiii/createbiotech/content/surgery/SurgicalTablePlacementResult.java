package com.nobodiiiii.createbiotech.content.surgery;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** User-facing outcome of trying to place a captured subject on a surgical table. */
public enum SurgicalTablePlacementResult {
	SUCCESS(null),
	INVALID_CAPTURE("message.create_biotech.surgical_table.invalid_capture"),
	UNSUPPORTED_SUBJECT("message.create_biotech.surgical_table.unsupported_subject"),
	INVALID_ASSEMBLY("message.create_biotech.surgical_table.invalid_assembly"),
	MODEL_UNAVAILABLE("message.create_biotech.surgical_table.model_unavailable"),
	INVALID_TABLE("message.create_biotech.surgical_table.invalid_table"),
	TABLE_FULL("message.create_biotech.surgical_table.table_full"),
	NO_SPACE("message.create_biotech.surgical_table.no_space");

	@Nullable
	private final String messageKey;

	SurgicalTablePlacementResult(@Nullable String messageKey) {
		this.messageKey = messageKey;
	}

	public boolean succeeded() {
		return this == SUCCESS;
	}

	public void display(Player player) {
		if (messageKey != null)
			player.displayClientMessage(Component.translatable(messageKey), true);
	}
}
