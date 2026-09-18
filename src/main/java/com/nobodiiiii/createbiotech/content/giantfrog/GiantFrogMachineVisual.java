package com.nobodiiiii.createbiotech.content.giantfrog;

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

public class GiantFrogMachineVisual extends AbstractBlockEntityVisual<GiantFrogBlockEntity>
	implements SimpleDynamicVisual {

	private static final float LIVING_ENTITY_MODEL_Y_OFFSET = -1.501f;
	private final MachineCreatureVisualModel frog;

	public GiantFrogMachineVisual(VisualizationContext context, GiantFrogBlockEntity blockEntity,
		float partialTick) {
		super(context, blockEntity, partialTick);
		frog = new MachineCreatureVisualModel(instancerProvider(), MachineCreatureModels.frog());
		updatePose(partialTick);
		updateLight(partialTick);
	}

	@Override
	public void beginFrame(DynamicVisual.Context context) {
		updatePose(context.partialTick());
	}

	@Override
	public void updateLight(float partialTick) {
		frog.light(computePackedLight());
	}

	@Override
	protected void _delete() {
		frog.delete();
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		frog.collectCrumblingInstances(consumer);
	}

	private void updatePose(float partialTick) {
		if (!GiantFrogBlock.isMain(blockState)) {
			frog.hide();
			return;
		}
		boolean beltConnected = blockEntity.isMouthHeldOpenByBelt();
		boolean tongue = blockEntity.isTongueAnimating() && !beltConnected;
		float age = tongue ? blockEntity.getTongueAnimationAge(partialTick) : 0;
		GiantFrogVisual.prepareBlockModel(frog.model(), tongue, age, beltConnected);
		Direction facing = blockState.getValue(GiantFrogBlock.FACING);
		PoseStack pose = new PoseStack();
		pose.translate(getVisualPosition().getX() + 0.5, getVisualPosition().getY(),
			getVisualPosition().getZ() + 0.5);
		pose.mulPose(Axis.YP.rotationDegrees(180.0f - facing.toYRot()));
		pose.scale(-GiantFrogBlock.FROG_SCALE, -GiantFrogBlock.FROG_SCALE,
			GiantFrogBlock.FROG_SCALE);
		pose.translate(0, LIVING_ENTITY_MODEL_Y_OFFSET, 0);
		frog.setTransform(pose);
	}
}
