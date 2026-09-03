package com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.foundation.feature.CBFeature;
import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.nobodiiiii.createbiotech.registry.CBPoiTypes;

import dev.ryanhcode.sable.companion.SubLevelAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class FixedCarrotFishingRodTargeting {

	private static final double ITEM_XZ_OFFSET = 6.5 / 16.0;

	private FixedCarrotFishingRodTargeting() {}

	@Nullable
	public static FixedCarrotFishingRodTarget findNearest(PathfinderMob mob,
		Predicate<ItemStack> temptations) {
		if (!(mob.level() instanceof ServerLevel level) || !CBFeature.FIXED_CARROT_FISHING_ROD.isEnabled())
			return null;

		double searchRange = getSearchRange();
		int poiRange = Mth.ceil(searchRange) + 1;
		SubLevelAccess trackedSpace = SubLevelCompat.getTrackingOrVehicleSubLevel(mob);
		Candidate nearest;

		if (trackedSpace != null) {
			nearest = findNearestInSpace(mob, temptations, searchRange, poiRange, trackedSpace);
		} else {
			nearest = findNearestInSpace(mob, temptations, searchRange, poiRange, null);
			AABB searchBounds = new AABB(mob.position(), mob.position()).inflate(searchRange);
			Set<UUID> visitedSpaces = new HashSet<>();
			for (SubLevelAccess subLevel : SubLevelCompat.getAllIntersecting(level, searchBounds)) {
				if (!visitedSpaces.add(subLevel.getUniqueId()))
					continue;
				nearest = nearer(nearest,
					findNearestInSpace(mob, temptations, searchRange, poiRange, subLevel));
			}
		}

		return nearest == null ? null : nearest.target();
	}

	@Nullable
	public static Vec3 getValidBaitPosition(PathfinderMob mob, FixedCarrotFishingRodTarget target) {
		if (!(mob.level() instanceof ServerLevel level) || !CBFeature.FIXED_CARROT_FISHING_ROD.isEnabled())
			return null;
		if (!level.isLoaded(target.rodPos())
			|| !SubLevelCompat.matchesSpace(level, target.rodPos(), target.subLevelId()))
			return null;

		SubLevelAccess rodSpace = SubLevelCompat.getContaining(level, target.rodPos());
		SubLevelAccess trackedSpace = SubLevelCompat.getTrackingOrVehicleSubLevel(mob);
		if (trackedSpace != null && !SubLevelCompat.sameSpace(trackedSpace, rodSpace))
			return null;

		Vec3 baitPosition = getMatchingBaitPosition(level, target.rodPos(), rodSpace,
			target.temptations());
		if (baitPosition == null)
			return null;

		double searchRange = getSearchRange();
		return mob.distanceToSqr(baitPosition) <= searchRange * searchRange ? baitPosition : null;
	}

	public static void awardIfAnimalReachedPowerBelt(PathfinderMob mob,
		FixedCarrotFishingRodTarget target) {
		if (!(mob.level() instanceof ServerLevel level))
			return;
		if (!SubLevelCompat.matchesSpace(level, target.rodPos(), target.subLevelId()))
			return;

		SubLevelAccess rodSpace = SubLevelCompat.getContaining(level, target.rodPos());
		BlockPos localFeet = BlockPos.containing(SubLevelCompat.toLocal(rodSpace, mob.position()));
		if (!isOnPowerBelt(level, localFeet) && !isOnPowerBelt(level, localFeet.below()))
			return;
		if (!(SubLevelCompat.resolveBlockEntityFast(level, target.rodPos(), target.subLevelId())
			instanceof FixedCarrotFishingRodBlockEntity rodEntity))
			return;

		UUID owner = rodEntity.getAdvancementOwner();
		if (owner != null)
			CBAdvancements.awardPlayer(level, owner, CBAdvancements.VOLUNTARY_OVERTIME);
	}

	@Nullable
	private static Candidate findNearestInSpace(PathfinderMob mob, Predicate<ItemStack> temptations,
		double searchRange, int poiRange, @Nullable SubLevelAccess subLevel) {
		if (!(mob.level() instanceof ServerLevel level))
			return null;

		Vec3 localOrigin = SubLevelCompat.toLocal(subLevel, mob.position());
		UUID subLevelId = subLevel == null ? null : subLevel.getUniqueId();
		return level.getPoiManager()
			.findAll(holder -> holder.is(CBPoiTypes.FIXED_CARROT_FISHING_ROD_KEY), level::isLoaded,
				BlockPos.containing(localOrigin), poiRange, PoiManager.Occupancy.ANY)
			.filter(pos -> SubLevelCompat.matchesSpace(level, pos, subLevelId))
			.map(pos -> candidateAt(mob, pos, subLevel, temptations, searchRange))
			.flatMap(Optional::stream)
			.min(Comparator.comparingDouble(Candidate::distanceSqr))
			.orElse(null);
	}

	private static Optional<Candidate> candidateAt(PathfinderMob mob, BlockPos pos,
		@Nullable SubLevelAccess subLevel, Predicate<ItemStack> temptations, double searchRange) {
		if (!(mob.level() instanceof ServerLevel level))
			return Optional.empty();

		Vec3 baitPosition = getMatchingBaitPosition(level, pos, subLevel, temptations);
		if (baitPosition == null)
			return Optional.empty();

		double distanceSqr = mob.distanceToSqr(baitPosition);
		if (distanceSqr > searchRange * searchRange)
			return Optional.empty();

		UUID subLevelId = subLevel == null ? null : subLevel.getUniqueId();
		return Optional.of(new Candidate(
			new FixedCarrotFishingRodTarget(pos, subLevelId, temptations), distanceSqr));
	}

	@Nullable
	private static Vec3 getMatchingBaitPosition(ServerLevel level, BlockPos pos,
		@Nullable SubLevelAccess subLevel, Predicate<ItemStack> temptations) {
		if (!(SubLevelCompat.getLoadedBlockEntity(level, pos)
			instanceof FixedCarrotFishingRodBlockEntity rodEntity))
			return null;
		if (!rodEntity.getBlockState().is(CBBlocks.FIXED_CARROT_FISHING_ROD.get()))
			return null;

		ItemStack bait = rodEntity.getBaitItem();
		if (bait.isEmpty() || !temptations.test(bait))
			return null;

		Direction facing = rodEntity.getBlockState().getValue(FixedCarrotFishingRodBlock.FACING);
		Vec3 localBaitPosition = new Vec3(
			pos.getX() + 0.5 + facing.getStepX() * ITEM_XZ_OFFSET,
			pos.getY(),
			pos.getZ() + 0.5 + facing.getStepZ() * ITEM_XZ_OFFSET);
		return SubLevelCompat.toWorld(subLevel, localBaitPosition);
	}

	private static boolean isOnPowerBelt(ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockState(pos).is(CBBlocks.POWER_BELT.get());
	}

	@Nullable
	private static Candidate nearer(@Nullable Candidate first, @Nullable Candidate second) {
		if (first == null)
			return second;
		if (second == null)
			return first;
		return first.distanceSqr() <= second.distanceSqr() ? first : second;
	}

	private static double getSearchRange() {
		return CBConfigs.SERVER.fixedCarrotFishingRod.searchRange.get();
	}

	public static int getSearchCooldownTicks() {
		return CBConfigs.SERVER.fixedCarrotFishingRod.searchCooldown.get();
	}

	private record Candidate(FixedCarrotFishingRodTarget target, double distanceSqr) {}
}
