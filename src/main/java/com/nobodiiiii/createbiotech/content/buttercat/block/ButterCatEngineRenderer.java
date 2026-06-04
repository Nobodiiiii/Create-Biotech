package com.nobodiiiii.createbiotech.content.buttercat.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

public class ButterCatEngineRenderer  extends KineticBlockEntityRenderer<ButterCatEngineBlockEntity> {
    public ButterCatEngineRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRenderOffScreen(ButterCatEngineBlockEntity be) {
        return false;
    }
    @Override
    protected BlockState getRenderedBlockState(ButterCatEngineBlockEntity be) {
        return shaft(getRotationAxisOf(be));
    }


    @Override
    protected void renderSafe(ButterCatEngineBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        if (VisualizationManager.supportsVisualization(be.getLevel()))
            return;

        BlockState blockState = be.getBlockState();
        Direction direction = blockState.getValue(HORIZONTAL_FACING);
        float degree = be.getInterpolatedAngle(partialTicks-1);
        if(direction == Direction.NORTH || direction == Direction.WEST) degree = -degree;

        //cat
        SuperByteBuffer cat = CachedBuffers.partialFacing(be.getCatModel(), blockState, direction);
        cat.rotateCenteredDegrees(degree,direction);
        cat.light(light).overlay(overlay).renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));

        //butter
        SuperByteBuffer butter = CachedBuffers.partialFacing(be.getButterModel(), blockState, direction);
        butter.rotateCenteredDegrees(degree,direction);
        butter.light(light).overlay(overlay).renderInto(ms, buffer.getBuffer(RenderType.solid()));

        //bread
        SuperByteBuffer bread = CachedBuffers.partialFacing(be.getBreadModel(), blockState, direction);
        bread.rotateCenteredDegrees(degree,direction);
        bread.light(light).overlay(overlay).renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

}

