package com.nobodiiiii.createbiotech.content.buttercat.register;

import java.util.IdentityHashMap;

import com.nobodiiiii.createbiotech.content.buttercat.ButterCatModule;
import com.nobodiiiii.createbiotech.content.buttercat.datagen.other.ModTags;
import com.nobodiiiii.createbiotech.content.buttercat.item.ButterFoodProperties;
import com.nobodiiiii.createbiotech.content.buttercat.item.ConfigurableButterFoodItem;
import com.nobodiiiii.createbiotech.content.buttercat.item.ConfigurableButterSequencedAssemblyItem;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import com.tterrag.registrate.util.entry.ItemEntry;

import static com.nobodiiiii.createbiotech.content.buttercat.ButterCatModule.REGISTRATE;

public class ModItems {
    static {
        ButterCatModule.REGISTRATE.setCreativeTab(ModCreativeModeTabs.CBC_TAB);
    }
    public static final ItemEntry<ConfigurableButterFoodItem> BUTTER = REGISTRATE
            .item("butter", properties -> new ConfigurableButterFoodItem(properties, ButterFoodProperties.Variant.BUTTER))
            .tag(ModTags.BUTTER)
            .tag(ModTags.FOOD_BUTTER)
            .properties(p -> p.food(ButterFoodProperties.create(ButterFoodProperties.Variant.BUTTER)))
            .register();
    public static final ItemEntry<ConfigurableButterFoodItem> HONEY_BUTTER = REGISTRATE
            .item("honey_butter", properties -> new ConfigurableButterFoodItem(properties, ButterFoodProperties.Variant.HONEY_BUTTER))
            .tag(ModTags.BUTTER)
            .tag(ModTags.FOOD_BUTTER)
            .properties(p -> p.food(ButterFoodProperties.create(ButterFoodProperties.Variant.HONEY_BUTTER)))
            .register();
    public static final ItemEntry<ConfigurableButterFoodItem> SUPER_BUTTER = REGISTRATE
            .item("super_butter", properties -> new ConfigurableButterFoodItem(properties, ButterFoodProperties.Variant.SUPER_BUTTER))
            .properties(p -> p.food(ButterFoodProperties.create(ButterFoodProperties.Variant.SUPER_BUTTER)).rarity(Rarity.EPIC))
            .register();

    public static final ItemEntry<ConfigurableButterSequencedAssemblyItem> INCOMPLETE_SUPER_BUTTER =  REGISTRATE
            .item("incomplete_super_butter", properties ->
                    new ConfigurableButterSequencedAssemblyItem(properties, ButterFoodProperties.Variant.INCOMPLETE_SUPER_BUTTER))
            .tag(ModTags.BUTTER)
            .tag(ModTags.FOOD_BUTTER)
            .properties(p -> p.food(ButterFoodProperties.create(ButterFoodProperties.Variant.INCOMPLETE_SUPER_BUTTER)))
            .register();

    private static final IdentityHashMap<Item, Integer> BUTTER_LEVEL_MAP = new IdentityHashMap<>();

    public static int getButterLevel(Item item) {
        if (item == null) return 1;
        // 懒加载
        if (BUTTER_LEVEL_MAP.isEmpty()) {
            BUTTER_LEVEL_MAP.put(BUTTER.get(), 1);
            BUTTER_LEVEL_MAP.put(HONEY_BUTTER.get(), 2);
            BUTTER_LEVEL_MAP.put(INCOMPLETE_SUPER_BUTTER.get(), 3);
            BUTTER_LEVEL_MAP.put(SUPER_BUTTER.get(), 5);
        }

        return BUTTER_LEVEL_MAP.getOrDefault(item, 1);
    }

    public static void register() {}

}

