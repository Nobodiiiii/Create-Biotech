package com.nobodiiiii.createbiotech.content.buttercat.register;

import java.util.HashMap;
import java.util.Map;

import com.nobodiiiii.createbiotech.content.buttercat.ButterCatModule;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.animal.CatVariant;

public class ModPartialModels {
    public static final PartialModel BCE_EMPTY = create("butter_cat_engine/butter/empty");
    public static final PartialModel BCE_BREAD = create("butter_cat_engine/butter/bread");
    public static final PartialModel BCE_BUTTER = create("butter_cat_engine/butter/butter");
    public static final PartialModel BCE_BUTTER_SMALL = create("butter_cat_engine/butter/butter_small");
    public static final PartialModel BCE_BUTTER_BIG = create("butter_cat_engine/butter/butter_big");
    public static final PartialModel BCE_SUPER_BUTTER = create("butter_cat_engine/butter/super_butter");

    public static final PartialModel BCE_CAT_ALL_BLACK = create("butter_cat_engine/cat/all_black");
    public static final PartialModel BCE_CAT_BLACK = create("butter_cat_engine/cat/black");
    public static final PartialModel BCE_CAT_BRITISH_SHORTHAIR = create("butter_cat_engine/cat/british_shorthair");
    public static final PartialModel BCE_CAT_CALICO = create("butter_cat_engine/cat/calico");
    public static final PartialModel BCE_CAT_JELLIE = create("butter_cat_engine/cat/jellie");
    public static final PartialModel BCE_CAT_PERSIAN = create("butter_cat_engine/cat/persian");
    public static final PartialModel BCE_CAT_RAGDOLL = create("butter_cat_engine/cat/ragdoll");
    public static final PartialModel BCE_CAT_RED = create("butter_cat_engine/cat/red");
    public static final PartialModel BCE_CAT_SIAMESE = create("butter_cat_engine/cat/siamese");
    public static final PartialModel BCE_CAT_TABBY = create("butter_cat_engine/cat/tabby");
    public static final PartialModel BCE_CAT_WHITE = create("butter_cat_engine/cat/white");

    public static final Map<ResourceKey<CatVariant>, PartialModel> CAT_VARIANT_MODEL_MAP = new HashMap<>();

    static {
        CAT_VARIANT_MODEL_MAP.put(CatVariant.TABBY, BCE_CAT_TABBY);
        CAT_VARIANT_MODEL_MAP.put(CatVariant.BLACK, BCE_CAT_BLACK);
        CAT_VARIANT_MODEL_MAP.put(CatVariant.RED, BCE_CAT_RED);
        CAT_VARIANT_MODEL_MAP.put(CatVariant.SIAMESE, BCE_CAT_SIAMESE);
        CAT_VARIANT_MODEL_MAP.put(CatVariant.BRITISH_SHORTHAIR, BCE_CAT_BRITISH_SHORTHAIR);
        CAT_VARIANT_MODEL_MAP.put(CatVariant.CALICO, BCE_CAT_CALICO);
        CAT_VARIANT_MODEL_MAP.put(CatVariant.PERSIAN, BCE_CAT_PERSIAN);
        CAT_VARIANT_MODEL_MAP.put(CatVariant.RAGDOLL, BCE_CAT_RAGDOLL);
        CAT_VARIANT_MODEL_MAP.put(CatVariant.WHITE, BCE_CAT_WHITE);
        CAT_VARIANT_MODEL_MAP.put(CatVariant.JELLIE, BCE_CAT_JELLIE);
        CAT_VARIANT_MODEL_MAP.put(CatVariant.ALL_BLACK, BCE_CAT_ALL_BLACK);
    }
    public static PartialModel getCatModel(ResourceKey<CatVariant> catVariant) {
            return CAT_VARIANT_MODEL_MAP.get(catVariant);
        }

    private static PartialModel create(String path) {
        return PartialModel.of(ButterCatModule.rl("block/" + path));
    }
    public static void init() {}
}

