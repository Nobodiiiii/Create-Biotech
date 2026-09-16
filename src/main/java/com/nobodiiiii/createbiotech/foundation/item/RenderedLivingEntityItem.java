package com.nobodiiiii.createbiotech.foundation.item;

import java.util.function.Consumer;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class RenderedLivingEntityItem<T extends LivingEntity> extends Item {
	private final EntityType<T> entityType;
	private final Consumer<T> entityConfigurer;
	private final float scaleMultiplier;

	public RenderedLivingEntityItem(Properties properties, EntityType<T> entityType) {
		this(properties, entityType, entity -> {
		});
	}

	public RenderedLivingEntityItem(Properties properties, EntityType<T> entityType, Consumer<T> entityConfigurer) {
		this(properties, entityType, entityConfigurer, 1.0f);
	}

	public RenderedLivingEntityItem(Properties properties, EntityType<T> entityType, Consumer<T> entityConfigurer,
		float scaleMultiplier) {
		super(properties);
		this.entityType = entityType;
		this.entityConfigurer = entityConfigurer;
		this.scaleMultiplier = scaleMultiplier;
	}

	public EntityType<T> getRenderedEntityType() {
		return entityType;
	}

	public float getRenderedEntityScaleMultiplier() {
		return scaleMultiplier;
	}

	public void configureRenderedEntity(T entity) {
		entityConfigurer.accept(entity);
	}

	public void configureRenderedEntity(T entity, ItemStack stack, ItemDisplayContext displayContext) {
		configureRenderedEntity(entity);
	}

	public void configureRenderedEntityForGeometryMeasurement(T entity, ItemStack stack,
		ItemDisplayContext displayContext) {
		configureRenderedEntity(entity, stack, displayContext);
	}

	public float getRenderedEntityYRotation(ItemStack stack, ItemDisplayContext displayContext) {
		return 0.0f;
	}
}
