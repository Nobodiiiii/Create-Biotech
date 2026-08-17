package com.nobodiiiii.createbiotech.content.sonicdogcannon;

import com.nobodiiiii.createbiotech.registry.CBItems;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Boosts the Shriek Sonic Boom upgrade's damage.
 *
 * <p>1.20.1 has no data-driven enchantments, so the values of the upstream
 * {@code enchantment/sonic_boom.json} are expressed in code: weight 1 maps to
 * {@link Rarity#VERY_RARE} (which also yields the anvil cost of 8), and the item tag becomes an
 * explicit {@link #canEnchant} check. Treasure-only and undiscoverable mirror upstream's presence
 * in {@code minecraft:enchantment/treasure} and absence from every other enchantment tag.
 */
public class SonicBoomEnchantment extends Enchantment {

	public SonicBoomEnchantment() {
		super(Rarity.VERY_RARE, EnchantmentCategory.VANISHABLE, EquipmentSlot.values());
	}

	@Override
	public int getMaxLevel() {
		return 5;
	}

	@Override
	public int getMinCost(int level) {
		return 25 + (level - 1) * 25;
	}

	@Override
	public int getMaxCost(int level) {
		return getMinCost(level) + 50;
	}

	@Override
	public boolean isTreasureOnly() {
		return true;
	}

	@Override
	public boolean isDiscoverable() {
		return false;
	}

	@Override
	public boolean isTradeable() {
		return false;
	}

	@Override
	public boolean canEnchant(ItemStack stack) {
		return stack.is(CBItems.SONIC_DOG_CANNON.get());
	}

	@Override
	public boolean canApplyAtEnchantingTable(ItemStack stack) {
		return canEnchant(stack);
	}
}
