package com.nobodiiiii.createbiotech.content.buttercat.register;

import com.nobodiiiii.createbiotech.content.buttercat.ButterCatModule;
import com.nobodiiiii.createbiotech.content.buttercat.block.ButterCatEngineBlock;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.foundation.data.BlockStateGen;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

import static com.nobodiiiii.createbiotech.content.buttercat.ButterCatModule.REGISTRATE;

public class ModBlocks {
    static {
        ButterCatModule.REGISTRATE.setCreativeTab(ModCreativeModeTabs.CBC_TAB);
    }
    public static final BlockEntry<ButterCatEngineBlock> BUTTER_CAT_ENGINE = REGISTRATE
            .block("butter_cat_engine", ButterCatEngineBlock::new)
            .initialProperties(SharedProperties::wooden)
            .properties(p -> p.sound(SoundType.WOOL).noOcclusion().mapColor(MapColor.TERRACOTTA_YELLOW))
            .blockstate(BlockStateGen.horizontalBlockProvider(true))
            .onRegister(block -> BlockStressValues.CAPACITIES.register(block, () -> {
                double maxStressCapacity = CBConfigs.SERVER.butterCat.maxStressCapacity.get();
                double maxGeneratedRpm = CBConfigs.SERVER.butterCat.maxGeneratedRpm.get();
                return maxGeneratedRpm == 0 ? 0 : maxStressCapacity / maxGeneratedRpm;
            }))
            .onRegister(block -> BlockStressValues.RPM.register(block,
                    new BlockStressValues.GeneratedRpm(
                            (int) Math.round(CBConfigs.SERVER.butterCat.maxGeneratedRpm.get()),
                            true)))
            .loot((loot,block)->loot.dropOther(block, AllBlocks.SHAFT))
            .item()
            .model((c, p) -> p.blockItem(c, "/item"))
            .build()
            .register();

    public static void register() {}
}

