package com.nobodiiiii.createbiotech.content.processing.basin;

import java.util.Collections;
import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurface;
import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurfaceResolver;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock.Shape;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;

public class BasinEntityProcessing {
	private static final double BASIN_INNER_MIN = 2 / 16d;
	private static final double BASIN_INNER_MAX = 14 / 16d;
	private static final double BASIN_SLIME_Y_OFFSET = 0.125d;
	private static final String DATA_ROOT = CreateBiotech.MOD_ID;
	private static final String CAPTURED_TAG = "BasinEntityProcessingCaptured";
	private static final String BASIN_POS_TAG = "BasinEntityProcessingBasinPos";
	private static final String PREVIOUS_NO_AI_TAG = "BasinEntityProcessingPreviousNoAi";
	private static final String PREVIOUS_NO_GRAVITY_TAG = "BasinEntityProcessingPreviousNoGravity";
	private static final String SYNCED_ITEM_COUNT_TAG = "BasinEntityProcessingSyncedItemCount";

	private static final String CREATE_FUNNEL_PACKAGE = "com.simibubi.create.content.logistics.funnel.";
	private static final String CREATE_BASIN_RECIPE = "com.simibubi.create.content.processing.basin.BasinRecipe";
	private static final String FUNNEL_MIXIN = "com.nobodiiiii.createbiotech.mixin.FunnelBlockEntityMixin";

	private BasinEntityProcessing() {}

	public static boolean isCapturedSmallSlimeItem(ItemStack stack) {
		return stack.is(CBItems.CAPTURED_SMALL_SLIME.get());
	}

	public static boolean hasCapturedSmallSlimes(BasinBlockEntity basin) {
		Level level = basin.getLevel();
		if (level == null)
			return false;
		return !getCapturedSmallSlimes(level, basin.getBlockPos()).isEmpty();
	}

	public static boolean canMoveCapturedSmallSlimeItems() {
		for (StackTraceElement frame : Thread.currentThread()
			.getStackTrace()) {
			String className = frame.getClassName();
			if (className.startsWith(CREATE_FUNNEL_PACKAGE) || className.equals(CREATE_BASIN_RECIPE)
				|| className.equals(FUNNEL_MIXIN))
				return true;
		}
		return false;
	}

	public static boolean syncCapturedSmallSlimeItems(BasinBlockEntity basin) {
		Level level = basin.getLevel();
		if (level == null || level.isClientSide)
			return false;

		CompoundTag data = getCreateBiotechData(basin);
		boolean hadSyncedCount = data.contains(SYNCED_ITEM_COUNT_TAG);
		int previousCount = hadSyncedCount ? data.getInt(SYNCED_ITEM_COUNT_TAG) : -1;
		int itemCount = getCapturedSmallSlimeItemCount(basin);
		List<Slime> slimes = getCapturedSmallSlimes(level, basin.getBlockPos());
		int entityCount = slimes.size();
		int syncedCount = itemCount;
		boolean changed = false;

		if (!hadSyncedCount && entityCount > itemCount) {
			syncedCount = syncItemsToEntityCount(basin, slimes, entityCount);
			changed = syncedCount != itemCount;
		} else if (itemCount != previousCount) {
			int syncedEntities = syncEntitiesToItemCount(basin, slimes, itemCount);
			if (syncedEntities != itemCount) {
				syncedCount = syncItemsToEntityCount(basin, getCapturedSmallSlimes(level, basin.getBlockPos()),
					syncedEntities);
				changed = syncedCount != itemCount;
			} else {
				syncedCount = itemCount;
				changed = entityCount != itemCount;
			}
		} else if (entityCount != itemCount) {
			syncedCount = syncItemsToEntityCount(basin, slimes, entityCount);
			changed = syncedCount != itemCount;
		}

		data.putInt(SYNCED_ITEM_COUNT_TAG, syncedCount);
		for (Slime slime : getCapturedSmallSlimes(level, basin.getBlockPos()))
			disableAiForSmallSlimeInBasin(level, basin.getBlockPos(), slime);

		if (changed) {
			notifyBasinContentsChanged(basin);
			return true;
		}
		return false;
	}

	public static boolean acceptsCapturedSmallSlimeOutput(BasinBlockEntity basin, List<ItemStack> outputItems,
		boolean simulate) {
		if (outputItems.isEmpty())
			return true;

		basin.getOutputInventory()
			.allowInsertion();
		try {
			for (ItemStack stack : outputItems) {
				if (stack.isEmpty())
					continue;
				if (!ItemHandlerHelper.insertItemStacked(basin.getOutputInventory(), stack.copy(), simulate)
					.isEmpty())
					return false;
			}
		} finally {
			basin.getOutputInventory()
				.forbidInsertion();
		}

		return true;
	}

