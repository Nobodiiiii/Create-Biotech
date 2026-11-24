package com.nobodiiiii.createbiotech.client;

import java.math.BigDecimal;

import com.nobodiiiii.createbiotech.content.capturenet.CaptureNetTier;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;

import net.createmod.catnip.lang.FontHelper;
import net.minecraft.client.resources.language.I18n;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class CaptureNetTooltipModifier implements TooltipModifier {
	private static final String TOOLTIP_KEY = "item.create_biotech.capture_net.tooltip";
	private final CaptureNetTier tier;

	public CaptureNetTooltipModifier(CaptureNetTier tier) {
		this.tier = tier;
	}

	@Override
	public void modify(ItemTooltipEvent context) {
		String healthLimit = BigDecimal.valueOf(tier.healthLimit()).stripTrailingZeros().toPlainString();
		ItemDescription description = new ItemDescription.Builder(FontHelper.Palette.STANDARD_CREATE)
			.addSummary(I18n.get(TOOLTIP_KEY + ".summary"))
			.addBehaviour(I18n.get(TOOLTIP_KEY + ".condition1"),
				I18n.get(TOOLTIP_KEY + ".behaviour1", healthLimit))
			.addBehaviour(I18n.get(TOOLTIP_KEY + ".condition2"), I18n.get(TOOLTIP_KEY + ".behaviour2"))
			.addBehaviour(I18n.get(TOOLTIP_KEY + ".condition3"), I18n.get(TOOLTIP_KEY + ".behaviour3"))
			.addBehaviour(I18n.get(TOOLTIP_KEY + ".arm_condition"), I18n.get(TOOLTIP_KEY + ".arm_behaviour"))
			.build();
		context.getToolTip().addAll(1, description.getCurrentLines());
	}
}
