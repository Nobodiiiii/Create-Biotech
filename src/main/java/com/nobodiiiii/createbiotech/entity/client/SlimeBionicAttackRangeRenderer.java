package com.nobodiiiii.createbiotech.entity.client;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.SlimeBionicCombat;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Continuous, depth-tested preview of the logical attack; never samples the animated arm. */
public final class SlimeBionicAttackRangeRenderer {
	private static final Map<SlimeBionicEntity, CachedMesh> CACHE = new WeakHashMap<>();
	private static final RenderType SURFACE = RenderType.create("create_biotech_attack_range",
		DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, true,
		RenderType.CompositeState.builder()
			.setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
			.setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
			.setCullState(RenderStateShard.NO_CULL)
			.setWriteMaskState(RenderStateShard.COLOR_WRITE)
			.setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
			.createCompositeState(false));
	private static final RenderType OUTLINE = lines("outline", 2.5d);
	private static final RenderType OUTLINE_BACKING = lines("outline_backing", 5.0d);

	private SlimeBionicAttackRangeRenderer() {}

	public static void clearCache() {
		CACHE.clear();
	}

	public static void render(SlimeBionicEntity entity, SurgicalAssembly assembly, float partialTick,
		PoseStack poseStack, MultiBufferSource buffer) {
		if (entity.getAttackActionTick() <= 0 || !entity.isAlive()) {
			CACHE.remove(entity);
			return;
		}
		SurgicalAssembly.ArmAttackGeometry arm = assembly.attackGeometry() == null
			? SlimeBionicCombat.fallbackArm(entity.getBbWidth(), entity.getBbHeight())
			: assembly.attackGeometry().arm(entity.isAttackActionLeft(), entity.getAttackActionArmSlot());
		if (arm == null)
			return;
		CachedMesh cached = CACHE.get(entity);
		float bodyYaw = entity.getAttackBodyYaw();
		float aimYaw = entity.getAttackAimYaw();
		float aimPitch = entity.getAttackAimPitch();
		if (cached == null || !cached.arm().equals(arm) || cached.bodyYaw() != bodyYaw
			|| cached.aimYaw() != aimYaw || cached.aimPitch() != aimPitch) {
			cached = new CachedMesh(arm, bodyYaw, aimYaw, aimPitch,
				SlimeBionicAttackRangeGeometry.build(arm, bodyYaw, aimYaw, aimPitch));
			CACHE.put(entity, cached);
		}
		int duration = entity.getAttackActionDuration();
		int elapsed = duration - entity.getAttackActionTick();
		boolean active = entity.isAttackPreviewContact();
		float preparation = Mth.clamp((elapsed + partialTick) / SlimeBionicCombat.activeStartTick(duration), 0.0f, 1.0f);
		float red = active ? 1.0f : 0.12f;
		float green = active ? 0.48f : 0.82f;
		float blue = active ? 0.08f : 1.0f;
		float fillAlpha = active ? 0.26f : Mth.lerp(preparation, 0.10f, 0.18f);
		float edgeAlpha = active ? 1.0f : Mth.lerp(preparation, 0.8f, 1.0f);
		PoseStack.Pose pose = poseStack.last();
		SlimeBionicAttackRangeGeometry.Mesh mesh = cached.mesh();
		VertexConsumer surface = buffer.getBuffer(SURFACE);
		for (SlimeBionicAttackRangeGeometry.Triangle face : mesh.faces()) {
			vertex(surface, pose, face.a(), red, green, blue, fillAlpha);
			vertex(surface, pose, face.b(), red, green, blue, fillAlpha);
			vertex(surface, pose, face.c(), red, green, blue, fillAlpha);
			// QUADS supports sorted translucency; the duplicated corner encodes one triangle.
			vertex(surface, pose, face.c(), red, green, blue, fillAlpha);
		}
		VertexConsumer backing = buffer.getBuffer(OUTLINE_BACKING);
		for (SlimeBionicAttackRangeGeometry.Segment segment : mesh.outline())
			line(backing, pose, segment, 0.025f, 0.045f, 0.065f, edgeAlpha * 0.65f);
		VertexConsumer outline = buffer.getBuffer(OUTLINE);
		for (SlimeBionicAttackRangeGeometry.Segment segment : mesh.guides())
			line(outline, pose, segment, red, green, blue, active ? 0.65f : 0.45f);
		for (SlimeBionicAttackRangeGeometry.Segment segment : mesh.outline())
			line(outline, pose, segment, red, green, blue, edgeAlpha);
	}

	private static RenderType lines(String name, double width) {
		return RenderType.create("create_biotech_attack_range_" + name,
			DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536,
			RenderType.CompositeState.builder()
				.setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
				.setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(width)))
				.setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
				.setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
				.setCullState(RenderStateShard.NO_CULL)
				.setWriteMaskState(RenderStateShard.COLOR_WRITE)
				.setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
				.createCompositeState(false));
	}

	private static void line(VertexConsumer consumer, PoseStack.Pose pose,
		SlimeBionicAttackRangeGeometry.Segment segment, float red, float green, float blue, float alpha) {
		Vec3 direction = segment.to().subtract(segment.from());
		// Clipping can leave very short segments, below Vec3.normalize()'s zero-length threshold.
		Vec3 normal = direction.scale(1.0d / direction.length());
		vertex(consumer, pose, segment.from(), red, green, blue, alpha)
			.setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
		vertex(consumer, pose, segment.to(), red, green, blue, alpha)
			.setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
	}

	private static VertexConsumer vertex(VertexConsumer consumer, PoseStack.Pose pose,
		Vec3 point, float red, float green, float blue, float alpha) {
		return consumer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
			.setColor(red, green, blue, alpha);
	}

	private record CachedMesh(SurgicalAssembly.ArmAttackGeometry arm, float bodyYaw, float aimYaw,
		float aimPitch, SlimeBionicAttackRangeGeometry.Mesh mesh) {}
}
