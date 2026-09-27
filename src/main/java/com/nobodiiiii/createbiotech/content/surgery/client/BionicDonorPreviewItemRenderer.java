package com.nobodiiiii.createbiotech.content.surgery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityRenderTime;
import com.nobodiiiii.createbiotech.content.surgery.BionicDonorPreviewItem;
import com.nobodiiiii.createbiotech.foundation.render.CachedRenderEntity;
import com.nobodiiiii.createbiotech.foundation.render.GuiEntityItemElement;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureRenderer;
import com.nobodiiiii.createbiotech.foundation.render.RenderProxyEntities;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Uses the same centered creature geometry and display transforms as the small slime item. */
public final class BionicDonorPreviewItemRenderer extends BlockEntityWithoutLevelRenderer {

	private final CachedRenderEntity<LivingEntity, EntityType<?>> entities =
		CachedRenderEntity.<LivingEntity, EntityType<?>>keyed((level, type) ->
			type.create(level) instanceof LivingEntity living ? RenderProxyEntities.mark(living) : null)
			.cacheCapacity(64);

	public BionicDonorPreviewItemRenderer() {
		super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int overlay) {
		EntityType<?> type = BionicDonorPreviewItem.entityType(stack);
		if (type == null)
			return;
		if (type == EntityType.SLIME || type == EntityType.MAGMA_CUBE) {
			MachineCreatureRenderer.renderCenteredSlime(poseStack, buffer, packedLight,
				GuiEntityItemElement.baseYRotation(displayContext), 1, 1.5f, type == EntityType.MAGMA_CUBE);
			return;
		}
		LivingEntity entity = entities.get(Minecraft.getInstance().level, type);
		if (entity == null)
			return;
		CapturedEntityRenderTime.runWithFixedPartialTick(() -> GuiEntityItemElement.of(entity)
			.blockCentered()
			.autoScale(1.0f)
			.yRotationFor(displayContext)
			.packedLight(packedLight)
			.render(poseStack, buffer));
	}
}
