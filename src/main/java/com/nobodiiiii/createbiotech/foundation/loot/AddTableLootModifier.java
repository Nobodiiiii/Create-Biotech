package com.nobodiiiii.createbiotech.foundation.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.LootModifier;

/**
 * Rolls an extra loot table on top of the original drops.
 *
 * <p>NeoForge ships this as {@code neoforge:add_table}; Forge 1.20.1 has no equivalent built-in
 * modifier, so the upstream data file's behaviour is reproduced here.
 */
public class AddTableLootModifier extends LootModifier {

	public static final Codec<AddTableLootModifier> CODEC = RecordCodecBuilder.create(instance ->
		codecStart(instance)
			.and(ResourceLocation.CODEC.fieldOf("table").forGetter(modifier -> modifier.table))
			.apply(instance, AddTableLootModifier::new));

	private final ResourceLocation table;

	public AddTableLootModifier(LootItemCondition[] conditions, ResourceLocation table) {
		super(conditions);
		this.table = table;
	}

	@Override
	protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
		LootTable added = context.getResolver().getLootTable(table);
		if (added == LootTable.EMPTY)
			return generatedLoot;

		added.getRandomItemsRaw(context,
			LootTable.createStackSplitter(context.getLevel(), generatedLoot::add));
		return generatedLoot;
	}

	@Override
	public Codec<? extends net.minecraftforge.common.loot.IGlobalLootModifier> codec() {
		return CODEC;
	}
}
