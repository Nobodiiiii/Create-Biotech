package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalJointIconModel;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitScope;
import com.nobodiiiii.createbiotech.registry.CBItems;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Item-sized anatomical regions and a comparator for the carrier rule, on the title row. */
final class BionicTraitInfoWidget implements IRecipeWidget {
	private static final int ICON_SIZE = 16;
	private static final int CELL = 18;
	private final ScreenPosition position;
	private final List<BionicTraitScope> scopes;
	private final List<Component> ruleTooltip;
	private final ItemStack transformStack = new ItemStack(CBItems.NECK_JOINT.get());
	private final ItemStack ruleIcon = new ItemStack(Items.COMPARATOR);

	BionicTraitInfoWidget(BionicTrait trait, int x, int y) {
		position = new ScreenPosition(x, y);
		scopes = trait.scopes().stream().sorted().toList();
		ruleTooltip = BionicTraitJeiText.ruleTooltip(trait);
	}

	static int width(BionicTrait trait) {
		return (trait.scopes().size() + 1) * CELL - (CELL - ICON_SIZE);
	}

	@Override
	public ScreenPosition getPosition() {
		return position;
	}

	@Override
	public void drawWidget(GuiGraphics graphics, double mouseX, double mouseY) {
		for (int index = 0; index < scopes.size(); index++)
			SurgicalJointIconModel.of(scopes.get(index)).element(ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT)
				.renderInGuiSlot(graphics, transformStack, index * CELL, 0);
		graphics.renderItem(ruleIcon, scopes.size() * CELL, 0);
	}

	@Override
	public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
		if (mouseX < 0 || mouseY < 0 || mouseY >= ICON_SIZE)
			return;
		int index = (int) mouseX / CELL;
		if (mouseX - index * CELL >= ICON_SIZE)
			return;
		if (index < scopes.size())
			tooltip.add(BionicTraitJeiText.scopeTooltip(scopes.get(index)));
		else if (index == scopes.size())
			tooltip.addAll(ruleTooltip);
	}
}
