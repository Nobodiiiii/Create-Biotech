package com.nobodiiiii.createbiotech.content.shulkerteleporter;

import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModel;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModels;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureVisualModel;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.Direction;

public class ShulkerTeleporterVisual extends AbstractBlockEntityVisual<ShulkerTeleporterBlockEntity>
	implements SimpleDynamicVisual {

	private static final float LOWER_SHELL_Y = -2.0f;
	private static final float FULL_SPIN_DEGREES = 720.0f;
	private final MachineCreatureVisualModel base;
	private final MachineCreatureVisualModel lid;

	public ShulkerTeleporterVisual(VisualizationContext context,
		ShulkerTeleporterBlockEntity blockEntity, float partialTick) {
		super(context, blockEntity, partialTick);
		base = new MachineCreatureVisualModel(instancerProvider(), MachineCreatureModels.shulker());
		lid = new MachineCreatureVisualModel(instancerProvider(), MachineCreatureModels.shulker());
		base.model().part("lid").visible = false;
		lid.model().part("base").visible = false;
		updatePose(partialTick);
		updateLight(partialTick);
	}

	@Override
	public void beginFrame(DynamicVisual.Context context) {
		updatePose(context.partialTick());
	}

	@Override
	public void updateLight(float partialTick) {
		int light = computePackedLight();
		base.light(light);
		lid.light(light);
	}

	@Override
	protected void _delete() {
		base.delete();
		lid.delete();
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		base.collectCrumblingInstances(consumer);
		lid.collectCrumblingInstances(consumer);
	}

	private void updatePose(float partialTick) {
		float progress = blockEntity.getClosingProgress(partialTick);
		float topY = blockEntity.getTopShellYOffset(partialTick);
		MachineCreatureModel.Part lidPart = lid.model().part("lid");
		lidPart.setPos(0, 24, 0);
		lidPart.yRot = (float) Math.toRadians(progress * FULL_SPIN_DEGREES);

		PoseStack basePose = atBlockOrigin();
		basePose.translate(0, LOWER_SHELL_Y, 0);
		applyShulkerBoxPose(basePose);
		base.setTransform(basePose);

		PoseStack lidPose = atBlockOrigin();
		lidPose.translate(0, topY, 0);
		applyShulkerBoxPose(lidPose);
		lid.setTransform(lidPose);
	}

	private PoseStack atBlockOrigin() {
		PoseStack pose = new PoseStack();
		pose.translate(getVisualPosition().getX(), getVisualPosition().getY(), getVisualPosition().getZ());
		return pose;
	}

	private static void applyShulkerBoxPose(PoseStack poseStack) {
		poseStack.translate(0.5f, 0.5f, 0.5f);
		poseStack.scale(0.9995f, 0.9995f, 0.9995f);
		poseStack.mulPose(Direction.UP.getRotation());
		poseStack.scale(1.0f, -1.0f, -1.0f);
		poseStack.translate(0.0f, -1.0f, 0.0f);
	}
}
