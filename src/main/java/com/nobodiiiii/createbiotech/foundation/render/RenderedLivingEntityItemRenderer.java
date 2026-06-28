package com.nobodiiiii.createbiotech.foundation.render;

import com.nobodiiiii.createbiotech.foundation.item.RenderedLivingEntityItem;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import com.mojang.blaze3d.vertex.PoseStack;

public class RenderedLivingEntityItemRenderer<T extends LivingEntity> extends BlockEntityWithoutLevelRenderer {
	private static final float MIN_AUTO_SCALE_DIMENSION = 0.6f;
	private static final float BASE_RENDER_SCALE = 1.75f;
	private static final double FOOT_GAP = 1.0d / 16.0d;

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

		renderEntity(entity, item.getRenderedEntityScaleMultiplier(), poseStack, buffer, packedLight);
	}

	public static void renderGuiEntityItem(GuiGraphics graphics, ItemStack transformStack, LivingEntity entity,
		float scaleMultiplier, int x, int y) {
		renderGuiEntityItem(graphics, transformStack, entity, new EntityRenderTuning(scaleMultiplier, 0.0f), x, y);
	}

	public static void renderGuiEntityItem(GuiGraphics graphics, ItemStack transformStack, LivingEntity entity,
		EntityRenderTuning tuning, int x, int y) {
		if (transformStack.isEmpty())
			return;

		Minecraft minecraft = Minecraft.getInstance();
		BakedModel model = minecraft.getItemRenderer()
			.getModel(transformStack, minecraft.level, minecraft.player, 0);

		PoseStack poseStack = graphics.pose();
		poseStack.pushPose();
		poseStack.translate(x + 8.0f, y + 8.0f, 150.0f);
		poseStack.mulPoseMatrix(new Matrix4f().scaling(1.0f, -1.0f, 1.0f));
		poseStack.scale(16.0f, 16.0f, 16.0f);
		ForgeHooksClient.handleCameraTransforms(poseStack, model, ItemDisplayContext.GUI, false);
		poseStack.translate(-0.5f, -0.5f, -0.5f);
		renderEntity(entity, tuning.scaleMultiplier(), tuning.footYOffset(), poseStack, graphics.bufferSource(), 15728880);
		graphics.flush();
		poseStack.popPose();
	}

	public static void renderGuiEntityItem(GuiGraphics graphics, ItemStack transformStack, LivingEntity entity,
		EntityRenderTuningProvider tuningProvider, int x, int y) {
		renderGuiEntityItem(graphics, transformStack, entity, tuningProvider.getRenderTuning(entity), x, y);
	}

	public static void renderEntity(LivingEntity entity, float scaleMultiplier, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight) {
		renderEntity(entity, scaleMultiplier, 0.0f, poseStack, buffer, packedLight);
	}

	public static void renderEntity(LivingEntity entity, float scaleMultiplier, float footYOffset, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight) {
		float scale = BASE_RENDER_SCALE * scaleMultiplier / getLargestDimension(entity);

		poseStack.pushPose();
		poseStack.translate(0.0d, FOOT_GAP + footYOffset, 0.0d);
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

	private static float getLargestDimension(LivingEntity entity) {
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

	@FunctionalInterface
	public interface EntityRenderTuningProvider {
		EntityRenderTuning getRenderTuning(LivingEntity entity);
	}

	public record EntityRenderTuning(float scaleMultiplier, float footYOffset) {}
}
