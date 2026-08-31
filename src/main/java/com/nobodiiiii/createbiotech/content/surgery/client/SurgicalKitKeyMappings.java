package com.nobodiiiii.createbiotech.content.surgery.client;

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

/** Client key mapping for the surgical kit wheel, independent from Create's toolbox binding. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public final class SurgicalKitKeyMappings {
	public static final KeyMapping OPEN = new KeyMapping(SurgicalKitItem.OPEN_KEY_TRANSLATION,
		GLFW.GLFW_KEY_LEFT_ALT, "Create: Biotech");

	private SurgicalKitKeyMappings() {}

	@SubscribeEvent
	public static void register(RegisterKeyMappingsEvent event) {
		event.register(OPEN);
	}

	public static boolean matches(InputEvent.Key event) {
		return OPEN.isActiveAndMatches(InputConstants.getKey(event.getKey(), event.getScanCode()));
	}

	public static boolean isBoundKey(InputConstants.Key key) {
		return key != InputConstants.UNKNOWN && key.equals(OPEN.getKey());
	}
}