	public static boolean tryCaptureSmallSlimeFromFunnel(FunnelBlockEntity funnel) {
		Level level = funnel.getLevel();
		if (level == null || level.isClientSide)
			return false;

		BlockState blockState = funnel.getBlockState();
		if (blockState.getOptionalValue(AbstractFunnelBlock.POWERED)
			.orElse(false))
			return false;

		Direction facing = getSmallSlimeInputFacing(level, funnel.getBlockPos(), blockState);
		if (facing == null)
			return false;

		BlockPos basinPos = funnel.getBlockPos()
			.relative(facing.getOpposite());
		if (!(level.getBlockEntity(basinPos) instanceof BasinBlockEntity basin))
			return false;

		Slime slime = findSmallSlimeInFunnelCaptureArea(level, funnel.getBlockPos());
		if (slime == null)
			return false;

		return captureSmallSlimeInBasinFromFunnel(basin, slime);
	}

	public static boolean isBeltFunnelSmallSlimeInput(FunnelBlockEntity funnel) {
		Level level = funnel.getLevel();
		if (level == null)
			return false;

		BlockState blockState = funnel.getBlockState();
		if (!(blockState.getBlock() instanceof BeltFunnelBlock))
			return false;

		return getSmallSlimeInputFacing(level, funnel.getBlockPos(), blockState) != null;
	}

	public static void tickCapturedSmallSlime(Slime slime) {
		Level level = slime.level();
		if (level.isClientSide)
			return;

		CompoundTag data = getExistingCreateBiotechData(slime);
		if (data == null || !data.getBoolean(CAPTURED_TAG))
			return;

		BlockPos basinPos = BlockPos.of(data.getLong(BASIN_POS_TAG));
		boolean valid = slime.getSize() == 1
			&& level.getBlockEntity(basinPos) instanceof BasinBlockEntity
			&& isInBasinProcessingArea(slime, basinPos);
		if (!valid) {
			releaseCapturedSlime(slime);
			return;
		}

		disableAiForSmallSlimeInBasin(level, basinPos, slime);
	}

	public static boolean isCapturedSmallSlime(Entity entity) {
		if (!(entity instanceof Slime slime) || slime.getSize() != 1)
			return false;
		return isCaptured(entity);
	}

	public static void releaseCapturedSmallSlime(Slime slime) {
		releaseCapturedSlime(slime);
	}

	public static void onCapturedSmallSlimeRemoved(Slime slime) {
		CompoundTag data = getExistingCreateBiotechData(slime);
		if (data == null || !data.getBoolean(CAPTURED_TAG) || !data.contains(BASIN_POS_TAG))
			return;

		Level level = slime.level();
		if (!(level.getBlockEntity(BlockPos.of(data.getLong(BASIN_POS_TAG))) instanceof BasinBlockEntity basin))
			return;

		syncCapturedSmallSlimeItems(basin);
	}

	public static Slime createSmallSlime(Level level, Vec3 position, Vec3 motion) {
		if (level == null)
			return null;

		Slime slime = EntityType.SLIME.create(level);
		if (slime == null)
			return null;

		slime.setSize(1, true);
		slime.setPersistenceRequired();
		slime.moveTo(position.x, position.y, position.z, level.random.nextFloat() * 360, 0);
		slime.setDeltaMovement(motion);
		slime.fallDistance = 0;
		return slime;
	}

	public static boolean disableAiForSmallSlimeInBasin(Level level, BlockPos basinPos, Entity entity) {
		if (level.isClientSide || !(entity instanceof Slime slime) || slime.getSize() != 1)
			return false;
		if (!isInBasinProcessingArea(entity, basinPos))
			return false;

		slime.setNoAi(true);
		CapturedEntityBoxHelper.markAiDisabledByMod(slime);
		slime.setNoGravity(false);
		slime.setJumping(false);
		Vec3 motion = slime.getDeltaMovement();
		slime.setDeltaMovement(0, motion.y, 0);
		return true;
	}

	private static int syncEntitiesToItemCount(BasinBlockEntity basin, List<Slime> slimes, int targetCount) {
		int count = slimes.size();
		while (count > targetCount) {
			Slime slime = slimes.get(count - 1);
			releaseCapturedSlime(slime);
			slime.discard();
			count--;
		}

		while (count < targetCount) {
			if (!spawnCapturedSmallSlimeInBasin(basin))
				break;
			count++;
		}
		return count;
	}

