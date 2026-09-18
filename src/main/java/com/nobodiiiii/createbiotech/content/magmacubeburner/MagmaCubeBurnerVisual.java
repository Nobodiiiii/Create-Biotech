package com.nobodiiiii.createbiotech.content.magmacubeburner;

import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModel;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModels;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureVisualModel;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Direction;

/** Flywheel path for the articulated magma cube; the lava remains in the block-entity renderer. */
public class MagmaCubeBurnerVisual extends AbstractBlockEntityVisual<MagmaCubeBurnerBlockEntity>
	implements SimpleDynamicVisual {

	private static final float MODEL_FOOT_OFFSET = 1.501f;
	private final MachineCreatureVisualModel magmaCube;

	public MagmaCubeBurnerVisual(VisualizationContext context, MagmaCubeBurnerBlockEntity blockEntity,
		float partialTick) {
		super(context, blockEntity, partialTick);
		magmaCube = new MachineCreatureVisualModel(instancerProvider(), MachineCreatureModels.magmaCube());
		magmaCube.light(LightTexture.FULL_BRIGHT);
		updatePose(partialTick);
	}

	@Override
	public void beginFrame(DynamicVisual.Context context) {
		updatePose(context.partialTick());
	}

	@Override
	public void updateLight(float partialTick) {
		magmaCube.light(LightTexture.FULL_BRIGHT);
	}

	@Override
	protected void _delete() {
		magmaCube.delete();
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		magmaCube.collectCrumblingInstances(consumer);
	}

	private void updatePose(float partialTick) {
		float squish = MagmaCubeBurnerRenderer.getSquish(blockEntity, partialTick);
		MachineCreatureModel model = magmaCube.model();
		model.resetPose();
		float separation = Math.max(0, squish) * 1.7f;
		for (int slice = 0; slice < 8; slice++)
			model.root().getChild("cube" + slice).y = (slice - 4) * separation;

		Direction facing = blockState.getValue(MagmaCubeBurnerBlock.FACING);
		float stretch = Math.max(.01f, 1 + squish / 1.5f);
		PoseStack pose = new PoseStack();
		pose.translate(getVisualPosition().getX() + .5,
			getVisualPosition().getY() + MagmaCubeBurnerRenderer.getMagmaCubeY(blockEntity, partialTick),
			getVisualPosition().getZ() + .5);
		pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
		pose.scale(-1 / stretch, -stretch, 1 / stretch);
		pose.translate(0, -MODEL_FOOT_OFFSET, 0);
		magmaCube.setTransform(pose);
	}
}
