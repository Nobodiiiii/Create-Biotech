package com.nobodiiiii.createbiotech.foundation.render;

import com.nobodiiiii.createbiotech.foundation.item.RenderedLivingEntityItem;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import com.mojang.blaze3d.vertex.PoseStack;

public class RenderedLivingEntityItemRenderer<T extends LivingEntity> extends BlockEntityWithoutLevelRenderer {
	private static final float MIN_AUTO_SCALE_DIMENSION = 0.6f;

	private final RenderedLivingEntityItem<T> item;

	@Nullable
	private T cachedEntity;
	@Nullable
	private Level cachedLevel;

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
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int overlay) {
		Level level = Minecraft.getInstance().level;
		if (level == null)
			return;

		T entity = getOrCreateEntity(level);
		if (entity == null)
			return;

		renderEntity(entity, poseStack, buffer, packedLight);
	}

	private void renderEntity(T entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		float scale = item.getRenderedEntityScaleMultiplier() / getLargestDimension(entity);

		poseStack.pushPose();
		poseStack.scale(scale, scale, scale);
		EntityRenderHelper.render(EntityRenderHelper.settings(entity)
			.packedLight(packedLight)
			.partialTicks(1.0f)
			.dispatcherYaw(0.0f)
			.yaw(0.0f)
			.bodyYaw(0.0f)
			.headYaw(0.0f)
			.pitch(0.0f)
			.flushBuffers(false), poseStack, buffer);
		poseStack.popPose();
	}

	private float getLargestDimension(T entity) {
		EntityDimensions dimensions = entity.getDimensions(entity.getPose());
		return Math.max(Math.max(dimensions.width, dimensions.height), MIN_AUTO_SCALE_DIMENSION);
	}

	@Nullable
	private T getOrCreateEntity(Level level) {
		if (cachedEntity != null && cachedLevel == level)
			return cachedEntity;

		T entity = item.getRenderedEntityType().create(level);
		if (entity == null)
			return null;

		item.configureRenderedEntity(entity);
		if (entity instanceof Mob mob)
			mob.setNoAi(true);
		entity.setSilent(true);
		entity.setOnGround(true);
		entity.tickCount = 0;
		entity.hurtTime = 0;
		entity.deathTime = 0;
		entity.setYRot(0.0f);
		entity.yRotO = 0.0f;
		entity.setXRot(0.0f);
		entity.xRotO = 0.0f;
		entity.setYBodyRot(0.0f);
		entity.yBodyRotO = 0.0f;
		entity.yHeadRot = 0.0f;
		entity.yHeadRotO = 0.0f;

		cachedLevel = level;
		cachedEntity = entity;
		return entity;
	}
}
