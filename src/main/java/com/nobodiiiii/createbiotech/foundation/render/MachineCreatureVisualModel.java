package com.nobodiiiii.createbiotech.foundation.render;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.instance.InstancerProvider;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Flywheel instances for every baked block-model part in one creature pose. */
public final class MachineCreatureVisualModel {

	private final MachineCreatureModel model;
	private final Map<MachineCreatureModel.Part, TransformedInstance> instances = new IdentityHashMap<>();
	private int packedLight;
	private int packedOverlay = OverlayTexture.NO_OVERLAY;
	private int color = -1;

	public MachineCreatureVisualModel(InstancerProvider instancerProvider, MachineCreatureModel model) {
		this.model = model;
		for (MachineCreatureModel.Part part : model.parts()) {
			if (part.partial() == null)
				continue;
			instances.put(part, instancerProvider
				.instancer(InstanceTypes.TRANSFORMED, Models.partial(part.partial()))
				.createInstance());
		}
	}

	public MachineCreatureModel model() {
		return model;
	}

	public void setTransform(PoseStack rootTransform) {
		updatePart(model.root(), rootTransform, true);
	}

	public void hide() {
		for (TransformedInstance instance : instances.values())
			instance.setVisible(false);
	}

	public void light(int packedLight) {
		this.packedLight = packedLight;
		for (TransformedInstance instance : instances.values())
			instance.light(packedLight)
				.setChanged();
	}

	public void overlay(int packedOverlay) {
		this.packedOverlay = packedOverlay;
	}

	public void color(int color) {
		this.color = color;
	}

	public void delete() {
		for (TransformedInstance instance : instances.values())
			instance.delete();
	}

	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		instances.values().forEach(consumer);
	}

	private void updatePart(MachineCreatureModel.Part part, PoseStack poseStack, boolean parentVisible) {
		boolean visible = parentVisible && part.visible;
		poseStack.pushPose();
		part.translateAndRotate(poseStack);
		TransformedInstance instance = instances.get(part);
		if (instance != null) {
			boolean draw = visible && !part.skipDraw;
			instance.setVisible(draw);
			if (draw)
				instance.setTransform(poseStack);
			if (draw) {
				instance.light(packedLight);
				instance.overlay(packedOverlay);
				instance.color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF,
					color >>> 24);
				instance.setChanged();
			}
		}
		for (MachineCreatureModel.Part child : part.children())
			updatePart(child, poseStack, visible);
		poseStack.popPose();
	}
}
