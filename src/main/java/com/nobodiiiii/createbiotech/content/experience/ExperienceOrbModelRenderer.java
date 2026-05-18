package com.nobodiiiii.createbiotech.content.experience;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class ExperienceOrbModelRenderer {
	private static final ResourceLocation TEXTURE = new ResourceLocation("textures/entity/experience_orb.png");
	private static final RenderType RENDER_TYPE = RenderType.entityTranslucent(TEXTURE);

	private ExperienceOrbModelRenderer() {}

	public static void render(PoseStack ms, MultiBufferSource buffer, int packedLight, float ageTicks, int iconValue,
		float scale) {
		int i = Math.max(0, Math.min(15, iconValue));
		float u0 = (i % 4 * 16) / 64.0F;
		float u1 = (i % 4 * 16 + 16) / 64.0F;
		float v0 = (i / 4 * 16) / 64.0F;
		float v1 = (i / 4 * 16 + 16) / 64.0F;

		float half = ageTicks / 2.0F;
		int red = (int) ((Mth.sin(half) + 1.0F) * 0.5F * 255.0F);
		int green = 255;
		int blue = (int) ((Mth.sin(half + 4.1887903F) + 1.0F) * 0.1F * 255.0F);

		ms.pushPose();
		ms.mulPose(Minecraft.getInstance()
			.getEntityRenderDispatcher()
			.cameraOrientation());
		ms.mulPose(Axis.YP.rotationDegrees(180.0F));
		float renderScale = scale * 0.3F;
		ms.scale(renderScale, renderScale, renderScale);
		VertexConsumer consumer = buffer.getBuffer(RENDER_TYPE);
		PoseStack.Pose pose = ms.last();
		Matrix4f matrix = pose.pose();
		Matrix3f normal = pose.normal();
		quad(consumer, matrix, normal, -0.5F, -0.25F, red, green, blue, u0, v1, packedLight);
		quad(consumer, matrix, normal, 0.5F, -0.25F, red, green, blue, u1, v1, packedLight);
		quad(consumer, matrix, normal, 0.5F, 0.75F, red, green, blue, u1, v0, packedLight);
		quad(consumer, matrix, normal, -0.5F, 0.75F, red, green, blue, u0, v0, packedLight);
		ms.popPose();
	}

	private static void quad(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, float x, float y, int r, int g,
		int b, float u, float v, int packedLight) {
		consumer.vertex(matrix, x, y, 0.0F)
			.color(r, g, b, 128)
			.uv(u, v)
			.overlayCoords(OverlayTexture.NO_OVERLAY)
			.uv2(packedLight)
			.normal(normal, 0.0F, 1.0F, 0.0F)
			.endVertex();
	}
}
