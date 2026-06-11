package com.nobodiiiii.createbiotech.content.experience;

import com.nobodiiiii.createbiotech.content.squidprinter.EnchantmentBookCopyItem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

public final class ExperienceHelper {

	private ExperienceHelper() {
	}

	public static int nuggetsToXp(int nuggets) {
		return Math.max(0, nuggets) * ExperienceConstants.xpPerNugget();
	}

	public static int xpToNuggets(int xp) {
		return Math.max(0, xp) / ExperienceConstants.xpPerNugget();
	}

	public static int sumStoredEnchantmentLevels(ItemStack copyStack) {
		int total = 0;
		ListTag enchantments = EnchantmentBookCopyItem.getStoredEnchantments(copyStack);
		for (int i = 0; i < enchantments.size(); i++) {
			CompoundTag entry = enchantments.getCompound(i);
			ResourceLocation id = ResourceLocation.tryParse(entry.getString("id"));
			if (id == null)
				continue;
			Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(id);
			if (enchantment == null)
				continue;
			total += Math.max(1, entry.getShort("lvl"));
		}
		return total;
	}

	public static int drainPlayerExperience(Player player, int maxAmount) {
		if (maxAmount <= 0 || player.isCreative() || player.isSpectator())
			return 0;
		int drained = Math.min(maxAmount, Math.max(0, player.totalExperience));
		if (drained <= 0)
			return 0;
		player.giveExperiencePoints(-drained);
		return drained;
	}

	public static void spawnExperience(Level level, Vec3 pos, int amount) {
		if (amount <= 0 || level.isClientSide || !(level instanceof ServerLevel serverLevel))
			return;
		ExperienceOrb.award(serverLevel, pos, amount);
	}
}
