package com.nobodiiiii.createbiotech.content.powerbelt;

import java.util.function.Supplier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.BeltRenderer;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SpriteShiftEntry;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.block.state.BlockState;

public class PowerBeltRenderer extends SafeBlockEntityRenderer<PowerBeltBlockEntity> {

	public PowerBeltRenderer(BlockEntityRendererProvider.Context context) {}

	@Override
	protected void renderSafe(PowerBeltBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
		int light, int overlay) {
		BlockState blockState = be.getBlockState();
		if (!blockState.is(CBBlocks.POWER_BELT.get()))
			return;

		BeltSlope beltSlope = blockState.getValue(PowerBeltBlock.SLOPE);
		BeltPart part = blockState.getValue(PowerBeltBlock.PART);
		Direction facing = blockState.getValue(PowerBeltBlock.HORIZONTAL_FACING);
		AxisDirection axisDirection = facing.getAxisDirection();

		boolean downward = beltSlope == BeltSlope.DOWNWARD;
		boolean upward = beltSlope == BeltSlope.UPWARD;
		boolean diagonal = downward || upward;
		boolean start = part == BeltPart.START;
		boolean end = part == BeltPart.END;
		boolean sideways = beltSlope == BeltSlope.SIDEWAYS;
		boolean alongX = facing.getAxis() == Direction.Axis.X;

		PoseStack localTransforms = new PoseStack();
		var msr = TransformStack.of(localTransforms);
		VertexConsumer vb = buffer.getBuffer(RenderType.solid());
		float renderTick = AnimationTickHolder.getRenderTime(be.getLevel());

		msr.center()
			.rotateYDegrees(AngleHelper.horizontalAngle(facing) + (upward ? 180 : 0) + (sideways ? 270 : 0))
			.rotateZDegrees(sideways ? 90 : 0)
			.rotateXDegrees(!diagonal && beltSlope != BeltSlope.HORIZONTAL ? 90 : 0)
			.uncenter();

		if (downward || beltSlope == BeltSlope.VERTICAL && axisDirection == AxisDirection.POSITIVE) {
			boolean swap = start;
			start = end;
			end = swap;
		}

		for (boolean bottom : Iterate.trueAndFalse) {
			PartialModel beltPartial = BeltRenderer.getBeltPartial(diagonal, start, end, bottom);
			SuperByteBuffer beltBuffer = CachedBuffers.partial(beltPartial, blockState)
				.light(light);

			SpriteShiftEntry spriteShift = BeltRenderer.getSpriteShiftEntry(null, diagonal, bottom);
			float speed = be.getSpeed();
			if (speed != 0)
				shiftBeltUvs(beltBuffer, spriteShift, speed, renderTick, axisDirection, diagonal, downward, alongX,
					sideways, bottom);

			beltBuffer.transform(localTransforms)
				.renderInto(ms, vb);

			if (diagonal)
				break;
		}

		if (be.hasPulley())
			renderPulley(be, blockState, ms, vb, light, sideways);
	}

	private static void shiftBeltUvs(SuperByteBuffer beltBuffer, SpriteShiftEntry spriteShift, float speed,
		float renderTick, AxisDirection axisDirection, boolean diagonal, boolean downward, boolean alongX, boolean sideways,
		boolean bottom) {
		float time = renderTick * axisDirection.getStep();
		if (diagonal && downward != alongX || !sideways && !diagonal && alongX
			|| sideways && axisDirection == AxisDirection.NEGATIVE)
			speed = -speed;

		TextureAtlasSprite targetSprite = spriteShift.getTarget();
		if (targetSprite == null)
			return;

		float spriteSize = targetSprite.getV1() - targetSprite.getV0();
		float scrollMult = diagonal ? 3f / 8f : .5f;
		double scroll = speed * time / (31.5 * 16) + (bottom ? .5 : 0);
		scroll = scroll - Math.floor(scroll);
		scroll = scroll * spriteSize * scrollMult;
		beltBuffer.shiftUVScrolling(spriteShift, (float) scroll);
	}

	private static void renderPulley(PowerBeltBlockEntity be, BlockState blockState, PoseStack ms, VertexConsumer vb,
		int light, boolean sideways) {
		Direction dir = sideways ? Direction.UP : blockState.getValue(PowerBeltBlock.HORIZONTAL_FACING)
			.getClockWise();
		Supplier<PoseStack> matrixStackSupplier = () -> {
			PoseStack stack = new PoseStack();
			var stacker = TransformStack.of(stack);
			stacker.center();
			if (dir.getAxis() == Direction.Axis.X)
				stacker.rotateYDegrees(90);
			if (dir.getAxis() == Direction.Axis.Y)
				stacker.rotateXDegrees(90);
			stacker.rotateXDegrees(90);
			stacker.uncenter();
			return stack;
		};

		SuperByteBuffer superBuffer =
			CachedBuffers.partialDirectional(AllPartialModels.BELT_PULLEY, blockState, dir, matrixStackSupplier);
		KineticBlockEntityRenderer.standardKineticRotationTransform(superBuffer, be, light)
			.renderInto(ms, vb);
	}
}
