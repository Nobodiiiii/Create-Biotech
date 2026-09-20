package com.nobodiiiii.createbiotech.content.surgery;

import java.util.List;

import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Creative-only tools for directly changing the mimic state of a living entity. */
public class CreativeSurgicalKitItem extends SurgicalKitItem {
	private static final List<Tool> TOOLS = List.of(Tool.MIMIC_INDUCER, Tool.MIMIC_RESTORATIVE);

	public CreativeSurgicalKitItem(Properties properties) {
		super(properties, TOOLS);
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
		InteractionHand usedHand) {
		Tool tool = selectedTool(stack);
		boolean makeMimic;
		if (tool == Tool.MIMIC_INDUCER)
			makeMimic = true;
		else if (tool == Tool.MIMIC_RESTORATIVE)
			makeMimic = false;
		else
			return InteractionResult.PASS;

		if (SlimeMimicHandler.isSlimeMimic(target) == makeMimic)
			return InteractionResult.PASS;
		if (!player.level().isClientSide) {
			target.getPersistentData().remove(SlimeMimicHandler.HAUNT_PROGRESS_TAG);
			SlimeMimicHandler.forceSetSlimeMimic(target, makeMimic);
		}
		return InteractionResult.sidedSuccess(player.level().isClientSide);
	}
}
