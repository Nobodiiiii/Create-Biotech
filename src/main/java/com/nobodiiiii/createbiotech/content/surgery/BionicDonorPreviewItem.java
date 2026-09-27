package com.nobodiiiii.createbiotech.content.surgery;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.foundation.item.CBItemData;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Hidden display carrier for donor creatures; it has no capture or spawning behavior. */
public final class BionicDonorPreviewItem extends Item {

	private static final String ENTITY_TYPE = "EntityType";

	public BionicDonorPreviewItem(Properties properties) {
		super(properties);
	}

	public ItemStack createStack(EntityType<?> type) {
		ItemStack stack = new ItemStack(this);
		CompoundTag data = new CompoundTag();
		data.putString(ENTITY_TYPE, BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
		CBItemData.set(stack, data);
		return stack;
	}

	@Nullable
	public static ResourceLocation entityId(ItemStack stack) {
		if (!(stack.getItem() instanceof BionicDonorPreviewItem))
			return null;
		CompoundTag data = CBItemData.getReadOnly(stack);
		return data == null ? null : ResourceLocation.tryParse(data.getString(ENTITY_TYPE));
	}

	@Nullable
	public static EntityType<?> entityType(ItemStack stack) {
		ResourceLocation id = entityId(stack);
		return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
	}

	@Override
	public Component getName(ItemStack stack) {
		EntityType<?> type = entityType(stack);
		return type == null ? super.getName(stack) : type.getDescription();
	}
}
