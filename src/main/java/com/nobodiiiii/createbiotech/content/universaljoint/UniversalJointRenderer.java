package com.nobodiiiii.createbiotech.content.universaljoint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.foundation.render.BlockEntityModelElement;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModel;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModels;
import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import com.nobodiiiii.createbiotech.mixin.client.LevelRendererAccessor;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class UniversalJointRenderer extends KineticBlockEntityRenderer<UniversalJointBlockEntity> {

	private static final double MIN_SHAFT_LENGTH = 1.0E-4d;
	private static final double PERPENDICULAR_EPSILON = 1.0E-7d;
	private static final ResourceLocation SLIME_TEXTURE = ResourceLocation.parse("textures/entity/slime/slime.png");
	private static final PartialModel ENDPOINT_SLIME_OVERLAY =
		PartialModel.of(CreateBiotech.asResource("block/universal_joint_endpoint_slime_overlay"));
	private static final float SLIME_MODEL_DIAMETER = 8 / 16f;
	private static final float SHAFT_DIAMETER = 4 / 16f;
	private static final float SHAFT_RADIUS = SHAFT_DIAMETER / 2;
	private static final float SHAFT_CROSS_SECTION_SCALE = SHAFT_DIAMETER / SLIME_MODEL_DIAMETER;
	private static final float SLIME_MODEL_Y_OFFSET = 1.501f;
	private static final int SLIME_CLUTCH_OVERLOAD_RGB = 0xF48522;

	private final MachineCreatureModel innerSlime;
	private final MachineCreatureModel outerSlime;

	public UniversalJointRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
		innerSlime = MachineCreatureModels.slimeInner();
		outerSlime = MachineCreatureModels.slimeOuter();
	}

	@Override
	protected void renderSafe(UniversalJointBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
		int light, int overlay) {
		BlockState state = getRenderedBlockState(be);
		VertexConsumer solidBuffer = buffer.getBuffer(RenderType.solid());
		renderSyncedRotatingBuffer(be, getRotatedModel(be, state), ms, solidBuffer, light, partialTicks);
		renderEndpointSlimeOverlay(be, state, ms, buffer, light, partialTicks);
		renderDriveShaft(be, state, ms, buffer, light, overlay, partialTicks);
	}

	@Override
	public boolean shouldRenderOffScreen(UniversalJointBlockEntity be) {
		if (isInvalid(be))
			return false;

		UniversalJointBlockEntity linkedJoint = be.getLoadedLinkedJoint();
		if (linkedJoint == null || isInvalid(linkedJoint)
			|| !be.references(linkedJoint) || !linkedJoint.references(be))
			return false;

		Level level = be.getLevel();
		Level linkedLevel = linkedJoint.getLevel();
		if (level == null || linkedLevel != level)
			return false;

		SubLevelAccess ownSpace = SubLevelCompat.getContaining(level, be.getBlockPos());
		SubLevelAccess linkedSpace = SubLevelCompat.getContaining(linkedLevel, linkedJoint.getBlockPos());
		return !SubLevelCompat.sameSpace(ownSpace, linkedSpace);
	}

	@Override
	public boolean isInvalid(UniversalJointBlockEntity be) {
		if (super.isInvalid(be) || be.isRemoved())
			return true;

		Level level = be.getLevel();
		BlockPos pos = be.getBlockPos();
		return level == null || level.getBlockEntity(pos) != be
			|| !level.getBlockState(pos).is(CBBlocks.UNIVERSAL_JOINT.get());
	}

	private void renderEndpointSlimeOverlay(UniversalJointBlockEntity be, BlockState state, PoseStack ms,
		MultiBufferSource buffer, int light, float partialTicks) {
		if (!state.hasProperty(UniversalJointBlock.FACING))
			return;

		Direction facing = state.getValue(UniversalJointBlock.FACING);
		SuperByteBuffer overlayBuffer = CachedBuffers.partialDirectional(ENDPOINT_SLIME_OVERLAY, state, facing,
			() -> getEndpointFacingTransform(facing));
		renderSyncedRotatingBuffer(be, overlayBuffer, ms, buffer.getBuffer(RenderType.translucent()), light,
			partialTicks);
	}

	private static PoseStack getEndpointFacingTransform(Direction facing) {
		PoseStack transform = new PoseStack();
		var stack = TransformStack.of(transform);
		stack.center();
		switch (facing) {
		case EAST -> stack.rotateYDegrees(270);
		case SOUTH -> stack.rotateYDegrees(180);
		case WEST -> stack.rotateYDegrees(90);
		case UP -> stack.rotateXDegrees(90);
		case DOWN -> stack.rotateXDegrees(270);
		case NORTH -> {
		}
		}
		stack.uncenter();
		return transform;
	}

	private void renderDriveShaft(UniversalJointBlockEntity be, BlockState state, PoseStack ms, MultiBufferSource buffer,
		int light, int overlay, float partialTicks) {
		UniversalJointBlockEntity linkedJoint = be.getLoadedLinkedJoint();
		if (linkedJoint == null || !linkedJoint.references(be)
			|| !UniversalJointBlockEntity.isPrimaryEndpoint(be, linkedJoint))
			return;

		Level level = be.getLevel();
		if (level == null)
			return;

		BlockState linkedState = linkedJoint.getBlockState();
		if (!state.hasProperty(UniversalJointBlock.FACING) || !linkedState.hasProperty(UniversalJointBlock.FACING))
			return;

		SubLevelAccess ownSpace = SubLevelCompat.getContaining(level, be.getBlockPos());
		SubLevelAccess linkedSpace = SubLevelCompat.getContaining(level, linkedJoint.getBlockPos());
		Vec3 startWorld = SubLevelCompat.toRenderWorld(ownSpace,
			UniversalJointBlockEntity.getInnerEndpoint(be.getBlockPos(), state), partialTicks);
		Vec3 endWorld = SubLevelCompat.toRenderWorld(linkedSpace,
			UniversalJointBlockEntity.getInnerEndpoint(linkedJoint.getBlockPos(), linkedState), partialTicks);
		Vec3 ownCenterWorld =
			SubLevelCompat.toRenderWorld(ownSpace, Vec3.atCenterOf(be.getBlockPos()), partialTicks);
		Vec3 linkedCenterWorld =
			SubLevelCompat.toRenderWorld(linkedSpace, Vec3.atCenterOf(linkedJoint.getBlockPos()), partialTicks);

		Vec3 blockOrigin = Vec3.atLowerCornerOf(be.getBlockPos());
		Vec3 start = SubLevelCompat.toRenderLocal(ownSpace, startWorld, partialTicks).subtract(blockOrigin);
		Vec3 end = SubLevelCompat.toRenderLocal(ownSpace, endWorld, partialTicks).subtract(blockOrigin);
		Vec3 shaft = end.subtract(start);
		double length = shaft.length();
		if (length < MIN_SHAFT_LENGTH)
			return;

		Vec3 direction = shaft.scale(1 / length);
		Vec3 worldDirection = endWorld.subtract(startWorld).normalize();
		float shaftRotationModifier = getShaftRotationModifier(state, linkedState, ownSpace, linkedSpace,
			worldDirection, partialTicks);

		PoseStack shaftTransforms = new PoseStack();
		TransformStack.of(shaftTransforms)
			.translate(start)
			.rotateTo(1, 0, 0, (float) direction.x, (float) direction.y, (float) direction.z)
			.rotateX(getShaftAngle(be, shaftRotationModifier, partialTicks))
			.translate(length / 2, -SHAFT_RADIUS, 0)
			.scale((float) (length / SLIME_MODEL_DIAMETER), SHAFT_CROSS_SECTION_SCALE, SHAFT_CROSS_SECTION_SCALE);

		ms.pushPose();
		TransformStack.of(ms)
			.transform(shaftTransforms);
		renderSlimeShaft(ms, buffer, light, overlay,
			getOverstretchOverlayColor(ownCenterWorld.distanceTo(linkedCenterWorld)));
		ms.popPose();
	}

	private void renderSlimeShaft(PoseStack ms, MultiBufferSource buffer, int light, int overlay,
		int overstretchColor) {
		BlockEntityModelElement.builder()
			.atLocal(0, SLIME_MODEL_Y_OFFSET, 0)
			.scale(-1, -1, 1)
			.packedLight(light)
			.render(ms, buffer, (poseStack, buf, lightArg) -> {
				innerSlime.renderToBuffer(poseStack, buf.getBuffer(innerSlime.renderType(SLIME_TEXTURE)), lightArg,
					overlay, 0xFFFFFFFF);
				outerSlime.renderToBuffer(poseStack, buf.getBuffer(RenderType.entityTranslucent(SLIME_TEXTURE)), lightArg,
					overlay, 0xFFFFFFFF);
				if (FastColor.ARGB32.alpha(overstretchColor) == 0)
					return;
				VertexConsumer overstretchBuffer =
					buf.getBuffer(RenderType.entityTranslucent(SLIME_TEXTURE));
				innerSlime.renderToBuffer(poseStack, overstretchBuffer, lightArg, overlay,
					overstretchColor);
				outerSlime.renderToBuffer(poseStack, overstretchBuffer, lightArg, overlay,
					overstretchColor);
			});
	}

	private static float getShaftRotationModifier(BlockState state, BlockState linkedState,
		SubLevelAccess ownSpace, SubLevelAccess linkedSpace, Vec3 worldDirection, float partialTicks) {
		Vec3 worldAxis = SubLevelCompat.renderNormalToWorld(ownSpace, getPositiveAxis(state), partialTicks).normalize();
		double side = worldAxis.dot(worldDirection);
		if (Math.abs(side) >= PERPENDICULAR_EPSILON)
			return (float) Math.signum(side);

		// Preserve the original perpendicular-link roll convention, but compare axes in
		// projected world space rather than relying on a raw plot-grid BlockPos delta.
		float modifier = state.getValue(UniversalJointBlock.FACING).getAxisDirection().getStep();
		Vec3 linkedWorldAxis =
			SubLevelCompat.renderNormalToWorld(linkedSpace, getPositiveAxis(linkedState), partialTicks).normalize();
		double axisAlignment = Math.abs(worldAxis.dot(linkedWorldAxis));
		return axisAlignment >= 1 - PERPENDICULAR_EPSILON ? -modifier : modifier;
	}

	private static Vec3 getPositiveAxis(BlockState state) {
		return switch (state.getValue(UniversalJointBlock.FACING).getAxis()) {
		case X -> new Vec3(1, 0, 0);
		case Y -> new Vec3(0, 1, 0);
		case Z -> new Vec3(0, 0, 1);
		};
	}

	static int getOverstretchOverlayColor(double distance) {
		int alpha =
			(int) Math.round(255.0d * UniversalJointBlockEntity.getStretchProgress(distance));
		return FastColor.ARGB32.color(alpha, SLIME_CLUTCH_OVERLOAD_RGB);
	}

	private static void renderSyncedRotatingBuffer(UniversalJointBlockEntity be, SuperByteBuffer superBuffer,
		PoseStack ms, VertexConsumer buffer, int light, float partialTicks) {
		Axis axis = getRotationAxisOf(be);
		float angle = getAngleForBe(be, be.getBlockPos(), axis, partialTicks);
		kineticRotationTransform(superBuffer, be, axis, angle, light).renderInto(ms, buffer);
	}

	private static float getAngleForBe(UniversalJointBlockEntity be, BlockPos pos, Axis axis, float partialTicks) {
		float time = getKineticRenderTicks(be.getLevel(), partialTicks);
		float offset = getRotationOffsetForPosition(be, pos, axis);
		return ((time * be.getSpeed() * 3f / 10 + offset) % 360) / 180 * (float) Math.PI;
	}

	private static float getShaftAngle(UniversalJointBlockEntity be, float shaftRotationModifier, float partialTicks) {
		Axis axis = getRotationAxisOf(be);
		float time = getKineticRenderTicks(be.getLevel(), partialTicks);
		float offset = getRotationOffsetForPosition(be, be.getBlockPos(), axis);
		float angle = (time * be.getSpeed() * 3f / 10) % 360;
		angle *= shaftRotationModifier;
		angle += offset;
		return angle / 180f * (float) Math.PI;
	}

	private static float getKineticRenderTicks(Level level, float partialTicks) {
		if (level != null && VisualizationManager.supportsVisualization(level)
			&& Minecraft.getInstance().levelRenderer instanceof LevelRendererAccessor accessor)
			return accessor.create_biotech$getTicks() + partialTicks;
		return AnimationTickHolder.getRenderTime(level);
	}

}
