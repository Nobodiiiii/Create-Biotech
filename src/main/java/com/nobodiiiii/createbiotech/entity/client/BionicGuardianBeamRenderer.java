package com.nobodiiiii.createbiotech.entity.client;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Minecraft 1.21.1 inlines its beam in GuardianRenderer.render, without a standalone entry point.
 * Keep its geometry, UVs, colours and timing identical, using the surgical eye as the origin.
 * References: ref/1.21.1/Minecraft/net/minecraft/client/renderer/entity/GuardianRenderer.java
 * and ref/1.21.1/Minecraft/net/minecraft/world/entity/monster/Guardian.java.
 */
public final class BionicGuardianBeamRenderer {

	private static final RenderType BEAM_RENDER_TYPE = RenderType.entityCutoutNoCull(
		ResourceLocation.withDefaultNamespace("textures/entity/guardian_beam.png"));

	private BionicGuardianBeamRenderer() {}

	@Nullable
	private static LivingEntity target(SlimeBionicEntity entity) {
		return !entity.isDeadOrDying() && entity.getSpecialAction() == 2
			&& entity.level().getEntity(entity.getSpecialTarget()) instanceof LivingEntity target ? target : null;
	}

	private static Vec3 position(LivingEntity entity, double height, float partialTick) {
		return new Vec3(Mth.lerp((double) partialTick, entity.xOld, entity.getX()),
			Mth.lerp((double) partialTick, entity.yOld, entity.getY()) + height,
			Mth.lerp((double) partialTick, entity.zOld, entity.getZ()));
	}

	public static boolean isVisible(SlimeBionicEntity entity, Frustum frustum) {
		LivingEntity target = target(entity);
		return target != null && frustum.isVisible(new AABB(entity.getSpecialOrigin(),
			position(target, target.getBbHeight() * 0.5, 1.0f)));
	}

	public static void tick(SlimeBionicEntity entity) {
		LivingEntity target = target(entity);
		if (target == null) return;
		Vec3 origin = entity.getSpecialOrigin();
		Vec3 offset = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(origin);
		double length = offset.length();
		Vec3 direction = offset.normalize();
		double scale = Math.min(entity.getSpecialActionTicks(), entity.getGuardianAttackDuration())
			/ (double) entity.getGuardianAttackDuration();
		double distance = entity.getRandom().nextDouble();
		while (distance < length) {
			distance += 1.8 - scale + entity.getRandom().nextDouble() * (1.7 - scale);
			Vec3 point = origin.add(direction.scale(distance));
			entity.level().addParticle(ParticleTypes.BUBBLE, point.x, point.y, point.z, 0, 0, 0);
		}
	}

	public static void render(SlimeBionicEntity entity, float partialTick, PoseStack poses,
		MultiBufferSource buffers) {
		LivingEntity target = target(entity);
		if (target == null) return;
		float time = Math.min(entity.getSpecialActionTicks(), entity.getGuardianAttackDuration()) + partialTick;
		float scale = time / entity.getGuardianAttackDuration();
		float scroll = time * 0.5f % 1.0f;
		Vec3 origin = entity.getSpecialOrigin(partialTick);
		Vec3 localOrigin = origin.subtract(position(entity, 0, partialTick));
		Vec3 offset = position(target, target.getBbHeight() * 0.5, partialTick).subtract(origin);
		float length = (float) (offset.length() + 1.0);
		Vec3 direction = offset.normalize();
		float pitch = (float) Math.acos(direction.y);
		float yaw = (float) Math.atan2(direction.z, direction.x);
		poses.pushPose();
		poses.translate(localOrigin.x, localOrigin.y, localOrigin.z);
		poses.mulPose(Axis.YP.rotationDegrees(((float) (Math.PI / 2) - yaw) * (180.0f / (float) Math.PI)));
		poses.mulPose(Axis.XP.rotationDegrees(pitch * (180.0f / (float) Math.PI)));
		float rotation = time * 0.05f * -1.5f;
		float intensity = scale * scale;
		int red = 64 + (int) (intensity * 191.0f);
		int green = 32 + (int) (intensity * 191.0f);
		int blue = 128 - (int) (intensity * 64.0f);
		float capX1 = Mth.cos(rotation + (float) (Math.PI * 3.0 / 4.0)) * 0.282f;
		float capZ1 = Mth.sin(rotation + (float) (Math.PI * 3.0 / 4.0)) * 0.282f;
		float capX2 = Mth.cos(rotation + (float) (Math.PI / 4)) * 0.282f;
		float capZ2 = Mth.sin(rotation + (float) (Math.PI / 4)) * 0.282f;
		float capX3 = Mth.cos(rotation + ((float) Math.PI * 5.0f / 4.0f)) * 0.282f;
		float capZ3 = Mth.sin(rotation + ((float) Math.PI * 5.0f / 4.0f)) * 0.282f;
		float capX4 = Mth.cos(rotation + ((float) Math.PI * 7.0f / 4.0f)) * 0.282f;
		float capZ4 = Mth.sin(rotation + ((float) Math.PI * 7.0f / 4.0f)) * 0.282f;
		float x1 = Mth.cos(rotation + (float) Math.PI) * 0.2f;
		float z1 = Mth.sin(rotation + (float) Math.PI) * 0.2f;
		float x2 = Mth.cos(rotation + 0.0f) * 0.2f;
		float z2 = Mth.sin(rotation + 0.0f) * 0.2f;
		float x3 = Mth.cos(rotation + (float) (Math.PI / 2)) * 0.2f;
		float z3 = Mth.sin(rotation + (float) (Math.PI / 2)) * 0.2f;
		float x4 = Mth.cos(rotation + (float) (Math.PI * 3.0 / 2.0)) * 0.2f;
		float z4 = Mth.sin(rotation + (float) (Math.PI * 3.0 / 2.0)) * 0.2f;
		float startV = -1.0f + scroll;
		float endV = length * 2.5f + startV;
		VertexConsumer vertices = buffers.getBuffer(BEAM_RENDER_TYPE);
		PoseStack.Pose pose = poses.last();
		vertex(vertices, pose, x1, length, z1, red, green, blue, 0.4999f, endV);
		vertex(vertices, pose, x1, 0, z1, red, green, blue, 0.4999f, startV);
		vertex(vertices, pose, x2, 0, z2, red, green, blue, 0, startV);
		vertex(vertices, pose, x2, length, z2, red, green, blue, 0, endV);
		vertex(vertices, pose, x3, length, z3, red, green, blue, 0.4999f, endV);
		vertex(vertices, pose, x3, 0, z3, red, green, blue, 0.4999f, startV);
		vertex(vertices, pose, x4, 0, z4, red, green, blue, 0, startV);
		vertex(vertices, pose, x4, length, z4, red, green, blue, 0, endV);
		float capV = entity.tickCount % 2 == 0 ? 0.5f : 0;
		vertex(vertices, pose, capX1, length, capZ1, red, green, blue, 0.5f, capV + 0.5f);
		vertex(vertices, pose, capX2, length, capZ2, red, green, blue, 1, capV + 0.5f);
		vertex(vertices, pose, capX4, length, capZ4, red, green, blue, 1, capV);
		vertex(vertices, pose, capX3, length, capZ3, red, green, blue, 0.5f, capV);
		poses.popPose();
	}

	private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float z,
		int red, int green, int blue, float u, float v) {
		vertices.addVertex(pose, x, y, z).setColor(red, green, blue, 255).setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
			.setNormal(pose, 0, 1, 0);
	}
}
