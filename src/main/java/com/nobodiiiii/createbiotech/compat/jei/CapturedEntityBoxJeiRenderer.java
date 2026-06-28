package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxItem;
import com.nobodiiiii.createbiotech.foundation.render.RenderedLivingEntityItemRenderer;
import com.nobodiiiii.createbiotech.foundation.render.RenderedLivingEntityItemRenderer.EntityRenderTuning;
import com.nobodiiiii.createbiotech.registry.CBItems;

import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class CapturedEntityBoxJeiRenderer {
	private static final ThreadLocal<Boolean> CURRENT_SLOT_HOVERED = ThreadLocal.withInitial(() -> false);
	private static final ThreadLocal<IRecipeSlotDrawable> CURRENT_SLOT = new ThreadLocal<>();
	private static final ItemStack ENTITY_ITEM_TRANSFORM = new ItemStack(CBItems.CAPTURED_SMALL_SLIME.get());
	private static final ItemStack LARGE_BOX_BADGE = new ItemStack(CBItems.LARGE_CARDBOARD_BOX.get());
	private static final long BOX_CYCLE_TIME_MS = 1000L;
	private static final float SQUID_ENTITY_SCALE_MULTIPLIER = 0.45f;
	private static final float SQUID_ENTITY_FOOT_Y_OFFSET = 1.1f;
	private static final float BADGE_SCALE = 0.55f;
	private static final int BADGE_Z = 200;

	@Nullable
	private static ItemStack cachedStack;
	@Nullable
	private static Level cachedLevel;
	@Nullable
	private static Entity cachedEntity;

	private CapturedEntityBoxJeiRenderer() {}

	public static void drawSlotWithHoverContext(IRecipeSlotDrawable slot, GuiGraphics graphics, double mouseX,
		double mouseY) {
		CURRENT_SLOT_HOVERED.set(slot.isMouseOver(mouseX, mouseY));
		CURRENT_SLOT.set(slot);
		try {
			slot.draw(graphics);
		} finally {
			CURRENT_SLOT.remove();
			CURRENT_SLOT_HOVERED.remove();
		}
	}

	public static boolean renderCapturedEntityBox(GuiGraphics graphics, ItemStack stack, int x, int y) {
		if (!(stack.getItem() instanceof CapturedEntityBoxItem) || !CapturedEntityBoxHelper.hasCapturedEntity(stack))
			return false;
		if (CURRENT_SLOT_HOVERED.get()) {
			ItemStack displayedBox = getHoveredBoxStack(stack);
			graphics.renderItem(displayedBox, x, y);
			return true;
		}

		LivingEntity entity = getOrCreateEntity(stack);
		if (entity == null)
			return false;

		renderEntity(graphics, entity, x, y);
		renderBadge(graphics, x, y);
		return true;
	}

	private static void renderEntity(GuiGraphics graphics, LivingEntity entity, int x, int y) {
		RenderedLivingEntityItemRenderer.renderGuiEntityItem(graphics, ENTITY_ITEM_TRANSFORM, entity,
			CapturedEntityBoxJeiRenderer::getEntityRenderTuning, x, y);
	}

	private static EntityRenderTuning getEntityRenderTuning(LivingEntity entity) {
		EntityType<?> type = entity.getType();
		if (type == EntityType.SQUID || type == EntityType.GLOW_SQUID)
			return new EntityRenderTuning(SQUID_ENTITY_SCALE_MULTIPLIER, SQUID_ENTITY_FOOT_Y_OFFSET);
		return new EntityRenderTuning(1.0f, 0.0f);
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

		List<ItemStack> boxes = slot.getItemStacks()
			.filter(CapturedEntityBoxJeiRenderer::isCapturedEntityBox)
			.toList();
		if (boxes.isEmpty())
			return fallback;

		int index = (int) ((System.currentTimeMillis() / BOX_CYCLE_TIME_MS) % boxes.size());
		return boxes.get(index);
	}

	private static boolean isCapturedEntityBox(ItemStack stack) {
		return stack.getItem() instanceof CapturedEntityBoxItem && CapturedEntityBoxHelper.hasCapturedEntity(stack);
	}

	@Nullable
	private static LivingEntity getOrCreateEntity(ItemStack stack) {
		Level level = Minecraft.getInstance().level;
		if (level == null)
			return null;
		if (cachedEntity instanceof LivingEntity livingEntity && cachedLevel == level && cachedStack != null
			&& ItemStack.isSameItemSameTags(cachedStack, stack))
			return livingEntity;

		Entity entity = CapturedEntityBoxHelper.createCapturedEntity(stack, level);
		if (!(entity instanceof LivingEntity livingEntity))
			return null;

		if (livingEntity instanceof Mob mob)
			mob.setNoAi(true);
		livingEntity.setSilent(true);
		livingEntity.setOnGround(true);
		livingEntity.tickCount = 0;
		livingEntity.hurtTime = 0;
		livingEntity.deathTime = 0;
		livingEntity.hurtMarked = false;

		cachedLevel = level;
		cachedStack = stack.copy();
		cachedEntity = livingEntity;
		return livingEntity;
	}
}
