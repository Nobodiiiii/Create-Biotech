package com.nobodiiiii.createbiotech.compat.jei;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.joml.Quaternionf;

@OnlyIn(Dist.CLIENT)
public class CapturedSmallSlimeJeiItemRenderer extends BlockEntityWithoutLevelRenderer {
	private static final int GUI_LIGHT = 15728880;
	private static final float ANGLE_X = 0.75f;
	private static final float ANGLE_Y = 0.6f;

	private static final IClientItemExtensions ITEM_EXTENSIONS = new IClientItemExtensions() {
		private CapturedSmallSlimeJeiItemRenderer renderer;

		@Override
		public BlockEntityWithoutLevelRenderer getCustomRenderer() {
			if (renderer == null)
				renderer = new CapturedSmallSlimeJeiItemRenderer();
			return renderer;
		}
	};

	private final SlimeEntityDrawable slimeDrawable =
		new SlimeEntityDrawable(16, 16, 10, 2, ANGLE_X, ANGLE_Y, -1, EntityType.SLIME);

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

		Slime slime = slimeDrawable.getOrCreateSlime(level);
		if (slime == null)
			return;

		poseStack.pushPose();
		applyTransform(displayContext, poseStack);

		boolean gui = displayContext == ItemDisplayContext.GUI;
		if (gui)
			Lighting.setupForEntityInInventory();

		renderSlime(slime, poseStack, buffer, gui ? GUI_LIGHT : packedLight);

		if (gui)
			Lighting.setupFor3DItems();
		poseStack.popPose();
	}

	private static void applyTransform(ItemDisplayContext displayContext, PoseStack poseStack) {
		if (displayContext == ItemDisplayContext.GUI) {
			poseStack.translate(0.5f, 0.2f, 0.5f);
			poseStack.scale(0.7f, 0.7f, -0.7f);
			return;
		}
		poseStack.translate(0.5f, 0.45f, 0.5f);
		float scale = displayContext == ItemDisplayContext.GROUND ? 0.75f : 0.55f;
		poseStack.scale(scale, scale, -scale);
	}

	private static void renderSlime(Slime slime, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf camera = new Quaternionf().rotateX(ANGLE_Y * 20.0f * ((float) Math.PI / 180.0f));
		pose.mul(camera);
		pose.rotateZ((float) Math.PI);

		float bodyRot = slime.yBodyRot;
		float yRot = slime.getYRot();
		float xRot = slime.getXRot();
		float headRotO = slime.yHeadRotO;
		float headRot = slime.yHeadRot;

		slime.yBodyRot = 180.0f + ANGLE_X * 20.0f;
		slime.setYRot(180.0f + ANGLE_X * 40.0f);
		slime.setXRot(-ANGLE_Y * 20.0f);
		slime.yHeadRot = slime.getYRot();
		slime.yHeadRotO = slime.getYRot();

		poseStack.mulPose(pose);

		EntityRenderDispatcher dispatcher = Minecraft.getInstance()
			.getEntityRenderDispatcher();
		Quaternionf previousCamera = dispatcher.cameraOrientation() == null ? null
			: new Quaternionf(dispatcher.cameraOrientation());
		Quaternionf cameraOverride = new Quaternionf(camera);
		cameraOverride.conjugate();
		dispatcher.overrideCameraOrientation(cameraOverride);
		dispatcher.setRenderShadow(false);
		RenderSystem.runAsFancy(() -> dispatcher.render(slime, 0, 0, 0, 0, 1, poseStack, buffer, packedLight));
		dispatcher.setRenderShadow(true);
		if (previousCamera != null)
			dispatcher.overrideCameraOrientation(previousCamera);

		slime.yBodyRot = bodyRot;
		slime.setYRot(yRot);
		slime.setXRot(xRot);
		slime.yHeadRotO = headRotO;
		slime.yHeadRot = headRot;
	}
}
