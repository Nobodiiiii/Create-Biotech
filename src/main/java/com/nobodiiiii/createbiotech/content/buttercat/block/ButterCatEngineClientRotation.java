package com.nobodiiiii.createbiotech.content.buttercat.block;

import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class ButterCatEngineClientRotation {
    private static final float ATTACHMENT_PLANE_STEP = 90.0f;

    private ButterCatEngineClientRotation() {
    }

    static void sync(ButterCatEngineBlockEntity blockEntity, float previousSpeed, boolean clientPacket) {
        if (blockEntity.getLevel() == null)
            return;

        float currentSpeed = blockEntity.getSpeed();
        if (!blockEntity.clientAttachmentRotationOffsetInitialized) {
            blockEntity.clientAttachmentRotationOffsetInitialized = true;
            blockEntity.clientAttachmentRotationOffset = 0;
        }

        if (!clientPacket || Mth.equal(previousSpeed, currentSpeed))
            return;

        if (Mth.equal(currentSpeed, 0)) {
            blockEntity.clientAttachmentRotationOffset = 0;
            return;
        }

        blockEntity.clientAttachmentRotationOffset =
            nextAttachmentPlane(blockEntity.clientAttachmentRotationOffset, currentSpeed);
    }

    private static float nextAttachmentPlane(float angle, float speed) {
        float normalized = Mth.positiveModulo(angle, 360.0f);
        if (speed > 0) {
            int plane = Mth.floor(normalized / ATTACHMENT_PLANE_STEP) + 1;
            return Mth.positiveModulo(plane * ATTACHMENT_PLANE_STEP, 360.0f);
        }

        int plane = Mth.ceil(normalized / ATTACHMENT_PLANE_STEP) - 1;
        return Mth.positiveModulo(plane * ATTACHMENT_PLANE_STEP, 360.0f);
    }
}
