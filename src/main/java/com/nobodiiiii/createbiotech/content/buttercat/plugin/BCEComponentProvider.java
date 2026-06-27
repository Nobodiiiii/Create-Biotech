package com.nobodiiiii.createbiotech.content.buttercat.plugin;

import com.nobodiiiii.createbiotech.content.buttercat.block.ButterCatEngineBlockEntity;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModBlocks;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.Identifiers;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

public enum  BCEComponentProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor>{
    INSTANCE;
    private static final String BREAD = "bread";
    private static final String INFINITE = "infinite";
    private static final String BUTTER_COUNT = "butterCount";
    private static final String MAX_BUTTER_COUNT = "maxButterCount";
    private static final String REMAINING = "cd";

    @Override
    public IElement getIcon(BlockAccessor accessor, IPluginConfig config, IElement currentIcon) {
        ItemStack iconStack = accessor.getServerData().getBoolean(BREAD)
            ? ModItems.BUTTER_CAT_ENGINE.asStack()
            : ModBlocks.BUTTER_CAT_ENGINE.asStack();
        return IElementHelper.get().item(iconStack, 0.5f);
    }

    @Override
    public void appendTooltip(ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
        CompoundTag compound = blockAccessor.getServerData();
        replaceTitle(iTooltip, compound);

        IElementHelper elements = IElementHelper.get();
        IElement butter = elements.item(new ItemStack(ModItems.BUTTER.get()), 0.5f).size(new Vec2(10, 10)).translate(new Vec2(0, -1));
        butter.message(null);
        IElement super_butter = elements.item(new ItemStack(ModItems.SUPER_BUTTER.get()), 0.5f).size(new Vec2(10, 10)).translate(new Vec2(0, -1));
        super_butter.message(null);
        IElement clock = elements.item(new ItemStack(Items.CLOCK), 0.5f).size(new Vec2(10, 10)).translate(new Vec2(0, -1));
        clock.message(null);
        if (!compound.getBoolean(BREAD)) {
            return;
        }

        if (compound.getBoolean(INFINITE)) {
            iTooltip.add(super_butter);
            iTooltip.append(Component.translatable("item.create_biotech.butter").append(":"));
            iTooltip.append(IThemeHelper.get().info(Component.translatable("jade.infinity")));
        } else {
            iTooltip.add(butter);
            iTooltip.append(Component.translatable("item.create_biotech.butter").append(":"));

            int butterCount = compound.getInt(BUTTER_COUNT);
            int maxButterCount = compound.getInt(MAX_BUTTER_COUNT);
            iTooltip.append(Component.literal(butterCount > maxButterCount ? "§c" : "§f")
                    .append(String.format("%d/%d", butterCount, maxButterCount))
            );

            if(butterCount>0)
            {
                iTooltip.add(clock);
                iTooltip.append(Component.translatable("string.create_biotech.remaining"));
                iTooltip.append(IThemeHelper.get().seconds(compound.getInt(REMAINING)));
            }
        }

    }

    private static void replaceTitle(ITooltip tooltip, CompoundTag compound) {
        if (tooltip.get(Identifiers.CORE_OBJECT_NAME).isEmpty())
            return;

        MutableComponent title = Component.translatable(compound.getBoolean(BREAD)
                ? "item.create_biotech.butter_cat_engine"
                : "item.create_biotech.cute_cat_on_shaft");
        tooltip.remove(Identifiers.CORE_OBJECT_NAME);
        tooltip.add(0, IThemeHelper.get().title(title), Identifiers.CORE_OBJECT_NAME);
    }

    @Override
    public void appendServerData(CompoundTag compoundTag, BlockAccessor blockAccessor) {
        ButterCatEngineBlockEntity blockEntity = (ButterCatEngineBlockEntity) blockAccessor.getBlockEntity();
        compoundTag.putBoolean(BREAD, blockEntity.hasBread());
        compoundTag.putBoolean(INFINITE, blockEntity.isInfinite());
        compoundTag.putInt(BUTTER_COUNT, blockEntity.getTotalCount());
        compoundTag.putInt(MAX_BUTTER_COUNT, blockEntity.getMaxButterCount());
        compoundTag.putInt(REMAINING, blockEntity.getCd(true));

    }
    @Override
    public ResourceLocation getUid() {
        return ModPlugin.BUTTER_CAT_ENGINE;
    }
}

