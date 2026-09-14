package com.nobodiiiii.createbiotech.content.automaticfishreleasemachine;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModel;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.waterwheel.LargeWaterWheelBlock;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelRenderer;
import com.simibubi.create.foundation.fluid.FluidHelper;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;

public class AutomaticFishReleaseMachineRenderer
	extends WaterWheelRenderer<AutomaticFishReleaseMachineBlockEntity> {

	public static final ResourceLocation BLADE_CLAMP_MODEL_LOCATION =
		CreateBiotech.asResource("block/automatic_fish_release_machine/blade_clamp");
	static final PartialModel BLADE_CLAMP = PartialModel.of(BLADE_CLAMP_MODEL_LOCATION);
	static final ResourceLocation SALMON_TEXTURE =
		ResourceLocation.withDefaultNamespace("textures/entity/fish/salmon.png");
	static final int BLADE_COUNT = 16;
	static final float FISH_RING_RADIUS = 2.47f;
	static final float FISH_SCALE = 0.8f;
	static final float FISH_IN_PLANE_ROTATION = -12.25f;
	static final float FISH_TAIL_OFFSET = 1.0f / 16.0f;
	private static final float FISH_LENGTH_CENTRE = 6.5f / 16.0f;
	private static final float FISH_MODEL_Y_OFFSET = -1.501f;
	private static final float FISH_HEAD_Y = 20.0f / 16.0f;
	private static final float FISH_HEAD_Z = -1.5f / 16.0f;
	private static final float SWIM_TAIL_AMPLITUDE = 0.4f;
	private static final float SWIM_TAIL_SPEED = 0.8f;
	static final float CARDINAL_BLADE_CLAMP_RADIUS = 2.125f;
	static final float INTERMEDIATE_BLADE_CLAMP_RADIUS = 2.1875f;
	static final float BLADE_CLAMP_OUTWARD_OFFSET = 6.0f / 16.0f;
	static final float SLOT_ANGLE = 360.0f / BLADE_COUNT;
	static final float FIRST_GAP_ANGLE = SLOT_ANGLE / 2.0f;
	private static final float MAX_MERIT_TEXT_SPEED = 16.0f;
	private static final float MERIT_TEXT_LIFETIME = 24.0f;
	private static final Component MERIT_TEXT = Component.literal("功德+1");

	private final MachineCreatureModel fishModel;
	private final ModelPart fishBodyBack;
	private final Map<AutomaticFishReleaseMachineBlockEntity, FishRenderState> fishRenderStates = new WeakHashMap<>();

	public AutomaticFishReleaseMachineRenderer(BlockEntityRendererProvider.Context context) {
		super(context, true);
		fishModel = MachineCreatureModels.salmon();
		fishBodyBack = fishModel.root()
			.getChild("body_back");
	}

	@Override
	protected void renderSafe(AutomaticFishReleaseMachineBlockEntity blockEntity, float partialTicks,
		PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
		super.renderSafe(blockEntity, partialTicks, poseStack, buffer, light, overlay);

		Direction.Axis rotationAxis = blockEntity.getBlockState()
			.getValue(LargeWaterWheelBlock.AXIS);
		float wheelAngle =
			KineticBlockEntityRenderer.getAngleForBe(blockEntity, blockEntity.getBlockPos(), rotationAxis);
		Level level = blockEntity.getLevel();
		double renderTime = level == null ? 0 : level.getGameTime() + partialTicks;
		float animationTime = (float) (renderTime % 10000.0);
		FishRenderState renderState = fishRenderStates.computeIfAbsent(blockEntity, ignored -> new FishRenderState());
		float speed = blockEntity.getSpeed();
		if (speed != 0)
			renderState.rotationDirection = Math.signum(speed);

		poseStack.pushPose();
		poseStack.translate(0.5f, 0.5f, 0.5f);
		alignVerticalModelToAxis(poseStack, rotationAxis);
		poseStack.mulPose(Axis.YP.rotation(wheelAngle));

		for (int fishIndex = 0; fishIndex < BLADE_COUNT; fishIndex++) {
			float gapAngle = FIRST_GAP_ANGLE + fishIndex * SLOT_ANGLE;
			Vector3f fishOffset = getFishOffset(rotationAxis, wheelAngle, gapAngle);
			boolean inWater = isFishInWater(blockEntity, rotationAxis, fishOffset);
			boolean reverseDirection = renderState.rotationDirection < 0;
			if (renderState.initialized && !renderState.swimming[fishIndex] && inWater
				&& shouldSpawnMeritText(renderState, speed))
				renderState.meritTexts.add(new FloatingMeritText(
					getFishHeadOffset(rotationAxis, wheelAngle, gapAngle, reverseDirection), renderTime));
			renderState.swimming[fishIndex] = inWater;
			renderFishInGap(poseStack, buffer, light, overlay, gapAngle, reverseDirection, inWater,
				animationTime + fishIndex * 1.5f);
		}
		renderState.initialized = true;
		for (int bladeIndex = 0; bladeIndex < BLADE_COUNT; bladeIndex++)
			renderBladeClamp(blockEntity, poseStack, buffer, light, bladeIndex);

		poseStack.popPose();
		renderMeritTexts(renderState, poseStack, buffer, renderTime);
	}

	private void renderFishInGap(PoseStack poseStack, MultiBufferSource buffer, int light, int overlay,
		float gapAngle, boolean reverseDirection, boolean inWater, float animationTime) {
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(gapAngle));
		poseStack.translate(0, 0, -FISH_RING_RADIUS);

		// Salmon models are long on Z, tall on Y and thin on X. This cyclic rotation
		// keeps them parallel to the tangent blades, with their bellies facing the
		// wheel centre and their thin dimension aligned with its axle.
		poseStack.mulPose(Axis.YP.rotationDegrees(FISH_IN_PLANE_ROTATION));
		poseStack.mulPose(Axis.YP.rotationDegrees(90));
		poseStack.mulPose(Axis.ZP.rotationDegrees(-90));
		poseStack.translate(0, 0, FISH_TAIL_OFFSET);
		poseStack.scale(-FISH_SCALE, -FISH_SCALE, FISH_SCALE);
		if (reverseDirection) {
			poseStack.translate(0, 0, FISH_LENGTH_CENTRE);
			poseStack.mulPose(Axis.YP.rotationDegrees(180));
			poseStack.translate(0, 0, -FISH_LENGTH_CENTRE);
		}
		poseStack.translate(0, FISH_MODEL_Y_OFFSET, 0);

		fishBodyBack.yRot =
			inWater ? -SWIM_TAIL_AMPLITUDE * Mth.sin(SWIM_TAIL_SPEED * animationTime) : 0;
		fishModel.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(SALMON_TEXTURE)),
			light, overlay, -1);
		poseStack.popPose();
	}

	private static Vector3f getFishOffset(Direction.Axis rotationAxis, float wheelAngle, float gapAngle) {
		Vector3f fishOffset = new Vector3f(0, 0, -FISH_RING_RADIUS)
			.rotateY(wheelAngle + (float) Math.toRadians(gapAngle));
		switch (rotationAxis) {
		case X -> fishOffset.rotateZ(-Mth.HALF_PI);
		case Z -> fishOffset.rotateX(Mth.HALF_PI);
		default -> {
		}
		}
		return fishOffset;
	}

	private static Vector3f getFishHeadOffset(Direction.Axis rotationAxis, float wheelAngle, float gapAngle,
		boolean reverseDirection) {
		// Mirror the fish render transforms from the vanilla salmon head cube centre.
		Vector3f headOffset = new Vector3f(0, FISH_HEAD_Y + FISH_MODEL_Y_OFFSET, FISH_HEAD_Z);
		if (reverseDirection)
			headOffset.sub(0, 0, FISH_LENGTH_CENTRE)
				.rotateY(Mth.PI)
				.add(0, 0, FISH_LENGTH_CENTRE);

		headOffset.mul(-FISH_SCALE, -FISH_SCALE, FISH_SCALE)
			.add(0, 0, FISH_TAIL_OFFSET)
			.rotateZ(-Mth.HALF_PI)
			.rotateY(Mth.HALF_PI)
			.rotateY((float) Math.toRadians(FISH_IN_PLANE_ROTATION))
			.add(0, 0, -FISH_RING_RADIUS)
			.rotateY(wheelAngle + (float) Math.toRadians(gapAngle));
		switch (rotationAxis) {
		case X -> headOffset.rotateZ(-Mth.HALF_PI);
		case Z -> headOffset.rotateX(Mth.HALF_PI);
		default -> {
		}
		}
		return headOffset;
	}

	private static boolean isFishInWater(AutomaticFishReleaseMachineBlockEntity blockEntity,
		Direction.Axis rotationAxis, Vector3f fishOffset) {
		Level level = blockEntity.getLevel();
		if (level == null)
			return false;

		BlockPos wheelPos = blockEntity.getBlockPos();
		BlockPos directSample = BlockPos.containing(
			wheelPos.getX() + 0.5 + fishOffset.x,
			wheelPos.getY() + 0.5 + fishOffset.y,
			wheelPos.getZ() + 0.5 + fishOffset.z);
		if (isWater(level, directSample))
			return true;

		BlockPos nearestOffset = null;
		float nearestDistance = Float.MAX_VALUE;
		for (BlockPos offset : WaterWheelBlockEntity.LARGE_OFFSETS.get(rotationAxis)) {
			float distance = fishOffset.distanceSquared(offset.getX(), offset.getY(), offset.getZ());
			if (distance >= nearestDistance)
				continue;
			nearestDistance = distance;
			nearestOffset = offset;
		}

		if (nearestOffset == null)
			return false;
		return isWater(level, wheelPos.offset(nearestOffset));
	}

	private static boolean isWater(Level level, BlockPos pos) {
		FluidState fluidState = level.getFluidState(pos);
		return fluidState.is(FluidTags.WATER) || FluidHelper.isWater(fluidState.getType());
	}

	private static boolean shouldSpawnMeritText(FishRenderState renderState, float speed) {
		float absoluteSpeed = Math.abs(speed);
		if (absoluteSpeed <= MAX_MERIT_TEXT_SPEED) {
			renderState.meritEmissionRemainder = 0;
			return true;
		}

		renderState.meritEmissionRemainder += MAX_MERIT_TEXT_SPEED / absoluteSpeed;
		if (renderState.meritEmissionRemainder < 1)
			return false;
		renderState.meritEmissionRemainder -= 1;
		return true;
	}

	private static void renderMeritTexts(FishRenderState renderState, PoseStack poseStack, MultiBufferSource buffer,
		double renderTime) {
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		Iterator<FloatingMeritText> iterator = renderState.meritTexts.iterator();
		while (iterator.hasNext()) {
			FloatingMeritText floatingText = iterator.next();
			float age = (float) (renderTime - floatingText.spawnTime);
			if (age >= MERIT_TEXT_LIFETIME) {
				iterator.remove();
				continue;
			}
			if (age < 0)
				continue;

			float progress = age / MERIT_TEXT_LIFETIME;
			float fade = 1.0f - Mth.clamp((progress - 0.35f) / 0.65f, 0, 1);
			int alpha = Mth.clamp((int) (fade * 255), 0, 255);
			if (alpha < Font.ALPHA_CUTOFF) {
				iterator.remove();
				continue;
			}
			int color = alpha << 24 | 0xFFE45A;

			poseStack.pushPose();
			poseStack.translate(
				0.5f + floatingText.offset.x,
				0.5f + floatingText.offset.y + progress * 0.75f,
				0.5f + floatingText.offset.z);
			poseStack.mulPose(minecraft.getEntityRenderDispatcher()
				.cameraOrientation());
			poseStack.scale(0.02f, -0.02f, 0.02f);
			Matrix4f matrix = poseStack.last()
				.pose();
			float textX = -font.width(MERIT_TEXT) / 2.0f;
			font.drawInBatch(MERIT_TEXT, textX, 0, color, false, matrix, buffer, Font.DisplayMode.NORMAL, 0,
				LightTexture.FULL_BRIGHT);
			poseStack.popPose();
		}
	}

	private static void renderBladeClamp(AutomaticFishReleaseMachineBlockEntity blockEntity, PoseStack poseStack,
		MultiBufferSource buffer, int light, int bladeIndex) {
		float clampRadius =
			((bladeIndex & 1) == 0 ? CARDINAL_BLADE_CLAMP_RADIUS : INTERMEDIATE_BLADE_CLAMP_RADIUS)
				+ BLADE_CLAMP_OUTWARD_OFFSET;
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(bladeIndex * SLOT_ANGLE));
		poseStack.translate(-0.5f, -0.5f, -clampRadius - 0.5f);
		CachedBuffers.partial(BLADE_CLAMP, blockEntity.getBlockState())
			.light(light)
			.renderInto(poseStack, buffer.getBuffer(RenderType.solid()));
		poseStack.popPose();
	}

	private static void alignVerticalModelToAxis(PoseStack poseStack, Direction.Axis axis) {
		switch (axis) {
		case X -> poseStack.mulPose(Axis.ZP.rotationDegrees(-90));
		case Z -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
		default -> {
		}
		}
	}

	private static class FishRenderState {
		private final boolean[] swimming = new boolean[BLADE_COUNT];
		private final List<FloatingMeritText> meritTexts = new ArrayList<>();
		private float meritEmissionRemainder;
		private float rotationDirection = 1;
		private boolean initialized;
	}

	private record FloatingMeritText(Vector3f offset, double spawnTime) {
	}
}
