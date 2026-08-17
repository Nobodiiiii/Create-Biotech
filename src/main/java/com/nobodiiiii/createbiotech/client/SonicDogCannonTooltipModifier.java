package com.nobodiiiii.createbiotech.client;

import java.util.List;

import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicDogCannonUpgrade;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;

import net.createmod.catnip.lang.FontHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

public final class SonicDogCannonTooltipModifier implements TooltipModifier {
	private static final String TOOLTIP_KEY = "item.create_biotech.sonic_dog_cannon.tooltip";
	private static final String POTATO_CANNON_TOOLTIP_KEY = "item.create.potato_cannon.tooltip";
	private static final List<SonicDogCannonUpgrade> DISPLAY_ORDER = List.of(
		SonicDogCannonUpgrade.VOICE_PACK,
		SonicDogCannonUpgrade.SCOPE,
		SonicDogCannonUpgrade.DOG_COLLAR,
		SonicDogCannonUpgrade.SHRIEK_SONIC_BOOM
	);

	@Override
	public void modify(ItemTooltipEvent context) {
		ItemStack stack = context.getItemStack();
		ItemDescription.Builder builder = new ItemDescription.Builder(FontHelper.Palette.STANDARD_CREATE)
			.addSummary(I18n.get(TOOLTIP_KEY + ".summary"))
			.addBehaviour(I18n.get(TOOLTIP_KEY + ".condition1"), I18n.get(TOOLTIP_KEY + ".behaviour1"))
			.addBehaviour(I18n.get(POTATO_CANNON_TOOLTIP_KEY + ".condition2"),
				I18n.get(TOOLTIP_KEY + ".behaviour_backtank"))
			.addBehaviour(I18n.get(TOOLTIP_KEY + ".condition2"), I18n.get(TOOLTIP_KEY + ".behaviour2"));

		for (SonicDogCannonUpgrade upgrade : DISPLAY_ORDER) {
			if (!upgrade.isInstalled(stack))
				continue;
			builder.addBehaviour(upgradeName(stack, upgrade).getString(),
				I18n.get(upgrade.tooltipKey() + ".description"));
		}

		context.getToolTip().addAll(1, builder.build().getCurrentLines());
		if (!Screen.hasShiftDown())
			appendInstalledUpgrades(stack, context.getToolTip());
	}

	private static void appendInstalledUpgrades(ItemStack stack, List<Component> tooltip) {
		if (!SonicDogCannonUpgrade.hasInstalledUpgrade(stack))
			return;

		tooltip.add(CommonComponents.EMPTY);
		tooltip.add(Component.translatable("item.create_biotech.sonic_dog_cannon.upgrades")
			.withStyle(ChatFormatting.DARK_GRAY));
		for (SonicDogCannonUpgrade upgrade : DISPLAY_ORDER) {
			if (upgrade.isInstalled(stack))
				tooltip.add(CommonComponents.space().append(upgradeName(stack, upgrade)));
		}
	}

	private static Component upgradeName(ItemStack stack, SonicDogCannonUpgrade upgrade) {
		if (upgrade == SonicDogCannonUpgrade.DOG_COLLAR) {
			DyeColor color = SonicDogCannonUpgrade.getCollarColor(stack);
			return Component.translatable(upgrade.tooltipKey(),
				Component.translatable("color.minecraft." + color.getName()))
				.withStyle(ChatFormatting.GRAY);
		}
		return Component.translatable(upgrade.tooltipKey()).withStyle(ChatFormatting.GRAY);
	}
}
