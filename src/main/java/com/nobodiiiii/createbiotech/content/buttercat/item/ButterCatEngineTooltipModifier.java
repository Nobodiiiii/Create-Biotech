package com.nobodiiiii.createbiotech.content.buttercat.item;

import java.util.List;
import java.util.Locale;

import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import net.minecraftforge.event.entity.player.ItemTooltipEvent;

public class ButterCatEngineTooltipModifier implements TooltipModifier {

	@Override
	public void modify(ItemTooltipEvent context) {
		List<Component> tooltip = context.getToolTip();
		tooltip.add(CommonComponents.EMPTY);

		CreateLang.translate("tooltip.capacityProvided")
			.style(ChatFormatting.GRAY)
			.addTo(tooltip);

		tooltip.add(Component.translatable("create_biotech.butter_cat_engine.tooltip.capacity_formula",
			format(CBConfigs.SERVER.butterCat.stressCapacityPerRpm.get()))
			.withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.translatable("create_biotech.butter_cat_engine.tooltip.capacity_cap",
			format(CBConfigs.SERVER.butterCat.maxStressCapacity.get()))
			.withStyle(ChatFormatting.DARK_GRAY));
	}

	private static String format(double value) {
		if (Math.rint(value) == value)
			return Integer.toString((int) value);
		return String.format(Locale.ROOT, "%.2f", value);
	}
}
