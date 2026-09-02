package com.nobodiiiii.createbiotech.compat.jade;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessing;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;

/** Displays the hidden control-item count without exposing it to ordinary item capabilities. */
public enum BasinContainedSlimeComponentProvider
	implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
	INSTANCE;

	private static final ResourceLocation ID = CreateBiotech.asResource("basin_contained_slime");
	private static final String COUNT = "CapturedSmallSlimeCount";

	@Override
	public void appendServerData(CompoundTag data, BlockAccessor accessor) {
		if (accessor.getBlockEntity() instanceof BasinBlockEntity basin)
			data.putInt(COUNT, BasinEntityProcessing.getCapturedSmallSlimeItemCount(basin));
	}

	@Override
	public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
		int count = accessor.getServerData().getInt(COUNT);
		if (count <= 0)
			return;

		ItemStack icon = new ItemStack(CBItems.CAPTURED_SMALL_SLIME.get());
		tooltip.add(IElementHelper.get().item(icon, .5f));
		tooltip.append(Component.translatable(icon.getDescriptionId()).append(" × " + count));
	}

	@Override
	public ResourceLocation getUid() {
		return ID;
	}
}
