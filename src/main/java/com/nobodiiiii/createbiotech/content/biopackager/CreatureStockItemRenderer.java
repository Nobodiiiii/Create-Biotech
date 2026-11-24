package com.nobodiiiii.createbiotech.content.biopackager;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityRenderTime;
import com.nobodiiiii.createbiotech.foundation.render.CachedRenderEntity;
import com.nobodiiiii.createbiotech.foundation.render.GuiEntityItemElement;
import com.nobodiiiii.createbiotech.foundation.render.RenderProxyEntities;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class CreatureStockItemRenderer extends BlockEntityWithoutLevelRenderer {
	private final CachedRenderEntity<LivingEntity, ItemStack> entities = CachedRenderEntity
		.<LivingEntity, ItemStack>keyed((level, box) ->
			CapturedEntityBoxHelper.createCapturedEntity(box, level) instanceof LivingEntity living
				? RenderProxyEntities.mark(living) : null)
		.keyEquality(ItemStack::isSameItemSameComponents)
		.cacheCapacity(128);

	public CreatureStockItemRenderer() {
		super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
		MultiBufferSource buffer, int light, int overlay) {
		LivingEntity entity = entities.get(Minecraft.getInstance().level, CreatureStockItem.getBox(stack));
		if (entity == null)
			return;
		CapturedEntityRenderTime.runWithFixedPartialTick(() -> GuiEntityItemElement.of(entity)
			.blockCentered()
			.autoScale(1.0f)
			.yRotationFor(context)
			.packedLight(light)
			.render(poseStack, buffer));
	}
}
