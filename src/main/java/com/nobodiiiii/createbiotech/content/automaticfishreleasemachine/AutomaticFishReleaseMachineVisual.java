package com.nobodiiiii.createbiotech.content.automaticfishreleasemachine;

import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModel;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModels;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureVisualModel;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.waterwheel.LargeWaterWheelBlock;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelVisual;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/** Large-water-wheel visual plus the sixteen articulated block-model salmon. */
public class AutomaticFishReleaseMachineVisual
	extends WaterWheelVisual<AutomaticFishReleaseMachineBlockEntity> implements SimpleDynamicVisual {

	private final MachineCreatureVisualModel[] fish;
	private float rotationDirection = 1;

	public AutomaticFishReleaseMachineVisual(VisualizationContext context,
		AutomaticFishReleaseMachineBlockEntity blockEntity, float partialTick) {
		super(context, blockEntity, true, partialTick);
		fish = new MachineCreatureVisualModel[AutomaticFishReleaseMachineRenderer.BLADE_COUNT];
		for (int i = 0; i < fish.length; i++) {
			fish[i] = new MachineCreatureVisualModel(instancerProvider(), MachineCreatureModels.salmon());
		}
		updateFish(partialTick);
		updateLight(partialTick);
	}

	@Override
	public void beginFrame(DynamicVisual.Context context) {
		updateFish(context.partialTick());
	}

	@Override
	public void updateLight(float partialTick) {
		super.updateLight(partialTick);
		int light = computePackedLight();
		for (MachineCreatureVisualModel model : fish)
			model.light(light);
	}

	@Override
	protected void _delete() {
		super._delete();
		for (MachineCreatureVisualModel model : fish)
			model.delete();
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		super.collectCrumblingInstances(consumer);
		for (MachineCreatureVisualModel model : fish)
			model.collectCrumblingInstances(consumer);
	}

	private void updateFish(float partialTick) {
		Direction.Axis axis = blockState.getValue(LargeWaterWheelBlock.AXIS);
		float wheelAngle = KineticBlockEntityRenderer.getAngleForBe(blockEntity,
			blockEntity.getBlockPos(), axis);
		if (blockEntity.getSpeed() != 0)
			rotationDirection = Math.signum(blockEntity.getSpeed());
		boolean reverse = rotationDirection < 0;
		float animationTime = blockEntity.getLevel() == null ? partialTick
			: blockEntity.getLevel().getGameTime() % 10_000L + partialTick;

		for (int index = 0; index < fish.length; index++) {
			float gapAngle = AutomaticFishReleaseMachineRenderer.FIRST_GAP_ANGLE
				+ index * AutomaticFishReleaseMachineRenderer.SLOT_ANGLE;
			Vector3f fishOffset = new Vector3f(0, 0, -AutomaticFishReleaseMachineRenderer.FISH_RING_RADIUS)
				.rotateY(wheelAngle + Mth.DEG_TO_RAD * gapAngle);
			switch (axis) {
			case X -> fishOffset.rotateZ(-Mth.HALF_PI);
			case Z -> fishOffset.rotateX(Mth.HALF_PI);
			default -> { }
			}
			boolean inWater = AutomaticFishReleaseMachineRenderer.isFishInWater(blockEntity, axis, fishOffset);
			MachineCreatureModel model = fish[index].model();
			model.resetPose();
			model.part("body_back").yRot = inWater
				? -AutomaticFishReleaseMachineRenderer.SWIM_TAIL_AMPLITUDE
					* Mth.sin(AutomaticFishReleaseMachineRenderer.SWIM_TAIL_SPEED
						* (animationTime + index * 1.5f))
				: 0;
			PoseStack pose = wheelPose(axis, wheelAngle);
			pose.mulPose(Axis.YP.rotationDegrees(gapAngle));
			pose.translate(0, 0, -AutomaticFishReleaseMachineRenderer.FISH_RING_RADIUS);
			pose.mulPose(Axis.YP.rotationDegrees(AutomaticFishReleaseMachineRenderer.FISH_IN_PLANE_ROTATION));
			pose.mulPose(Axis.YP.rotationDegrees(90));
			pose.mulPose(Axis.ZP.rotationDegrees(-90));
			pose.translate(0, 0, AutomaticFishReleaseMachineRenderer.FISH_TAIL_OFFSET);
			float scale = AutomaticFishReleaseMachineRenderer.FISH_SCALE;
			pose.scale(-scale, -scale, scale);
			if (reverse) {
				pose.translate(0, 0, AutomaticFishReleaseMachineRenderer.FISH_LENGTH_CENTRE);
				pose.mulPose(Axis.YP.rotationDegrees(180));
				pose.translate(0, 0, -AutomaticFishReleaseMachineRenderer.FISH_LENGTH_CENTRE);
			}
			pose.translate(0, AutomaticFishReleaseMachineRenderer.FISH_MODEL_Y_OFFSET, 0);
			fish[index].setTransform(pose);
		}
	}

	private PoseStack wheelPose(Direction.Axis axis, float wheelAngle) {
		PoseStack pose = new PoseStack();
		pose.translate(getVisualPosition().getX() + 0.5, getVisualPosition().getY() + 0.5,
			getVisualPosition().getZ() + 0.5);
		AutomaticFishReleaseMachineRenderer.alignVerticalModelToAxis(pose, axis);
		pose.mulPose(Axis.YP.rotation(wheelAngle));
		return pose;
	}
}
