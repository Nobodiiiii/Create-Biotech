package com.nobodiiiii.createbiotech.content.experience;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.Level;

public class ExperienceTankRenderer implements BlockEntityRenderer<ExperienceTankBlockEntity> {

	public ExperienceTankRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(ExperienceTankBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
		int packedLight, int packedOverlay) {
		if (!be.isController() || !be.hasWindow() || be.getStoredExperience() <= 0)
			return;
		Level level = be.getLevel();
		if (level == null)
			return;

		float fill = be.getFillState();
		int height = be.getHeight();
		int width = be.getWidth();
		int volume = width * width * height;
		int orbCount = Math.max(1, Math.min(48, (int) Math.ceil(fill * volume * 6.0f)));
		float time = (level.getGameTime() + partialTick) * 0.22f;
		float ageTicks = level.getGameTime() + partialTick;
		double halfWidth = width * 0.5d;
		double radius = Math.max(0.27d, width * 0.5d - 0.23d);

		for (int i = 0; i < orbCount; i++) {
			float seed = i * 19.371f;
			double x = halfWidth + Math.sin(time * 1.7f + seed) * radius;
			double z = halfWidth + Math.cos(time * 1.3f + seed * 0.7f) * radius;
			double y = 0.25d + Math.abs(Math.sin(time * 2.1f + seed * 0.31f)) * Math.max(0.2d, height - 0.5d);

			int icon = (int) ((seed * 0.37f) + i) & 0x0F;

			poseStack.pushPose();
			poseStack.translate(x, Math.min(height - 0.18d, y), z);
			ExperienceOrbModelRenderer.render(poseStack, buffer, packedLight, ageTicks + seed, icon, 1.0f);
			poseStack.popPose();
		}
	}
}
