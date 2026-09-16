package com.nobodiiiii.createbiotech.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.nobodiiiii.createbiotech.content.buttercat.block.ButterCatEngineBlock;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.registries.GameData;

class CBSharedPropertiesTest {
	private static ButterCatEngineBlock unforced;
	private static ButterCatEngineBlock nonSolid;

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
		Bootstrap.bootStrap();
		// Standalone JUnit has no mod registration phase. Open it for these two fixtures
		// and restore frozen registries before assertions or other test classes run.
		GameData.unfreezeData();
		try {
			unforced = Registry.register(BuiltInRegistries.BLOCK,
				ResourceLocation.fromNamespaceAndPath("create_biotech_test", "cat_unforced"),
				new ButterCatEngineBlock(Block.Properties.of().noOcclusion()));
			nonSolid = Registry.register(BuiltInRegistries.BLOCK,
				ResourceLocation.fromNamespaceAndPath("create_biotech_test", "cat_non_solid"),
				new ButterCatEngineBlock(CBSharedProperties.withLegacyNonSolid(Block.Properties.of().noOcclusion())));
		} finally {
			BuiltInRegistries.REGISTRY.forEach(Registry::freeze);
		}
	}

	@ParameterizedTest
	@EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
	// The compatibility flag intentionally controls the deprecated 1.21.1 solid predicate.
	@SuppressWarnings("deprecation")
	void nonSolidCatKeepsCollisionShapeInEveryFacing(Direction facing) {
		BlockState unforcedState = unforced.defaultBlockState()
			.setValue(ButterCatEngineBlock.HORIZONTAL_FACING, facing);
		BlockState nonSolidState = nonSolid.defaultBlockState()
			.setValue(ButterCatEngineBlock.HORIZONTAL_FACING, facing);
		unforcedState.initCache();
		nonSolidState.initCache();

		assertTrue(unforcedState.isSolid(), "noOcclusion alone does not clear legacy solidity");
		assertFalse(nonSolidState.isSolid());
		assertFalse(nonSolidState.canOcclude());
		assertEquals(
			unforcedState.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs(),
			nonSolidState.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs());
		assertFalse(nonSolidState.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty());
	}
}
