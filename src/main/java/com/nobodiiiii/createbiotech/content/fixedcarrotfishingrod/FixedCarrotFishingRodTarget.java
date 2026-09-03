package com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * Transient AI target produced from the same item predicate used by vanilla temptation AI.
 */
public record FixedCarrotFishingRodTarget(BlockPos rodPos, @Nullable UUID subLevelId,
	Predicate<ItemStack> temptations) {

	public FixedCarrotFishingRodTarget {
		rodPos = rodPos.immutable();
		Objects.requireNonNull(temptations, "temptations");
	}
}
