package com.nobodiiiii.createbiotech.content.capturenet.client;

import com.nobodiiiii.createbiotech.content.capturenet.CaptureNetItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.IItemDecorator;

/** Paints the escape timer over the normal durability bar, without changing item durability. */
public final class CaptureNetItemDecorator {
	public static final IItemDecorator INSTANCE = (graphics, font, stack, x, y) -> {
		var level = Minecraft.getInstance().level;
		if (level == null) return false;
		float remaining = CaptureNetItem.remainingEscapeFraction(stack, level);
		if (remaining < 0) return false;
		int width = Mth.clamp(Math.round(13 * remaining), 0, 13);
		graphics.fill(RenderType.guiOverlay(), x + 2, y + 13, x + 15, y + 15, 0xFF000000);
		graphics.fill(RenderType.guiOverlay(), x + 2, y + 13, x + 2 + width, y + 14, 0xFF3399FF);
		return false;
	};

	private CaptureNetItemDecorator() {}
}
