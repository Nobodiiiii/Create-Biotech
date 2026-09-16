package com.nobodiiiii.createbiotech.foundation.item;

import java.util.function.Consumer;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * An entity-rendered item whose geometry is centered in a unit block before
 * normal block-item display transforms are applied.
 */
public class BlockCenteredRenderedLivingEntityItem<T extends LivingEntity> extends RenderedLivingEntityItem<T> {

	public BlockCenteredRenderedLivingEntityItem(Properties properties, EntityType<T> entityType) {
		super(properties, entityType);
	}

	public BlockCenteredRenderedLivingEntityItem(Properties properties, EntityType<T> entityType,
		float scaleMultiplier) {
		super(properties, entityType, entity -> {
		}, scaleMultiplier);
	}

	public BlockCenteredRenderedLivingEntityItem(Properties properties, EntityType<T> entityType,
		Consumer<T> entityConfigurer) {
		super(properties, entityType, entityConfigurer);
	}

	public BlockCenteredRenderedLivingEntityItem(Properties properties, EntityType<T> entityType,
		Consumer<T> entityConfigurer, float scaleMultiplier) {
		super(properties, entityType, entityConfigurer, scaleMultiplier);
	}

}
