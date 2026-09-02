package com.nobodiiiii.createbiotech.content.processing.basin;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.beltsurface.BeltFunnelStateExtensions;
import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurface;
import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurfaceResolver;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.mixin.BlockEntityPersistentDataAccessor;
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
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Contained basin slimes are plain inventory items. The only slime-specific behaviour left here is
 * the boundary in both directions: a live slime entering through a funnel becomes a control item,
 * and a control item leaving the basin becomes a live slime again.
 */
public final class BasinEntityProcessing {
	private static final String DATA_ROOT = CreateBiotech.MOD_ID;

	// 1.3.0.1 mirrored every control item with a real world Slime and stamped these tags. Nothing
	// writes them any more; they exist only so a legacy save can be reconciled exactly once.
	private static final String LEGACY_SYNCED_ITEM_COUNT_TAG = "BasinEntityProcessingSyncedItemCount";
	private static final String LEGACY_DATA_VERSION_TAG = "BasinEntityProcessingDataVersion";
	private static final String LEGACY_CAPTURED_TAG = "BasinEntityProcessingCaptured";
	private static final String LEGACY_BASIN_POS_TAG = "BasinEntityProcessingBasinPos";
	private static final String LEGACY_NO_AI_TAG = "BasinEntityProcessingPreviousNoAi";
	private static final String LEGACY_NO_GRAVITY_TAG = "BasinEntityProcessingPreviousNoGravity";
	private static final double LEGACY_SCAN_INNER_MIN = 2 / 16d;
	private static final double LEGACY_SCAN_INNER_MAX = 14 / 16d;
	private static final double LEGACY_SCAN_HEIGHT = 1.25d;

	private BasinEntityProcessing() {}

	public static boolean isCapturedSmallSlimeItem(ItemStack stack) {
		return stack.getItem() == CBItems.CAPTURED_SMALL_SLIME.get();
	}

	/** The basin inventories are the sole authoritative contained-slime state. */
	public static boolean hasCapturedSmallSlimes(BasinBlockEntity basin) {
		return getCapturedSmallSlimeItemCount(basin) > 0;
	}

	public static int getCapturedSmallSlimeItemCount(BasinBlockEntity basin) {
		return countCapturedSmallSlimeItems(basin.getInputInventory())
			+ countCapturedSmallSlimeItems(basin.getOutputInventory());
	}

	/** Internal recipes and basin-owned operations see an ordinary, unfiltered inventory. */
	public static IItemHandlerModifiable getInternalItemHandler(BasinBlockEntity basin) {
		return ((BasinInternalItemAccess) (Object) basin).createBiotech$getInternalItemHandler();
	}

	/** Funnels may extract the control stack, but cannot insert one from item transport. */
	public static IItemHandlerModifiable getFunnelItemHandler(BasinBlockEntity basin) {
		return ((BasinInternalItemAccess) (Object) basin).createBiotech$getFunnelItemHandler();
	}

	public static boolean hasLegacyContainedSlimeData(BasinBlockEntity basin) {
		return getLegacyData(basin) != null;
	}

