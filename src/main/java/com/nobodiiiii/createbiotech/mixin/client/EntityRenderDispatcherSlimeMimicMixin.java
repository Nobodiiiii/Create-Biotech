package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.entity.BionicRiding;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalCapturedRenderPlan;
import com.nobodiiiii.createbiotech.content.possession.client.PossessionClientRenderer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherSlimeMimicMixin {
	@WrapOperation(method = "renderHitbox",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderVector(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Vector3f;Lnet/minecraft/world/phys/Vec3;I)V"),
		require = 1, expect = 1)
	private static void createBiotech$attachedDebugView(PoseStack pose, VertexConsumer buffer,
		Vector3f start, Vec3 vector, int color, Operation<Void> original,
		PoseStack debugPose, VertexConsumer debugBuffer, Entity entity, float partialTick,
		float red, float green, float blue) {
		// F3+B hard-codes (0, eyeHeight, 0), bypassing Entity.getEyePosition entirely.
		// The direction already comes from the common-side attachment view-vector transform.
		if (entity instanceof SlimeBionicEntity bionic && bionic.isBlockAttached())
			start = bionic.blockAttachmentRotation().rotate(bionic.getBlockAttachment().eyePoint()).toVector3f();
		original.call(pose, buffer, start, vector, color);
	}

	@WrapOperation(method = "renderHitbox",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLineBox(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;DDDDDDFFFF)V"),
		require = 1)
	private static void createBiotech$attachedDebugEyePlane(PoseStack pose, VertexConsumer buffer,
		double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
		float red, float green, float blue, float alpha, Operation<Void> original,
		PoseStack debugPose, VertexConsumer debugBuffer, Entity entity, float partialTick,
		float bodyRed, float bodyGreen, float bodyBlue) {
		if (red == 1 && green == 0 && blue == 0 && entity instanceof SlimeBionicEntity bionic
			&& bionic.isBlockAttached() && bionic.getAssembly() != null
			&& bionic.getAssembly().hitboxGeometry() != null) {
			var bounds = bionic.getAssembly().hitboxGeometry().overall();
			double eyeY = bionic.getBlockAttachment().eyePoint().y;
			pose.pushPose();
			pose.mulPose(BionicRiding.quaternion(bionic.blockAttachmentRotation()));
			original.call(pose, buffer, (double) bounds.minX(), eyeY - 0.01, (double) bounds.minZ(),
				(double) bounds.maxX(), eyeY + 0.01, (double) bounds.maxZ(), red, green, blue, alpha);
			pose.popPose();
		} else original.call(pose, buffer, minX, minY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
	}

	@WrapOperation(
		method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
		require = 1,
		expect = 1)
	private <E extends Entity> void createBiotech$renderCompleteSlimeMimic(EntityRenderer<? super E> renderer,
		E entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		Operation<Void> original) {
		if (entity instanceof Player player
			&& PossessionClientRenderer.tryRender(player, yaw, partialTick, poseStack, buffer, packedLight))
			return;
		if (!(entity instanceof LivingEntity living)
			|| !SurgicalCapturedRenderPlan.tryRenderSlimeMimic(renderer, living, yaw, partialTick,
				poseStack, buffer, packedLight)) {
			original.call(renderer, entity, yaw, partialTick, poseStack, buffer, packedLight);
		}
	}
}
