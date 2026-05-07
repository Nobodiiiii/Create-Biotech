package com.nobodiiiii.createbiotech.content.processing.basin;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraftforge.registries.ForgeRegistries;

public class BasinEntityIngredient {
	public static final int ANY_SLIME_SIZE = -1;

	@Nullable
	private final EntityType<?> entityType;
	private final int slimeSize;
	@Nullable
	private final CompoundTag nbt;

	private BasinEntityIngredient(@Nullable EntityType<?> entityType, int slimeSize, @Nullable CompoundTag nbt) {
		this.entityType = entityType;
		this.slimeSize = slimeSize;
		this.nbt = nbt;
	}

	public boolean test(Entity entity) {
		if (entityType != null && entity.getType() != entityType)
			return false;
		if (slimeSize != ANY_SLIME_SIZE && (!(entity instanceof Slime slime) || slime.getSize() != slimeSize))
			return false;
		if (nbt == null)
			return true;
		return NbtUtils.compareNbt(nbt, entity.saveWithoutId(new CompoundTag()), true);
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeBoolean(entityType != null);
		if (entityType != null)
			buffer.writeResourceLocation(ForgeRegistries.ENTITY_TYPES.getKey(entityType));
		buffer.writeVarInt(slimeSize);
		buffer.writeNbt(nbt);
	}

	public static BasinEntityIngredient fromJson(JsonObject json) {
		EntityType<?> entityType = null;
		if (GsonHelper.isValidNode(json, "type")) {
			ResourceLocation id = new ResourceLocation(GsonHelper.getAsString(json, "type"));
			entityType = ForgeRegistries.ENTITY_TYPES.getValue(id);
			if (entityType == null)
				throw new JsonSyntaxException("Unknown entity type: " + id);
		}

		int slimeSize = GsonHelper.getAsInt(json, "slime_size", ANY_SLIME_SIZE);
		CompoundTag nbt = null;
		if (GsonHelper.isValidNode(json, "nbt"))
			nbt = parseNbt(json.get("nbt"));

		return new BasinEntityIngredient(entityType, slimeSize, nbt);
	}

	public static BasinEntityIngredient fromNetwork(FriendlyByteBuf buffer) {
		EntityType<?> entityType = null;
		if (buffer.readBoolean()) {
			ResourceLocation id = buffer.readResourceLocation();
			entityType = ForgeRegistries.ENTITY_TYPES.getValue(id);
			if (entityType == null)
				throw new JsonSyntaxException("Unknown entity type: " + id);
		}

		return new BasinEntityIngredient(entityType, buffer.readVarInt(), buffer.readNbt());
	}

	private static CompoundTag parseNbt(JsonElement element) {
		String serialized = element.isJsonObject() ? element.toString() : GsonHelper.convertToString(element, "nbt");
		try {
			return TagParser.parseTag(serialized);
		} catch (CommandSyntaxException exception) {
			throw new JsonSyntaxException("Invalid entity NBT: " + serialized, exception);
		}
	}
}
