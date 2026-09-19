package com.nobodiiiii.createbiotech.content.bionicmechanism;

import java.util.concurrent.ThreadLocalRandom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Gives every rendered living mechanism the same randomly-timed eye animation. */
public class BionicMechanismItemRenderer extends CustomRenderedItemModelRenderer {
	private static final long NANOS_PER_MILLISECOND = 1_000_000L;
	private static final int CLOSED_MIN_MILLISECONDS = 3_000;
	private static final int CLOSED_MAX_MILLISECONDS = 10_001;

	public static final ResourceLocation HALF_OPEN_MODEL_LOCATION =
		CreateBiotech.asResource("item/bionic_mechanism_half_open");
	public static final ResourceLocation LOOK_RIGHT_MODEL_LOCATION =
		CreateBiotech.asResource("item/bionic_mechanism_look_right");
	public static final ResourceLocation LOOK_CENTER_MODEL_LOCATION =
		CreateBiotech.asResource("item/bionic_mechanism_look_center");
	public static final ResourceLocation LOOK_LEFT_MODEL_LOCATION =
		CreateBiotech.asResource("item/bionic_mechanism_look_left");

	private static EyePhase eyePhase = EyePhase.CLOSED;
	private static long phaseEndNanos = System.nanoTime() + randomDurationNanos(
		CLOSED_MIN_MILLISECONDS, CLOSED_MAX_MILLISECONDS);

	@Override
	protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
		ItemDisplayContext transformType, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
		renderer.render(getCurrentModel(model.getOriginalModel()), light);
	}

	private static BakedModel getCurrentModel(BakedModel closedModel) {
		long now = System.nanoTime();
		if (now >= phaseEndNanos)
			advanceAnimation(now);

		ResourceLocation modelLocation = eyePhase.pose.modelLocation;
		return modelLocation == null ? closedModel : getModel(modelLocation, closedModel);
	}

	private static void advanceAnimation(long now) {
		switch (eyePhase) {
			case CLOSED -> setPhase(EyePhase.OPENING, now, 100);
			case OPENING -> setPhase(EyePhase.LOOK_RIGHT, now, randomDuration(650, 1_401));
			case LOOK_RIGHT -> setPhase(EyePhase.TURNING_LEFT, now, 140);
			case TURNING_LEFT -> setPhase(EyePhase.LOOK_LEFT, now, randomDuration(650, 1_401));
			case LOOK_LEFT -> {
				if (ThreadLocalRandom.current().nextInt(3) == 0)
					setPhase(EyePhase.BLINK_CLOSING, now, 75);
				else
					setPhase(EyePhase.TURNING_RIGHT, now, 140);
			}
			case BLINK_CLOSING -> setPhase(EyePhase.BLINK_CLOSED, now, 90);
			case BLINK_CLOSED -> setPhase(EyePhase.BLINK_OPENING, now, 75);
			case BLINK_OPENING -> setPhase(EyePhase.LOOK_LEFT_AFTER_BLINK, now, randomDuration(250, 701));
			case LOOK_LEFT_AFTER_BLINK -> setPhase(EyePhase.TURNING_RIGHT, now, 140);
			case TURNING_RIGHT -> setPhase(EyePhase.LOOK_RIGHT_AGAIN, now, randomDuration(350, 901));
			case LOOK_RIGHT_AGAIN -> setPhase(EyePhase.CLOSING, now, 100);
			case CLOSING -> setPhase(EyePhase.CLOSED, now,
				randomDuration(CLOSED_MIN_MILLISECONDS, CLOSED_MAX_MILLISECONDS));
		}
	}

	private static void setPhase(EyePhase nextPhase, long now, int durationMilliseconds) {
		eyePhase = nextPhase;
		phaseEndNanos = now + durationMilliseconds * NANOS_PER_MILLISECOND;
	}

	private static int randomDuration(int minimumInclusive, int maximumExclusive) {
		return ThreadLocalRandom.current().nextInt(minimumInclusive, maximumExclusive);
	}

	private static long randomDurationNanos(int minimumInclusive, int maximumExclusive) {
		return randomDuration(minimumInclusive, maximumExclusive) * NANOS_PER_MILLISECOND;
	}

	private static BakedModel getModel(ResourceLocation location, BakedModel fallback) {
		ModelManager modelManager = Minecraft.getInstance()
			.getModelManager();
		BakedModel model = modelManager.getModel(
			new ModelResourceLocation(location, ModelResourceLocation.STANDALONE_VARIANT));
		return model == modelManager.getMissingModel() ? fallback : model;
	}

	private enum EyePose {
		CLOSED(null),
		HALF_OPEN(HALF_OPEN_MODEL_LOCATION),
		RIGHT(LOOK_RIGHT_MODEL_LOCATION),
		CENTER(LOOK_CENTER_MODEL_LOCATION),
		LEFT(LOOK_LEFT_MODEL_LOCATION);

		private final ResourceLocation modelLocation;

		EyePose(ResourceLocation modelLocation) {
			this.modelLocation = modelLocation;
		}
	}

	private enum EyePhase {
		CLOSED(EyePose.CLOSED),
		OPENING(EyePose.HALF_OPEN),
		LOOK_RIGHT(EyePose.RIGHT),
		TURNING_LEFT(EyePose.CENTER),
		LOOK_LEFT(EyePose.LEFT),
		BLINK_CLOSING(EyePose.HALF_OPEN),
		BLINK_CLOSED(EyePose.CLOSED),
		BLINK_OPENING(EyePose.HALF_OPEN),
		LOOK_LEFT_AFTER_BLINK(EyePose.LEFT),
		TURNING_RIGHT(EyePose.CENTER),
		LOOK_RIGHT_AGAIN(EyePose.RIGHT),
		CLOSING(EyePose.HALF_OPEN);

		private final EyePose pose;

		EyePhase(EyePose pose) {
			this.pose = pose;
		}
	}
}
