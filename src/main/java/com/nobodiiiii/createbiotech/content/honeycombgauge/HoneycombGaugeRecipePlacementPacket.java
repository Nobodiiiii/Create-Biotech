package com.nobodiiiii.createbiotech.content.honeycombgauge;

import java.util.ArrayList;
import java.util.List;

import com.nobodiiiii.createbiotech.content.honeycombgauge.HoneycombGaugeRecipePlacement.Ingredient;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** The server scans and replans the face; the client never selects world positions. */
public record HoneycombGaugeRecipePlacementPacket(BlockPos origin, List<Ingredient> inputs,
	List<Ingredient> outputs) {
	public HoneycombGaugeRecipePlacementPacket(RegistryFriendlyByteBuf buffer) {
		this(buffer.readBlockPos(), readIngredients(buffer), readIngredients(buffer));
	}

	private static List<Ingredient> readIngredients(RegistryFriendlyByteBuf buffer) {
		int size = buffer.readVarInt();
		if (size < 0 || size > HoneycombGaugeRecipePlacement.MAX_RECIPE_SLOTS)
			throw new IllegalArgumentException("Invalid gauge recipe size");
		List<Ingredient> result = new ArrayList<>(size);
		for (int i = 0; i < size; i++)
			result.add(new Ingredient(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), buffer.readVarInt()));
		return List.copyOf(result);
	}

	public void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeBlockPos(origin);
		writeIngredients(buffer, inputs);
		writeIngredients(buffer, outputs);
	}

	private static void writeIngredients(RegistryFriendlyByteBuf buffer, List<Ingredient> ingredients) {
		buffer.writeVarInt(ingredients.size());
		for (Ingredient ingredient : ingredients) {
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, ingredient.stack());
			buffer.writeVarInt(ingredient.amount());
		}
	}

	public void handle(ServerPlayer player) {
		if (!(player.containerMenu instanceof HoneycombGaugeClusterMenu menu)
			|| !menu.origin().equals(origin) || !menu.stillValid(player)
			|| !player.mayBuild() || !player.level().isLoaded(origin)
			|| inputs.size() + outputs.size() > HoneycombGaugeRecipePlacement.MAX_RECIPE_SLOTS)
			return;
		for (Ingredient ingredient : inputs)
			if (ingredient.stack().isEmpty() || ingredient.amount() < 1 || ingredient.amount() > 4096)
				return;
		for (Ingredient ingredient : outputs)
			if (ingredient.stack().isEmpty() || ingredient.amount() < 1 || ingredient.amount() > 4096)
				return;
		var plan = HoneycombGaugeRecipePlacement.plan(player, origin, menu.facing(),
			menu.workspaceUp(), inputs, outputs);
		if (!plan.ready()) {
			player.displayClientMessage(HoneycombGaugeRecipePlacement.message(plan.error()), true);
			return;
		}
		if (HoneycombGaugeRecipePlacement.place(player, plan))
			player.displayClientMessage(HoneycombGaugeRecipePlacement.message("placed"), true);
		else
			player.displayClientMessage(HoneycombGaugeRecipePlacement.message("space"), true);
	}
}
