package com.nobodiiiii.createbiotech.content.buttercat.register;

import java.util.IdentityHashMap;

import com.nobodiiiii.createbiotech.content.buttercat.ButterCatModule;
import com.nobodiiiii.createbiotech.content.buttercat.datagen.other.ModTags;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import com.tterrag.registrate.util.entry.ItemEntry;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import static com.nobodiiiii.createbiotech.content.buttercat.ButterCatModule.REGISTRATE;

public class ModItems {
    static {
        ButterCatModule.REGISTRATE.setCreativeTab(ModCreativeModeTabs.CBC_TAB);
    }
    public static final ItemEntry<Item> BUTTER = REGISTRATE
            .item("butter",Item::new)
            .tag(ModTags.BUTTER)
            .tag(ModTags.FOOD_BUTTER)
            .properties(p -> p.food(new FoodProperties.Builder()
                    .nutrition(1)
                    .saturationMod(0.5F)
                    .build())
            )
            .register();
    public static final ItemEntry<Item> HONEY_BUTTER = REGISTRATE
            .item("honey_butter",Item::new)
            .tag(ModTags.BUTTER)
            .tag(ModTags.FOOD_BUTTER)
            .properties(p -> p.food(new FoodProperties.Builder()
                    .nutrition(2)
                    .saturationMod(0.5F)
                    .effect(()->new MobEffectInstance(ModEffects.BUTTER_ROTATION_EFFECT.get(),60,0),1)
                    .build())

            )
            .register();
    public static final ItemEntry<Item> SUPER_BUTTER = REGISTRATE
            .item("super_butter",Item::new)
            .properties(p -> p.food(new FoodProperties.Builder()
                    .nutrition(18)
                    .saturationMod(0.5F)
                    .effect(()->new MobEffectInstance(ModEffects.BUTTER_ROTATION_EFFECT.get(),90,1),1)
                    .effect(()->new MobEffectInstance(MobEffects.LEVITATION,90,0),1)
                    .build()).rarity(Rarity.EPIC)
            )
            .register();

    public static final ItemEntry<SequencedAssemblyItem> INCOMPLETE_SUPER_BUTTER =  REGISTRATE
            .item("incomplete_super_butter", SequencedAssemblyItem::new)
            .tag(ModTags.BUTTER)
            .tag(ModTags.FOOD_BUTTER)
            .properties(p -> p.food(new FoodProperties.Builder()
                    .nutrition(2)
                    .saturationMod(0.5F)
                    .effect(()->new MobEffectInstance(ModEffects.BUTTER_ROTATION_EFFECT.get(),30,2),1)
                    .build())

            )
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

