package com.nobodiiiii.createbiotech.content.buttercat.plugin;

import com.nobodiiiii.createbiotech.content.buttercat.block.ButterCatEngineBlockEntity;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

public enum  BCEComponentProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor>{
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
        CompoundTag compound = blockAccessor.getServerData();

        IElementHelper elements = IElementHelper.get();
        IElement butter = elements.item(new ItemStack(ModItems.BUTTER.get()), 0.5f).size(new Vec2(10, 10)).translate(new Vec2(0, -1));
        butter.message(null);
        IElement super_butter = elements.item(new ItemStack(ModItems.SUPER_BUTTER.get()), 0.5f).size(new Vec2(10, 10)).translate(new Vec2(0, -1));
        super_butter.message(null);
        IElement clock = elements.item(new ItemStack(Items.CLOCK), 0.5f).size(new Vec2(10, 10)).translate(new Vec2(0, -1));
        clock.message(null);
        IElement bread = elements.item(new ItemStack(Items.BREAD), 0.5f).size(new Vec2(10, 10)).translate(new Vec2(0, -1));
        bread.message(null);
        if(!compound.getBoolean("bread")){
            iTooltip.add(bread);
            iTooltip.append(Items.BREAD.getName(Items.BREAD.getDefaultInstance()).copy().append(":"));
            iTooltip.append(Component.literal("0/1"));
            return;
        }

        if ( compound.getBoolean("infinite")) {
            iTooltip.add(super_butter);
            iTooltip.append(Component.translatable("item.create_biotech.butter").append(":"));
            iTooltip.append(IThemeHelper.get().info(Component.translatable("jade.infinity")));
        } else {
            iTooltip.add(butter);
            iTooltip.append(Component.translatable("item.create_biotech.butter").append(":"));

            int butterCount = compound.getInt("butterCount");
            ButterCatEngineBlockEntity be = (ButterCatEngineBlockEntity)blockAccessor.getBlockEntity();
            iTooltip.append(Component.literal(butterCount > be.getMaxButterCount() ? "§c" : "§f")
                    .append(String.format("%d/%d", butterCount, be.getMaxButterCount()))
            );

            if(butterCount>0)
            {
                iTooltip.add(clock);
                iTooltip.append(Component.translatable("string.create_biotech.remaining"));
                iTooltip.append( IThemeHelper.get().seconds(compound.getInt("cd")));
            }
        }

    }

    @Override
    public void appendServerData(CompoundTag compoundTag, BlockAccessor blockAccessor) {
        ButterCatEngineBlockEntity blockEntity = (ButterCatEngineBlockEntity) blockAccessor.getBlockEntity();
        compoundTag.putBoolean("bread", blockEntity.hasBread());
        compoundTag.putBoolean("infinite", blockEntity.isInfinite());
        compoundTag.putInt("butterCount", blockEntity.getTotalCount());

        compoundTag.putInt("cd", blockEntity.getCd(true));

    }
    @Override
    public ResourceLocation getUid() {
        return ModPlugin.BUTTER_CAT_ENGINE;
    }
}

