package com.nobodiiiii.createbiotech.content.processing.basin;

import java.util.function.Consumer;

import com.nobodiiiii.createbiotech.compat.jei.CapturedSmallSlimeJeiItemRenderer;

import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.fml.ModList;

public class CapturedSmallSlimeItem extends Item {
	public CapturedSmallSlimeItem(Properties properties) {
		super(properties);
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void initializeClient(Consumer<IClientItemExtensions> consumer) {
		if (ModList.get()
			.isLoaded("jei"))
			consumer.accept(CapturedSmallSlimeJeiItemRenderer.itemExtensions());
	}
}
