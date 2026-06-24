package com.nobodiiiii.createbiotech.content.cardboardbox;

import java.util.List;
import java.util.function.Function;

import com.nobodiiiii.createbiotech.CreateBiotech;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.registries.ForgeRegistries;

public class CapturedEntityBoxHelper {
	private static final String CAPTURED_ENTITY_TAG = "CapturedEntity";
	private static final String CAPTURED_ENTITY_DESC_ID_TAG = "CapturedEntityDescId";
	private static final String CAPTURED_ENTITY_HEALTH_TAG = "CapturedEntityHealth";
	private static final String DATA_ROOT = CreateBiotech.MOD_ID;
	private static final String AI_DISABLED_BY_MOD_TAG = "AiDisabledByMod";
	private static final String NO_AI_TAG = "NoAI";
	private static final String FORGE_DATA_TAG = "ForgeData";

	private CapturedEntityBoxHelper() {}

	public static void markAiDisabledByMod(Entity entity) {
		if (entity == null)
			return;
		CompoundTag persistentData = entity.getPersistentData();
		CompoundTag data = persistentData.contains(DATA_ROOT, Tag.TAG_COMPOUND)
			? persistentData.getCompound(DATA_ROOT) : new CompoundTag();
		data.putBoolean(AI_DISABLED_BY_MOD_TAG, true);
		persistentData.put(DATA_ROOT, data);
	}

	public static void unmarkAiDisabledByMod(Entity entity) {
		if (entity == null)
			return;
		CompoundTag persistentData = entity.getPersistentData();
		if (!persistentData.contains(DATA_ROOT, Tag.TAG_COMPOUND))
			return;
		CompoundTag data = persistentData.getCompound(DATA_ROOT);
		data.remove(AI_DISABLED_BY_MOD_TAG);
		if (data.isEmpty())
			persistentData.remove(DATA_ROOT);
		else
			persistentData.put(DATA_ROOT, data);
	}

	public static boolean isAiDisabledByMod(Entity entity) {
		if (entity == null)
			return false;
		CompoundTag persistentData = entity.getPersistentData();
		if (!persistentData.contains(DATA_ROOT, Tag.TAG_COMPOUND))
			return false;
		return persistentData.getCompound(DATA_ROOT)
			.getBoolean(AI_DISABLED_BY_MOD_TAG);
	}

	public static void appendHoverText(ItemStack stack, List<Component> tooltipComponents, String filledTranslationKey) {
		CompoundTag tag = stack.getTag();
		if (tag == null || !tag.contains(CAPTURED_ENTITY_DESC_ID_TAG))
			return;

		String entityDescId = tag.getString(CAPTURED_ENTITY_DESC_ID_TAG);
		if (entityDescId.isEmpty())
			return;

		tooltipComponents.add(Component.translatable(filledTranslationKey, Component.translatable(entityDescId)));
	}

	public static boolean captureEntity(ItemStack stack, LivingEntity target) {
		if (hasCapturedEntity(stack))
			return false;

		CompoundTag entityData = new CompoundTag();
		target.saveWithoutId(entityData);

		ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
		if (entityId == null)
			return false;

		entityData.putString("id", entityId.toString());
		restoreAiInSavedEntityData(entityData);

		CompoundTag stackTag = stack.getOrCreateTag();
		stackTag.put(CAPTURED_ENTITY_TAG, entityData);
		stackTag.putString(CAPTURED_ENTITY_DESC_ID_TAG, target.getType().getDescriptionId());
		stackTag.putFloat(CAPTURED_ENTITY_HEALTH_TAG, target.getHealth());
		return true;
	}

	public static boolean captureEntityFromPlayerStack(ItemStack stack, Player player, LivingEntity target) {
		if (stack.getCount() <= 1)
			return captureEntity(stack, target);

		ItemStack filledBox = stack.copyWithCount(1);
		if (!captureEntity(filledBox, target))
			return false;

		stack.shrink(1);
		if (!player.getInventory().add(filledBox))
			player.drop(filledBox, false);
		return true;
	}

