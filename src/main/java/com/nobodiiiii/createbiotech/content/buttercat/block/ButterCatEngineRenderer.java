package com.nobodiiiii.createbiotech.content.buttercat.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.mixin.client.LevelRendererAccessor;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.Level;
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
        Axis axis = getRotationAxisOf(be);
        Direction positiveAxis = Direction.get(AxisDirection.POSITIVE, axis);
        float degree = getAttachmentAngleForBe(be, be.getBlockPos(), axis, partialTicks);

        //cat
        SuperByteBuffer cat = CachedBuffers.partialFacing(be.getCatModel(), blockState, direction);
        cat.rotateCenteredDegrees(degree, positiveAxis);
        cat.light(light).overlay(overlay).renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));

        //butter
        SuperByteBuffer butter = CachedBuffers.partialFacing(be.getButterModel(), blockState, direction);
        butter.rotateCenteredDegrees(degree, positiveAxis);
        butter.light(light).overlay(overlay).renderInto(ms, buffer.getBuffer(RenderType.solid()));

        //bread
        SuperByteBuffer bread = CachedBuffers.partialFacing(be.getBreadModel(), blockState, direction);
        bread.rotateCenteredDegrees(degree, positiveAxis);
        bread.light(light).overlay(overlay).renderInto(ms, buffer.getBuffer(RenderType.solid()));

        //rope
        SuperByteBuffer rope = CachedBuffers.partialFacing(be.getRopeModel(), blockState, direction);
        rope.rotateCenteredDegrees(degree, positiveAxis);
        rope.light(light).overlay(overlay).renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    static float getAttachmentAngleForBe(ButterCatEngineBlockEntity be, BlockPos pos, Axis axis, float partialTicks) {
        float time = getKineticRenderTicks(be.getLevel(), partialTicks);
        return (time * be.getSpeed() * 3f / 10 + getAttachmentRotationOffsetForBe(be, pos, axis)) % 360;
    }

    static float getAttachmentRotationOffsetForBe(ButterCatEngineBlockEntity be, BlockPos pos, Axis axis) {
        return getRotationOffsetForPosition(be, pos, axis) + be.getAttachmentRotationOffset();
    }

    static float getKineticRenderTicks(Level level, float partialTicks) {
        if (level != null && VisualizationManager.supportsVisualization(level)
            && Minecraft.getInstance().levelRenderer instanceof LevelRendererAccessor accessor)
            return accessor.create_biotech$getTicks() + partialTicks;
        return AnimationTickHolder.getRenderTime(level);
    }

}

