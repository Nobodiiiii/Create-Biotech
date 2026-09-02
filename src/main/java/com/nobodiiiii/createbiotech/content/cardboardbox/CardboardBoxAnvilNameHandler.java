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
 * renaming the <em>box itself</em> — independent of the beast's name.
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
		if (event.getPlayer().level().isClientSide)
			return;

		ItemStack left = event.getLeft();
		ItemStack right = event.getRight();
		if (!CapturedEntityBoxHelper.hasCapturedEntity(left))
			return;
		if (!right.is(Items.NAME_TAG))
			return;

		// The creature's name comes only from the name tag's own name.
		String beastName = right.has(DataComponents.CUSTOM_NAME)
			? right.getHoverName().getString().strip() : "";
		if (beastName.isEmpty())
			return;

		ItemStack out = left.copy();
		if (!CapturedEntityBoxHelper.applyNameToCapturedEntity(out, event.getPlayer().level().registryAccess(), beastName))
			return;

		// The anvil text field renames the box (item), not the beast. null means the
		// field was never edited (keep the box's existing name); empty clears it.
		String field = event.getName();
		if (field != null) {
			if (field.isBlank())
				out.remove(DataComponents.CUSTOM_NAME);
			else
				out.set(DataComponents.CUSTOM_NAME, Component.literal(field));
		}

		event.setOutput(out);
		event.setCost(1);
		event.setMaterialCost(1);
	}
}