	private static void restoreAiInSavedEntityData(CompoundTag entityData) {
		if (!entityData.contains(FORGE_DATA_TAG, Tag.TAG_COMPOUND))
			return;
		CompoundTag forgeData = entityData.getCompound(FORGE_DATA_TAG);
		if (!forgeData.contains(DATA_ROOT, Tag.TAG_COMPOUND))
			return;
		CompoundTag modData = forgeData.getCompound(DATA_ROOT);
		if (!modData.getBoolean(AI_DISABLED_BY_MOD_TAG))
			return;

		entityData.remove(NO_AI_TAG);
		modData.remove(AI_DISABLED_BY_MOD_TAG);
		if (modData.isEmpty())
			forgeData.remove(DATA_ROOT);
		else
			forgeData.put(DATA_ROOT, modData);
		if (forgeData.isEmpty())
			entityData.remove(FORGE_DATA_TAG);
		else
			entityData.put(FORGE_DATA_TAG, forgeData);
	}

	public static boolean releaseCapturedEntity(UseOnContext context) {
		ItemStack stack = context.getItemInHand();
		Level level = context.getLevel();
		Entity entity = createCapturedEntity(stack, level);
		if (entity == null)
			return false;

		CompoundTag stackTag = stack.getTag();
		if (stackTag == null)
			return false;

		BlockPos clickedPos = context.getClickedPos();
		Direction face = context.getClickedFace();
		BlockState clickedState = level.getBlockState(clickedPos);
		BlockPos spawnPos = clickedState.getCollisionShape(level, clickedPos).isEmpty()
			? clickedPos : clickedPos.relative(face);

		entity.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
		if (entity instanceof LivingEntity living && stackTag.contains(CAPTURED_ENTITY_HEALTH_TAG, Tag.TAG_ANY_NUMERIC))
			living.setHealth(Math.min(living.getMaxHealth(), stackTag.getFloat(CAPTURED_ENTITY_HEALTH_TAG)));

		if (!level.addFreshEntity(entity))
			return false;

		clearCapturedEntity(stack);
		return true;
	}

	public static boolean containsEntityType(ItemStack stack, EntityType<?> entityType) {
		CompoundTag entityData = getCapturedEntityData(stack);
		if (entityData == null)
			return false;

		ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(entityType);
		return entityId != null && entityId.toString()
			.equals(entityData.getString("id"));
	}

	public static Entity createCapturedEntity(ItemStack stack, Level level) {
		CompoundTag entityData = getCapturedEntityData(stack);
		if (entityData == null)
			return null;

		Entity entity = EntityType.loadEntityRecursive(entityData.copy(), level, Function.identity());
		if (entity == null)
			return null;

		CompoundTag stackTag = stack.getTag();
		if (entity instanceof LivingEntity living && stackTag != null
			&& stackTag.contains(CAPTURED_ENTITY_HEALTH_TAG, Tag.TAG_ANY_NUMERIC))
			living.setHealth(Math.min(living.getMaxHealth(), stackTag.getFloat(CAPTURED_ENTITY_HEALTH_TAG)));

		return entity;
	}

	public static void clearCapturedEntity(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		if (tag == null)
			return;

		tag.remove(CAPTURED_ENTITY_TAG);
		tag.remove(CAPTURED_ENTITY_DESC_ID_TAG);
		tag.remove(CAPTURED_ENTITY_HEALTH_TAG);
		if (tag.isEmpty())
			stack.setTag(null);
	}

	public static boolean hasCapturedEntity(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		return tag != null && tag.contains(CAPTURED_ENTITY_TAG, Tag.TAG_COMPOUND);
	}

	private static CompoundTag getCapturedEntityData(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		if (tag == null || !tag.contains(CAPTURED_ENTITY_TAG, Tag.TAG_COMPOUND))
			return null;
		return tag.getCompound(CAPTURED_ENTITY_TAG);
	}
}
