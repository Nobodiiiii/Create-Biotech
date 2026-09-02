package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public enum CBCreativeTabSection {
	BIOTECHNOLOGY("biotechnology", "biological_capture"),
	TRANSMISSION_AND_LOGISTICS("transmission_and_logistics", "power_transmission"),
	DEVICES_AND_EQUIPMENT("devices_and_equipment", "functional_devices"),
	ENCHANTMENT_AND_EXPERIENCE("enchantment_and_experience", "enchantment"),
	MATERIALS_AND_DECORATION("materials_and_decoration", "materials"),
	WORK_IN_PROGRESS("work_in_progress");

	private final String id;
	private final Component title;
	private final ResourceLocation sprite;

	CBCreativeTabSection(String id) {
		this(id, id);
	}

	CBCreativeTabSection(String id, String spriteId) {
		this.id = id;
		this.title = Component.translatable("creative_tab.create_biotech.section." + id);
		this.sprite = CreateBiotech.asResource("creative_tab/" + spriteId);
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
