package com.nobodiiiii.createbiotech.content.slimebelt;

import java.util.Random;
import java.util.function.Supplier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.BeltRenderer;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import com.simibubi.create.foundation.render.ShadowRenderHelper;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.levelWrappers.WrappedLevel;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SpriteShiftEntry;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.joml.Quaternionf;

import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper.LoopSection;

public class SlimeBeltRenderer extends SafeBlockEntityRenderer<SlimeBeltBlockEntity> {

	public SlimeBeltRenderer(BlockEntityRendererProvider.Context context) {}

	@Override
	public boolean shouldRenderOffScreen(SlimeBeltBlockEntity be) {
		return be.isController();
	}

	@Override
	protected void renderSafe(SlimeBeltBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light,
		int overlay) {
		BlockState blockState = be.getBlockState();
		if (!blockState.is(com.nobodiiiii.createbiotech.registry.CBBlocks.SLIME_BELT.get()))
			return;

		BeltSlope beltSlope = blockState.getValue(SlimeBeltBlock.SLOPE);
		BeltPart part = blockState.getValue(SlimeBeltBlock.PART);
		Direction facing = blockState.getValue(SlimeBeltBlock.HORIZONTAL_FACING);
		AxisDirection axisDirection = facing.getAxisDirection();

		boolean downward = beltSlope == BeltSlope.DOWNWARD;
		boolean upward = beltSlope == BeltSlope.UPWARD;
		boolean diagonal = downward || upward;
		boolean start = part == BeltPart.START;
		boolean end = part == BeltPart.END;
		boolean sideways = beltSlope == BeltSlope.SIDEWAYS;
		boolean alongX = facing.getAxis() == Direction.Axis.X;

		PoseStack localTransforms = new PoseStack();
		if (beltSlope == BeltSlope.VERTICAL)
			localTransforms.translate(0, -SlimeBeltHelper.VERTICAL_BELT_DROP, 0);
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

			SpriteShiftEntry spriteShift = getSpriteShiftEntry(diagonal, bottom);
			float speed = be.getSpeed();
			double scroll = bottom ? 0.5 : 0.0;
			if (speed != 0) {
				float time = renderTick * axisDirection.getStep();
				if (diagonal && (downward ^ alongX) || !diagonal && alongX)
					speed = -speed;

				scroll += speed * time / (31.5 * 16);
			}
			float scrollMult = diagonal ? 3f / 8f : 0.5f;
			TextureAtlasSprite originalSprite = spriteShift.getOriginal();
			TextureAtlasSprite targetSprite = spriteShift.getTarget();
			if (originalSprite != null && targetSprite != null) {
				float spriteSize = targetSprite.getV1() - targetSprite.getV0();
				scroll = scroll - Math.floor(scroll);
				scroll = scroll * spriteSize * scrollMult;
				beltBuffer.shiftUVScrolling(spriteShift, (float) scroll);
			}

			beltBuffer.transform(localTransforms).renderInto(ms, vb);
			if (diagonal)
				break;
		}

		if (be.hasPulley()) {
			Direction dir = sideways ? Direction.UP : blockState.getValue(SlimeBeltBlock.HORIZONTAL_FACING).getClockWise();
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
			KineticBlockEntityRenderer.standardKineticRotationTransform(superBuffer, be, light).renderInto(ms, vb);
		}

