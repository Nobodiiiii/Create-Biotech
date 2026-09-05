package com.nobodiiiii.createbiotech.compat.jei;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxItem;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityRenderTime;
import com.nobodiiiii.createbiotech.foundation.render.CachedRenderEntity;
import com.nobodiiiii.createbiotech.foundation.render.GuiEntityItemElement;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.nobodiiiii.createbiotech.registry.CBItems;

import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class CapturedEntityBoxJeiRenderer {
	private static final ThreadLocal<Boolean> CURRENT_SLOT_HOVERED = ThreadLocal.withInitial(() -> false);
	private static final ThreadLocal<IRecipeSlotDrawable> CURRENT_SLOT = new ThreadLocal<>();
	private static final ItemStack ENTITY_ITEM_TRANSFORM = new ItemStack(CBItems.CAPTURED_SMALL_SLIME.get());
	private static final ItemStack LARGE_BOX_BADGE = new ItemStack(CBItems.LARGE_CARDBOARD_BOX.get());
	private static final float BADGE_SCALE = 0.55f;
	private static final int BADGE_Z = 200;
	private static final int ENTITY_CACHE_CAPACITY = 16;

	private static final CachedRenderEntity<LivingEntity, ItemStack> CAPTURED_ENTITY =
		CachedRenderEntity.<LivingEntity, ItemStack>keyed(CapturedEntityBoxJeiRenderer::createCapturedEntity)
			.keyEquality(ItemStack::isSameItemSameComponents)
			.keyCopier(ItemStack::copy)
			.cacheCapacity(ENTITY_CACHE_CAPACITY);

	private CapturedEntityBoxJeiRenderer() {}

	public static void beginSlotDraw(IRecipeSlotDrawable slot, boolean hovered) {
		CURRENT_SLOT_HOVERED.set(hovered);
		CURRENT_SLOT.set(slot);
	}

	public static void endSlotDraw() {
		CURRENT_SLOT.remove();
		CURRENT_SLOT_HOVERED.remove();
	}

	public static boolean renderCapturedEntityBox(GuiGraphics graphics, ItemStack stack, int x, int y) {
		if (!(stack.getItem() instanceof CapturedEntityBoxItem) || !CapturedEntityBoxHelper.hasCapturedEntity(stack))
			return false;
		if (!CBConfigs.CLIENT.renderCapturedEntitiesOnBoxes.get()) {
			CAPTURED_ENTITY.clear();
			return false;
		}
		if (CURRENT_SLOT_HOVERED.get()) {
			ItemStack displayedBox = getHoveredBoxStack(stack);
			graphics.renderItem(displayedBox, x, y);
			return true;
		}

		LivingEntity entity = CAPTURED_ENTITY.get(Minecraft.getInstance().level, stack);
		if (entity == null)
			return false;

		renderEntity(graphics, entity, x, y);
		renderBadge(graphics, x, y);
		return true;
	}

	private static void renderEntity(GuiGraphics graphics, LivingEntity entity, int x, int y) {
		CapturedEntityRenderTime.runWithFixedPartialTick(() ->
			GuiEntityItemElement.of(entity)
				.blockCentered()
				.autoScale(1.0f)
				.renderInGuiSlot(graphics, ENTITY_ITEM_TRANSFORM, x, y));
	}

	private static void renderBadge(GuiGraphics graphics, int x, int y) {
		var poseStack = graphics.pose();
		poseStack.pushPose();
		poseStack.translate(x + 8.0f, y + 8.0f, BADGE_Z);
		poseStack.scale(BADGE_SCALE, BADGE_SCALE, BADGE_SCALE);
		graphics.renderItem(LARGE_BOX_BADGE, 0, 0);
		poseStack.popPose();
	}

	private static ItemStack getHoveredBoxStack(ItemStack fallback) {
		IRecipeSlotDrawable slot = CURRENT_SLOT.get();
		if (slot == null)
			return fallback;

		return slot.getDisplayedItemStack()
			.filter(CapturedEntityBoxJeiRenderer::isCapturedEntityBox)
			.orElse(fallback);
	}

	private static boolean isCapturedEntityBox(ItemStack stack) {
		return stack.getItem() instanceof CapturedEntityBoxItem && CapturedEntityBoxHelper.hasCapturedEntity(stack);
	}

	@Nullable
	private static LivingEntity createCapturedEntity(Level level, ItemStack stack) {
		Entity entity = CapturedEntityBoxHelper.createCapturedEntity(stack, level);
		return entity instanceof LivingEntity livingEntity ? livingEntity : null;
	}
}