	/**
	 * One-time reconciliation of a 1.3.0.1 basin. Control items are authoritative, so mirrored
	 * slimes are absorbed; a surplus slime becomes an extra control item when the basin still has
	 * room and is otherwise released with its pre-capture movement flags restored.
	 * <p>
	 * Callers must invoke this at most a handful of times per block entity: a basin that never saw
	 * the old mirror carries no persistent data at all and returns on the first null check.
	 */
	public static void migrateLegacyContainedSlimes(BasinBlockEntity basin) {
		Level level = basin.getLevel();
		if (level == null || level.isClientSide)
			return;
		CompoundTag data = getLegacyData(basin);
		if (data == null)
			return;
		CompoundTag persistentData =
			((BlockEntityPersistentDataAccessor) basin).createBiotech$getExistingPersistentData();

		int authoritativeItems = getCapturedSmallSlimeItemCount(basin);
		BlockPos basinPos = basin.getBlockPos();
		List<Slime> legacySlimes = level.getEntitiesOfClass(Slime.class, getLegacyScanBounds(basinPos),
			slime -> slime.getSize() == 1 && isLegacyMirrorOf(slime, basinPos));
		int absorbed = 0;
		for (Slime slime : legacySlimes) {
			if (absorbed < authoritativeItems) {
				clearLegacyCaptureData(slime, false);
				slime.discard();
				absorbed++;
				continue;
			}
			if (insertCapturedSmallSlimeItem(basin, true) && insertCapturedSmallSlimeItem(basin, false)) {
				clearLegacyCaptureData(slime, false);
				slime.discard();
				continue;
			}
			clearLegacyCaptureData(slime, true);
		}

		data.remove(LEGACY_SYNCED_ITEM_COUNT_TAG);
		data.remove(LEGACY_DATA_VERSION_TAG);
		if (data.isEmpty())
			persistentData.remove(DATA_ROOT);
		notifyBasinContentsChanged(basin);
	}

	public static void handleFunnelEntityInside(Level level, BlockPos funnelPos, Entity entity) {
		if (level.isClientSide || !(entity instanceof Slime slime) || !slime.isAlive() || slime.getSize() != 1
			|| !getSmallSlimeCaptureBounds(funnelPos).intersects(slime.getBoundingBox()))
			return;
		if (level.getBlockEntity(funnelPos) instanceof SlimeCaptureFunnelAccess captureFunnel)
			captureFunnel.createBiotech$tryCaptureSmallSlime(slime);
	}

	public static boolean tryCaptureSmallSlimeFromFunnel(FunnelBlockEntity funnel, Slime slime) {
		Level level = funnel.getLevel();
		if (level == null || level.isClientSide || slime.level() != level || !slime.isAlive()
			|| slime.getSize() != 1
			|| !getSmallSlimeCaptureBounds(funnel.getBlockPos()).intersects(slime.getBoundingBox()))
			return false;

		BlockState blockState = funnel.getBlockState();
		if (blockState.getOptionalValue(AbstractFunnelBlock.POWERED).orElse(false))
			return false;
		Direction facing = getSmallSlimeInputFacing(level, funnel.getBlockPos(), blockState);
		if (facing == null)
			return false;
		BlockPos basinPos = funnel.getBlockPos().relative(facing.getOpposite());
		if (!(level.getBlockEntity(basinPos) instanceof BasinBlockEntity basin))
			return false;

		// Commit the machine state first: the entity may only disappear once its item exists.
		if (!insertCapturedSmallSlimeItem(basin, true) || !insertCapturedSmallSlimeItem(basin, false))
			return false;
		slime.discard();
		notifyBasinContentsChanged(basin);
		return true;
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

	public static float getContainedSlimeAnimationPhase(Level level, BlockPos basinPos, int visualIndex,
		float partialTicks) {
		return (level.getGameTime() + partialTicks) * .22f + visualIndex * 1.73f
			+ (basinPos.asLong() & 31) * .11f;
	}

	private static boolean insertCapturedSmallSlimeItem(BasinBlockEntity basin, boolean simulate) {
		ItemStack stack = new ItemStack(CBItems.CAPTURED_SMALL_SLIME.get());
		return ItemHandlerHelper.insertItemStacked(basin.getInputInventory(), stack, simulate)
			.isEmpty();
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
			return facing != null && facing.getAxis().isHorizontal() ? facing : null;
		}
		if (!(blockState.getBlock() instanceof BeltFunnelBlock))
			return null;
		Direction facing = AbstractFunnelBlock.getFunnelFacing(blockState);
		if (facing == null)
			return null;
		Direction outwardNormal = BeltFunnelStateExtensions.tiltedOutwardNormal(blockState);
		if (outwardNormal != null)
			facing = BeltSurface.worldizeCanonical(facing, outwardNormal);
		if (!facing.getAxis().isHorizontal())
			return null;
		Shape shape = blockState.getValue(BeltFunnelBlock.SHAPE);
		if (shape == Shape.PULLING)
			return facing;
		if (shape == Shape.PUSHING)
			return null;
		BeltSurface surface = BeltSurfaceResolver.resolve(level, funnelPos, blockState);
		return isTakingFromBelt(level, funnelPos, facing, surface) ? facing : null;
	}

