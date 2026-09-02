package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public enum CBCreativeTabSection {
	BIOLOGICAL_CAPTURE("biological_capture"),
	POWER_TRANSMISSION("power_transmission"),
	LOGISTICS("logistics"),
	FUNCTIONAL_DEVICES("functional_devices"),
	STRUCTURES("structures"),
	ENCHANTMENT("enchantment"),
	MATERIALS("materials"),
	WORK_IN_PROGRESS("work_in_progress");

	private final String id;
	private final Component title;
	private final ResourceLocation sprite;

	CBCreativeTabSection(String id) {
		this.id = id;
		this.title = Component.translatable("creative_tab.create_biotech.section." + id);
		this.sprite = CreateBiotech.asResource("creative_tab/" + id);
	}

	public String id() {
		return id;
	}

	public Component title() {
		return title;
	}

	public ResourceLocation sprite() {
		return sprite;
	}
}
