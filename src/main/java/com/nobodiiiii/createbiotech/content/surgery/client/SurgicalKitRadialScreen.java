package com.nobodiiiii.createbiotech.content.surgery.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.client.CBKeyMappings;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitItem;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitSelectionPacket;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.theme.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A toolbox-styled wheel containing the tools supplied by the held surgical kit. */
public class SurgicalKitRadialScreen extends AbstractSimiScreen {
	private static final double INNER_RADIUS_SQR = 24.0d * 24.0d;
	private static final double OUTER_RADIUS_SQR = 82.0d * 82.0d;

	private final InteractionHand hand;
	private final Item kitItem;
	private final SurgicalKitItem.Tool[] tools;
	private final double slotAngle;
	private int ticksOpen;
	private int hoveredSlot = -1;
	private boolean committed;

	public SurgicalKitRadialScreen(InteractionHand hand, ItemStack stack) {
		this.hand = hand;
		kitItem = stack.getItem();
		tools = SurgicalKitItem.availableTools(stack).toArray(SurgicalKitItem.Tool[]::new);
		slotAngle = 360.0d / tools.length;
	}

	@Override
	protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		float fade = Mth.clamp((ticksOpen + AnimationTickHolder.getPartialTicks()) / 8.0f,
			1.0f / 512.0f, 1.0f);
		double relativeX = mouseX - width / 2.0d;
		double relativeY = mouseY - height / 2.0d;
		double distanceSqr = relativeX * relativeX + relativeY * relativeY;
		hoveredSlot = distanceSqr >= INNER_RADIUS_SQR && distanceSqr <= OUTER_RADIUS_SQR
			? hoveredSlot(relativeX, relativeY) : -1;

		PoseStack poseStack = graphics.pose();
		poseStack.pushPose();
		poseStack.translate(width / 2.0f, height / 2.0f, 0.0f);
		double radius = 54.0d - 10.0d * (1.0d - fade) * (1.0d - fade);
		for (int slot = 0; slot < tools.length; slot++) {
			double angle = Math.toRadians(slot * slotAngle - 90.0d);
			int x = Mth.floor(Math.cos(angle) * radius) - 12;
			int y = Mth.floor(Math.sin(angle) * radius) - 12;
			AllGuiTextures.TOOLBELT_SLOT.render(graphics, x, y);
			GuiGameElement.of(tools[slot].displayStack()).at(x + 3, y + 3).render(graphics);
			if (slot == hoveredSlot)
				AllGuiTextures.TOOLBELT_SLOT_HIGHLIGHT.render(graphics, x - 1, y - 1);
		}

		AllGuiTextures.TOOLBELT_SLOT.render(graphics, -12, -12);
		GuiGameElement.of(new ItemStack(kitItem)).at(-9, -9).render(graphics);
		poseStack.popPose();

		Component tip = hoveredSlot >= 0
			? tools[hoveredSlot].displayName().copy().withStyle(ChatFormatting.GOLD)
			: Component.translatable("item.create_biotech.surgical_kit.radial_hint",
				Component.keybind(SurgicalKitItem.OPEN_KEY_TRANSLATION))
				.withStyle(ChatFormatting.GRAY);
		int alpha = Mth.clamp((int) (fade * 255.0f), 0, 255);
		if (alpha > 8) {
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			int textWidth = font.width(tip);
			graphics.drawString(font, tip, (width - textWidth) / 2, height - 68,
				0xFFFFFF | alpha << 24, false);
			RenderSystem.disableBlend();
		}
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		Color color = BACKGROUND_COLOR.scaleAlpha(Math.min(1.0f,
			(ticksOpen + AnimationTickHolder.getPartialTicks()) / 20.0f));
		graphics.fillGradient(0, 0, width, height, color.getRGB(), color.getRGB());
	}

	@Override
	public void tick() {
		ticksOpen++;
		if (minecraft.player == null || !minecraft.player.getItemInHand(hand).is(kitItem)) {
			onClose();
			return;
		}
		super.tick();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && hoveredSlot >= 0) {
			commitAndClose();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
		if (CBKeyMappings.isBoundKey(CBKeyMappings.SURGICAL_KIT, key)) {
			commitAndClose();
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int hoveredSlot(double x, double y) {
		double fromTop = Math.toDegrees(Math.atan2(y, x)) + 90.0d;
		fromTop = (fromTop % 360.0d + 360.0d) % 360.0d;
		return Mth.floor((fromTop + slotAngle / 2.0d) / slotAngle) % tools.length;
	}

	private void commitAndClose() {
		if (!committed && hoveredSlot >= 0 && minecraft.player != null) {
			ItemStack kit = minecraft.player.getItemInHand(hand);
			if (SurgicalKitItem.isKit(kit)) {
				SurgicalKitItem.Tool selected = tools[hoveredSlot];
				SurgicalKitItem.setSelectedTool(kit, selected);
				CBPackets.sendToServer(new SurgicalKitSelectionPacket(hand, selected));
			}
		}
		committed = true;
		onClose();
	}
}