	private static int syncItemsToEntityCount(BasinBlockEntity basin, List<Slime> slimes, int targetCount) {
		int itemCount = setCapturedSmallSlimeItemCount(basin, targetCount);
		if (itemCount >= targetCount)
			return itemCount;

		for (int i = itemCount; i < slimes.size(); i++) {
			Slime slime = slimes.get(i);
			releaseCapturedSlime(slime);
			slime.discard();
		}
		return itemCount;
	}

	private static boolean spawnCapturedSmallSlimeInBasin(BasinBlockEntity basin) {
		Level level = basin.getLevel();
		if (level == null || level.isClientSide)
			return false;

		BlockPos basinPos = basin.getBlockPos();
		Slime slime = createSmallSlime(level, Vec3.atCenterOf(basinPos)
			.add(0, BASIN_SLIME_Y_OFFSET - 0.5d, 0), Vec3.ZERO);
		if (slime == null)
			return false;
		if (!level.addFreshEntity(slime))
			return false;
		if (markSmallSlimeInBasin(basin, slime))
			return true;

		slime.discard();
		return false;
	}

	private static boolean captureSmallSlimeInBasinFromFunnel(BasinBlockEntity basin, Slime slime) {
		if (!canInsertCapturedSmallSlimeItems(basin, 1))
			return false;
		if (!markSmallSlimeInBasin(basin, slime))
			return false;
		if (insertCapturedSmallSlimeItems(basin, 1, false) != 1) {
			releaseCapturedSlime(slime);
			return false;
		}

		notifyBasinContentsChanged(basin);
		return true;
	}

	private static boolean markSmallSlimeInBasin(BasinBlockEntity basin, Slime slime) {
		Level level = basin.getLevel();
		if (level == null || level.isClientSide || !slime.isAlive() || slime.getSize() != 1)
			return false;

		BlockPos basinPos = basin.getBlockPos();
		CompoundTag data = getCreateBiotechData(slime);
		boolean wasCaptured = data.getBoolean(CAPTURED_TAG);
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
		return true;
	}

	private static boolean canInsertCapturedSmallSlimeItems(BasinBlockEntity basin, int count) {
		return insertCapturedSmallSlimeItems(basin, count, true) == count;
	}

	private static int insertCapturedSmallSlimeItems(BasinBlockEntity basin, int count, boolean simulate) {
		int inserted = 0;
		int remaining = count;
		int maxStackSize = new ItemStack(CBItems.CAPTURED_SMALL_SLIME.get()).getMaxStackSize();
		while (remaining > 0) {
			ItemStack stack = new ItemStack(CBItems.CAPTURED_SMALL_SLIME.get(), Math.min(remaining, maxStackSize));
			ItemStack remainder = ItemHandlerHelper.insertItemStacked(basin.getInputInventory(), stack, simulate);
			int insertedThisPass = stack.getCount() - remainder.getCount();
			if (insertedThisPass <= 0)
				break;
			inserted += insertedThisPass;
			remaining -= insertedThisPass;
		}
		return inserted;
	}

	private static int setCapturedSmallSlimeItemCount(BasinBlockEntity basin, int targetCount) {
		int currentCount = getCapturedSmallSlimeItemCount(basin);
		if (targetCount < currentCount) {
			shrinkCapturedSmallSlimeItems(basin, currentCount - targetCount);
			return getCapturedSmallSlimeItemCount(basin);
		}
		if (targetCount > currentCount)
			insertCapturedSmallSlimeItems(basin, targetCount - currentCount, false);
		return getCapturedSmallSlimeItemCount(basin);
	}

	private static void shrinkCapturedSmallSlimeItems(BasinBlockEntity basin, int amount) {
		int remaining = shrinkCapturedSmallSlimeItems(basin.getInputInventory(), amount);
		if (remaining > 0)
			shrinkCapturedSmallSlimeItems(basin.getOutputInventory(), remaining);
	}

