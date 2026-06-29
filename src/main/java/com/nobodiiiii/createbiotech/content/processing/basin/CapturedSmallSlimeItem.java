package com.nobodiiiii.createbiotech.content.processing.basin;

import com.nobodiiiii.createbiotech.foundation.item.RenderedLivingEntityItem;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class CapturedSmallSlimeItem extends RenderedLivingEntityItem<Slime> {
	public CapturedSmallSlimeItem(Properties properties) {
		super(properties, EntityType.SLIME, CapturedSmallSlimeItem::configureSlime);
	}

	@Override
	public boolean hasCustomEntity(ItemStack stack) {
		return !stack.isEmpty();
	}

	@Override
	public Entity createEntity(Level level, Entity location, ItemStack stack) {
		if (stack.isEmpty())
			return null;

		Vec3 position = location.position();
		Vec3 motion = location.getDeltaMovement();
		int count = stack.getCount();
		Slime firstSlime = BasinEntityProcessing.createSmallSlime(level, position.add(getDropSpread(0, count)), motion);
		if (firstSlime == null)
			return null;

		if (!level.isClientSide) {
			for (int i = 1; i < count; i++) {
				Slime slime = BasinEntityProcessing.createSmallSlime(level, position.add(getDropSpread(i, count)), motion);
				if (slime != null)
					level.addFreshEntity(slime);
			}
		}

		return firstSlime;
	}

	public static boolean syncInBasin(BasinBlockEntity basin) {
		return BasinEntityProcessing.syncCapturedSmallSlimeItems(basin);
	}

	private static void configureSlime(Slime slime) {
		slime.setSize(1, false);
	}

	private static Vec3 getDropSpread(int index, int count) {
		if (count <= 1)
			return Vec3.ZERO;

		double angle = Math.PI * 2 * index / count;
		double radius = 0.15d + 0.03d * Math.min(count, 8);
		return new Vec3(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
	}
}
