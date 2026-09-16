package com.nobodiiiii.createbiotech.foundation.item;

import java.util.function.Consumer;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * A spawnable entity item whose rendered geometry is centered in a unit block before
 * the normal block-item display transforms are applied.
 */
public class BlockCenteredSpawnableRenderedLivingEntityItem<T extends Mob>
	extends SpawnableRenderedLivingEntityItem<T> {

	public BlockCenteredSpawnableRenderedLivingEntityItem(Properties properties, EntityType<T> entityType) {
		super(properties, entityType);
	}

	public BlockCenteredSpawnableRenderedLivingEntityItem(Properties properties, EntityType<T> entityType,
		float scaleMultiplier) {
		super(properties, entityType, entity -> {
		}, scaleMultiplier);
	}

	public BlockCenteredSpawnableRenderedLivingEntityItem(Properties properties, EntityType<T> entityType,
		Consumer<T> entityConfigurer) {
		super(properties, entityType, entityConfigurer);
	}

	public BlockCenteredSpawnableRenderedLivingEntityItem(Properties properties, EntityType<T> entityType,
		Consumer<T> entityConfigurer, float scaleMultiplier) {
		super(properties, entityType, entityConfigurer, scaleMultiplier);
	}

}
