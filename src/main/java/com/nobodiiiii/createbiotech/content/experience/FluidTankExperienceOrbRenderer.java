package com.nobodiiiii.createbiotech.content.experience;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;

import org.joml.Quaternionf;

public final class FluidTankExperienceOrbRenderer {
	private static final int FLUID_PER_ORB = 300;
	private static final int MAX_ORBS = 384;
	private static final float ORB_SPEED = 0.06f;
	private static final float ORB_TURN_RATE = 0.04f;
	private static final float EDGE_PADDING = 0.15f;
	private static final float HORIZONTAL_WANDER = 0.38f;
	private static final float VERTICAL_WANDER = 0.46f;

	private FluidTankExperienceOrbRenderer() {
	}

	public static boolean render(FluidTankBlockEntity be, FluidStack fluidStack, float partialTicks, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, float xMin, float yMin, float zMin, float xMax, float yMax,
		float zMax) {
		Level level = be.getLevel();
		if (level == null)
			return false;

		int fluidAmount = fluidStack.getAmount();
		if (fluidAmount <= 0)
			return false;

		float fullYMin = fullTankYMin(be, fluidStack, yMin, yMax);
		float fullYMax = fullYMin + renderableTankHeight(be);

		float x0 = paddedMin(xMin, xMax);
		float x1 = paddedMax(xMin, xMax);
		float y0 = paddedMin(fullYMin, fullYMax);
		float y1 = paddedMax(fullYMin, fullYMax);
		float z0 = paddedMin(zMin, zMax);
		float z1 = paddedMax(zMin, zMax);
		if (x1 < x0 || y1 < y0 || z1 < z0)
			return false;

		int maxOrbsForTank = Math.min(MAX_ORBS, Math.max(24, be.getTotalTankSize() * 12));
		int orbCount = Math.max(1, Math.min(maxOrbsForTank, fluidAmount / FLUID_PER_ORB));
		float ageTicks = AnimationTickHolder.getRenderTime(level);
		float time = ageTicks * ORB_SPEED;
		Quaternionf billboardRotation = createBillboardRotation(level, partialTicks);

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

			float nx = animatedCoordinate(baseX, time * (0.93f + (i % 5) * 0.11f), seedA, seedB,
				HORIZONTAL_WANDER);
			float ny = animatedCoordinate(baseY, time * (0.71f + (i % 7) * 0.09f), seedC, seedD,
				VERTICAL_WANDER);
			float nz = animatedCoordinate(baseZ, time * (1.08f + (i % 6) * 0.08f), seedE, seedF,
				HORIZONTAL_WANDER);

			float x = Mth.lerp(nx, x0, x1);
			float y = Mth.lerp(ny, y0, y1);
			float z = Mth.lerp(nz, z0, z1);
			int icon = (int) (seedA * 0.37f + i) & 0x0F;

			poseStack.pushPose();
			poseStack.translate(x, y, z);
			ExperienceOrbModelRenderer.render(poseStack, buffer, packedLight, ageTicks * ORB_TURN_RATE * 25f + seedA,
				icon, 1.0f, billboardRotation);
			poseStack.popPose();
		}

		return true;
	}

	private static float paddedMin(float min, float max) {
		return min + padding(min, max);
	}

	private static float paddedMax(float min, float max) {
		return max - padding(min, max);
	}

	private static float padding(float min, float max) {
		return Math.min(EDGE_PADDING, Math.max(0, max - min) * 0.35f);
	}

	private static float fullTankYMin(FluidTankBlockEntity be, FluidStack fluidStack, float yMin, float yMax) {
		float visibleHeight = renderableTankHeight(be);
		if (!fluidStack.getFluid()
			.getFluidType()
			.isLighterThanAir())
			return yMin;
		return yMax - visibleHeight;
	}

	private static float renderableTankHeight(FluidTankBlockEntity be) {
		float capHeight = 1 / 4f;
		float minPuddleHeight = 1 / 16f;
		return Math.max(0, be.getHeight() - 2 * capHeight - minPuddleHeight);
	}

	private static Quaternionf createBillboardRotation(Level level, float partialTicks) {
		if (!(level instanceof PonderLevel ponderLevel) || ponderLevel.scene == null)
			return null;

		PonderScene.SceneTransform transform = ponderLevel.scene.getTransform();
		PonderScene.SceneCamera camera = new PonderScene.SceneCamera();
		camera.set(transform.xRotation.getValue(partialTicks) + 90.0f,
			transform.yRotation.getValue(partialTicks) + 180.0f);
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
