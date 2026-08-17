package com.yision.allay.item.allaycourier;

import com.nobodiiiii.createbiotech.foundation.item.BlockCenteredRenderedLivingEntityItem;
import com.yision.allay.entity.courier.AllayCourierEntity;
import com.yision.allay.registry.AllEntityTypes;

import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class IncompleteAllayCourierItem extends BlockCenteredRenderedLivingEntityItem<AllayCourierEntity> {
	private static final float ITEM_RENDER_SCALE = 1.5f;

	public IncompleteAllayCourierItem(Properties properties) {
		super(properties, AllEntityTypes.ALLAY_COURIER.get(), ITEM_RENDER_SCALE);
	}

	@Override
	public void configureRenderedEntity(AllayCourierEntity courier, ItemStack stack,
		ItemDisplayContext displayContext) {
		AllayCourierItem.configureRenderedCourier(courier, ItemStack.EMPTY, false);
	}
}
