package com.nobodiiiii.createbiotech.compat.jade;

import java.util.List;

import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessing;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.Accessor;
import snownee.jade.api.JadeIds;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ItemViewUtils;
import snownee.jade.api.view.ViewGroup;

/** Exposes the basin's complete internal inventory through Jade's standard item-storage renderer. */
public enum BasinItemStorageProvider implements IServerExtensionProvider<ItemStack> {
	INSTANCE;

	@Override
	public List<ViewGroup<ItemStack>> getGroups(Accessor<?> accessor) {
		if (!(accessor.getTarget() instanceof BasinBlockEntity basin))
			return null;
		return ItemViewUtils.groupOf(BasinEntityProcessing.getInternalItemHandler(basin), accessor);
	}

	@Override
	public ResourceLocation getUid() {
		return JadeIds.UNIVERSAL_ITEM_STORAGE_DEFAULT;
	}
}
