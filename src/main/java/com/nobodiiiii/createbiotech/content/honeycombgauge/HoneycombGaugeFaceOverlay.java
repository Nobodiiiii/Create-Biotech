package com.nobodiiiii.createbiotech.content.honeycombgauge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.model.data.ModelData;

/** A black half-alpha layer over exactly one honeycomb face, owned by the cluster. */
final class HoneycombGaugeFaceOverlay {
	private static final float EPSILON = 0.002f;
	private static final float[][][] VERTICES = {
		{{0, -EPSILON, 0}, {1, -EPSILON, 0}, {1, -EPSILON, 1}, {0, -EPSILON, 1}}, // down
		{{0, 1 + EPSILON, 0}, {0, 1 + EPSILON, 1}, {1, 1 + EPSILON, 1}, {1, 1 + EPSILON, 0}}, // up
		{{0, 0, -EPSILON}, {0, 1, -EPSILON}, {1, 1, -EPSILON}, {1, 0, -EPSILON}}, // north
		{{0, 0, 1 + EPSILON}, {1, 0, 1 + EPSILON}, {1, 1, 1 + EPSILON}, {0, 1, 1 + EPSILON}}, // south
		{{-EPSILON, 0, 0}, {-EPSILON, 0, 1}, {-EPSILON, 1, 1}, {-EPSILON, 1, 0}}, // west
		{{1 + EPSILON, 0, 0}, {1 + EPSILON, 1, 0}, {1 + EPSILON, 1, 1}, {1 + EPSILON, 0, 1}} // east
	};

	private HoneycombGaugeFaceOverlay() {}

	static void render(PoseStack poseStack, MultiBufferSource buffer, Direction face, int light) {
		TextureAtlasSprite sprite = Minecraft.getInstance().getBlockRenderer()
			.getBlockModel(Blocks.HONEYCOMB_BLOCK.defaultBlockState()).getParticleIcon(ModelData.EMPTY);
		VertexConsumer vertices = buffer.getBuffer(RenderType.translucent());
		float[][] corners = VERTICES[face.get3DDataValue()];
		for (int i = 0; i < corners.length; i++) {
			float[] corner = corners[i];
			vertices.addVertex(poseStack.last().pose(), corner[0], corner[1], corner[2])
				.setColor(0, 0, 0, 128)
				.setUv(i < 2 ? sprite.getU0() : sprite.getU1(),
					i == 0 || i == 3 ? sprite.getV0() : sprite.getV1())
				.setUv1(OverlayTexture.NO_OVERLAY & 0xffff, OverlayTexture.NO_OVERLAY >>> 16)
				.setUv2(light & 0xffff, light >>> 16)
				.setNormal(face.getStepX(), face.getStepY(), face.getStepZ());
		}
	}
}
