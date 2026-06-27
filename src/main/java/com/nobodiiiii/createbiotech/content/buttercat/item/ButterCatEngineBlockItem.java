package com.nobodiiiii.createbiotech.content.buttercat.item;

import com.nobodiiiii.createbiotech.content.buttercat.block.ButterCatEngineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class ButterCatEngineBlockItem extends BlockItem {
    private final String descriptionId;
    private final boolean startsWithBread;

    public ButterCatEngineBlockItem(Block block, Properties properties, String descriptionId, boolean startsWithBread) {
        super(block, properties);
        this.descriptionId = descriptionId;
        this.startsWithBread = startsWithBread;
    }

    @Override
    public String getDescriptionId() {
        return descriptionId;
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player, ItemStack stack,
        BlockState state) {
        if (startsWithBread && level.getBlockEntity(pos) instanceof ButterCatEngineBlockEntity be && !be.hasBread()) {
            be.addBread();
            be.setChanged();
        }

        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    }
}
