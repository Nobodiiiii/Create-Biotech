package com.nobodiiiii.createbiotech.content.buttercat.ponder;

import com.simibubi.create.content.kinetics.gauge.StressGaugeBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.nobodiiiii.createbiotech.content.buttercat.block.ButterCatEngineBlockEntity;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModBlocks;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModItems;
import com.nobodiiiii.createbiotech.registry.CBItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.CatVariant;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class ModPonderScenes {
    public static void butterCatEngine(SceneBuilder sceneBuilder, SceneBuildingUtil util){
        CreateSceneBuilder scene = new CreateSceneBuilder(sceneBuilder);
        scene.title("butter_cat_engine", "Let the Butter Cat spin around");
        scene.configureBasePlate(0, 0, 5 );
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(0, 1, 1, 4, 2, 4), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().everywhere(), 0);
        scene.idle(10);

        BlockPos catPos = util.grid().at(2, 1, 1);
        BlockPos enginePos = util.grid().at(2, 2, 2);
        BlockPos stressPos = util.grid().at(4, 2, 2);
        BlockPos speedPos = util.grid().at(0, 2, 2);

        scene.world().createEntity(world->{
               Cat c = EntityType.CAT.create(world);
               c.setPos(catPos.getCenter().x,catPos.getCenter().y-.5,catPos.getCenter().z);
               c.setYRot(180);
               c.setInSittingPose(true);
               c.setVariant(BuiltInRegistries.CAT_VARIANT.get(CatVariant.TABBY));
               return c;
        });

        scene.overlay().showText(50)
                .text("Use a cardboard box containing a cat on the Shafts.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(catPos));
        scene.idle(60);

        ItemStack boxedCat = CBItems.CARDBOARD_BOX.get().getDefaultInstance();
        scene.overlay().showControls(util.vector().topOf(catPos), Pointing.LEFT, 20).rightClick().withItem(boxedCat);
        scene.idle(45);

        scene.overlay().showControls(util.vector().topOf(enginePos), Pointing.LEFT, 20).rightClick();
        scene.idle(5);
        scene.world().modifyEntities(Cat.class, Entity::discard);
        scene.world().setBlock(enginePos,
                ModBlocks.CUTE_CAT_ON_SHAFT
                .getDefaultState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST),
                true);
        scene.idle(60);

        scene.overlay().showText(50)
                .text("This is a Cute Cat on a Shaft. Add bread and butter to turn it into a Butter Cat Engine.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(enginePos));
        scene.idle(90);

        scene.addKeyframe();

        scene.overlay().showControls(util.vector().topOf(enginePos), Pointing.DOWN, 20).rightClick().withItem(net.minecraft.world.item.Items.BREAD.getDefaultInstance());
        scene.idle(5);
        scene.world().setBlock(enginePos,
                ModBlocks.BUTTER_CAT_ENGINE
                        .getDefaultState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST),
                true);
        scene.idle(40);

        scene.overlay().showControls(util.vector().topOf(enginePos), Pointing.DOWN, 20).rightClick().withItem(ModItems.BUTTER.asStack());
        scene.idle(5);
        scene.world().modifyBlockEntity(enginePos,ButterCatEngineBlockEntity.class,(be)->be.addButterCount(1));
        scene.world().setKineticSpeed(util.select().everywhere(), 16);
        scene.world().modifyBlockEntityNBT(util.select().position(stressPos), StressGaugeBlockEntity.class,
                nbt -> nbt.putFloat("Value", .1f));
        scene.effects().indicateSuccess(speedPos);
        scene.effects().indicateRedstone(stressPos);
        scene.idle(60);
        scene.overlay().showText(50)
                .text("The more butter you add, the greater its kinetic output.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(enginePos));

        scene.idle(90);
        ItemStack[] butters = new ItemStack[]{ModItems.BUTTER.asStack(),ModItems.HONEY_BUTTER.asStack()};
        for (int i = 0;i<5 ;i ++){
            scene.overlay().showControls(util.vector().topOf(enginePos), Pointing.DOWN, 10).rightClick().withItem(butters[i%2]);
            scene.idle(5);
            scene.world().modifyBlockEntity(enginePos,ButterCatEngineBlockEntity.class,(be)->be.addButterCount(1));

            int i1 = i+1;
            int totalButter = i1 + 1;
            scene.world().setKineticSpeed(util.select().everywhere(), Math.min(256, totalButter * 16));
            scene.world().modifyBlockEntityNBT(util.select().position(stressPos), StressGaugeBlockEntity.class,
                    nbt -> nbt.putFloat("Value", Math.min(.9f, totalButter / 8f * .9f)));
            scene.effects().indicateSuccess(speedPos);
            scene.effects().indicateRedstone(stressPos);
            scene.idle(15);
        }
        scene.idle(40);

        scene.addKeyframe();

        scene.overlay().showText(50)
                .text("And with super butter? Perpetual motion machine.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(enginePos));
        scene.idle(90);
        scene.overlay().showControls(util.vector().topOf(enginePos), Pointing.DOWN, 20).rightClick().withItem(ModItems.SUPER_BUTTER.asStack());
        scene.idle(5);
        scene.world().modifyBlockEntity(enginePos,ButterCatEngineBlockEntity.class,(be)->be.setInfinite(true));
        scene.world().setKineticSpeed(util.select().everywhere(), 256);
        scene.world().modifyBlockEntityNBT(util.select().position(stressPos), StressGaugeBlockEntity.class,
                nbt -> nbt.putFloat("Value",.9f));
        scene.effects().indicateSuccess(speedPos);
        scene.effects().indicateRedstone(stressPos);
        scene.idle(60);
        scene.markAsFinished();
    }
}

