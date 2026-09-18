package com.nobodiiiii.createbiotech.content.universaljoint;

import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModels;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureVisualModel;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.util.FastColor;

/** Flywheel instances for the linked slime shaft; the endpoint mechanics remain in the kinetic renderer. */
public class UniversalJointVisual extends AbstractBlockEntityVisual<UniversalJointBlockEntity>
	implements SimpleDynamicVisual {

	private static final float SLIME_MODEL_Y_OFFSET = 1.501f;
	private final MachineCreatureVisualModel inner =
		new MachineCreatureVisualModel(instancerProvider(), MachineCreatureModels.slimeInner());
	private final MachineCreatureVisualModel outer =
		new MachineCreatureVisualModel(instancerProvider(), MachineCreatureModels.slimeOuter());
	private final MachineCreatureVisualModel innerOverstretch =
		new MachineCreatureVisualModel(instancerProvider(), MachineCreatureModels.slimeInner());
	private final MachineCreatureVisualModel outerOverstretch =
		new MachineCreatureVisualModel(instancerProvider(), MachineCreatureModels.slimeOuter());

	public UniversalJointVisual(VisualizationContext context, UniversalJointBlockEntity blockEntity,
		float partialTick) {
		super(context, blockEntity, partialTick);
		updateShaft(partialTick);
		updateLight(partialTick);
	}

	@Override
	public void beginFrame(DynamicVisual.Context context) {
		updateShaft(context.partialTick());
	}

	@Override
	public void updateLight(float partialTick) {
		int light = computePackedLight();
		inner.light(light);
		outer.light(light);
		innerOverstretch.light(light);
		outerOverstretch.light(light);
	}

	@Override
	protected void _delete() {
		inner.delete();
		outer.delete();
		innerOverstretch.delete();
		outerOverstretch.delete();
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		inner.collectCrumblingInstances(consumer);
		outer.collectCrumblingInstances(consumer);
		innerOverstretch.collectCrumblingInstances(consumer);
		outerOverstretch.collectCrumblingInstances(consumer);
	}

	private void updateShaft(float partialTick) {
		UniversalJointRenderer.ShaftRenderState state =
			UniversalJointRenderer.getDriveShaftState(blockEntity, blockState, partialTick);
		if (state == null) {
			hideAll();
			return;
		}

		PoseStack pose = new PoseStack();
		pose.translate(getVisualPosition().getX(), getVisualPosition().getY(), getVisualPosition().getZ());
		TransformStack.of(pose).transform(state.transform());
		pose.translate(0, SLIME_MODEL_Y_OFFSET, 0);
		pose.scale(-1, -1, 1);
		inner.setTransform(pose);
		outer.setTransform(pose);

		int color = state.overstretchColor();
		if (FastColor.ARGB32.alpha(color) == 0) {
			innerOverstretch.hide();
			outerOverstretch.hide();
			return;
		}
		innerOverstretch.color(color);
		outerOverstretch.color(color);
		innerOverstretch.setTransform(pose);
		outerOverstretch.setTransform(pose);
	}

	private void hideAll() {
		inner.hide();
		outer.hide();
		innerOverstretch.hide();
		outerOverstretch.hide();
	}
}
