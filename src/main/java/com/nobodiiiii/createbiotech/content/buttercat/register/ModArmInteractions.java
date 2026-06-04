package com.nobodiiiii.createbiotech.content.buttercat.register;

import com.nobodiiiii.createbiotech.content.buttercat.ButterCatModule;
import com.nobodiiiii.createbiotech.content.buttercat.block.ButterCatEngineBlock;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.AllArmInteractionPointTypes;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class ModArmInteractions {

    private static <T extends ArmInteractionPointType> void register(String name, T type) {
        Registry.register(CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE, ButterCatModule.rl(name), type);
    }

    static {
        register("butter_cat_engine", new ButterCatEngineType());
    }

    public static class ButterCatEngineType extends ArmInteractionPointType {
        @Override
        public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
            return ModBlocks.BUTTER_CAT_ENGINE.has(state);
        }

        @Override
        public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
            return new ButterCatEnginePoint(this, level, pos, state);
        }
    }

    public static class ButterCatEnginePoint extends AllArmInteractionPointTypes.DepositOnlyArmInteractionPoint {
        public ButterCatEnginePoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
            super(type, level, pos, state);
        }


        @Override
        public ItemStack insert(ItemStack stack, boolean simulate) {
            ItemStack input = stack.copy();
            InteractionResultHolder<ItemStack> holder =
                    ButterCatEngineBlock.armInsert(cachedState, level, pos, input, simulate);
            ItemStack remainder = holder.getObject();
            if(input.isEmpty())
                return remainder;
            else {
                if (!simulate)
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), remainder);
                return input;
            }
        }
    }

    public static void register() {}
}

