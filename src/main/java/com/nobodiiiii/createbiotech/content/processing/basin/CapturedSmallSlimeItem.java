package com.nobodiiiii.createbiotech.content.processing.basin;

import com.nobodiiiii.createbiotech.foundation.item.RenderedLivingEntityItem;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandlerModifiable;

public class CapturedSmallSlimeItem extends RenderedLivingEntityItem<Slime> {
	public CapturedSmallSlimeItem(Properties properties) {
		super(properties, EntityType.SLIME, CapturedSmallSlimeItem::configureSlime);
	}

	public static boolean materializeInBasin(BasinBlockEntity basin) {
		Level level = basin.getLevel();
		if (level == null || level.isClientSide)
			return false;

		boolean changed = materializeInInventory(basin, basin.getInputInventory());
		changed |= materializeInInventory(basin, basin.getOutputInventory());
		if (changed) {
			basin.notifyChangeOfContents();
			basin.notifyUpdate();
		}
		return changed;
	}

	private static void configureSlime(Slime slime) {
		slime.setSize(1, false);
	}

	private static boolean materializeInInventory(BasinBlockEntity basin, IItemHandlerModifiable inventory) {
		boolean changed = false;
		for (int slot = 0; slot < inventory.getSlots(); slot++) {
			ItemStack stack = inventory.getStackInSlot(slot);
			if (!(stack.getItem() instanceof CapturedSmallSlimeItem))
				continue;

			int materialized = 0;
			while (materialized < stack.getCount() && spawnCapturedSlimeInBasin(basin))
				materialized++;
			if (materialized == 0)
				continue;

			stack.shrink(materialized);
			inventory.setStackInSlot(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
			changed = true;
		}
		return changed;
	}

	private static boolean spawnCapturedSlimeInBasin(BasinBlockEntity basin) {
		Level level = basin.getLevel();
		if (level == null || level.isClientSide)
			return false;
		if (BasinEntityProcessing.getCapturedSmallSlimeCount(basin) >= BasinEntityProcessing.getMaxCapturedSmallSlimes())
			return false;

		Slime slime = EntityType.SLIME.create(level);
		if (slime == null)
			return false;

		configureSlime(slime);
		var basinPos = basin.getBlockPos();
		slime.moveTo(basinPos.getX() + .5d, basinPos.getY() + .125d, basinPos.getZ() + .5d,
			level.random.nextFloat() * 360, 0);
		if (!level.addFreshEntity(slime))
			return false;

		if (BasinEntityProcessing.captureSmallSlimeInBasin(basin, slime))
			return true;

		slime.discard();
		return false;
	}
}
