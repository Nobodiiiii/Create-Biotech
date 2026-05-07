package com.nobodiiiii.createbiotech.content.processing.basin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

public class BasinEntityProcessing {
	private static final double BASIN_INNER_MIN = 2 / 16d;
	private static final double BASIN_INNER_MAX = 14 / 16d;
	private static final double ENTITY_SCAN_HEIGHT = 1.25d;
	private static final String DATA_ROOT = CreateBiotech.MOD_ID;
	private static final String CAPTURED_TAG = "BasinEntityProcessingCaptured";
	private static final String BASIN_POS_TAG = "BasinEntityProcessingBasinPos";
	private static final String PREVIOUS_NO_AI_TAG = "BasinEntityProcessingPreviousNoAi";
	private static final String PREVIOUS_NO_GRAVITY_TAG = "BasinEntityProcessingPreviousNoGravity";

	private BasinEntityProcessing() {}

	public static boolean apply(BasinBlockEntity basin, BasinEntityProcessingRecipe recipe, boolean test) {
		Level level = basin.getLevel();
		if (level == null)
			return false;

		IItemHandler availableItems = basin.getCapability(ForgeCapabilities.ITEM_HANDLER)
			.orElse(null);
		if (availableItems == null)
			return false;

		Entity entity = findMatchingEntity(basin, recipe);
		if (entity == null)
			return false;

		if (!extractIngredients(availableItems, recipe.getIngredients(), true))
			return false;
		if (!basin.acceptOutputs(copyResults(recipe), Collections.emptyList(), true))
			return false;
		if (test)
			return true;

		if (!extractIngredients(availableItems, recipe.getIngredients(), false))
			return false;
		if (!basin.acceptOutputs(copyResults(recipe), Collections.emptyList(), false))
			return false;

		entity.discard();
		return true;
	}

	public static Entity findMatchingEntity(BasinBlockEntity basin, BasinEntityProcessingRecipe recipe) {
		Level level = basin.getLevel();
		if (level == null)
			return null;

		BlockPos pos = basin.getBlockPos();
		AABB bounds = getEntityProcessingBounds(pos);
		List<Entity> entities = level.getEntities((Entity) null, bounds, entity -> canProcessEntity(entity, pos, recipe));
		return entities.isEmpty() ? null : entities.get(0);
	}

	public static AABB getEntityProcessingBounds(BlockPos basinPos) {
		return new AABB(
			basinPos.getX() + BASIN_INNER_MIN,
			basinPos.getY(),
			basinPos.getZ() + BASIN_INNER_MIN,
			basinPos.getX() + BASIN_INNER_MAX,
			basinPos.getY() + ENTITY_SCAN_HEIGHT,
			basinPos.getZ() + BASIN_INNER_MAX);
	}

	public static boolean handleSmallSlimeInBasin(Slime slime) {
		Level level = slime.level();
		if (level.isClientSide)
			return false;
		if (slime.getSize() != 1) {
			releaseCapturedSlime(slime);
			return false;
		}

		BlockPos basinPos = findContainingBasin(level, slime);
		if (basinPos == null) {
			releaseCapturedSlime(slime);
			return false;
		}

		CompoundTag data = getCreateBiotechData(slime);
		boolean wasCaptured = data.getBoolean(CAPTURED_TAG);
		boolean changedBasin = !wasCaptured || data.getLong(BASIN_POS_TAG) != basinPos.asLong();
		if (!wasCaptured) {
			data.putBoolean(PREVIOUS_NO_AI_TAG, slime.isNoAi());
			data.putBoolean(PREVIOUS_NO_GRAVITY_TAG, slime.isNoGravity());
		}

		data.putBoolean(CAPTURED_TAG, true);
		data.putLong(BASIN_POS_TAG, basinPos.asLong());
		disableAiForSmallSlimeInBasin(level, basinPos, slime);

		if (changedBasin && level.getBlockEntity(basinPos) instanceof BasinBlockEntity basin)
			basin.notifyChangeOfContents();

		return true;
	}