		renderItems(be, partialTicks, ms, buffer, light, overlay);
	}

	private static SpriteShiftEntry getSpriteShiftEntry(boolean diagonal, boolean bottom) {
		return diagonal ? SlimeBeltSpriteShifts.BELT_DIAGONAL
			: bottom ? SlimeBeltSpriteShifts.BELT_OFFSET : SlimeBeltSpriteShifts.BELT;
	}

	private void renderItems(SlimeBeltBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
		int light, int overlay) {
		if (!be.isController() || be.beltLength == 0)
			return;

		ms.pushPose();

		Direction beltFacing = be.getBeltFacing();
		Vec3i directionVec = beltFacing.getNormal();
		boolean vertical = be.getBlockState().getValue(SlimeBeltBlock.SLOPE) == BeltSlope.VERTICAL;
		Vec3 beltStartOffset = vertical ? Vec3.ZERO : Vec3.atLowerCornerOf(directionVec).scale(-.5).add(.5, 15 / 16f, .5);
		ms.translate(beltStartOffset.x, beltStartOffset.y, beltStartOffset.z);
		BeltSlope slope = be.getBlockState().getValue(SlimeBeltBlock.SLOPE);
		int verticality = slope == BeltSlope.DOWNWARD ? -1 : slope == BeltSlope.UPWARD ? 1 : 0;
		boolean slopeAlongX = beltFacing.getAxis() == Direction.Axis.X;
		boolean onContraption = be.getLevel() instanceof WrappedLevel;

		for (TransportedItemStack transported : be.getInventory().getTransportedItems())
			renderItem(be, partialTicks, ms, buffer, light, overlay, beltFacing, directionVec, slope, verticality,
				slopeAlongX, onContraption, transported, beltStartOffset);
		if (be.getInventory().getLazyClientItem() != null)
			renderItem(be, partialTicks, ms, buffer, light, overlay, beltFacing, directionVec, slope, verticality,
				slopeAlongX, onContraption, be.getInventory().getLazyClientItem(), beltStartOffset);

		ms.popPose();
	}

	private void renderItem(SlimeBeltBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light,
		int overlay, Direction beltFacing, Vec3i directionVec, BeltSlope slope, int verticality, boolean slopeAlongX,
		boolean onContraption, TransportedItemStack transported, Vec3 beltStartOffset) {
		if (slope == BeltSlope.VERTICAL) {
			renderVerticalItem(be, partialTicks, ms, buffer, light, overlay, onContraption, transported);
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		ItemRenderer itemRenderer = mc.getItemRenderer();
		MutableBlockPos mutablePos = new MutableBlockPos();
		float loopLength = SlimeBeltHelper.getLoopLength(be);

		float prev = transported.prevBeltPosition;
		float current = transported.beltPosition;
		if (Math.abs(current - prev) > loopLength / 2f) {
			if (current > prev)
				prev += loopLength;
			else
				current += loopLength;
		}

		float loopPosition = SlimeBeltHelper.normalizeLoopPosition(be, Mth.lerp(partialTicks, prev, current));
		float sideOffset = Mth.lerp(partialTicks, transported.prevSideOffset, transported.sideOffset);
		float frontOffset = SlimeBeltHelper.getFrontOffsetForLoopPosition(be, loopPosition);

		if (be.getSpeed() == 0) {
			loopPosition = transported.beltPosition;
			frontOffset = SlimeBeltHelper.getFrontOffsetForLoopPosition(be, loopPosition);
			sideOffset = transported.sideOffset;
		}

		Vec3 itemPos = SlimeBeltHelper.getVectorForOffset(be, loopPosition);
		if (shouldCullItem(itemPos, be.getLevel()))
			return;
		Vec3 localPos = itemPos.subtract(Vec3.atLowerCornerOf(be.getBlockPos()))
			.subtract(beltStartOffset);

		ms.pushPose();
		TransformStack.of(ms).nudge(transported.angle);
		ms.translate(localPos.x, localPos.y, localPos.z);

		boolean alongX = beltFacing.getClockWise().getAxis() == Direction.Axis.X;
		if (!alongX)
			sideOffset *= -1;
		ms.translate(alongX ? sideOffset : 0, 0, alongX ? 0 : sideOffset);

		int stackLight;
		if (onContraption) {
			stackLight = light;
		} else {
			int segment = Mth.clamp((int) Math.floor(frontOffset), 0, be.beltLength - 1);
			mutablePos.set(SlimeBeltHelper.getPositionForOffset(be, segment));
			stackLight = LevelRenderer.getLightColor(be.getLevel(), mutablePos);
		}

		boolean renderUpright = SlimeBeltHelper.isItemUpright(transported.stack);
		BakedModel bakedModel = itemRenderer.getModel(transported.stack, be.getLevel(), null, 0);
		boolean blockItem = bakedModel.isGui3d();
		LoopSection section = SlimeBeltHelper.getLoopSection(be, loopPosition);

		int count = 0;
		if (be.getLevel() instanceof PonderLevel || mc.player.getEyePosition(1.0F).distanceTo(itemPos) < 16)
			count = (int) (Mth.log2((int) transported.stack.getCount())) / 2;

		Random random = new Random(transported.angle);
		boolean vanillaSlopeTop = (slope == BeltSlope.DOWNWARD || slope == BeltSlope.UPWARD) && section == LoopSection.FRONT;
		if (vanillaSlopeTop) {
			boolean onSlope = Mth.clamp(frontOffset, .5f, be.beltLength - .5f) == frontOffset;
			boolean tiltForward = (slope == BeltSlope.DOWNWARD
				^ beltFacing.getAxisDirection() == AxisDirection.POSITIVE) == (beltFacing.getAxis() == Direction.Axis.Z);
			float slopeAngle = onSlope ? tiltForward ? -45 : 45 : 0;
			boolean slopeShadowOnly = renderUpright && onSlope;
			float slopeOffset = 1 / 8f;
			if (slopeShadowOnly)
				ms.pushPose();
			if (!renderUpright || slopeShadowOnly)
				ms.mulPose((slopeAlongX ? Axis.ZP : Axis.XP).rotationDegrees(slopeAngle));
			if (onSlope)
				ms.translate(0, slopeOffset, 0);
			ms.pushPose();
			ms.translate(0, -1 / 8f + 0.005f, 0);
			ShadowRenderHelper.renderShadow(ms, buffer, .75f, .2f);
			ms.popPose();
			if (slopeShadowOnly) {
				ms.popPose();
				ms.translate(0, slopeOffset, 0);
			}
		} else {
			applyTrackNormal(ms, SlimeBeltHelper.getTrackNormal(be, loopPosition));
			ms.pushPose();
			ms.translate(0, -1 / 8f + 0.005f, 0);
			ShadowRenderHelper.renderShadow(ms, buffer, .75f, .2f);
			ms.popPose();
		}

		if (renderUpright) {
			Entity renderViewEntity = mc.cameraEntity;
			if (renderViewEntity != null) {
				Vec3 positionVec = renderViewEntity.position();
				Vec3 diff = itemPos.subtract(positionVec);
				float yRot = (float) (Mth.atan2(diff.x, diff.z) + Math.PI);
				ms.mulPose(Axis.YP.rotation(yRot));
			}
			ms.translate(0, 3 / 32d, 1 / 16f);
		}

		for (int i = 0; i <= count; i++) {
			ms.pushPose();
			boolean box = PackageItem.isPackage(transported.stack);
			ms.mulPose(Axis.YP.rotationDegrees(transported.angle));
			if (!blockItem && !renderUpright) {
				ms.translate(0, -.09375, 0);
				ms.mulPose(Axis.XP.rotationDegrees(90));
			}

			if (blockItem && !box)
				ms.translate(random.nextFloat() * .0625f * i, 0, random.nextFloat() * .0625f * i);

			if (box) {
				ms.translate(0, 4 / 16f, 0);
				ms.scale(1.5f, 1.5f, 1.5f);
			} else {
				ms.scale(.5f, .5f, .5f);
			}

			itemRenderer.render(transported.stack, ItemDisplayContext.FIXED, false, ms, buffer, stackLight, overlay, bakedModel);
			ms.popPose();

			if (!renderUpright) {
				if (!blockItem)
					ms.mulPose(Axis.YP.rotationDegrees(10));
				ms.translate(0, blockItem ? 1 / 64d : 1 / 16d, 0);
			} else {
				ms.translate(0, 0, -1 / 16f);
			}
		}

		ms.popPose();
	}

	private void renderVerticalItem(SlimeBeltBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
		int light, int overlay, boolean onContraption, TransportedItemStack transported) {
		Minecraft mc = Minecraft.getInstance();
		ItemRenderer itemRenderer = mc.getItemRenderer();
		MutableBlockPos mutablePos = new MutableBlockPos();
		float loopLength = SlimeBeltHelper.getLoopLength(be);

		float prev = transported.prevBeltPosition;
		float current = transported.beltPosition;
		if (Math.abs(current - prev) > loopLength / 2f) {
			if (current > prev)
				prev += loopLength;
			else
				current += loopLength;
		}

		float loopPosition = SlimeBeltHelper.normalizeLoopPosition(be, Mth.lerp(partialTicks, prev, current));
		float sideOffset = Mth.lerp(partialTicks, transported.prevSideOffset, transported.sideOffset);
		if (be.getSpeed() == 0) {
			loopPosition = transported.beltPosition;
			sideOffset = transported.sideOffset;
		}

		Vec3 itemPos = SlimeBeltHelper.getVectorForOffset(be, loopPosition);
		if (shouldCullItem(itemPos, be.getLevel()))
			return;

		ms.pushPose();
		TransformStack.of(ms).nudge(transported.angle);

		Vec3 localPos = itemPos.subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
		ms.translate(localPos.x, localPos.y, localPos.z);

		Vec3 lateral = Vec3.atLowerCornerOf(be.getBeltFacing().getClockWise().getNormal()).scale(sideOffset);
		ms.translate(lateral.x, lateral.y, lateral.z);
		applyTrackNormal(ms, SlimeBeltHelper.getTrackNormal(be, loopPosition));

		int stackLight;
		if (onContraption) {
			stackLight = light;
		} else {
			int segment = Mth.clamp((int) Math.floor(SlimeBeltHelper.getFrontOffsetForLoopPosition(be, loopPosition)), 0,
				be.beltLength - 1);
			mutablePos.set(SlimeBeltHelper.getPositionForOffset(be, segment));
			stackLight = LevelRenderer.getLightColor(be.getLevel(), mutablePos);
		}

		boolean renderUpright = SlimeBeltHelper.isItemUpright(transported.stack);
		BakedModel bakedModel = itemRenderer.getModel(transported.stack, be.getLevel(), null, 0);
		boolean blockItem = bakedModel.isGui3d();

		int count = 0;
		if (be.getLevel() instanceof PonderLevel || mc.player != null && mc.player.getEyePosition(1.0F).distanceTo(itemPos) < 16)
			count = (int) (Mth.log2((int) transported.stack.getCount())) / 2;

		Random random = new Random(transported.angle);

		ms.pushPose();
		ms.translate(0, -1 / 8f + 0.005f, 0);
		ShadowRenderHelper.renderShadow(ms, buffer, .75f, .2f);
		ms.popPose();

		if (renderUpright) {
			Entity renderViewEntity = mc.cameraEntity;
			if (renderViewEntity != null) {
				Vec3 diff = itemPos.subtract(renderViewEntity.position());
				float yRot = (float) (Mth.atan2(diff.x, diff.z) + Math.PI);
				ms.mulPose(Axis.YP.rotation(yRot));
			}
			ms.translate(0, 3 / 32d, 1 / 16f);
		}

		for (int i = 0; i <= count; i++) {
			ms.pushPose();
			boolean box = PackageItem.isPackage(transported.stack);
			ms.mulPose(Axis.YP.rotationDegrees(transported.angle));
			if (!blockItem && !renderUpright) {
				ms.translate(0, -.09375, 0);
				ms.mulPose(Axis.XP.rotationDegrees(90));
			}

			if (blockItem && !box)
				ms.translate(random.nextFloat() * .0625f * i, 0, random.nextFloat() * .0625f * i);

			if (box) {
				ms.translate(0, 4 / 16f, 0);
				ms.scale(1.5f, 1.5f, 1.5f);
			} else {
				ms.scale(.5f, .5f, .5f);
			}

			itemRenderer.render(transported.stack, ItemDisplayContext.FIXED, false, ms, buffer, stackLight, overlay,
				bakedModel);
			ms.popPose();

			if (!renderUpright) {
				if (!blockItem)
					ms.mulPose(Axis.YP.rotationDegrees(10));
				ms.translate(0, blockItem ? 1 / 64d : 1 / 16d, 0);
			} else {
				ms.translate(0, 0, -1 / 16f);
			}
		}

		ms.popPose();
	}

	private void applyTrackNormal(PoseStack ms, Vec3 trackNormal) {
		Vec3 normal = trackNormal.normalize();
		ms.mulPose(new Quaternionf().rotationTo(0, 1, 0, (float) normal.x, (float) normal.y, (float) normal.z));
	}
}
