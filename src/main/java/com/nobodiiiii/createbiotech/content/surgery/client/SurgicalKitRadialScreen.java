package com.nobodiiiii.createbiotech.content.surgery.client;

import static com.nobodiiiii.createbiotech.content.surgery.client.SurgicalRadialLayout.ICON_OFFSET;
import static com.nobodiiiii.createbiotech.content.surgery.client.SurgicalRadialLayout.SLOT_HALF_SIZE;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.client.CBKeyMappings;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitBlockEntity;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitItem;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitSelectionPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalJointItem;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalJointSelectionPacket;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;

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
	private static final int STOW_X = 84;
	private static final Component STOW = Component.translatable("item.create_biotech.surgical_kit.stow");

	private final InteractionHand hand;
	private final Item kitItem;
	@Nullable
	private final SurgicalKitBlockEntity placedKit;
	private final boolean showKeyHint;
	private final SurgicalKitItem.Tool[] tools;
	private final ItemStack[] options;
	private final Component[] names;
	private final boolean jointWheel;
	private final int hotbarSlot;
	private int ticksOpen;
	private int hoveredSlot = -1;
	private boolean committed;

	public SurgicalKitRadialScreen(InteractionHand hand, ItemStack stack) {
		this(hand, stack, null, true);
	}

	public SurgicalKitRadialScreen(SurgicalKitBlockEntity placedKit, boolean showKeyHint) {
		this(InteractionHand.MAIN_HAND, new ItemStack(placedKit.getBlockState().getBlock()), placedKit, showKeyHint);
	}

	private SurgicalKitRadialScreen(InteractionHand hand, ItemStack stack,
		@Nullable SurgicalKitBlockEntity placedKit, boolean showKeyHint) {
		this.hand = hand;
		this.placedKit = placedKit;
		this.showKeyHint = showKeyHint;
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
		hoveredSlot = hoveredSlot(mouseX, mouseY);

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
		if (!jointWheel) {
			AllGuiTextures.TOOLBELT_SLOT.render(graphics, STOW_X, -SLOT_HALF_SIZE);
			AllIcons.I_DISABLE.render(graphics, STOW_X + ICON_OFFSET, -SLOT_HALF_SIZE + ICON_OFFSET);
			if (hoveredSlot == options.length)
				AllGuiTextures.TOOLBELT_SLOT_HIGHLIGHT.render(graphics, STOW_X - 1, -SLOT_HALF_SIZE - 1);
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

		Component tip = switch (hoveredSlot) {
			case -1 -> showKeyHint
				? Component.translatable(jointWheel ? "item.create_biotech.surgical_joint.radial_hint"
				: "item.create_biotech.surgical_kit.radial_hint",
				Component.keybind(SurgicalKitItem.OPEN_KEY_TRANSLATION))
				.withStyle(ChatFormatting.GRAY) : null;
			default -> (hoveredSlot == options.length ? STOW : names[hoveredSlot]).copy().withStyle(ChatFormatting.GOLD);
		};
		int alpha = Mth.clamp((int) (fade * 255.0f), 0, 255);
		if (tip != null && alpha > 8) {
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
		if (!hasSource()) {
			onClose();
			return;
		}
		super.tick();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		hoveredSlot = hoveredSlot(mouseX, mouseY);
		if (button == 0 && hoveredSlot >= 0) {
			commit();
			onClose();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
		if (CBKeyMappings.isBoundKey(CBKeyMappings.SURGICAL_KIT, key)) {
			commit();
			minecraft.tell(() -> {
				if (minecraft.screen == this)
					onClose();
			});
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int hoveredSlot(double mouseX, double mouseY) {
		double x = mouseX - width / 2.0d;
		double y = mouseY - height / 2.0d;
		if (!jointWheel && x >= STOW_X && x < STOW_X + 2 * SLOT_HALF_SIZE
			&& y >= -SLOT_HALF_SIZE && y < SLOT_HALF_SIZE)
			return options.length;
		return SurgicalRadialLayout.hoveredSlot(x, y, options.length);
	}

	private boolean hasSource() {
		if (minecraft.player == null || minecraft.player.isSpectator()
			|| hand == InteractionHand.MAIN_HAND && minecraft.player.getInventory().selected != hotbarSlot)
			return false;
		return placedKit == null ? minecraft.player.getItemInHand(hand).is(kitItem) : placedKit.inRange(minecraft.player);
	}

	private void commit() {
		if (!committed && hoveredSlot >= 0 && hasSource()) {
			ItemStack held = minecraft.player.getItemInHand(hand);
			if (jointWheel && SurgicalJointItem.canSelect(held)) {
				var type = SurgicalJointItem.TYPES.get(hoveredSlot);
				SurgicalJointItem.select(held, type);
				CBPackets.sendToServer(new SurgicalJointSelectionPacket(hand, hotbarSlot, type));
			} else if (!jointWheel) {
				SurgicalKitItem.Tool selected = hoveredSlot == options.length ? null : tools[hoveredSlot];
				if (placedKit == null && (selected != null || !SurgicalKitItem.isBorrowedTool(held)))
					SurgicalKitItem.setSelectedTool(held, selected);
				CBPackets.sendToServer(new SurgicalKitSelectionPacket(hand, hotbarSlot,
					placedKit == null ? null : placedKit.getBlockPos(), selected));
			}
		}
		committed = true;
	}
}
