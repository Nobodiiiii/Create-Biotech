package com.nobodiiiii.createbiotech.content.processing.basin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.foundation.item.ItemHelper.ExtractionCountMode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

public class BasinEntityProcessing {
	public static final int MAX_CAPTURED_SMALL_SLIMES = 4;

	private static final double BASIN_INNER_MIN = 2 / 16d;
	private static final double BASIN_INNER_MAX = 14 / 16d;
	private static final double ENTITY_SCAN_HEIGHT = 1.25d;
	private static final double BASIN_SLIME_Y_OFFSET = 0.125d;
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
		notifyBasinContentsChanged(basin);
		return true;
	}

	public static boolean hasCapturedSmallSlimes(BasinBlockEntity basin) {
		return getCapturedSmallSlimeCount(basin) > 0;
	}

	public static int getCapturedSmallSlimeCount(BasinBlockEntity basin) {
		Level level = basin.getLevel();
		if (level == null)
			return 0;
		return getCapturedSmallSlimes(level, basin.getBlockPos())
			.size();
	}

	public static ItemStack getCapturedSmallSlimeItemStack(BasinBlockEntity basin) {
		int count = getCapturedSmallSlimeCount(basin);
		return count == 0 ? ItemStack.EMPTY : new ItemStack(CBItems.CAPTURED_SMALL_SLIME.get(), count);
	}

	public static boolean isCapturedSmallSlimeItem(ItemStack stack) {
		return stack.is(CBItems.CAPTURED_SMALL_SLIME.get());
	}

	public static ItemStack extractCapturedSmallSlimeItems(BasinBlockEntity basin, int amount, boolean simulate) {
		return extractCapturedSmallSlimeItems(basin, ExtractionCountMode.UPTO, amount, stack -> true, simulate);
	}

	public static ItemStack extractCapturedSmallSlimeItems(BasinBlockEntity basin, ExtractionCountMode mode, int amount,
		Predicate<ItemStack> filter, boolean simulate) {
		ItemStack stack = getCapturedSmallSlimeExtractionStack(basin, mode, amount, filter);
		if (stack.isEmpty())
			return ItemStack.EMPTY;
		if (!simulate)
			extractCapturedSmallSlimes(basin, mode, amount, filter, false);
		return stack;
	}

	public static ItemStack getCapturedSmallSlimeExtractionStack(BasinBlockEntity basin, ExtractionCountMode mode,
		int amount, Predicate<ItemStack> filter) {
		if (amount <= 0)
			amount = MAX_CAPTURED_SMALL_SLIMES;

		List<Slime> slimes = getCapturedSmallSlimes(basin.getLevel(), basin.getBlockPos());
		int available = slimes.size();
		int extracted = mode == ExtractionCountMode.EXACTLY ? amount : Math.min(amount, available);
		if (mode == ExtractionCountMode.EXACTLY && available < amount)
			return ItemStack.EMPTY;
		if (extracted == 0)
			return ItemStack.EMPTY;

		ItemStack stack = new ItemStack(CBItems.CAPTURED_SMALL_SLIME.get(), extracted);
		if (!filter.test(stack))
			return ItemStack.EMPTY;

		return stack;
	}

	public static List<Slime> extractCapturedSmallSlimes(BasinBlockEntity basin, ExtractionCountMode mode, int amount,
		Predicate<ItemStack> filter, boolean simulate) {
		ItemStack stack = getCapturedSmallSlimeExtractionStack(basin, mode, amount, filter);
		if (stack.isEmpty())
			return Collections.emptyList();

		List<Slime> slimes = getCapturedSmallSlimes(basin.getLevel(), basin.getBlockPos());
		List<Slime> extracted = new ArrayList<>(slimes.subList(0, Math.min(stack.getCount(), slimes.size())));
		if (!simulate) {
			for (Slime slime : extracted)
				releaseCapturedSlime(slime);
			notifyBasinContentsChanged(basin);
		}

		return extracted;
	}

	public static void setCapturedSmallSlimeItemCount(BasinBlockEntity basin, int count) {
		int targetCount = Math.max(0, Math.min(MAX_CAPTURED_SMALL_SLIMES, count));
		int currentCount = getCapturedSmallSlimeCount(basin);
		if (targetCount >= currentCount)
			return;

		extractCapturedSmallSlimeItems(basin, currentCount - targetCount, false);
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

	public static boolean tryCaptureSmallSlimeFromFunnel(FunnelBlockEntity funnel) {
		Level level = funnel.getLevel();
		if (level == null || level.isClientSide)
			return false;

		BlockState blockState = funnel.getBlockState();
		if (!(blockState.getBlock() instanceof FunnelBlock))
			return false;
		if (blockState.getOptionalValue(AbstractFunnelBlock.POWERED)
			.orElse(false))
			return false;
		if (blockState.getValue(FunnelBlock.EXTRACTING))
			return false;

		Direction facing = AbstractFunnelBlock.getFunnelFacing(blockState);
		if (facing == null || !facing.getAxis()
			.isHorizontal())
			return false;

		BlockPos basinPos = funnel.getBlockPos()
			.relative(facing.getOpposite());
		if (!(level.getBlockEntity(basinPos) instanceof BasinBlockEntity basin))
			return false;

		Slime slime = findSmallSlimeInFunnelCaptureArea(level, funnel.getBlockPos());
		if (slime == null)
			return false;

		return captureSmallSlimeInBasin(basin, slime);
	}

	public static void tickCapturedSmallSlime(Slime slime) {
		Level level = slime.level();
		if (level.isClientSide)
			return;

		CompoundTag data = getExistingCreateBiotechData(slime);
		if (data == null || !data.getBoolean(CAPTURED_TAG))
			return;
		if (slime.getSize() != 1) {
			releaseCapturedSlime(slime);
			return;
		}

		BlockPos basinPos = BlockPos.of(data.getLong(BASIN_POS_TAG));
		if (!(level.getBlockEntity(basinPos) instanceof BasinBlockEntity)
			|| !isInBasinProcessingArea(slime, basinPos)) {
			releaseCapturedSlime(slime);
			return;
		}

		disableAiForSmallSlimeInBasin(level, basinPos, slime);
	}

	private static boolean captureSmallSlimeInBasin(BasinBlockEntity basin, Slime slime) {
		Level level = basin.getLevel();
		if (level == null || level.isClientSide || !slime.isAlive() || slime.getSize() != 1)
			return false;

		BlockPos basinPos = basin.getBlockPos();
		if (!isCapturedInBasin(slime, basinPos)
			&& getCapturedSmallSlimes(level, basinPos).size() >= MAX_CAPTURED_SMALL_SLIMES)
			return false;

		CompoundTag data = getCreateBiotechData(slime);
		boolean wasCaptured = data.getBoolean(CAPTURED_TAG);
		boolean changedBasin = !wasCaptured || data.getLong(BASIN_POS_TAG) != basinPos.asLong();
		if (!wasCaptured) {
			data.putBoolean(PREVIOUS_NO_AI_TAG, slime.isNoAi());
			data.putBoolean(PREVIOUS_NO_GRAVITY_TAG, slime.isNoGravity());
		}

		data.putBoolean(CAPTURED_TAG, true);
		data.putLong(BASIN_POS_TAG, basinPos.asLong());

		Vec3 target = Vec3.atCenterOf(basinPos)
			.add(0, BASIN_SLIME_Y_OFFSET - 0.5d, 0);
		slime.stopRiding();
		slime.moveTo(target.x, target.y, target.z, slime.getYRot(), slime.getXRot());
		slime.fallDistance = 0;
		disableAiForSmallSlimeInBasin(level, basinPos, slime);

		if (changedBasin)
			notifyBasinContentsChanged(basin);

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

	private static void releaseCapturedSlime(Slime slime) {
		CompoundTag data = getExistingCreateBiotechData(slime);
		if (data == null || !data.getBoolean(CAPTURED_TAG))
			return;

		BlockPos basinPos = data.contains(BASIN_POS_TAG) ? BlockPos.of(data.getLong(BASIN_POS_TAG)) : null;
		slime.setNoAi(data.getBoolean(PREVIOUS_NO_AI_TAG));
		slime.setNoGravity(data.getBoolean(PREVIOUS_NO_GRAVITY_TAG));
		data.remove(CAPTURED_TAG);
		data.remove(BASIN_POS_TAG);
		data.remove(PREVIOUS_NO_AI_TAG);
		data.remove(PREVIOUS_NO_GRAVITY_TAG);

		if (basinPos != null && slime.level()
			.getBlockEntity(basinPos) instanceof BasinBlockEntity basin)
			notifyBasinContentsChanged(basin);
	}

	private static boolean canProcessEntity(Entity entity, BlockPos basinPos, BasinEntityProcessingRecipe recipe) {
		if (!entity.isAlive() || entity instanceof Player)
			return false;
		if (entity instanceof Slime && !isCapturedInBasin(entity, basinPos))
			return false;
		if (!recipe.getEntityIngredient()
			.test(entity))
			return false;

		return isInBasinProcessingArea(entity, basinPos);
	}

	private static boolean isCapturedInBasin(Entity entity, BlockPos basinPos) {
		CompoundTag data = getExistingCreateBiotechData(entity);
		if (data == null)
			return false;
		return data.getBoolean(CAPTURED_TAG) && data.getLong(BASIN_POS_TAG) == basinPos.asLong();
	}

	private static boolean isCaptured(Entity entity) {
		CompoundTag data = getExistingCreateBiotechData(entity);
		return data != null && data.getBoolean(CAPTURED_TAG);
	}

	private static List<Slime> getCapturedSmallSlimes(Level level, BlockPos basinPos) {
		if (level == null)
			return Collections.emptyList();

		AABB bounds = getEntityProcessingBounds(basinPos);
		return level.getEntitiesOfClass(Slime.class, bounds,
			slime -> slime.isAlive() && slime.getSize() == 1 && isBasinSmallSlime(level, slime, basinPos));
	}

	private static boolean isBasinSmallSlime(Level level, Slime slime, BlockPos basinPos) {
		if (!isInBasinProcessingArea(slime, basinPos))
			return false;
		return level.isClientSide || isCapturedInBasin(slime, basinPos);
	}

	private static Slime findSmallSlimeInFunnelCaptureArea(Level level, BlockPos funnelPos) {
		AABB bounds = getSmallSlimeCaptureBounds(funnelPos);
		List<Slime> slimes = level.getEntitiesOfClass(Slime.class, bounds,
			slime -> slime.isAlive() && slime.getSize() == 1 && !isCaptured(slime));
		return slimes.isEmpty() ? null : slimes.get(0);
	}

	private static AABB getSmallSlimeCaptureBounds(BlockPos funnelPos) {
		return new AABB(
			funnelPos.getX(),
			funnelPos.getY(),
			funnelPos.getZ(),
			funnelPos.getX() + 1,
			funnelPos.getY() + 0.5d,
			funnelPos.getZ() + 1);
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

	private static void notifyBasinContentsChanged(BasinBlockEntity basin) {
		basin.notifyChangeOfContents();
		basin.notifyUpdate();
	}

	private static CompoundTag getCreateBiotechData(Entity entity) {
		CompoundTag persistentData = entity.getPersistentData();
		if (!persistentData.contains(DATA_ROOT))
			persistentData.put(DATA_ROOT, new CompoundTag());
		return persistentData.getCompound(DATA_ROOT);
	}

	private static CompoundTag getExistingCreateBiotechData(Entity entity) {
		CompoundTag persistentData = entity.getPersistentData();
		return persistentData.contains(DATA_ROOT) ? persistentData.getCompound(DATA_ROOT) : null;
	}
}
