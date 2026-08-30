package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.List;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalTableClientHandler.InteractionPrompt;
import com.simibubi.create.compat.Mods;
import com.simibubi.create.foundation.gui.RemovedGuiUtils;
import com.simibubi.create.foundation.mixin.accessor.MouseHandlerAccessor;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.infrastructure.config.CClient;

import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.theme.Color;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Create-style interaction help anchored to the center of the right edge. */
@OnlyIn(Dist.CLIENT)
public final class SurgicalTableInteractionOverlay implements LayeredDraw.Layer {
	public static final SurgicalTableInteractionOverlay INSTANCE = new SurgicalTableInteractionOverlay();

	private static final int RIGHT_PADDING = 18;
	private static final long FADE_IN_MILLIS = 200L;
	private static final long MISS_HOLD_MILLIS = 150L;
	private static final long FADE_OUT_MILLIS = 200L;
	private static InteractionPrompt retainedPrompt;
	private static float visibility;
	private static long lastFrameMillis;
	private static long missStartedMillis = -1L;

	private SurgicalTableInteractionOverlay() {}

	@Override
	public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.options.hideGui || minecraft.player == null || minecraft.level == null
			|| minecraft.screen != null) {
			resetTransition();
			return;
		}
		InteractionPrompt currentPrompt = SurgicalTableClientHandler.interactionPrompt();
		if (currentPrompt != null && currentPrompt.tooltip().isEmpty())
			currentPrompt = null;
		InteractionPrompt prompt = updateTransition(currentPrompt);
		if (prompt == null)
			return;

		List<Component> tooltip = prompt.tooltip();
		int tooltipTextWidth = 0;
		for (FormattedText line : tooltip)
			tooltipTextWidth = Math.max(tooltipTextWidth, minecraft.font.width(line));
		int tooltipHeight = 8;
		if (tooltip.size() > 1)
			tooltipHeight += 2 + (tooltip.size() - 1) * 10;

		int screenWidth = graphics.guiWidth();
		int screenHeight = graphics.guiHeight();
		int tooltipX = Math.max(16, screenWidth - RIGHT_PADDING - tooltipTextWidth - 4);
		int tooltipY = Math.max(4, (screenHeight - tooltipHeight) / 2);
		int anchorX = tooltipX - 12;
		int anchorY = tooltipY + 12;

		float fade = Mth.clamp(visibility, 0.0f, 1.0f);
		CClient config = AllConfigs.client();
		boolean customColor = config.overlayCustomColor.get();
		Color background = customColor ? new Color(config.overlayBackgroundColor.get())
			: BoxElement.COLOR_VANILLA_BACKGROUND.scaleAlpha(0.75f);
		Color borderTop = customColor ? new Color(config.overlayBorderColorTop.get())
			: BoxElement.COLOR_VANILLA_BORDER.getFirst().copy();
		Color borderBottom = customColor ? new Color(config.overlayBorderColorBot.get())
			: BoxElement.COLOR_VANILLA_BORDER.getSecond().copy();
		if (fade < 1.0f) {
			background.scaleAlpha(fade);
			borderTop.scaleAlpha(fade);
			borderBottom.scaleAlpha(fade);
		}

		PoseStack poseStack = graphics.pose();
		poseStack.pushPose();
		poseStack.translate(Math.pow(1.0f - fade, 3.0f) * 8.0f, 0.0f, 0.0f);
		GuiGameElement.of(prompt.icon())
			.at(anchorX + 10, anchorY - 16, 450)
			.render(graphics);

		if (!Mods.MODERNUI.isLoaded()) {
			drawTooltip(graphics, tooltip, anchorX, anchorY, screenWidth, screenHeight,
				background, borderTop, borderBottom, minecraft);
			poseStack.popPose();
			return;
		}

		// Match Create's workaround so Modern UI cannot move the fixed overlay with the real cursor.
		MouseHandler mouseHandler = minecraft.mouseHandler;
		Window window = minecraft.getWindow();
		double guiScale = window.getGuiScale();
		double cursorX = mouseHandler.xpos();
		double cursorY = mouseHandler.ypos();
		((MouseHandlerAccessor) mouseHandler).create$setXPos(Math.round(cursorX / guiScale) * guiScale);
		((MouseHandlerAccessor) mouseHandler).create$setYPos(Math.round(cursorY / guiScale) * guiScale);
		drawTooltip(graphics, tooltip, anchorX, anchorY, screenWidth, screenHeight,
			background, borderTop, borderBottom, minecraft);
		((MouseHandlerAccessor) mouseHandler).create$setXPos(cursorX);
		((MouseHandlerAccessor) mouseHandler).create$setYPos(cursorY);
		poseStack.popPose();
	}

	private static InteractionPrompt updateTransition(InteractionPrompt currentPrompt) {
		long now = Util.getMillis();
		long elapsedMillis = lastFrameMillis == 0L ? 0L : Mth.clamp(now - lastFrameMillis, 0L, 100L);
		lastFrameMillis = now;
		if (currentPrompt != null) {
			retainedPrompt = currentPrompt;
			missStartedMillis = -1L;
			visibility = Math.min(1.0f, visibility + elapsedMillis / (float) FADE_IN_MILLIS);
			return retainedPrompt;
		}
		if (retainedPrompt == null)
			return null;
		if (missStartedMillis < 0L)
			missStartedMillis = now;
		if (now - missStartedMillis >= MISS_HOLD_MILLIS)
			visibility = Math.max(0.0f, visibility - elapsedMillis / (float) FADE_OUT_MILLIS);
		if (visibility > 0.0f)
			return retainedPrompt;
		retainedPrompt = null;
		missStartedMillis = -1L;
		return null;
	}

	private static void resetTransition() {
		retainedPrompt = null;
		visibility = 0.0f;
		lastFrameMillis = 0L;
		missStartedMillis = -1L;
	}

	private static void drawTooltip(GuiGraphics graphics, List<Component> tooltip, int anchorX, int anchorY,
		int screenWidth, int screenHeight, Color background, Color borderTop, Color borderBottom,
		Minecraft minecraft) {
		RemovedGuiUtils.drawHoveringText(graphics, tooltip, anchorX, anchorY, screenWidth, screenHeight, -1,
			background.getRGB(), borderTop.getRGB(), borderBottom.getRGB(), minecraft.font);
	}
}
