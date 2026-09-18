package com.nobodiiiii.createbiotech.content.squidprinter;

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
import net.minecraft.core.Direction;

/** Flywheel counterpart of the printer's standard block-model squid parts. */
public class SquidPrinterVisual extends AbstractBlockEntityVisual<SquidPrinterBlockEntity>
	implements SimpleDynamicVisual {

	private final MachineCreatureVisualModel squid;

	public SquidPrinterVisual(VisualizationContext context, SquidPrinterBlockEntity blockEntity,
		float partialTick) {
		super(context, blockEntity, partialTick);
		squid = new MachineCreatureVisualModel(instancerProvider(), MachineCreatureModels.squid());
		updatePose(partialTick);
		updateLight(partialTick);
	}

	@Override
	public void beginFrame(DynamicVisual.Context context) {
		updatePose(context.partialTick());
	}

	@Override
	public void updateLight(float partialTick) {
		squid.light(computePackedLight());
	}

	@Override
	protected void _delete() {
		squid.delete();
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		squid.collectCrumblingInstances(consumer);
	}

	private void updatePose(float partialTick) {
		SquidPrinterSquidVisual.prepareModel(squid.model(), blockEntity.getSquidPose(partialTick));
		Direction facing = blockState.getValue(SquidPrinterBlock.FACING);
		PoseStack pose = new PoseStack();
		pose.translate(getVisualPosition().getX() + 0.5, getVisualPosition().getY()
			+ SquidPrinterSquidVisual.HEAD_TOP_Y, getVisualPosition().getZ() + 0.5);
		pose.mulPose(Axis.YP.rotationDegrees(180.0f - facing.toYRot()));
		float scale = SquidPrinterSquidVisual.RENDER_SCALE;
		pose.scale(-scale, -scale, scale);
		squid.setTransform(pose);
	}
}
