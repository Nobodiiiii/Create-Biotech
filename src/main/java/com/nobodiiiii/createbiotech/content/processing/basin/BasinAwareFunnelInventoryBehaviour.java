package com.nobodiiiii.createbiotech.content.processing.basin;

import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;

import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Gives a Create funnel the extraction-only boundary view when its target is a basin. */
public final class BasinAwareFunnelInventoryBehaviour extends InvManipulationBehaviour {

	public BasinAwareFunnelInventoryBehaviour(FunnelBlockEntity funnel, InterfaceProvider target) {
		super(funnel, target);
	}

	@Override
	public void findNewCapability() {
		Level level = getWorld();
		BlockFace targetFace = getTarget().getOpposite();
		BlockPos targetPos = targetFace.getPos();
		if (level.isLoaded(targetPos)) {
			BlockEntity targetBlockEntity = level.getBlockEntity(targetPos);
			if (targetBlockEntity instanceof com.simibubi.create.content.processing.basin.BasinBlockEntity basin
				&& filter.test(targetBlockEntity)) {
				targetCapability = BasinEntityProcessing.getFunnelItemHandler(basin);
				return;
			}
		}
		super.findNewCapability();
	}
}
