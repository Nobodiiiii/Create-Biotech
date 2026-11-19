package com.nobodiiiii.createbiotech.content.surgery;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Display-only container for the registered surgical Ponder pages. */
public final class SurgicalPonderMenu extends AbstractContainerMenu {
    public SurgicalPonderMenu(MenuType<?> type, int id, boolean showBox) {
        super(type, id);
        if (showBox) {
            addSlot(new Slot(new SimpleContainer(1), 0, 80, 4) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }
            });
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
