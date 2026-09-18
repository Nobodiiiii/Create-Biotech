package com.nobodiiiii.createbiotech.content.creeperblastchamber;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModels;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureRenderer;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureVisualModel;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** A reusable Flywheel instance pool for the chamber's variable number of creepers. */
public class CreeperBlastChamberVisual extends AbstractBlockEntityVisual<CreeperBlastChamberBlockEntity>
	implements SimpleDynamicVisual {

	private final List<CreeperBlastChamberRenderer.CreeperRenderState> states = new ArrayList<>();
	private final List<CreeperSlot> slots = new ArrayList<>();

	public CreeperBlastChamberVisual(VisualizationContext context, CreeperBlastChamberBlockEntity blockEntity,
		float partialTick) {
		super(context, blockEntity, partialTick);
		updateCreepers(partialTick);
	}

	@Override
	public void beginFrame(DynamicVisual.Context context) {
		updateCreepers(context.partialTick());
	}

	@Override
	public void updateLight(float partialTick) {
		// Each creature samples the block above its own packager position while its pose is updated.
	}

	@Override
	protected void _delete() {
		slots.forEach(CreeperSlot::delete);
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		slots.forEach(slot -> slot.collectCrumblingInstances(consumer));
	}

	private void updateCreepers(float partialTick) {
		if (blockEntity.isStructureValid())
			CreeperBlastChamberRenderer.collectCreeperRenderStates(blockEntity, partialTick, states);
		else
			states.clear();
		while (slots.size() < states.size())
			slots.add(new CreeperSlot());
		for (int i = 0; i < states.size(); i++)
			slots.get(i).update(states.get(i));
		for (int i = states.size(); i < slots.size(); i++)
			slots.get(i).hide();
	}

	private final class CreeperSlot {
		private final MachineCreatureVisualModel body =
			new MachineCreatureVisualModel(instancerProvider(), MachineCreatureModels.creeper(0));
		private final MachineCreatureVisualModel power =
			new MachineCreatureVisualModel(instancerProvider(), MachineCreatureModels.creeper(2));

		private CreeperSlot() {
			power.color(0xFF808080);
			power.overlay(OverlayTexture.NO_OVERLAY);
		}

		private void update(CreeperBlastChamberRenderer.CreeperRenderState state) {
			MachineCreatureRenderer.prepareCreeperPose(body.model(), state.headYaw(), state.headPitch());
			body.light(state.packedLight());
			body.overlay(MachineCreatureRenderer.creeperOverlay(state.swelling()));

			PoseStack pose = new PoseStack();
			pose.translate(getVisualPosition().getX() + state.x(),
				getVisualPosition().getY() + state.y(), getVisualPosition().getZ() + state.z());
			pose.scale(state.scale(), state.scale(), state.scale());
			pose.scale(state.horizontalScale(), state.verticalScale(), state.horizontalScale());
			MachineCreatureRenderer.applyCreeperTransform(pose, state.bodyYaw(), state.swelling());
			body.setTransform(pose);

			if (!state.charged()) {
				power.hide();
				return;
			}
			MachineCreatureRenderer.prepareCreeperPose(power.model(), state.headYaw(), state.headPitch());
			power.light(state.packedLight());
			power.setTransform(pose);
		}

		private void hide() {
			body.hide();
			power.hide();
		}

		private void delete() {
			body.delete();
			power.delete();
		}

		private void collectCrumblingInstances(Consumer<Instance> consumer) {
			body.collectCrumblingInstances(consumer);
			power.collectCrumblingInstances(consumer);
		}
	}
}
