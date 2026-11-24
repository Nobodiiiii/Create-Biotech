package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.buttercat.block.ButterCatArmInteraction;
import com.nobodiiiii.createbiotech.content.beltsurface.StandardItemBeltArmInteraction;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.AllArmInteractionPointTypes.PackagerType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class CBArmInteractionPointTypes {
	private static boolean registered;

	private CBArmInteractionPointTypes() {}

	public static void register() {
		if (registered)
			return;
		registered = true;
		Registry.register(CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE,
			CreateBiotech.asResource("butter_cat_engine"), new ButterCatArmInteraction.Type());
		Registry.register(CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE,
			CreateBiotech.asResource("slime_belt"), new StandardItemBeltArmInteraction.Type(CBBlocks.SLIME_BELT));
		Registry.register(CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE,
			CreateBiotech.asResource("magma_belt"), new StandardItemBeltArmInteraction.Type(CBBlocks.MAGMA_BELT));
		Registry.register(CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE,
			CreateBiotech.asResource("bio_packager"), new PackagerType() {
				@Override
				public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
					return state.is(CBBlocks.BIO_PACKAGER.get());
				}
			});
	}
}
