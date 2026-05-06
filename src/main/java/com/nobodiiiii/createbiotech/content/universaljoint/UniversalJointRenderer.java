package com.nobodiiiii.createbiotech.content.universaljoint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class UniversalJointRenderer extends KineticBlockEntityRenderer<UniversalJointBlockEntity> {

	private static final double MIN_SHAFT_LENGTH = 1.0E-4d;

	public UniversalJointRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void renderSafe(UniversalJointBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
		int light, int overlay) {
		BlockState state = getRenderedBlockState(be);
		VertexConsumer solidBuffer = buffer.getBuffer(RenderType.solid());
		renderRotatingBuffer(be, getRotatedModel(be, state), ms, solidBuffer, light);
		renderDriveShaft(be, state, ms, solidBuffer, light);
	}

	private void renderDriveShaft(UniversalJointBlockEntity be, BlockState state, PoseStack ms, VertexConsumer buffer,
		int light) {
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
		PoseStack shaftTransforms = new PoseStack();
		TransformStack.of(shaftTransforms)
			.translate(start)
			.rotateTo(0, 0, 1, (float) direction.x, (float) direction.y, (float) direction.z)
			.rotateZ(getAngleForBe(be, be.getBlockPos(), getRotationAxisOf(be)) * shaftRotationModifier)
			.scale(1, 1, (float) length)
			.translate(-.5f, -.5f, 0);

		SuperByteBuffer shaftBuffer = CachedBuffers.block(KINETIC_BLOCK, shaft(Direction.Axis.Z))
			.light(light)
			.transform(shaftTransforms);
		shaftBuffer.renderInto(ms, buffer);
	}

	private static boolean isPrimaryEndpoint(BlockPos first, BlockPos second) {
		if (first.getX() != second.getX())
			return first.getX() < second.getX();
		if (first.getY() != second.getY())
			return first.getY() < second.getY();
		return first.getZ() < second.getZ();
	}
}
