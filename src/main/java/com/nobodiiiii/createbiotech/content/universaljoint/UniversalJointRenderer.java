package com.nobodiiiii.createbiotech.content.universaljoint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class UniversalJointRenderer extends KineticBlockEntityRenderer<UniversalJointBlockEntity> {

	private static final double MIN_SHAFT_LENGTH = 1.0E-4d;
	private static final double PERPENDICULAR_EPSILON = 1.0E-7d;
	private static final ResourceLocation SLIME_TEXTURE = new ResourceLocation("textures/entity/slime/slime.png");
	private static final float SLIME_MODEL_DIAMETER = 8 / 16f;
	private static final float SHAFT_DIAMETER = 4 / 16f;
	private static final float SHAFT_RADIUS = SHAFT_DIAMETER / 2;
	private static final float SHAFT_CROSS_SECTION_SCALE = SHAFT_DIAMETER / SLIME_MODEL_DIAMETER;
	private static final float SLIME_MODEL_Y_OFFSET = -1.501f;

	private final SlimeModel<Entity> innerSlime;
	private final SlimeModel<Entity> outerSlime;

	public UniversalJointRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
		innerSlime = new SlimeModel<>(context.bakeLayer(ModelLayers.SLIME));
		outerSlime = new SlimeModel<>(context.bakeLayer(ModelLayers.SLIME_OUTER));
	}

	@Override
	protected void renderSafe(UniversalJointBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
		int light, int overlay) {
		BlockState state = getRenderedBlockState(be);
		VertexConsumer solidBuffer = buffer.getBuffer(RenderType.solid());
		renderRotatingBuffer(be, getRotatedModel(be, state), ms, solidBuffer, light);
		renderDriveShaft(be, state, ms, buffer, light, overlay);
	}

	private void renderDriveShaft(UniversalJointBlockEntity be, BlockState state, PoseStack ms, MultiBufferSource buffer,
		int light, int overlay) {
		BlockPos linkedPos = be.getLinkedPos();
		if (linkedPos == null || !isPrimaryEndpoint(be.getBlockPos(), linkedPos))
			return;

		Level level = be.getLevel();
		if (level == null || !level.isLoaded(linkedPos))
			return;

		BlockEntity linkedBlockEntity = level.getBlockEntity(linkedPos);
		if (!(linkedBlockEntity instanceof UniversalJointBlockEntity linkedJoint))
			return;
		if (!be.getBlockPos().equals(linkedJoint.getLinkedPos()))
			return;

		BlockState linkedState = linkedJoint.getBlockState();
		if (!state.hasProperty(UniversalJointBlock.FACING) || !linkedState.hasProperty(UniversalJointBlock.FACING))
			return;

		Vec3 blockOrigin = Vec3.atLowerCornerOf(be.getBlockPos());
		Vec3 start = UniversalJointBlockEntity.getInnerEndpoint(be.getBlockPos(), state).subtract(blockOrigin);
		Vec3 end = UniversalJointBlockEntity.getInnerEndpoint(linkedPos, linkedState).subtract(blockOrigin);
		Vec3 shaft = end.subtract(start);
		double length = shaft.length();
		if (length < MIN_SHAFT_LENGTH)
			return;

		Vec3 direction = shaft.scale(1 / length);
		float shaftRotationModifier =
			UniversalJointBlockEntity.getShaftRotationModifier(state, linkedState, linkedPos.subtract(be.getBlockPos()));
		if (isPerpendicularBridgeBetweenParallelEndpoints(state, linkedState, direction))
			shaftRotationModifier *= -1;

		PoseStack shaftTransforms = new PoseStack();
		TransformStack.of(shaftTransforms)
			.translate(start)
			.rotateTo(1, 0, 0, (float) direction.x, (float) direction.y, (float) direction.z)
			.rotateX(getAngleForBe(be, be.getBlockPos(), getRotationAxisOf(be)) * shaftRotationModifier)
			.translate(length / 2, -SHAFT_RADIUS, 0)
			.scale((float) (length / SLIME_MODEL_DIAMETER), SHAFT_CROSS_SECTION_SCALE, SHAFT_CROSS_SECTION_SCALE);

		ms.pushPose();
		TransformStack.of(ms)
			.transform(shaftTransforms);
		renderSlimeShaft(ms, buffer, light, overlay);
		ms.popPose();
	}

	private void renderSlimeShaft(PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
		ms.pushPose();
		ms.scale(-1, -1, 1);
		ms.translate(0, SLIME_MODEL_Y_OFFSET, 0);

		innerSlime.renderToBuffer(ms, buffer.getBuffer(innerSlime.renderType(SLIME_TEXTURE)), light, overlay, 1, 1, 1, 1);
		outerSlime.renderToBuffer(ms, buffer.getBuffer(RenderType.entityTranslucent(SLIME_TEXTURE)), light, overlay,
			1, 1, 1, 1);
		ms.popPose();
	}

	private static boolean isPerpendicularBridgeBetweenParallelEndpoints(BlockState state, BlockState linkedState,
		Vec3 direction) {
		Direction facing = state.getValue(UniversalJointBlock.FACING);
		Direction linkedFacing = linkedState.getValue(UniversalJointBlock.FACING);
		if (facing.getAxis() != linkedFacing.getAxis())
			return false;

		return Math.abs(facing.getAxis().choose(direction.x, direction.y, direction.z)) < PERPENDICULAR_EPSILON;
	}

	private static boolean isPrimaryEndpoint(BlockPos first, BlockPos second) {
		if (first.getX() != second.getX())
			return first.getX() < second.getX();
		if (first.getY() != second.getY())
			return first.getY() < second.getY();
		return first.getZ() < second.getZ();
	}
}
