package com.nobodiiiii.createbiotech.foundation.item;

import java.util.function.Consumer;

import com.nobodiiiii.createbiotech.foundation.render.BlockCenteredRenderedLivingEntityItemRenderer;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

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

	@Override
	@OnlyIn(Dist.CLIENT)
	public void initializeClient(Consumer<IClientItemExtensions> consumer) {
		consumer.accept(BlockCenteredRenderedLivingEntityItemRenderer.create(this));
	}
}
