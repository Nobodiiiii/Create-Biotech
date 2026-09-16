package com.nobodiiiii.createbiotech.content.buttercat.block;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.nobodiiiii.createbiotech.content.buttercat.ButterCatVariants;
import com.nobodiiiii.createbiotech.content.buttercat.item.ButterCatIngredients;
import com.nobodiiiii.createbiotech.content.buttercat.event.ClientEffect;
import com.nobodiiiii.createbiotech.foundation.block.CBWrenchHelper;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.CatVariant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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
    protected ItemInteractionResult useItemOn(ItemStack itemStack, BlockState blockState, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult result) {
        if (hand != InteractionHand.MAIN_HAND || CBWrenchHelper.isWrench(itemStack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (player.isCrouching() || !(level.getBlockEntity(pos) instanceof ButterCatEngineBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!be.hasBread()) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        boolean isSuperButter = !be.isInfinite() && itemStack.is(CBItems.SUPER_BUTTER.get());
        boolean isButter = ButterCatIngredients.isButter(itemStack);

        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }

        if (isSuperButter) {
            be.setInfinite(true);
            itemStack.shrink(1);
            ClientEffect.create(level, pos, ClientEffect.EffectType.SUPER_BUTTER);
            displayMessage(player, "string.create_biotech.infinite");
            return ItemInteractionResult.sidedSuccess(false);
        }

        if (isButter) {
            int butterLevel = CBItems.getButterLevel(itemStack.getItem());
            if (!be.canAcceptButter(butterLevel)) {
                displayMessage(player, "string.create_biotech.full");
                return ItemInteractionResult.FAIL;
            }
            be.addButterCount(butterLevel);
            itemStack.shrink(1);
            ClientEffect.create(level, pos, ClientEffect.EffectType.BUTTER);
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
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

        if (ButterCatIngredients.isButter(itemStack))
            butterType = 0;
        else if (itemStack.is(CBItems.SUPER_BUTTER.get()))
            butterType = 1;

        if(butterType == -1 )
            return InteractionResultHolder.fail(ItemStack.EMPTY);

        if(!simulate){
            switch (butterType){
                case 0 -> {
                    int levelCount = CBItems.getButterLevel(itemStack.getItem());
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
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        // The cat arrives either boxed up, on its way onto a shaft, or as a block that was
        // picked up with its breed remembered. Applied on both sides, so the block the client
        // predicts is never a tabby until the server's block entity data catches up.
        ResourceKey<CatVariant> variant = ButterCatVariants.ofPlacementItem(stack);
        if (variant != null)
            withBlockEntityDo(level, pos, be -> be.setCatVariant(variant));
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos,
        Player player) {
        ItemStack stack = super.getCloneItemStack(state, target, level, pos, player);
        if (level.getBlockEntity(pos) instanceof ButterCatEngineBlockEntity be)
            ButterCatVariants.saveToBlockItem(stack, be.getCatVariant());
        return stack;
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
        return CBBlockEntityTypes.BUTTER_CAT_ENGINE.get();
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        ItemStack tool = builder.getOptionalParameter(LootContextParams.TOOL);
        BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (preservesWholeBlock(tool, builder.getLevel())) {
            List<ItemStack> wholeBlockDrops = super.getDrops(state, builder);
            if (blockEntity instanceof ButterCatEngineBlockEntity be)
                wholeBlockDrops.forEach(drop -> ButterCatVariants.saveToBlockItem(drop, be.getCatVariant()));
            return wholeBlockDrops;
        }

        List<ItemStack> drops = new ArrayList<>();
        drops.add(AllBlocks.SHAFT.asStack());
        if (hasBread(state))
            drops.add(new ItemStack(Items.BREAD));

        if (blockEntity instanceof ButterCatEngineBlockEntity be)
            spawnCat(builder, be);

        return drops;
    }

    private boolean preservesWholeBlock(ItemStack tool, ServerLevel level) {
        return tool != null && (CBWrenchHelper.isWrench(tool)
            || tool.getEnchantmentLevel(level.registryAccess().holderOrThrow(Enchantments.SILK_TOUCH)) > 0);
    }

    private boolean hasBread(BlockState state) {
        return state.is(CBBlocks.BUTTER_CAT_ENGINE.get());
    }

    private void spawnCat(LootParams.Builder builder, ButterCatEngineBlockEntity be) {
        ServerLevel level = builder.getLevel();
        Vec3 origin = builder.getOptionalParameter(LootContextParams.ORIGIN);
        if (origin == null)
            origin = Vec3.atCenterOf(be.getBlockPos());

        Entity source = builder.getOptionalParameter(LootContextParams.THIS_ENTITY);
        Cat cat = EntityType.CAT.create(level);
        if (cat == null)
            return;

        BuiltInRegistries.CAT_VARIANT.getHolder(be.getCatVariant()).ifPresent(cat::setVariant);
        cat.moveTo(origin.x, origin.y, origin.z, source == null ? level.random.nextFloat() * 360 : source.getYRot(),
            0);
        cat.setDeltaMovement(0, .15, 0);
        level.addFreshEntity(cat);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.hasBlockEntity() && (state.getBlock() != newState.getBlock() || !newState.hasBlockEntity())) {
            if (level.getBlockEntity(pos) instanceof ButterCatEngineBlockEntity be) {
                if (!level.isClientSide) {
                    if (be.isInfinite()) {
                        Block.popResource(level, pos, new ItemStack(CBItems.SUPER_BUTTER.get()));
                    } else {
                        int butterCount = be.getTotalCount();
                        if (butterCount > 0)
                            Block.popResource(level, pos, new ItemStack(CBItems.BUTTER.get(), butterCount));
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}

