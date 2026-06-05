package com.nobodiiiii.createbiotech.content.buttercat.block;

import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class ButterCatEngineClientRotation {

    private ButterCatEngineClientRotation() {
    }

    static void sync(ButterCatEngineBlockEntity blockEntity, float previousSpeed, boolean clientPacket) {
        if (blockEntity.getLevel() == null) {
            return;
        }

        float currentSpeed = blockEntity.getTheoreticalSpeed();
        float renderTime = ButterCatEngineRenderer.getKineticRenderTicks(blockEntity.getLevel(), 0);

        if (!blockEntity.clientVisualRotationOffsetInitialized) {
            blockEntity.clientVisualRotationOffsetInitialized = true;
            blockEntity.clientVisualRotationOffset = 0;

            if (clientPacket && Mth.equal(previousSpeed, 0) && !Mth.equal(currentSpeed, 0)) {
                blockEntity.clientVisualRotationOffset = wrapHomeAlignedOffset(renderTime, currentSpeed);
            }
            return;
        }

        if (Mth.equal(previousSpeed, currentSpeed)) {
            return;
        }

        if (Mth.equal(currentSpeed, 0)) {
            blockEntity.clientVisualRotationOffset = 0;
            return;
        }

        if (Mth.equal(previousSpeed, 0)) {
            blockEntity.clientVisualRotationOffset = wrapHomeAlignedOffset(renderTime, currentSpeed);
            return;
        }

        float delta = renderTime * (previousSpeed - currentSpeed) * 3f / 10f;
        blockEntity.clientVisualRotationOffset = Mth.wrapDegrees(blockEntity.clientVisualRotationOffset + delta);
    }

    private static float wrapHomeAlignedOffset(float renderTime, float speed) {
        return Mth.wrapDegrees(-renderTime * speed * 3f / 10f);
    }
}
