package com.nobodiiiii.createbiotech.content.cardboardbox;

import com.nobodiiiii.createbiotech.CreateBiotech;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * Lets a player name the creature held by a captured-entity box in an anvil: the box
 * goes in the left slot, a {@link Items#NAME_TAG name tag} whose own name is the
 * creature's new name goes in the middle. The anvil text field keeps its usual role —
 * renaming the <em>box itself</em> — independent of the creature's name.
 *
 * <p>Consumes one name tag per use. The anvil requires a positive level cost to allow
 * the result to be picked up (see {@code AnvilMenu#mayPickup}), so this charges exactly
 * one level.</p>
 */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public class CardboardBoxAnvilNameHandler {
	private CardboardBoxAnvilNameHandler() {
	}

	@SubscribeEvent
	public static void onAnvilUpdate(AnvilUpdateEvent event) {
		ItemStack left = event.getLeft();
		ItemStack right = event.getRight();
		if (!CapturedEntityBoxHelper.hasCapturedEntity(left))
			return;
		if (!right.is(Items.NAME_TAG))
			return;

		// The creature's name comes only from the name tag's own name.
		Component beastName = right.get(DataComponents.CUSTOM_NAME);
		if (beastName == null || beastName.getString().isBlank())
			return;

		ItemStack out = left.copy();
		if (!CapturedEntityBoxHelper.applyNameToCapturedEntity(out,
			event.getPlayer().level().registryAccess(), beastName))
			return;

		// The text field controls only the box. null means it was never edited
		// (preserve the current box name); empty explicitly clears that name.
		String boxName = event.getName();
		if (boxName != null) {
			if (boxName.isBlank())
				out.remove(DataComponents.CUSTOM_NAME);
			else if (!boxName.equals(left.getHoverName().getString()))
				out.set(DataComponents.CUSTOM_NAME, Component.literal(boxName));
		}

		event.setOutput(out);
		event.setCost(1);
		event.setMaterialCost(1);
	}
}
