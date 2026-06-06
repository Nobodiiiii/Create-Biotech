package com.nobodiiiii.createbiotech.content.buttercat.event;

import com.nobodiiiii.createbiotech.content.buttercat.ButterCatModule;
import com.nobodiiiii.createbiotech.content.buttercat.block.ButterCatEngineBlockEntity;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModBlocks;
import com.simibubi.create.AllBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.simibubi.create.content.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;


@Mod.EventBusSubscriber(modid = ButterCatModule.MODID)
public class AddCatHandler {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void addCat(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ItemStack heldItem = player.getItemInHand(event.getHand());

        if (player.isCrouching() || !player.mayBuild()) return;
        if (!state.is(AllBlocks.SHAFT.get())) return;
        if (state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y) return;
        if (!CapturedEntityBoxHelper.containsEntityType(heldItem, net.minecraft.world.entity.EntityType.CAT)) return;

        event.setCanceled(true);

        if (level.isClientSide()) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        Cat cat = createCatFromBox(heldItem, level);
        if (cat == null) {
            event.setCancellationResult(InteractionResult.FAIL);
            ClientEffect.create(level, pos, ClientEffect.EffectType.FAIL);
            return;
        }

        event.setCancellationResult(InteractionResult.SUCCESS);

        replaceBlock(level, player, pos, cat);
        if (!player.isCreative()) {
            CapturedEntityBoxHelper.clearCapturedEntity(heldItem);
        }
    }


    private static Cat createCatFromBox(ItemStack stack, Level level) {
        if (!(CapturedEntityBoxHelper.createCapturedEntity(stack, level) instanceof Cat cat)) {
            return null;
        }
        if (cat.isLeashed()) {
            cat.dropLeash(true, false);
        }
        return cat;
    }

    private static void replaceBlock(Level level,Player player, BlockPos pos, Cat cat) {
        Direction.Axis shaftAxis = level.getBlockState(pos).getValue(BlockStateProperties.AXIS);
        BlockState newBlockState = ModBlocks.BUTTER_CAT_ENGINE.getDefaultState()
            .setValue(HORIZONTAL_FACING, determineFacingFromPlayerContext(player, shaftAxis));

        level.setBlockAndUpdate(pos,newBlockState);

        if(level.getBlockEntity(pos) instanceof ButterCatEngineBlockEntity be){
            be.setCat(cat);
            cat.discard();
        }

        ClientEffect.create(level,pos, ClientEffect.EffectType.CAT);
    }

    private static Direction determineFacingFromPlayerContext(Player player, Direction.Axis shaftAxis) {
        if (player == null) {
            return shaftAxis == Direction.Axis.X ? Direction.EAST : Direction.NORTH;
        }

        if (shaftAxis == Direction.Axis.Y) {
            return player.getDirection().getOpposite();
        }

        Direction preferredFacing = player.getDirection().getOpposite();
        if (preferredFacing.getAxis() == shaftAxis) {
            return preferredFacing;
        }

        for (Direction direction : Direction.orderedByNearest(player)) {
            if (direction.getAxis().isVertical()) {
                continue;
            }

            Direction candidate = direction.getOpposite();
            if (candidate.getAxis() == shaftAxis) {
                return candidate;
            }
        }

        return shaftAxis == Direction.Axis.X ? Direction.EAST : Direction.NORTH;
    }
}

