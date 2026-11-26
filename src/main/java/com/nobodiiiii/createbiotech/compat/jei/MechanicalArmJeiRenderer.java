package com.nobodiiiii.createbiotech.compat.jei;

import java.lang.ref.WeakReference;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import com.simibubi.create.content.contraptions.wrench.NonVisualizationLevel;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

public final class MechanicalArmJeiRenderer {
	private static WeakReference<ArmBlockEntity> preview = new WeakReference<>(null);

	private MechanicalArmJeiRenderer() {}

	public static boolean render(GuiGraphics graphics, IRecipeSlotsView recipeSlotsView) {
		ItemStack input = recipeSlotsView.getSlotViews().getFirst().getDisplayedIngredient(VanillaTypes.ITEM_STACK)
			.orElse(ItemStack.EMPTY);
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || !AllBlocks.MECHANICAL_ARM.isIn(input))
			return false;

		ArmBlockEntity arm = getPreview(level);
		AllGuiTextures.JEI_SHADOW.render(graphics, 62, 47);
		AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 74, 2);
		AnimatedKineticsWithEntities.renderScene(graphics, 74, 51, () -> GuiGameElement.of(arm)
			.lighting(AnimatedKinetics.DEFAULT_LIGHTING)
			.rotateBlock(0, 90, 0)
			.scale(20)
			.render(graphics));
		return true;
	}

	private static ArmBlockEntity getPreview(ClientLevel level) {
		ArmBlockEntity arm = preview.get();
		if (arm != null && arm.getLevel() instanceof NonVisualizationLevel view && view.getLevel() == level)
			return arm;

		arm = new ArmBlockEntity(AllBlockEntityTypes.MECHANICAL_ARM.get(), BlockPos.ZERO,
			AllBlocks.MECHANICAL_ARM.getDefaultState());
		// A preview has no Flywheel visual, so let Create's block entity renderer draw the whole arm.
		arm.setLevel(new NonVisualizationLevel(level));
		preview = new WeakReference<>(arm);
		return arm;
	}
}
