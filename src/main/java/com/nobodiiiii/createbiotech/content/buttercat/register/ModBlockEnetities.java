package com.nobodiiiii.createbiotech.content.buttercat.register;

import com.nobodiiiii.createbiotech.content.buttercat.block.ButterCatEngineBlockEntity;
import com.nobodiiiii.createbiotech.content.buttercat.block.ButterCatEngineRenderer;
import com.nobodiiiii.createbiotech.content.buttercat.block.ButterCatEngineVisual;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

import static com.nobodiiiii.createbiotech.content.buttercat.ButterCatModule.REGISTRATE;

public class ModBlockEnetities {

    public static final BlockEntityEntry<ButterCatEngineBlockEntity> BUTTER_CAT_ENGINE_BE= REGISTRATE
            .blockEntity("butter_cat_engine_be", ButterCatEngineBlockEntity::new)
            .visual(()-> ButterCatEngineVisual::new,false )
            .validBlocks(ModBlocks.BUTTER_CAT_ENGINE)
            .renderer(() -> ButterCatEngineRenderer::new)
            .register();

    public static void register() {}

}

