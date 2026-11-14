package com.nobodiiiii.createbiotech.content.shulkerpackager;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

import javax.annotation.Nullable;

public class ShulkerPackagerArmInteractions {

	public static final ShulkerPackagerType SHULKER_PACKAGER = new ShulkerPackagerType();

	private ShulkerPackagerArmInteractions() {}

	static {
		Registry.register(CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE, CreateBiotech.asResource("shulker_packager"),
			SHULKER_PACKAGER);
	}

	public static void register() {}

	public static boolean isPackager(BlockState state) {
		return isShulkerPackager(state) || isVanillaPackager(state);
	}

	public static boolean isSelectable(Level level, BlockPos pos, BlockState state) {
		return isPackager(state) || ArmInteractionPoint.isInteractable(level, pos, state);
	}

	public static boolean isShulkerPackager(BlockState state) {
		return state.is(CBBlocks.SHULKER_PACKAGER.get());
	}

	public static boolean isVanillaPackager(BlockState state) {
		return AllBlocks.PACKAGER.has(state) || AllBlocks.REPACKAGER.has(state);
	}

	public static boolean isPoint(ArmInteractionPoint point) {
		return point != null && point.getType() == SHULKER_PACKAGER;
	}

	public static boolean isValidConnection(Level level, BlockPos anchor, @Nullable UUID anchorSubLevelId,
		BlockPos target, @Nullable UUID targetSubLevelId) {
		if (anchor.equals(target))
			return false;
		if (!level.isLoaded(anchor) || !level.isLoaded(target))
			return false;
		if (!SubLevelCompat.matchesSpace(level, anchor, anchorSubLevelId)
			|| !SubLevelCompat.matchesSpace(level, target, targetSubLevelId))
			return false;
		if (!isSelectable(level, target, level.getBlockState(target)))
			return false;

		int range = CBConfigs.SERVER.shulkerPackager.connectionRange.get();
		return ShulkerPackagerRange.isWithinCube(level, anchor, target, range);
	}

	public static class ShulkerPackagerType extends ArmInteractionPointType {
		@Override
		public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
			// Keep this registered type specific to packagers so other targets retain
			// their own interaction point and insertion rules.
			return isPackager(state);
		}

		@Override
		public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
			return new ArmInteractionPoint(this, level, pos, state);
		}
	}
}