	public static boolean disableAiForSmallSlimeInBasin(Level level, BlockPos basinPos, Entity entity) {
		if (level.isClientSide || !(entity instanceof Slime slime) || slime.getSize() != 1)
			return false;
		if (!isInBasinProcessingArea(entity, basinPos))
			return false;

		slime.setNoAi(true);
		slime.setNoGravity(false);
		slime.setJumping(false);
		Vec3 motion = slime.getDeltaMovement();
		slime.setDeltaMovement(0, motion.y, 0);
		return true;
	}

	private static BlockPos findContainingBasin(Level level, Slime slime) {
		BlockPos entityPos = slime.blockPosition();
		for (int yOffset = 0; yOffset >= -2; yOffset--) {
			BlockPos basinPos = entityPos.offset(0, yOffset, 0);
			BlockEntity blockEntity = level.getBlockEntity(basinPos);
			if (blockEntity instanceof BasinBlockEntity && isInBasinProcessingArea(slime, basinPos))
				return basinPos;
		}
		return null;
	}

	private static void releaseCapturedSlime(Slime slime) {
		CompoundTag data = getCreateBiotechData(slime);
		if (!data.getBoolean(CAPTURED_TAG))
			return;

		slime.setNoAi(data.getBoolean(PREVIOUS_NO_AI_TAG));
		slime.setNoGravity(data.getBoolean(PREVIOUS_NO_GRAVITY_TAG));
		data.remove(CAPTURED_TAG);
		data.remove(BASIN_POS_TAG);
		data.remove(PREVIOUS_NO_AI_TAG);
		data.remove(PREVIOUS_NO_GRAVITY_TAG);
	}

	private static boolean canProcessEntity(Entity entity, BlockPos basinPos, BasinEntityProcessingRecipe recipe) {
		if (!entity.isAlive() || entity instanceof Player)
			return false;
		if (!recipe.getEntityIngredient()
			.test(entity))
			return false;

		return isInBasinProcessingArea(entity, basinPos);
	}

	private static boolean isInBasinProcessingArea(Entity entity, BlockPos basinPos) {
		Vec3 center = entity.getBoundingBox()
			.getCenter();
		return center.x >= basinPos.getX() + BASIN_INNER_MIN
			&& center.x <= basinPos.getX() + BASIN_INNER_MAX
			&& center.z >= basinPos.getZ() + BASIN_INNER_MIN
			&& center.z <= basinPos.getZ() + BASIN_INNER_MAX
			&& center.y >= basinPos.getY()
			&& center.y <= basinPos.getY() + ENTITY_SCAN_HEIGHT;
	}

	private static boolean extractIngredients(IItemHandler availableItems, List<Ingredient> ingredients,
		boolean simulate) {
		int[] extractedItemsFromSlot = new int[availableItems.getSlots()];

		Ingredients:
		for (Ingredient ingredient : ingredients) {
			for (int slot = 0; slot < availableItems.getSlots(); slot++) {
				if (simulate && availableItems.getStackInSlot(slot)
					.getCount() <= extractedItemsFromSlot[slot])
					continue;

				ItemStack extracted = availableItems.extractItem(slot, 1, true);
				if (!ingredient.test(extracted))
					continue;

				if (!simulate)
					availableItems.extractItem(slot, 1, false);
				extractedItemsFromSlot[slot]++;
				continue Ingredients;
			}

			return false;
		}

		return true;
	}

	private static List<ItemStack> copyResults(BasinEntityProcessingRecipe recipe) {
		List<ItemStack> results = new ArrayList<>();
		for (ItemStack result : recipe.getRollableResults())
			if (!result.isEmpty())
				results.add(result.copy());
		return results;
	}

	private static CompoundTag getCreateBiotechData(Entity entity) {
		CompoundTag persistentData = entity.getPersistentData();
		if (!persistentData.contains(DATA_ROOT))
			persistentData.put(DATA_ROOT, new CompoundTag());
		return persistentData.getCompound(DATA_ROOT);
	}
}
