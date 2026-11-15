package com.nobodiiiii.createbiotech.ponder;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.gauge.SpeedGaugeBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Biotech compression and Create press integration; generic actions live in Ponderer Lib. */
public final class PonderSupportExt {
    private static final String FORGE_DATA_ROOT = "create_biotech";
    private PonderSupportExt() {}

    private static void markSmartBlockEntitiesVirtual(SceneBuilder scene, Selection selection) {
        scene.addInstruction(ponderScene -> selection.forEach(pos -> {
            BlockEntity blockEntity = ponderScene.getWorld().getBlockEntity(pos);
            if (blockEntity instanceof SmartBlockEntity smartBlockEntity) {
                smartBlockEntity.markVirtual();
            }
        }));
    }

    public static void startCompressionAnimation(SceneBuilder scene, String entityId,
                                                 int rtStart, int kineticSpeed, float peak) {
        ResourceLocation filter = entityId == null || entityId.isBlank() ? null : ResourceLocation.tryParse(entityId);
        int tickSpeed = kineticSpeed == 0 ? 0
            : (int) Mth.lerp(Mth.clamp(Math.abs(kineticSpeed) / 512f, 0f, 1f), 1f, 60f);
        scene.world().modifyEntities(Creeper.class, creeper -> {
            if (filter != null && !EntityType.getKey(creeper.getType()).equals(filter)) {
                return;
            }
            CompoundTag animTag = new CompoundTag();
            animTag.putInt("StartTick", creeper.tickCount);
            animTag.putInt("RtStart", rtStart);
            animTag.putInt("TickSpeed", tickSpeed);
            animTag.putFloat("Peak", peak);
            CompoundTag forgeData = creeper.getPersistentData();
            CompoundTag biotech = forgeData.contains(FORGE_DATA_ROOT, Tag.TAG_COMPOUND)
                ? forgeData.getCompound(FORGE_DATA_ROOT) : new CompoundTag();
            biotech.put("PonderCompressionAnim", animTag);
            biotech.remove("PonderCompression");
            forgeData.put(FORGE_DATA_ROOT, biotech);
        });
    }

    public static void clearCompressionAnimation(SceneBuilder scene, String entityId) {
        ResourceLocation filter = entityId == null || entityId.isBlank() ? null : ResourceLocation.tryParse(entityId);
        scene.world().modifyEntities(Creeper.class, creeper -> {
            if (filter != null && !EntityType.getKey(creeper.getType()).equals(filter)) {
                return;
            }
            CompoundTag forgeData = creeper.getPersistentData();
            if (!forgeData.contains(FORGE_DATA_ROOT, Tag.TAG_COMPOUND)) {
                return;
            }
            CompoundTag biotech = forgeData.getCompound(FORGE_DATA_ROOT);
            biotech.remove("PonderCompressionAnim");
            biotech.remove("PonderCompression");
        });
    }

    public static void startPressCycle(SceneBuilder scene, BlockPos pos1, BlockPos pos2, float kineticSpeed) {
        BlockPos posMin = new BlockPos(
            Math.min(pos1.getX(), pos2.getX()),
            Math.min(pos1.getY(), pos2.getY()),
            Math.min(pos1.getZ(), pos2.getZ()));
        BlockPos posMax = new BlockPos(
            Math.max(pos1.getX(), pos2.getX()),
            Math.max(pos1.getY(), pos2.getY()),
            Math.max(pos1.getZ(), pos2.getZ()));
        Selection selection = scene.getScene().getSceneBuildingUtil().select().fromTo(posMin, posMax);
        scene.world().modifyBlockEntityNBT(selection, SpeedGaugeBlockEntity.class,
            nbt -> nbt.putFloat("Value", SpeedGaugeBlockEntity.getDialTarget(kineticSpeed)));
        scene.world().modifyBlockEntityNBT(selection, KineticBlockEntity.class,
            nbt -> nbt.putFloat("Speed", kineticSpeed));
        markSmartBlockEntitiesVirtual(scene, selection);
        for (BlockPos pos : BlockPos.betweenClosed(posMin, posMax)) {
            BlockPos copy = pos.immutable();
            scene.world().modifyBlockEntity(copy, MechanicalPressBlockEntity.class,
                press -> press.getPressingBehaviour().start(PressingBehaviour.Mode.WORLD));
        }
    }
}