	private static int shrinkCapturedSmallSlimeItems(IItemHandlerModifiable inventory, int amount) {
		int remaining = amount;
		for (int slot = 0; slot < inventory.getSlots() && remaining > 0; slot++) {
			ItemStack stack = inventory.getStackInSlot(slot);
			if (!isCapturedSmallSlimeItem(stack))
				continue;

			int removed = Math.min(remaining, stack.getCount());
			stack.shrink(removed);
			inventory.setStackInSlot(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
			remaining -= removed;
		}
		return remaining;
	}

	private static int getCapturedSmallSlimeItemCount(BasinBlockEntity basin) {
		return countCapturedSmallSlimeItems(basin.getInputInventory())
			+ countCapturedSmallSlimeItems(basin.getOutputInventory());
	}

	private static int countCapturedSmallSlimeItems(IItemHandlerModifiable inventory) {
		int count = 0;
		for (int slot = 0; slot < inventory.getSlots(); slot++) {
			ItemStack stack = inventory.getStackInSlot(slot);
			if (isCapturedSmallSlimeItem(stack))
				count += stack.getCount();
		}
		return count;
	}

	private static Direction getSmallSlimeInputFacing(Level level, BlockPos funnelPos, BlockState blockState) {
		if (blockState.getBlock() instanceof FunnelBlock) {
			if (blockState.getValue(FunnelBlock.EXTRACTING))
				return null;
			Direction facing = AbstractFunnelBlock.getFunnelFacing(blockState);
			return facing != null && facing.getAxis()
				.isHorizontal() ? facing : null;
		}

		if (!(blockState.getBlock() instanceof BeltFunnelBlock))
			return null;

		Direction facing = AbstractFunnelBlock.getFunnelFacing(blockState);
		if (facing == null)
			return null;

		BeltSurface surface = BeltSurfaceResolver.resolve(level, funnelPos);
		if (surface != null)
			facing = surface.worldize(facing);
		if (!facing.getAxis()
			.isHorizontal())
			return null;

		return isTakingFromBelt(level, funnelPos, blockState, facing, surface) ? facing : null;
	}

	private static boolean isTakingFromBelt(Level level, BlockPos funnelPos, BlockState blockState,
		Direction worldFacing, BeltSurface surface) {
		Shape shape = blockState.getValue(BeltFunnelBlock.SHAPE);
		if (shape == Shape.PULLING)
			return true;
		if (shape == Shape.PUSHING)
			return false;

		if (surface != null && surface.host() != null)
			return surface.movementFacing() != worldFacing;

		BeltBlockEntity belt = BeltHelper.getSegmentBE(level, funnelPos.below());
		return belt != null && belt.getMovementFacing() != worldFacing;
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

	private static AABB getEntityProcessingBounds(BlockPos basinPos) {
		return new AABB(
			basinPos.getX() + BASIN_INNER_MIN,
			basinPos.getY(),
			basinPos.getZ() + BASIN_INNER_MIN,
			basinPos.getX() + BASIN_INNER_MAX,
			basinPos.getY() + getEntityScanHeight(),
			basinPos.getZ() + BASIN_INNER_MAX);
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
			&& center.y <= basinPos.getY() + getEntityScanHeight();
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

	private static void releaseCapturedSlime(Slime slime) {
		CompoundTag data = getExistingCreateBiotechData(slime);
		if (data == null || !data.getBoolean(CAPTURED_TAG))
			return;

		BlockPos basinPos = data.contains(BASIN_POS_TAG) ? BlockPos.of(data.getLong(BASIN_POS_TAG)) : null;
		slime.setNoAi(data.getBoolean(PREVIOUS_NO_AI_TAG));
		CapturedEntityBoxHelper.unmarkAiDisabledByMod(slime);
		slime.setNoGravity(data.getBoolean(PREVIOUS_NO_GRAVITY_TAG));
		data.remove(CAPTURED_TAG);
		data.remove(BASIN_POS_TAG);
		data.remove(PREVIOUS_NO_AI_TAG);
		data.remove(PREVIOUS_NO_GRAVITY_TAG);

		if (basinPos != null && slime.level()
			.getBlockEntity(basinPos) instanceof BasinBlockEntity basin)
			notifyBasinContentsChanged(basin);
	}

	private static double getEntityScanHeight() {
		return CBConfigs.SERVER.basinEntityProcessing.entityScanHeight.get();
	}

	private static void notifyBasinContentsChanged(BasinBlockEntity basin) {
		basin.notifyChangeOfContents();
		basin.notifyUpdate();
	}

	private static CompoundTag getCreateBiotechData(BasinBlockEntity basin) {
		CompoundTag persistentData = basin.getPersistentData();
		if (!persistentData.contains(DATA_ROOT))
			persistentData.put(DATA_ROOT, new CompoundTag());
		return persistentData.getCompound(DATA_ROOT);
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
