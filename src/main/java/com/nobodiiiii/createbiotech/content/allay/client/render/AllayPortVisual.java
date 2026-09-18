package com.nobodiiiii.createbiotech.content.allay.client.render;

import java.util.function.Consumer;

import org.joml.Matrix4f;

import com.simibubi.create.content.logistics.FlapStuffs;
import com.nobodiiiii.createbiotech.content.allay.block.allayport.AllayPortBlock;
import com.nobodiiiii.createbiotech.content.allay.block.allayport.AllayPortBlockEntity;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModels;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureVisualModel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.createmod.ponder.api.level.PonderLevel;

public class AllayPortVisual extends AbstractBlockEntityVisual<AllayPortBlockEntity>
	implements SimpleDynamicVisual {

	private final Matrix4f commonTransform;
	private final TransformedInstance[] curtains;
	private final MachineCreatureVisualModel allay;

	public AllayPortVisual(VisualizationContext context, AllayPortBlockEntity blockEntity, float partialTick) {
		super(context, blockEntity, partialTick);

		Direction facing = blockState.getValue(AllayPortBlock.FACING);
		float horizontalAngle = AngleHelper.horizontalAngle(facing.getOpposite());
		commonTransform = new Matrix4f()
			.translate(getVisualPosition().getX(), getVisualPosition().getY(), getVisualPosition().getZ())
			.translate(0.5f, 0.5f, 0.5f)
			.rotateY(Mth.DEG_TO_RAD * horizontalAngle)
			.translate(-0.5f, -0.5f, -0.5f)
			.translate((float) AllayPortRenderer.CURTAIN_PIVOT.x,
				(float) AllayPortRenderer.CURTAIN_PIVOT.y,
				(float) AllayPortRenderer.CURTAIN_PIVOT.z);

		curtains = new TransformedInstance[AllayPortRenderer.CURTAIN_SEGMENT_COUNT];
		for (int segment = 0; segment < curtains.length; segment++) {
			curtains[segment] = instancerProvider()
				.instancer(InstanceTypes.TRANSFORMED,
					Models.partial(AllayPortRenderer.CURTAIN_SEGMENTS.get(segment)))
				.createInstance();
		}
		allay = new MachineCreatureVisualModel(instancerProvider(), MachineCreatureModels.allay());
		updateCurtain(blockEntity.getFlap(partialTick));
		updateAllay(partialTick);
		updateLight(partialTick);
	}

	@Override
	public void beginFrame(Context ctx) {
		updateCurtain(blockEntity.getFlap(ctx.partialTick()));
		updateAllay(ctx.partialTick());
	}

	@Override
	public void updateLight(float partialTick) {
		int light = computePackedLight();
		for (TransformedInstance curtain : curtains) {
			curtain.light(light)
				.setChanged();
		}
		allay.light(LightTexture.pack(15, LightTexture.sky(light)));
	}

	@Override
	protected void _delete() {
		for (TransformedInstance curtain : curtains) {
			curtain.delete();
		}
		allay.delete();
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		for (TransformedInstance curtain : curtains) {
			consumer.accept(curtain);
		}
		allay.collectCrumblingInstances(consumer);
	}

	private void updateCurtain(float flapness) {
		for (int segment = 0; segment < curtains.length; segment++) {
			curtains[segment]
				.setTransform(commonTransform)
				.rotateXDegrees(FlapStuffs.flapAngle(flapness, segment))
				.translateBack(AllayPortRenderer.CURTAIN_PIVOT)
				.setChanged();
		}
	}

	private void updateAllay(float partialTick) {
		float flapness = blockEntity.getFlap(partialTick);
		float flapWaveStrength = Mth.clamp((Math.abs(flapness) - 0.25f) * 4.0f, 0, 1);
		float waveStrength = blockEntity.isCourierWaving() ? 1 : flapWaveStrength;
		float animationTime;
		if (blockEntity.getLevel() instanceof PonderLevel ponderLevel && ponderLevel.scene != null)
			animationTime = ponderLevel.scene.getCurrentTime() + partialTick;
		else
			animationTime = blockEntity.getLevel() == null ? partialTick
				: blockEntity.getLevel().getGameTime() % 24_000L + partialTick;
		GreetingAllayRenderer.prepareGreetingPose(allay.model(), animationTime, waveStrength);
		Direction facing = blockState.getValue(AllayPortBlock.FACING);
		PoseStack pose = new PoseStack();
		pose.translate(getVisualPosition().getX() + 0.5,
			getVisualPosition().getY() + GreetingAllayRenderer.ALLAY_POSITION_Y,
			getVisualPosition().getZ() + 0.5);
		pose.mulPose(Axis.YP.rotationDegrees(180.0f - facing.toYRot()));
		float scale = GreetingAllayRenderer.ALLAY_SCALE;
		pose.scale(-scale, -scale, scale);
		pose.translate(0, GreetingAllayRenderer.LIVING_ENTITY_MODEL_Y_OFFSET, 0);
		allay.setTransform(pose);
	}
}
