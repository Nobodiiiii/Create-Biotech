package com.nobodiiiii.createbiotech.foundation.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Blocks;

/** Mutable pose for a hierarchy of standard Java block-model parts. */
public final class MachineCreatureModel {

	private final MachineCreatureModels.ModelSpec spec;
	private final Map<String, Part> parts = new LinkedHashMap<>();
	private final Map<String, Anchor> anchors;
	private final Part root;

	MachineCreatureModel(MachineCreatureModels.ModelSpec spec) {
		this.spec = spec;
		for (MachineCreatureModels.PartSpec partSpec : spec.parts())
			parts.put(partSpec.path(), new Part(partSpec,
				partSpec.hasGeometry()
					? PartialModel.of(MachineCreatureModels.partLocation(spec.id(), partSpec.modelName()))
					: null));
		for (MachineCreatureModels.PartSpec partSpec : spec.parts()) {
			if (partSpec.parentPath() == null)
				continue;
			Part parent = requirePart(partSpec.parentPath());
			Part child = requirePart(partSpec.path());
			child.parent = parent;
			parent.children.add(child);
		}
		root = requirePart("$root");
		Map<String, Anchor> resolvedAnchors = new LinkedHashMap<>();
		for (Map.Entry<String, MachineCreatureModels.AnchorSpec> entry : spec.anchors().entrySet()) {
			MachineCreatureModels.AnchorSpec anchor = entry.getValue();
			resolvedAnchors.put(entry.getKey(), new Anchor(requirePart(anchor.partPath()),
				anchor.x(), anchor.y(), anchor.z()));
		}
		anchors = Map.copyOf(resolvedAnchors);
		resetPose();
	}

	public Part root() {
		return root;
	}

	public Part part(String path) {
		return requirePart(path);
	}

	public Anchor anchor(String name) {
		Anchor anchor = anchors.get(name);
		if (anchor == null)
			throw new IllegalArgumentException("Unknown anchor '" + name + "' in machine model " + spec.id());
		return anchor;
	}

	public void resetPose() {
		for (Part part : parts.values())
			part.resetPose();
	}

	public void copyPoseFrom(MachineCreatureModel source) {
		for (Map.Entry<String, Part> entry : parts.entrySet()) {
			Part from = source.requirePart(entry.getKey());
			Part to = entry.getValue();
			to.x = from.x;
			to.y = from.y;
			to.z = from.z;
			to.xRot = from.xRot;
			to.yRot = from.yRot;
			to.zRot = from.zRot;
			to.xScale = from.xScale;
			to.yScale = from.yScale;
			to.zScale = from.zScale;
			to.visible = from.visible;
			to.skipDraw = from.skipDraw;
		}
	}

	public RenderType renderType() {
		return spec.layer().renderType();
	}

	public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		int packedOverlay, int color) {
		renderToBuffer(poseStack, buffer.getBuffer(renderType()), packedLight, packedOverlay, color);
	}

	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight,
		int packedOverlay, int color) {
		root.render(poseStack, buffer, packedLight, packedOverlay, color);
	}

	public void applyTransformTo(String path, PoseStack poseStack) {
		Part target = requirePart(path);
		List<Part> lineage = new ArrayList<>();
		for (Part part = target; part != null; part = part.parent)
			lineage.add(part);
		for (int i = lineage.size() - 1; i >= 0; i--)
			lineage.get(i).translateAndRotate(poseStack);
	}

	List<Part> parts() {
		return List.copyOf(parts.values());
	}

	private Part requirePart(String path) {
		Part part = parts.get(path);
		if (part == null)
			throw new IllegalArgumentException("Unknown part '" + path + "' in machine model " + spec.id());
		return part;
	}

	public static final class Part {
		private final MachineCreatureModels.PartSpec spec;
		private final PartialModel partial;
		private final List<Part> children = new ArrayList<>();
		private Part parent;

		public float x;
		public float y;
		public float z;
		public float xRot;
		public float yRot;
		public float zRot;
		public float xScale;
		public float yScale;
		public float zScale;
		public boolean visible;
		public boolean skipDraw;

		private Part(MachineCreatureModels.PartSpec spec, PartialModel partial) {
			this.spec = spec;
			this.partial = partial;
		}

		public String path() {
			return spec.path();
		}

		public Part getChild(String name) {
			for (Part child : children)
				if (child.path().equals(name) || child.path().endsWith("/" + name))
					return child;
			throw new IllegalArgumentException("Part '" + path() + "' has no child '" + name + "'");
		}

		public MachineCreatureModels.Bounds firstCubeBounds() {
			return spec.firstCube();
		}

		public boolean isEmpty() {
			return partial == null;
		}

		PartialModel partial() {
			return partial;
		}

		List<Part> children() {
			return children;
		}

		public void setPos(float x, float y, float z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		public void resetPose() {
			x = spec.x();
			y = spec.y();
			z = spec.z();
			xRot = spec.xRot();
			yRot = spec.yRot();
			zRot = spec.zRot();
			xScale = yScale = zScale = 1;
			visible = spec.visible();
			skipDraw = spec.skipDraw();
		}

		public void translateAndRotate(PoseStack poseStack) {
			poseStack.translate(x / 16.0f, y / 16.0f, z / 16.0f);
			if (zRot != 0)
				poseStack.mulPose(Axis.ZP.rotation(zRot));
			if (yRot != 0)
				poseStack.mulPose(Axis.YP.rotation(yRot));
			if (xRot != 0)
				poseStack.mulPose(Axis.XP.rotation(xRot));
			if (xScale != 1 || yScale != 1 || zScale != 1)
				poseStack.scale(xScale, yScale, zScale);
		}

		public void render(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay) {
			render(poseStack, buffer, packedLight, packedOverlay, -1);
		}

		private void render(PoseStack poseStack, VertexConsumer buffer, int packedLight,
			int packedOverlay, int color) {
			if (!visible)
				return;
			poseStack.pushPose();
			translateAndRotate(poseStack);
			if (!skipDraw && partial != null) {
				SuperByteBuffer geometry = CachedBuffers.partial(partial, Blocks.AIR.defaultBlockState())
					.light(packedLight)
					.overlay(packedOverlay)
					.color(color);
				geometry.renderInto(poseStack, buffer);
			}
			for (Part child : children)
				child.render(poseStack, buffer, packedLight, packedOverlay, color);
			poseStack.popPose();
		}
	}

	/** A point in the local coordinate system of a named rigid part. */
	public record Anchor(Part part, float x, float y, float z) {}
}
