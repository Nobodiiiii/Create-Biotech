package com.nobodiiiii.createbiotech.foundation.render.material;

import com.nobodiiiii.createbiotech.foundation.render.material.palette.MaterialPalette;
import com.nobodiiiii.createbiotech.foundation.render.material.palette.MaterialPaletteSync;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Objects;
import java.util.Optional;

public final class CastedMaterialsApi {
    private CastedMaterialsApi() {
    }

    public static MaterialSetResult setMaterial(ItemStack stack, ResourceLocation materialId) {
        Objects.requireNonNull(stack, "stack");
        MaterialSetResult validation = validate(materialId);
        if (validation != null) {
            return validation;
        }
        if (materialId.equals(stack.get(MaterialRenderingModule.MATERIAL.get()))) {
            return MaterialSetResult.UNCHANGED;
        }
        stack.set(MaterialRenderingModule.MATERIAL.get(), materialId);
        return MaterialSetResult.CHANGED;
    }

    public static MaterialSetResult setMaterial(CastedMaterialHolder holder, ResourceLocation materialId) {
        Objects.requireNonNull(holder, "holder");
        MaterialSetResult validation = validate(materialId);
        if (validation != null) {
            return validation;
        }
        MaterialSetResult result = holder.castedMaterialState().set(materialId);
        if (result == MaterialSetResult.CHANGED) {
            notifyChanged(holder);
        }
        return result;
    }

    public static MaterialSetResult clearMaterial(CastedMaterialHolder holder) {
        Objects.requireNonNull(holder, "holder");
        MaterialSetResult result = holder.castedMaterialState().clear();
        if (result == MaterialSetResult.CHANGED) {
            notifyChanged(holder);
        }
        return result;
    }

    public static Optional<ResourceLocation> getMaterial(ItemStack stack) {
        return Optional.ofNullable(Objects.requireNonNull(stack, "stack")
                .get(MaterialRenderingModule.MATERIAL.get()));
    }

    public static Optional<ResourceLocation> getMaterial(CastedMaterialHolder holder) {
        return Objects.requireNonNull(holder, "holder").castedMaterialState().get();
    }

    private static MaterialSetResult validate(ResourceLocation materialId) {
        Objects.requireNonNull(materialId, "materialId");
        Optional<MaterialPalette> active = MaterialPaletteSync.serverPalette();
        if (active.isEmpty()) {
            return MaterialSetResult.NO_ACTIVE_PALETTE;
        }
        return active.orElseThrow().indexOf(materialId).isEmpty()
                ? MaterialSetResult.UNKNOWN_MATERIAL
                : null;
    }

    private static void notifyChanged(CastedMaterialHolder holder) {
        holder.onCastedMaterialChanged();
        if (!(holder instanceof BlockEntity blockEntity)) {
            return;
        }

        blockEntity.setChanged();
        blockEntity.requestModelDataUpdate();
        if (blockEntity.getLevel() != null && !blockEntity.getLevel().isClientSide()) {
            blockEntity.getLevel().sendBlockUpdated(
                    blockEntity.getBlockPos(),
                    blockEntity.getBlockState(),
                    blockEntity.getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }

    public interface CastedMaterialHolder {
        CastedMaterialState castedMaterialState();

        default void onCastedMaterialChanged() { }
    }

    public enum MaterialSetResult {
        CHANGED, UNCHANGED, UNKNOWN_MATERIAL, NO_ACTIVE_PALETTE
    }
}
