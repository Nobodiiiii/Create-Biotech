package com.nobodiiiii.createbiotech.content.experience;

import com.mojang.blaze3d.vertex.PoseStack;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;

public class ExperienceTankRenderer implements BlockEntityRenderer<ExperienceTankBlockEntity> {

	private static final int XP_PER_ORB = 300;
	private static final float ORB_SPEED = 0.06f; // blocks per tick - constant
	private static final float ORB_TURN_RATE = 0.04f;
	private static final float EDGE_PADDING = 0.15f;
	private static final float HORIZONTAL_WANDER = 0.38f;
	private static final float VERTICAL_WANDER = 0.46f;

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

		BlockPos controllerPos = be.getBlockPos();
		int storedExperience = be.getStoredExperience();
		int height = be.getHeight();
		int width = be.getWidth();
		int orbCount = Math.max(1, storedExperience / XP_PER_ORB);
		float ageTicks = AnimationTickHolder.getRenderTime(level);

		float xMin = EDGE_PADDING;
		float xMax = width - EDGE_PADDING;
		float yMin = EDGE_PADDING;
		float yMax = height - EDGE_PADDING;
		float zMin = EDGE_PADDING;
		float zMax = width - EDGE_PADDING;
		float time = ageTicks * ORB_SPEED;
		Quaternionf billboardRotation = createBillboardRotation(level, partialTick);

		for (int i = 0; i < orbCount; i++) {
			float seedA = i * 13.7f;
			float seedB = i * 17.3f + 5.1f;
			float seedC = i * 23.1f + 11.7f;
			float seedD = i * 29.7f + 7.3f;
			float seedE = i * 31.3f + 17.9f;
			float seedF = i * 37.9f + 23.5f;

			float baseX = radicalInverse(i + 1, 2);
			float baseY = radicalInverse(i + 1, 3);
			float baseZ = radicalInverse(i + 1, 5);

			float nx = animatedCoordinate(baseX, time * (0.93f + (i % 5) * 0.11f), seedA, seedB, HORIZONTAL_WANDER);
			float ny = animatedCoordinate(baseY, time * (0.71f + (i % 7) * 0.09f), seedC, seedD, VERTICAL_WANDER);
			float nz = animatedCoordinate(baseZ, time * (1.08f + (i % 6) * 0.08f), seedE, seedF, HORIZONTAL_WANDER);

			double x = xMin + nx * (xMax - xMin);
			double y = yMin + ny * (yMax - yMin);
			double z = zMin + nz * (zMax - zMin);

			BlockPos orbPos = new BlockPos(
				controllerPos.getX() + Mth.floor(x),
				controllerPos.getY() + Mth.floor(y),
				controllerPos.getZ() + Mth.floor(z));
			int orbLight = LevelRenderer.getLightColor(level, orbPos);

			int icon = (int) ((seedA * 0.37f) + i) & 0x0F;

			poseStack.pushPose();
			poseStack.translate(x, y, z);
			ExperienceOrbModelRenderer.render(poseStack, buffer, orbLight, ageTicks * ORB_TURN_RATE * 25f + seedA,
				icon, 1.0f, billboardRotation);
			poseStack.popPose();
		}
	}

	private static Quaternionf createBillboardRotation(Level level, float partialTick) {
		if (!(level instanceof PonderLevel ponderLevel) || ponderLevel.scene == null)
			return null;

		PonderScene.SceneTransform transform = ponderLevel.scene.getTransform();
		PonderScene.SceneCamera camera = new PonderScene.SceneCamera();
		camera.set(transform.xRotation.getValue(partialTick) + 90.0f, transform.yRotation.getValue(partialTick) + 180.0f);
		return new Quaternionf(camera.rotation());
	}

	private static float animatedCoordinate(float base, float time, float phaseA, float phaseB, float amplitude) {
		float offset = (float) Math.sin(time + phaseA) * amplitude;
		offset += (float) Math.sin(time * 0.61f + phaseB) * amplitude * 0.55f;
		return reflectUnit(base + offset);
	}

	private static float reflectUnit(float value) {
		float wrapped = value % 2.0f;
		if (wrapped < 0)
			wrapped += 2.0f;
		return wrapped > 1.0f ? 2.0f - wrapped : wrapped;
	}

	private static float radicalInverse(int index, int base) {
		float reversed = 0.0f;
		float inverseBase = 1.0f / base;
		float placeValue = inverseBase;
		int value = index;

		while (value > 0) {
			reversed += (value % base) * placeValue;
			value /= base;
			placeValue *= inverseBase;
		}

		return reversed;
	}
}
