package com.nobodiiiii.createbiotech.foundation.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class BlockEntityModelElement {

	private BlockEntityModelElement() {
	}

	public static Builder builder() {
		return new Builder();
	}

	@FunctionalInterface
	public interface ModelRenderer {
		void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight);
	}

	public static class Builder {
		private double xLocal;
		private double yLocal;
		private double zLocal;
		private double xRot;
		private double yRot;
		private double zRot;
		private double scaleX = 1;
		private double scaleY = 1;
		private double scaleZ = 1;
		private Vec3 rotationOffset = Vec3.ZERO;
		private int packedLight;

		private Builder() {
		}

		public Builder atLocal(double x, double y, double z) {
			this.xLocal = x;
			this.yLocal = y;
			this.zLocal = z;
			return this;
		}

		public Builder rotate(double xRot, double yRot, double zRot) {
			this.xRot = xRot;
			this.yRot = yRot;
			this.zRot = zRot;
			return this;
		}

		public Builder rotateY(double yRot) {
			return rotate(0, yRot, 0);
		}

		public Builder rotateCentered(double xRot, double yRot, double zRot) {
			return rotate(xRot, yRot, zRot)
				.withRotationOffset(VecHelper.getCenterOf(BlockPos.ZERO));
		}

		public Builder withRotationOffset(Vec3 offset) {
			this.rotationOffset = offset;
			return this;
		}

		public Builder scale(double scale) {
			return scale(scale, scale, scale);
		}

		public Builder scale(double xScale, double yScale, double zScale) {
			this.scaleX = xScale;
			this.scaleY = yScale;
			this.scaleZ = zScale;
			return this;
		}

		public Builder packedLight(int packedLight) {
			this.packedLight = packedLight;
			return this;
		}

		public void render(PoseStack poseStack, MultiBufferSource buffer, ModelRenderer modelRenderer) {
			poseStack.pushPose();
			try {
				poseStack.translate(xLocal, yLocal, zLocal);
				poseStack.translate(rotationOffset.x, rotationOffset.y, rotationOffset.z);
				poseStack.mulPose(Axis.ZP.rotationDegrees((float) zRot));
				poseStack.mulPose(Axis.XP.rotationDegrees((float) xRot));
				poseStack.mulPose(Axis.YP.rotationDegrees((float) yRot));
				poseStack.translate(-rotationOffset.x, -rotationOffset.y, -rotationOffset.z);
				poseStack.scale((float) scaleX, (float) scaleY, (float) scaleZ);

				modelRenderer.render(poseStack, buffer, packedLight);
			} finally {
				poseStack.popPose();
			}
		}
	}
}