	private static boolean isTakingFromBelt(Level level, BlockPos funnelPos, Direction worldFacing,
		BeltSurface surface) {
		if (surface != null)
			return surface.movementFacing() != worldFacing;
		BeltBlockEntity belt = BeltHelper.getSegmentBE(level, funnelPos.below());
		return belt != null && belt.getMovementFacing() != worldFacing;
	}

	private static AABB getSmallSlimeCaptureBounds(BlockPos funnelPos) {
		return new AABB(funnelPos.getX(), funnelPos.getY(), funnelPos.getZ(), funnelPos.getX() + 1,
			funnelPos.getY() + .5d, funnelPos.getZ() + 1);
	}

	private static AABB getLegacyScanBounds(BlockPos basinPos) {
		return new AABB(basinPos.getX() + LEGACY_SCAN_INNER_MIN, basinPos.getY(),
			basinPos.getZ() + LEGACY_SCAN_INNER_MIN, basinPos.getX() + LEGACY_SCAN_INNER_MAX,
			basinPos.getY() + LEGACY_SCAN_HEIGHT, basinPos.getZ() + LEGACY_SCAN_INNER_MAX);
	}

	private static boolean isLegacyMirrorOf(Entity entity, BlockPos basinPos) {
		CompoundTag persistentData = entity.getPersistentData();
		if (!persistentData.contains(DATA_ROOT, Tag.TAG_COMPOUND))
			return false;
		CompoundTag data = persistentData.getCompound(DATA_ROOT);
		return data.getBoolean(LEGACY_CAPTURED_TAG) && data.contains(LEGACY_BASIN_POS_TAG, Tag.TAG_LONG)
			&& data.getLong(LEGACY_BASIN_POS_TAG) == basinPos.asLong();
	}

	@Nullable
	private static CompoundTag getLegacyData(BasinBlockEntity basin) {
		CompoundTag persistentData =
			((BlockEntityPersistentDataAccessor) basin).createBiotech$getExistingPersistentData();
		if (persistentData == null || !persistentData.contains(DATA_ROOT, Tag.TAG_COMPOUND))
			return null;
		CompoundTag data = persistentData.getCompound(DATA_ROOT);
		return data.contains(LEGACY_SYNCED_ITEM_COUNT_TAG) || data.contains(LEGACY_DATA_VERSION_TAG)
			? data : null;
	}

	private static void clearLegacyCaptureData(Slime slime, boolean restoreState) {
		CompoundTag persistentData = slime.getPersistentData();
		if (!persistentData.contains(DATA_ROOT, Tag.TAG_COMPOUND))
			return;
		CompoundTag data = persistentData.getCompound(DATA_ROOT);
		if (restoreState) {
			slime.setNoAi(data.getBoolean(LEGACY_NO_AI_TAG));
			slime.setNoGravity(data.getBoolean(LEGACY_NO_GRAVITY_TAG));
			CapturedEntityBoxHelper.unmarkAiDisabledByMod(slime);
		}
		data.remove(LEGACY_CAPTURED_TAG);
		data.remove(LEGACY_BASIN_POS_TAG);
		data.remove(LEGACY_NO_AI_TAG);
		data.remove(LEGACY_NO_GRAVITY_TAG);
		if (data.isEmpty())
			persistentData.remove(DATA_ROOT);
	}

	private static void notifyBasinContentsChanged(BasinBlockEntity basin) {
		basin.notifyChangeOfContents();
		basin.notifyUpdate();
	}
}
