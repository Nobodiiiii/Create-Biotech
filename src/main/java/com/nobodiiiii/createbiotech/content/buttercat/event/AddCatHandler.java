package com.nobodiiiii.createbiotech.content.buttercat.event;

import java.util.List;

import com.nobodiiiii.createbiotech.content.buttercat.ButterCatModule;
import com.nobodiiiii.createbiotech.content.buttercat.block.ButterCatEngineBlockEntity;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModBlocks;
import com.simibubi.create.AllBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.simibubi.create.content.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;


@Mod.EventBusSubscriber(modid = ButterCatModule.MODID)
public class AddCatHandler {
    @SubscribeEvent
    public static void addCat(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);

        if (level.isClientSide()|| player.isCrouching()) return;

        if (state.is(AllBlocks.SHAFT.get())) {
            AABB searchBox = player.getBoundingBox().inflate(10);
            List<Cat> leashedCats = player.level().getEntitiesOfClass(
                    Cat.class,
                    searchBox,
                    cat -> cat.isLeashed() && cat.getLeashHolder() == player
            );
            if (!leashedCats.isEmpty()){
                replaceBlock(level,player, pos,leashedCats.get(0));
                event.setCanceled(true);
            }

        }
    }


    private static void replaceBlock(Level level,Player player, BlockPos pos, Cat cat) {

        BlockState newBlockState =  ModBlocks.BUTTER_CAT_ENGINE.getDefaultState();

        if (level.getBlockState(pos).getValue(BlockStateProperties.AXIS) == Direction.Axis.X)
            newBlockState= newBlockState.setValue(HORIZONTAL_FACING, Direction.EAST);
        else if(level.getBlockState(pos).getValue(BlockStateProperties.AXIS) == Direction.Axis.Z)
            newBlockState= newBlockState.setValue(HORIZONTAL_FACING, Direction.NORTH);
        else
            newBlockState= newBlockState.setValue(HORIZONTAL_FACING,  player.getDirection().getOpposite());

        level.setBlockAndUpdate(pos,newBlockState);

        if(level.getBlockEntity(pos) instanceof ButterCatEngineBlockEntity be){
            be.setCat(cat);
            cat.discard();
        }

        ClientEffect.create(level,pos, ClientEffect.EffectType.CAT);
    }
}

