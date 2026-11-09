package com.nobodiiiii.createbiotech.content.dingdongchicken;

import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureItemRenderer;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModels;

import net.minecraft.world.item.ItemStack;

/** Standard JSON chicken parts, with an optional fixed JSON bell assembly. */
public final class ChickenItemRenderer extends MachineCreatureItemRenderer {

	private final boolean hasBell;

	public ChickenItemRenderer(boolean hasBell, float scale) {
		super(MachineCreatureModels.chicken(), scale);
		this.hasBell = hasBell;
	}

	@Override
	protected void prepareModel(ItemStack stack, boolean measuring) {
		model.resetPose();
		model.part("body/bell").visible = hasBell;
	}
}
