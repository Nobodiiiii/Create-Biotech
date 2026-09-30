package com.nobodiiiii.createbiotech.content.surgery.client;

import static com.nobodiiiii.createbiotech.content.surgery.client.SurgicalRadialLayout.ICON_OFFSET;
import static com.nobodiiiii.createbiotech.content.surgery.client.SurgicalRadialLayout.SLOT_HALF_SIZE;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.client.CBKeyMappings;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitItem;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitSelectionPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalJointItem;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalJointSelectionPacket;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.theme.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Shared toolbox-styled wheel for surgical tools and joint types. */
public class SurgicalKitRadialScreen extends AbstractSimiScreen {
	private final InteractionHand hand;
	private final Item kitItem;
	private final SurgicalKitItem.Tool[] tools;
	private final ItemStack[] options;
	private final Component[] names;
	private final boolean jointWheel;
	private final int hotbarSlot;
	private int ticksOpen;
	private int hoveredSlot = -1;
	private boolean committed;

	public SurgicalKitRadialScreen(InteractionHand hand, ItemStack stack) {
		this.hand = hand;
		kitItem = stack.getItem();
		jointWheel = SurgicalJointItem.canSelect(stack);
		hotbarSlot = Minecraft.getInstance().player.getInventory().selected;
		tools = SurgicalKitItem.availableTools(stack).toArray(SurgicalKitItem.Tool[]::new);
		options = jointWheel
			? SurgicalJointItem.TYPES.stream().map(SurgicalJointItem::previewStack).toArray(ItemStack[]::new)
			: java.util.Arrays.stream(tools).map(SurgicalKitItem.Tool::displayStack).toArray(ItemStack[]::new);
		names = jointWheel
			? SurgicalJointItem.TYPES.stream().map(SurgicalJointItem::typeName).toArray(Component[]::new)
			: java.util.Arrays.stream(tools).map(SurgicalKitItem.Tool::displayName).toArray(Component[]::new);
	}

	@Override
	protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		float fade = Mth.clamp((ticksOpen + AnimationTickHolder.getPartialTicks()) / 8.0f,
			1.0f / 512.0f, 1.0f);
		double relativeX = mouseX - width / 2.0d;
		double relativeY = mouseY - height / 2.0d;
		hoveredSlot = SurgicalRadialLayout.hoveredSlot(relativeX, relativeY, options.length);

		PoseStack poseStack = graphics.pose();
		poseStack.pushPose();
		poseStack.translate(width / 2.0f, height / 2.0f, 0.0f);
		var selectedType = jointWheel && minecraft.player != null
			? SurgicalJointItem.selectedType(minecraft.player.getItemInHand(hand)) : null;
		int selectedSlot = selectedType == null ? -1 : SurgicalJointItem.TYPES.indexOf(selectedType);
		for (int slot = 0; slot < options.length; slot++) {
			int x = SurgicalRadialLayout.slotX(slot, options.length, fade);
			int y = SurgicalRadialLayout.slotY(slot, options.length, fade);
			AllGuiTextures.TOOLBELT_SLOT.render(graphics, x, y);
			if (jointWheel)
				graphics.renderItem(options[slot], x + ICON_OFFSET, y + ICON_OFFSET);
			else
				GuiGameElement.of(options[slot]).at(x + ICON_OFFSET, y + ICON_OFFSET).render(graphics);
			if (slot == hoveredSlot || hoveredSlot < 0 && slot == selectedSlot)
				AllGuiTextures.TOOLBELT_SLOT_HIGHLIGHT.render(graphics, x - 1, y - 1);
		}

		AllGuiTextures.TOOLBELT_SLOT.render(graphics, -SLOT_HALF_SIZE, -SLOT_HALF_SIZE);
		if (jointWheel && minecraft.player != null) {
			ItemStack selected = minecraft.player.getItemInHand(hand).copyWithCount(1);
			graphics.renderItem(selected, -8, -8);
			graphics.renderItemDecorations(font, selected, -8, -8);
		} else {
			GuiGameElement.of(new ItemStack(kitItem)).at(-8, -8).render(graphics);
		}
		poseStack.popPose();

		Component tip = hoveredSlot >= 0
			? names[hoveredSlot].copy().withStyle(ChatFormatting.GOLD)
			: Component.translatable(jointWheel ? "item.create_biotech.surgical_joint.radial_hint"
				: "item.create_biotech.surgical_kit.radial_hint",
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
		if (!stillHoldingItem()) {
			onClose();
			return;
		}
		super.tick();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		double x = mouseX - width / 2.0d;
		double y = mouseY - height / 2.0d;
		hoveredSlot = SurgicalRadialLayout.hoveredSlot(x, y, options.length);
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

	private boolean stillHoldingItem() {
		return minecraft.player != null && minecraft.player.getItemInHand(hand).is(kitItem)
			&& (hand == InteractionHand.OFF_HAND || minecraft.player.getInventory().selected == hotbarSlot);
	}

	private void commitAndClose() {
		if (!committed && hoveredSlot >= 0 && stillHoldingItem()) {
			ItemStack kit = minecraft.player.getItemInHand(hand);
			if (jointWheel && SurgicalJointItem.canSelect(kit)) {
				var type = SurgicalJointItem.TYPES.get(hoveredSlot);
				SurgicalJointItem.select(kit, type);
				CBPackets.sendToServer(new SurgicalJointSelectionPacket(hand, hotbarSlot, type));
			} else if (SurgicalKitItem.isKit(kit)) {
				SurgicalKitItem.Tool selected = tools[hoveredSlot];
				SurgicalKitItem.setSelectedTool(kit, selected);
				CBPackets.sendToServer(new SurgicalKitSelectionPacket(hand, selected));
			}
		}
		committed = true;
		onClose();
	}
}
