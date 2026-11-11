package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Anatomical regions and an 18px inheritance-rule sprite on the title row. */
final class BionicTraitInfoWidget implements IRecipeWidget {
	private static final int ICON_SIZE = 16;
	private static final int CELL = 18;
	private static final int ITEM_INSET = (CELL - ICON_SIZE) / 2;
	private final ScreenPosition position;
	private final BionicTrait trait;
	private final List<BionicTraitScope> scopes;
	private final ItemStack transformStack = new ItemStack(CBItems.NECK_JOINT.get());
	private final ResourceLocation ruleIcon;

	BionicTraitInfoWidget(BionicTrait trait, int x, int y) {
		position = new ScreenPosition(x, y);
		this.trait = trait;
		scopes = trait.scopes().stream().sorted().toList();
		ruleIcon = CreateBiotech.asResource("jei/trait_rule/" + switch (trait.rule().type()) {
		case PRESENCE -> "presence";
		case COVERAGE_THRESHOLD -> "threshold";
		case COVERAGE_SCALED -> "scaled";
		case SPECIAL -> "special";
		});
	}

	static int width(BionicTrait trait) {
		return (trait.scopes().size() + 1) * CELL;
	}

	@Override
	public ScreenPosition getPosition() {
		return position;
	}

	@Override
	public void drawWidget(GuiGraphics graphics, double mouseX, double mouseY) {
		for (int index = 0; index < scopes.size(); index++)
			SurgicalJointIconModel.of(scopes.get(index)).element(ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT)
				.renderInGuiSlot(graphics, transformStack, index * CELL + ITEM_INSET, ITEM_INSET);
		graphics.blitSprite(ruleIcon, scopes.size() * CELL, 0, CELL, CELL);
	}

	@Override
	public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
		if (mouseX < 0 || mouseY < 0 || mouseY >= CELL)
			return;
		int index = (int) mouseX / CELL;
		if (index < scopes.size()) {
			double cellX = mouseX - index * CELL;
			if (cellX < ITEM_INSET || cellX >= ITEM_INSET + ICON_SIZE
				|| mouseY < ITEM_INSET || mouseY >= ITEM_INSET + ICON_SIZE)
				return;
			tooltip.add(BionicTraitJeiText.scopeTooltip(scopes.get(index)));
		} else if (index == scopes.size())
			tooltip.addAll(BionicTraitJeiText.ruleTooltip(trait));
	}
}
