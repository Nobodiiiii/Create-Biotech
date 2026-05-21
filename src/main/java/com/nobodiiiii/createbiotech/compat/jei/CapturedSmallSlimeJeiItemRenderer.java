package com.nobodiiiii.createbiotech.compat.jei;

import org.joml.Quaternionf;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.foundation.render.EntityRenderHelper;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

@OnlyIn(Dist.CLIENT)
public class CapturedSmallSlimeJeiItemRenderer extends BlockEntityWithoutLevelRenderer {
	private static final int SLIME_SIZE = 2;
	private static final float DEFAULT_INVENTORY_ANGLE_X = 0.75f;
	private static final float DEFAULT_INVENTORY_ANGLE_Y = 0.6f;
	private static final double DEFAULT_GUI_X = 0.5d;
	private static final double DEFAULT_GUI_Y = 0.2d;
	private static final double DEFAULT_GUI_Z = 0.5d;
	private static final double DEFAULT_WORLD_X = 0.5d;
	private static final double DEFAULT_WORLD_Y = 0.45d;
	private static final double DEFAULT_WORLD_Z = 0.5d;
	private static final double DEFAULT_GUI_SCALE = 0.7d;
	private static final double DEFAULT_GROUND_SCALE = 0.75d;
	private static final double DEFAULT_OTHER_SCALE = 0.55d;

	private static final IClientItemExtensions ITEM_EXTENSIONS = new IClientItemExtensions() {
		private CapturedSmallSlimeJeiItemRenderer renderer;

		@Override
		public BlockEntityWithoutLevelRenderer getCustomRenderer() {
			if (renderer == null)
				renderer = new CapturedSmallSlimeJeiItemRenderer();
			return renderer;
		}
	};

	@Nullable
	private Slime cachedSlime;
	@Nullable
	private Level cachedLevel;

	private CapturedSmallSlimeJeiItemRenderer() {
		super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
	}

	public static IClientItemExtensions itemExtensions() {
		return ITEM_EXTENSIONS;
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int packedOverlay) {
		Level level = Minecraft.getInstance().level;
		if (level == null)
			return;

		Slime slime = getOrCreateSlime(level);
		if (slime == null)
			return;

		renderSlimeItem(slime, displayContext, poseStack, buffer, packedLight);
	}

	private void renderSlimeItem(Slime slime, ItemDisplayContext displayContext, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight) {
		boolean guiLighting = displayContext == ItemDisplayContext.GUI;
		double x = guiLighting ? DEFAULT_GUI_X : DEFAULT_WORLD_X;
		double y = guiLighting ? DEFAULT_GUI_Y : DEFAULT_WORLD_Y;
		double z = guiLighting ? DEFAULT_GUI_Z : DEFAULT_WORLD_Z;
		double scale =
			guiLighting ? DEFAULT_GUI_SCALE : displayContext == ItemDisplayContext.GROUND ? DEFAULT_GROUND_SCALE
				: DEFAULT_OTHER_SCALE;
		int appliedLight = guiLighting ? LightTexture.FULL_BRIGHT : packedLight;

		Quaternionf camera = new Quaternionf().rotateX(DEFAULT_INVENTORY_ANGLE_Y * 20.0F * Mth.DEG_TO_RAD);
		Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
		pose.mul(camera);
		pose.rotateZ((float) Math.PI);
		float yaw = 180.0F + DEFAULT_INVENTORY_ANGLE_X * 40.0F;

		poseStack.pushPose();
		if (guiLighting)
			Lighting.setupForEntityInInventory();
		try {
			poseStack.translate(x, y, z);
			poseStack.scale((float) scale, (float) scale, (float) -scale);
			poseStack.mulPose(pose);

			EntityRenderHelper.render(EntityRenderHelper.settings(slime)
				.packedLight(appliedLight)
				.partialTicks(1.0f)
				.cameraOrientation(camera)
				.bodyYaw(180.0F + DEFAULT_INVENTORY_ANGLE_X * 20.0F)
				.yaw(yaw)
				.pitch(-DEFAULT_INVENTORY_ANGLE_Y * 20.0F)
				.headYaw(yaw)
				.dispatcherYaw(0.0F)
				.flushBuffers(false), poseStack, buffer);
		} finally {
			poseStack.popPose();
			if (guiLighting)
				Lighting.setupFor3DItems();
		}
	}

	@Nullable
	private Slime getOrCreateSlime(Level level) {
		if (cachedSlime != null && cachedLevel == level)
			return cachedSlime;

		Slime slime = EntityType.SLIME.create(level);
		if (slime == null)
			return null;
		slime.setNoAi(true);
		slime.setSize(SLIME_SIZE, false);
		slime.tickCount = 0;
		cachedLevel = level;
		cachedSlime = slime;
		return slime;
	}
}
