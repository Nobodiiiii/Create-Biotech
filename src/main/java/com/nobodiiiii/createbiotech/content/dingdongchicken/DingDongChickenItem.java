package com.nobodiiiii.createbiotech.content.dingdongchicken;

import com.nobodiiiii.createbiotech.foundation.item.BlockCenteredSpawnableRenderedLivingEntityItem;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class DingDongChickenItem extends BlockCenteredSpawnableRenderedLivingEntityItem<DingDongChickenEntity> {
	private static final float ITEM_RENDER_SCALE = 1.5f;

	public DingDongChickenItem(Properties properties) {
		super(properties, CBEntityTypes.DING_DONG_CHICKEN.get(), ITEM_RENDER_SCALE);
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
		DingDongChickenEntity firstChicken = createChicken(level, position.add(getDropSpread(0, count)), motion);
		if (firstChicken == null)
			return null;

		if (!level.isClientSide) {
			for (int i = 1; i < count; i++) {
				DingDongChickenEntity chicken = createChicken(level, position.add(getDropSpread(i, count)), motion);
				if (chicken != null)
					level.addFreshEntity(chicken);
			}
		}

		return firstChicken;
	}

	private static DingDongChickenEntity createChicken(Level level, Vec3 position, Vec3 motion) {
		DingDongChickenEntity chicken = CBEntityTypes.DING_DONG_CHICKEN.get().create(level);
		if (chicken == null)
			return null;

		chicken.setPersistenceRequired();
		chicken.moveTo(position.x, position.y, position.z, level.random.nextFloat() * 360, 0);
		chicken.setDeltaMovement(motion);
		chicken.fallDistance = 0;
		return chicken;
	}

	private static Vec3 getDropSpread(int index, int count) {
		if (count <= 1)
			return Vec3.ZERO;

		double angle = Math.PI * 2 * index / count;
		double radius = 0.15d + 0.03d * Math.min(count, 8);
		return new Vec3(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
	}
}
