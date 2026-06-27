package com.nobodiiiii.createbiotech.content.buttercat.block;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.nobodiiiii.createbiotech.content.buttercat.datagen.other.ModTags;
import com.nobodiiiii.createbiotech.content.buttercat.event.ClientEffect;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModBlockEnetities;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ButterCatEngineBlock extends HorizontalKineticBlock implements  IBE<ButterCatEngineBlockEntity> {


    public ButterCatEngineBlock(Properties properties) {
        super(properties);
    }
    private static final VoxelShape SHAPE_1 = Shapes.box(0.0, .2, .2, 1.0, .8, .8);
    private static final VoxelShape SHAPE_2 = Shapes.box(.2, .2, 0.0, .8, .8, 1.0);

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(HORIZONTAL_FACING)) {
            case NORTH, SOUTH -> SHAPE_2;
            default -> SHAPE_1;
        };
    }


    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ButterCatEngineBlockEntity be) {
            int butterCount = be.getButterCount();
            int maxButter = be.getMaxButterCount();
            if(maxButter == 0) return 0;
            return (int) Math.ceil((double) butterCount / maxButter * 15);
        }
        return 0;
    }


    @Override
    public InteractionResult use(BlockState blockState, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult result) {
        ItemStack itemStack = player.getItemInHand(hand);

        if (hand != InteractionHand.MAIN_HAND || itemStack.is(AllItems.WRENCH.get())) {
            return InteractionResult.FAIL;
        }

        if (player.isCrouching() || !(level.getBlockEntity(pos) instanceof ButterCatEngineBlockEntity be)) {
            return InteractionResult.PASS;
        }

        if (!be.hasBread()) {
            return InteractionResult.SUCCESS;
        }

        if (!be.isInfinite() && itemStack.is(ModItems.SUPER_BUTTER.get())) {
            be.setInfinite(true);
            itemStack.shrink(1);
            if (level.isClientSide) {
                ClientEffect.create(level, pos, ClientEffect.EffectType.SUPER_BUTTER);
            }
            displayMessage(player, "string.create_biotech.infinite");
        }

        if (ModTags.matchesIngredient(itemStack, ModTags.getButters())) {
            int butterLevel = ModItems.getButterLevel(itemStack.getItem());
            if (!be.canAcceptButter(butterLevel)) {
                displayMessage(player, "string.create_biotech.full");
                return InteractionResult.FAIL;
            }
            be.addButterCount(butterLevel);
            itemStack.shrink(1);
            if (level.isClientSide) {
                ClientEffect.create(level, pos, ClientEffect.EffectType.BUTTER);
            }
        }

        return InteractionResult.SUCCESS;
    }


    private void displayMessage(Player player, String translationKey) {
        player.displayClientMessage(Component.translatable(translationKey), true);
    }
    public static InteractionResultHolder<ItemStack> armInsert(BlockState state, Level level, BlockPos pos, ItemStack itemStack, boolean simulate) {
        if (!state.hasBlockEntity())
            return InteractionResultHolder.fail(ItemStack.EMPTY);

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ButterCatEngineBlockEntity blockEntity))
            return InteractionResultHolder.fail(ItemStack.EMPTY);

        if (!blockEntity.hasBread())
            return InteractionResultHolder.fail(ItemStack.EMPTY);
        if ( blockEntity.isFull())
            return InteractionResultHolder.fail(ItemStack.EMPTY);

        // 0=butter
        // 1=super butter
        // else
        int butterType = -1;

        if (ModTags.matchesIngredient(itemStack,ModTags.getButters()))
            butterType = 0;
        else if (itemStack.is(ModItems.SUPER_BUTTER.get()))
            butterType = 1;

        if(butterType == -1 )
            return InteractionResultHolder.fail(ItemStack.EMPTY);

        if(!simulate){
            switch (butterType){
                case 0 -> {
                    int levelCount = ModItems.getButterLevel(itemStack.getItem());
                    if (!blockEntity.canAcceptButter(levelCount))
                        return InteractionResultHolder.fail(ItemStack.EMPTY);
                    blockEntity.addButterCount(levelCount);
                }
                case 1 ->  {
                    blockEntity.setInfinite(true);
                }
            }
        }

        ItemStack container = itemStack.hasCraftingRemainingItem() ? itemStack.getCraftingRemainingItem() : ItemStack.EMPTY;
        if(!level.isClientSide) itemStack.shrink(1);
        return InteractionResultHolder.success(container);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == getRotationAxis(state);
    }

    @Override
    protected boolean areStatesKineticallyEquivalent(BlockState oldState, BlockState newState) {
        if (!super.areStatesKineticallyEquivalent(oldState, newState)) {
            return false;
        }
        return oldState.getValue(HORIZONTAL_FACING) == newState.getValue(HORIZONTAL_FACING);
    }

    @Override
    public Class<ButterCatEngineBlockEntity> getBlockEntityClass() {
        return ButterCatEngineBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ButterCatEngineBlockEntity> getBlockEntityType() {
        return ModBlockEnetities.BUTTER_CAT_ENGINE_BE.get();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.hasBlockEntity() && (state.getBlock() != newState.getBlock() || !newState.hasBlockEntity())) {
            if (level.getBlockEntity(pos) instanceof ButterCatEngineBlockEntity be) {
                if (!level.isClientSide) {
                    if (be.isInfinite()) {
                        Block.popResource(level, pos, new ItemStack(ModItems.SUPER_BUTTER.get()));
                    } else {
                        int butterCount = be.getTotalCount();
                        if (butterCount > 0)
                            Block.popResource(level, pos, new ItemStack(ModItems.BUTTER.get(), butterCount));
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}

