package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModels;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModel;
import com.simibubi.create.AllPartialModels;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureVisualModel;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.client.renderer.texture.OverlayTexture;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;

public class SpiderAssemblyTableVisual extends AbstractBlockEntityVisual<SpiderAssemblyTableBlockEntity>
	implements SimpleDynamicVisual {

	private static final float SPIDER_Y_OFFSET = 0.5f + 15f / 16f;
	private final MachineCreatureVisualModel spider;
	private final List<CasingPart> casingParts = new ArrayList<>();
	private final TransformedInstance[] gears = new TransformedInstance[SpiderAssemblyTableBlockEntity.LEG_COUNT];
	private final TransformedInstance[] machines = new TransformedInstance[SpiderAssemblyTableBlockEntity.LEG_COUNT];
	private final Model[] machineModels = new Model[SpiderAssemblyTableBlockEntity.LEG_COUNT];
	private final PoseStack pose = new PoseStack();
	private Block casing;
	private int packedLight;

	public SpiderAssemblyTableVisual(VisualizationContext context,
		SpiderAssemblyTableBlockEntity blockEntity, float partialTick) {
		super(context, blockEntity, partialTick);
		spider = new MachineCreatureVisualModel(instancerProvider(), MachineCreatureModels.spider());
		for (int i = 0; i < gears.length; i++)
			gears[i] = instancerProvider().instancer(InstanceTypes.TRANSFORMED,
				Models.partial(AllPartialModels.SHAFTLESS_COGWHEEL)).createInstance();
		updatePose(partialTick);
		updateLight(partialTick);
	}

	@Override
	public void beginFrame(DynamicVisual.Context context) {
		updatePose(context.partialTick());
	}

	@Override
	public void updateLight(float partialTick) {
		packedLight = computePackedLight();
		spider.light(packedLight);
		casingParts.forEach(part -> part.instance.light(packedLight).setChanged());
		for (TransformedInstance gear : gears) gear.light(packedLight).setChanged();
		for (TransformedInstance machine : machines)
			if (machine != null) machine.light(packedLight).setChanged();
	}

	@Override
	protected void _delete() {
		spider.delete();
		casingParts.forEach(part -> part.instance.delete());
		for (TransformedInstance gear : gears) gear.delete();
		for (TransformedInstance machine : machines) if (machine != null) machine.delete();
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		spider.collectCrumblingInstances(consumer);
		casingParts.forEach(part -> consumer.accept(part.instance));
		for (TransformedInstance gear : gears) consumer.accept(gear);
		for (TransformedInstance machine : machines) if (machine != null) consumer.accept(machine);
	}

	private void updatePose(float partialTick) {
		if (!blockEntity.getBlockState().hasProperty(SpiderAssemblyTableBlock.FACING)) {
			spider.hide();
			casingParts.forEach(part -> part.instance.setVisible(false));
			for (TransformedInstance gear : gears) gear.setVisible(false);
			for (TransformedInstance machine : machines) if (machine != null) machine.setVisible(false);
			return;
		}
		SpiderAssemblyTableRenderer.prepareSpiderModel(spider.model(), blockEntity, partialTick);
		Direction facing = blockEntity.getBlockState().getValue(SpiderAssemblyTableBlock.FACING);
		pose.pushPose();
		pose.translate(getVisualPosition().getX() + 0.5, getVisualPosition().getY() + SPIDER_Y_OFFSET,
			getVisualPosition().getZ() + 0.5);
		pose.mulPose(Axis.YP.rotationDegrees(yRotation(facing)));
		pose.scale(-1, -1, 1);
		Block nextCasing = blockEntity.getBlockState().getValue(SpiderAssemblyTableBlock.CASING)
			? blockEntity.getCasing() : null;
		if (casing != nextCasing) {
			casingParts.forEach(part -> part.instance.delete());
			casingParts.clear();
			casing = nextCasing;
			if (casing != null)
				SpiderAssemblyTableCasingModel.visualModels(casing).forEach((name, model) ->
					casingParts.add(new CasingPart(spider.model().root().getChild(name),
						instancerProvider().instancer(InstanceTypes.TRANSFORMED, model).createInstance())));
		}
		if (casing != null) {
			spider.hide();
			for (CasingPart part : casingParts) {
				pose.pushPose();
				part.part.translateAndRotate(pose);
				setTransform(part.instance, pose);
				pose.popPose();
			}
		} else spider.setTransform(pose);
		for (TransformedInstance gear : gears) gear.setVisible(false);
		for (TransformedInstance machine : machines) if (machine != null) machine.setVisible(false);
		SpiderAssemblyTableRenderer.visitLegs(blockEntity, spider.model(), partialTick, pose, false,
			(slot, kind, state, legPose, active) -> {
				legPose.pushPose();
				SpiderAssemblyTableRenderer.applyJointGearTransform(legPose);
				setTransform(gears[slot], legPose);
				legPose.popPose();
				SpiderAssemblyTableRenderer.applyMachineRoot(legPose, kind);
				boolean holding = !blockEntity.getInventory().getStackInSlot(
					SpiderAssemblyTableBlockEntity.HYBRID_SLOT_START + slot).isEmpty();
				Model model = SpiderAssemblyTableRenderer.machineModel(kind, holding);
				if (machineModels[slot] != model) {
					if (machines[slot] != null) machines[slot].delete();
					machines[slot] = instancerProvider().instancer(InstanceTypes.TRANSFORMED, model).createInstance();
					machineModels[slot] = model;
				}
				setTransform(machines[slot], legPose);
			});
		pose.popPose();
	}

	private void setTransform(TransformedInstance instance, PoseStack transform) {
		instance.setVisible(true);
		instance.setTransform(transform);
		instance.light(packedLight);
		instance.overlay(OverlayTexture.NO_OVERLAY).setChanged();
	}

	private record CasingPart(MachineCreatureModel.Part part, TransformedInstance instance) {}

	private static float yRotation(Direction facing) {
		return switch (facing) {
		case EAST -> 270;
		case SOUTH -> 180;
		case WEST -> 90;
		default -> 0;
		};
	}
}
