package com.nobodiiiii.createbiotech.content.ghasthotairballoon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

@OnlyIn(Dist.CLIENT)
public class GhastBalloonMagnetSnapOverlay implements IGuiOverlay {

	public static final GhastBalloonMagnetSnapOverlay INSTANCE = new GhastBalloonMagnetSnapOverlay();

	private GhastBalloonMagnetSnapOverlay() {}

	@Override
	public void render(ForgeGui gui, GuiGraphics graphics, float partialTicks, int width, int height) {
		if (!GhastHelmClientHandler.shouldShowMagnetPrompt())
			return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui)
			return;

		Component text = Component.translatable("create_biotech.gui.ghast_balloon.magnet_prompt");
		int textWidth = mc.font.width(text);
		int x = (width - textWidth) / 2;
		int y = height - 64;
		graphics.drawString(mc.font, text, x, y, 0xFFFFFFFF, true);
	}
}
