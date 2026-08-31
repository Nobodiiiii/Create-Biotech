package com.nobodiiiii.createbiotech.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitItem;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** Central declaration, registration, and matching helpers for Create: Biotech key mappings. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public final class CBKeyMappings {
	public static final String CATEGORY = "key.categories.create_biotech";

	private static final List<KeyMapping> ALL = new ArrayList<>();

	public static final KeyMapping SURGICAL_KIT = register(
		SurgicalKitItem.OPEN_KEY_TRANSLATION, GLFW.GLFW_KEY_LEFT_ALT);

	private CBKeyMappings() {}

	private static KeyMapping register(String translationKey, int defaultKey) {
		KeyMapping mapping = new KeyMapping(translationKey, defaultKey, CATEGORY);
		ALL.add(mapping);
		return mapping;
	}

	@SubscribeEvent
	public static void registerAll(RegisterKeyMappingsEvent event) {
		ALL.forEach(event::register);
	}

	public static boolean matches(KeyMapping mapping, InputEvent.Key event) {
		return mapping.isActiveAndMatches(InputConstants.getKey(event.getKey(), event.getScanCode()));
	}

	public static boolean isBoundKey(KeyMapping mapping, InputConstants.Key key) {
		return key != InputConstants.UNKNOWN && key.equals(mapping.getKey());
	}
}
