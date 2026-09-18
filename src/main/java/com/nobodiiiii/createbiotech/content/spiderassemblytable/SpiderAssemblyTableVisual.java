package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModels;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureVisualModel;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Direction;

public class SpiderAssemblyTableVisual extends AbstractBlockEntityVisual<SpiderAssemblyTableBlockEntity>
	implements SimpleDynamicVisual {

	private static final float SPIDER_Y_OFFSET = 0.5f + 15f / 16f;
	private final MachineCreatureVisualModel spider;
	private final MachineCreatureVisualModel eyes;

	public SpiderAssemblyTableVisual(VisualizationContext context,
		SpiderAssemblyTableBlockEntity blockEntity, float partialTick) {
		super(context, blockEntity, partialTick);
		spider = new MachineCreatureVisualModel(instancerProvider(), MachineCreatureModels.spider());
		eyes = new MachineCreatureVisualModel(instancerProvider(), MachineCreatureModels.spiderEyes());
		updatePose(partialTick);
		updateLight(partialTick);
	}

	@Override
	public void beginFrame(DynamicVisual.Context context) {
		updatePose(context.partialTick());
	}

	@Override
	public void updateLight(float partialTick) {
		spider.light(computePackedLight());
		eyes.light(LightTexture.FULL_BRIGHT);
	}

	@Override
	protected void _delete() {
		spider.delete();
		eyes.delete();
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		spider.collectCrumblingInstances(consumer);
		eyes.collectCrumblingInstances(consumer);
	}

	private void updatePose(float partialTick) {
		if (!blockEntity.getBlockState().hasProperty(SpiderAssemblyTableBlock.FACING)) {
			spider.hide();
			eyes.hide();
			return;
		}
		SpiderAssemblyTableRenderer.prepareSpiderModel(spider.model(), blockEntity, partialTick);
		eyes.model().copyPoseFrom(spider.model());
		Direction facing = blockEntity.getBlockState().getValue(SpiderAssemblyTableBlock.FACING);
		PoseStack pose = new PoseStack();
		pose.translate(getVisualPosition().getX() + 0.5, getVisualPosition().getY() + SPIDER_Y_OFFSET,
			getVisualPosition().getZ() + 0.5);
		pose.mulPose(Axis.YP.rotationDegrees(yRotation(facing)));
		pose.scale(-1, -1, 1);
		boolean encased = blockEntity.getBlockState().getValue(SpiderAssemblyTableBlock.CASING)
			&& blockEntity.getCasing() != null;
		if (encased)
			spider.hide();
		else
			spider.setTransform(pose);
		eyes.setTransform(pose);
	}

	private static float yRotation(Direction facing) {
		return switch (facing) {
		case EAST -> 270;
		case SOUTH -> 180;
		case WEST -> 90;
		default -> 0;
		};
	}
}
