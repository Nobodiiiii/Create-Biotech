package com.nobodiiiii.createbiotech.content.processing.basin;

import com.nobodiiiii.createbiotech.foundation.item.RenderedLivingEntityItem;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;

public class CapturedSmallSlimeItem extends RenderedLivingEntityItem<Slime> {
	public CapturedSmallSlimeItem(Properties properties) {
		super(properties, EntityType.SLIME, CapturedSmallSlimeItem::configureSlime);
	}

	private static void configureSlime(Slime slime) {
		slime.setSize(2, false);
	}
}
