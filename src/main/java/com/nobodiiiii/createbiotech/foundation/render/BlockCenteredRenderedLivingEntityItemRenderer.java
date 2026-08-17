package com.nobodiiiii.createbiotech.foundation.render;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

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
 * Item renderer for entity items that read as "a creature in a box": the center
 * of the entity's drawn vertices is mapped to the center of a unit cube, so
 * vanilla block-item transforms rotate and offset every display context around
 * the same point.
 *
 * @see GuiEntityItemElement.Anchor#BLOCK_CENTERED
 */
public class BlockCenteredRenderedLivingEntityItemRenderer<T extends LivingEntity>
	extends BlockEntityWithoutLevelRenderer {

	private final RenderedLivingEntityItem<T> item;
	private final CachedRenderEntity<T, Void> renderEntity;

	public static <T extends LivingEntity> IClientItemExtensions create(RenderedLivingEntityItem<T> item) {
		return new IClientItemExtensions() {
			private final BlockEntityWithoutLevelRenderer renderer =
				new BlockCenteredRenderedLivingEntityItemRenderer<>(item);

			@Override
			public BlockEntityWithoutLevelRenderer getCustomRenderer() {
				return renderer;
			}
		};
	}

	private BlockCenteredRenderedLivingEntityItemRenderer(RenderedLivingEntityItem<T> item) {
		super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
		this.item = item;
		this.renderEntity = CachedRenderEntity.of(item.getRenderedEntityType())
			.configure(item::configureRenderedEntity);
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int overlay) {
		@Nullable
		T entity = renderEntity.get(Minecraft.getInstance().level);
		if (entity == null)
			return;

		// Measured before the display-context configuration is applied, so a pose
		// that only exists in one context cannot shift the centering of the others.
		item.configureRenderedEntityForGeometryMeasurement(entity, stack, transformType);
		Vector3f geometryCenter = EntityGeometry.measureCenter(entity);
		item.configureRenderedEntity(entity, stack, transformType);

		GuiEntityItemElement.of(entity)
			.blockCentered()
			.geometryCenter(geometryCenter)
			.fixedScale(item.getRenderedEntityScaleMultiplier())
			.yRotation(GuiEntityItemElement.baseYRotation(transformType)
				+ item.getRenderedEntityYRotation(stack, transformType))
			.packedLight(packedLight)
			.render(poseStack, buffer);
	}
}
