package com.nobodiiiii.createbiotech.foundation.render;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.foundation.item.RenderedLivingEntityItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Item renderer for entity items that read as "a creature standing there":
 * the entity is anchored by its feet and normalized to a consistent size.
 *
 * @see GuiEntityItemElement.Anchor#FOOT_ANCHORED
 */
public class RenderedLivingEntityItemRenderer<T extends LivingEntity> extends BlockEntityWithoutLevelRenderer {

	private final RenderedLivingEntityItem<T> item;
	private final CachedRenderEntity<T, Void> renderEntity;

	public static <T extends LivingEntity> IClientItemExtensions create(RenderedLivingEntityItem<T> item) {
		return new IClientItemExtensions() {
			private final BlockEntityWithoutLevelRenderer renderer = new RenderedLivingEntityItemRenderer<>(item);

			@Override
			public BlockEntityWithoutLevelRenderer getCustomRenderer() {
				return renderer;
			}
		};
	}

	private RenderedLivingEntityItemRenderer(RenderedLivingEntityItem<T> item) {
		super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
		this.item = item;
		this.renderEntity = CachedRenderEntity.of(level -> item.getRenderedEntityType().create(level))
			.configure(item::configureRenderedEntity);
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int overlay) {
		@Nullable
		T entity = renderEntity.get(Minecraft.getInstance().level);
		if (entity == null)
			return;

		renderEntity(entity, item.getRenderedEntityScaleMultiplier(), poseStack, buffer, packedLight);
	}

	/**
	 * Draws an arbitrary entity through the foot-anchored item path, for renderers
	 * that manage their own entity instance.
	 */
	public static void renderEntity(LivingEntity entity, float scaleMultiplier, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight) {
		GuiEntityItemElement.of(entity)
			.footAnchored()
			.autoScale(scaleMultiplier)
			.packedLight(packedLight)
			.render(poseStack, buffer);
	}
}
