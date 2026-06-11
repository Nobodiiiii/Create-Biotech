package com.nobodiiiii.createbiotech.content.shulkerpackager;

import com.nobodiiiii.createbiotech.CreateBiotech;
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

public class ShulkerPackagerArmInteractions {

	public static final ShulkerPackagerType SHULKER_PACKAGER = new ShulkerPackagerType();

	private ShulkerPackagerArmInteractions() {}

	static {
		Registry.register(CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE, CreateBiotech.asResource("shulker_packager"),
			SHULKER_PACKAGER);
	}

	public static void register() {}

	public static boolean isSelectable(BlockState state) {
		return isShulkerPackager(state) || (!shouldOnlyConnectToShulkerPackagers() && isVanillaPackager(state));
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

	public static boolean canBeInput(ArmInteractionPoint point) {
		if (point == null || point.getLevel() == null)
			return false;
		return isShulkerPackager(point.getLevel()
			.getBlockState(point.getPos()));
	}

	public static class ShulkerPackagerType extends ArmInteractionPointType {
		@Override
		public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
			return isSelectable(state);
		}

		@Override
		public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
			return new ArmInteractionPoint(this, level, pos, state);
		}
	}

	private static boolean shouldOnlyConnectToShulkerPackagers() {
		return CBConfigs.COMMON.shulkerPackager.onlyConnectToShulkerPackagers.get();
	}
}
