package com.nobodiiiii.createbiotech.content.ghasthotairballoon;

import java.util.Collection;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ActorVisual;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;

import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class GhastHelmMovementBehaviour implements MovementBehaviour {

	static final PartialModel GHAST_HELM_COVER =
		PartialModel.of(CreateBiotech.asResource("block/ghast_helm/train/cover"));
	static final PartialModel GHAST_HELM_LEVER =
		PartialModel.of(CreateBiotech.asResource("block/ghast_helm/train/lever"));

	static class LeverAngles {
		LerpedFloat steering = LerpedFloat.linear();
		LerpedFloat altitude = LerpedFloat.linear();
		LerpedFloat speed = LerpedFloat.linear();
		LerpedFloat equipAnimation = LerpedFloat.linear();
	}

	@Override
	public ItemStack canBeDisabledVia(MovementContext context) {
		return null;
	}

	@Override
	public void stopMoving(MovementContext context) {
		context.contraption.entity.stopControlling(context.localPos);
		MovementBehaviour.super.stopMoving(context);
	}

	@Override
	public void tick(MovementContext context) {
		MovementBehaviour.super.tick(context);
		if (!context.world.isClientSide)
			return;
		getAngles(context).steering.tickChaser();
		getAngles(context).altitude.tickChaser();
		getAngles(context).speed.tickChaser();
		getAngles(context).equipAnimation.tickChaser();
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld,
		ContraptionMatrices matrices, MultiBufferSource buffer) {
		if (VisualizationManager.supportsVisualization(context.world))
			return;
		if (!(context.contraption.entity instanceof GhastHotAirBalloonEntity balloon))
			return;
		LeverAngles angles = getAngles(context);
		updateTargetAngles(context, balloon, angles);

		float pt = AnimationTickHolder.getPartialTicks();
		renderControls(context, renderWorld, matrices, buffer, angles.equipAnimation.getValue(pt),
			angles.speed.getValue(pt), angles.altitude.getValue(pt), angles.steering.getValue(pt));
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public ActorVisual createVisual(VisualizationContext visualizationContext, VirtualRenderWorld simulationWorld,
		MovementContext movementContext) {
		return new GhastHelmActorVisual(visualizationContext, simulationWorld, movementContext);
	}

	static LeverAngles getAngles(MovementContext context) {
		if (!(context.temporaryData instanceof LeverAngles))
			context.temporaryData = new LeverAngles();
		return (LeverAngles) context.temporaryData;
	}

	static void updateTargetAngles(MovementContext context, GhastHotAirBalloonEntity balloon, LeverAngles angles) {
		AbstractContraptionEntity entity = context.contraption.entity;
		Direction controlForward = balloon.getInitialOrientation().getOpposite();
		boolean inverted = context.state.hasProperty(ControlsBlock.FACING)
			&& !context.state.getValue(ControlsBlock.FACING).equals(controlForward);

		if (ControlsHandler.getContraption() == entity && ControlsHandler.getControlsPos() != null
			&& ControlsHandler.getControlsPos().equals(context.localPos)) {
			Collection<Integer> pressed = ControlsHandler.currentlyPressed;
			angles.equipAnimation.chase(1, .2f, Chaser.EXP);
			angles.steering.chase((pressed.contains(3) ? 1 : 0) + (pressed.contains(2) ? -1 : 0), 0.2f, Chaser.EXP);
			angles.altitude.chase((pressed.contains(5) ? 1 : 0) + (pressed.contains(4) ? -1 : 0), 0.2f,
				Chaser.EXP);
			float speedInput = (pressed.contains(1) ? 1 : 0) + (pressed.contains(0) ? -1 : 0);
			angles.speed.chase(inverted ? -speedInput : speedInput, 0.2f, Chaser.EXP);
			return;
		}

		angles.equipAnimation.chase(0, .2f, Chaser.EXP);
		angles.steering.chase(0, 0, Chaser.EXP);
		angles.altitude.chase(0, 0, Chaser.EXP);
		angles.speed.chase(0, 0, Chaser.EXP);
	}

	@OnlyIn(Dist.CLIENT)
	private static void renderControls(MovementContext context, VirtualRenderWorld renderWorld,
		ContraptionMatrices matrices, MultiBufferSource buffer, float equipAnimation, float firstLever,
		float middleLever, float thirdLever) {
		BlockState state = context.state;
		Direction facing = state.getValue(ControlsBlock.FACING);

		SuperByteBuffer cover = CachedBuffers.partial(GHAST_HELM_COVER, state);
		float hAngle = 180 + AngleHelper.horizontalAngle(facing);
		PoseStack ms = matrices.getModel();
		cover.transform(ms)
			.center()
			.rotateYDegrees(hAngle)
			.uncenter()
			.light(LevelRenderer.getLightColor(renderWorld, context.localPos))
			.useLevelLight(context.world, matrices.getWorld())
			.renderInto(matrices.getViewProjection(), buffer.getBuffer(net.minecraft.client.renderer.RenderType.cutoutMipped()));

		double yOffset = Mth.lerp(equipAnimation * equipAnimation, -0.15f, 0.05f);
		float[] leverValues = {firstLever, middleLever, thirdLever};
		float[] xOffsets = {0, 3 / 16f, 6 / 16f};

		for (int i = 0; i < leverValues.length; i++) {
			float leverValue = leverValues[i];
			float vAngle = Mth.clamp(leverValue * 15, -45, 45);
			SuperByteBuffer lever = CachedBuffers.partial(GHAST_HELM_LEVER, state);
			ms.pushPose();
			TransformStack.of(ms)
				.center()
				.rotateYDegrees(hAngle)
				.translate(0, 4 / 16f, 4 / 16f)
				.rotateXDegrees(vAngle - 45)
				.translate(0, yOffset, 0)
				.rotateXDegrees(45)
				.uncenter()
				.translate(0, -6 / 16f, -3 / 16f)
				.translate(xOffsets[i], 0, 0);
			lever.transform(ms)
				.light(LevelRenderer.getLightColor(renderWorld, context.localPos))
				.useLevelLight(context.world, matrices.getWorld())
				.renderInto(matrices.getViewProjection(), buffer.getBuffer(net.minecraft.client.renderer.RenderType.solid()));
			ms.popPose();
		}
	}
}
