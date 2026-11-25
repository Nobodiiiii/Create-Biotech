package com.nobodiiiii.createbiotech.content.shulkerpackager;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ShulkerPackagerRenderer extends SmartBlockEntityRenderer<ShulkerPackagerBlockEntity> {

	private static final PartialModel HATCH_OPEN =
		PartialModel.of(CreateBiotech.asResource("block/shulker_packager/hatch_open"));
	private static final PartialModel HATCH_CLOSED =
		PartialModel.of(CreateBiotech.asResource("block/shulker_packager/hatch_closed"));
	private static final PartialModel TRAY =
		PartialModel.of(CreateBiotech.asResource("block/shulker_packager/tray"));

	public ShulkerPackagerRenderer(Context context) {
		super(context);
	}

	@Override
	protected void renderSafe(ShulkerPackagerBlockEntity be, float partialTicks, PoseStack ms,
		MultiBufferSource buffer, int light, int overlay) {
		super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

		BlockState blockState = be.getBlockState();
		renderAnimated(blockState, be.getLevel(), be.getRenderedBox(), be.getTrayOffset(partialTicks),
			getHatchModel(be.animationInward, be.animationTicks), ms, buffer, light, overlay);
	}

	public static PartialModel getHatchModel(boolean animationInward, int animationTicks) {
		return isHatchOpen(animationInward, animationTicks) ? HATCH_OPEN : HATCH_CLOSED;
	}

	public static PartialModel getTrayModel(BlockState blockState) {
		return TRAY;
	}

	public static boolean isHatchOpen(boolean animationInward, int animationTicks) {
		return animationTicks > (animationInward ? 1 : 5)
			&& animationTicks < PackagerBlockEntity.CYCLE - (animationInward ? 5 : 1);
	}

	public static void renderAnimated(BlockState blockState, Level level, ItemStack renderedBox, float trayOffset,
		PartialModel hatchModel, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
		Direction facing = blockState.getValue(ShulkerPackagerBlock.FACING)
			.getOpposite();

		if (!VisualizationManager.supportsVisualization(level)) {
			SuperByteBuffer sbb = CachedBuffers.partial(hatchModel, blockState);
			sbb.translate(Vec3.atLowerCornerOf(facing.getNormal()).scale(.49999f))
				.rotateYCenteredDegrees(AngleHelper.horizontalAngle(facing))
				.rotateXCenteredDegrees(AngleHelper.verticalAngle(facing))
				.light(light)
				.renderInto(ms, buffer.getBuffer(RenderType.solid()));

			sbb = CachedBuffers.partial(getTrayModel(blockState), blockState);
			sbb.translate(Vec3.atLowerCornerOf(facing.getNormal()).scale(trayOffset))
				.rotateYCenteredDegrees(facing.toYRot())
				.light(light)
				.renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
		}

		if (renderedBox.isEmpty())
			return;

		ms.pushPose();
		var msr = TransformStack.of(ms);
		msr.translate(Vec3.atLowerCornerOf(facing.getNormal()).scale(trayOffset))
			.translate(.5f, .5f, .5f)
			.rotateYDegrees(facing.toYRot())
			.translate(0, 2 / 16f, 0)
			.scale(1.49f, 1.49f, 1.49f);
		Minecraft.getInstance()
			.getItemRenderer()
			.renderStatic(null, renderedBox, ItemDisplayContext.FIXED, false, ms, buffer, level, light, overlay, 0);
		ms.popPose();
	}
}
