package com.nobodiiiii.createbiotech.content.explosionproofitemvault;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryWrapper;
import com.simibubi.create.foundation.utility.SameSizeCombinedInvWrapper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

public class ExplosionProofItemVaultBlockEntity extends ItemVaultBlockEntity {

	public ExplosionProofItemVaultBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public ExplosionProofItemVaultBlockEntity(BlockPos pos, BlockState state) {
		this(CBBlockEntityTypes.EXPLOSION_PROOF_ITEM_VAULT.get(), pos, state);
	}

	@Override
	public InventoryIdentifier getInvId() {
		initExplosionProofCapability();
		return invId;
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
		if (isItemHandlerCap(cap)) {
			initExplosionProofCapability();
			return itemCapability.cast();
		}
		return super.getCapability(cap, side);
	}

	@Override
	public void removeController(boolean keepContents) {
		if (level.isClientSide())
			return;
		updateConnectivity = true;
		controller = null;
		radius = 1;
		length = 1;

		BlockState state = getBlockState();
		if (ExplosionProofItemVaultBlock.isVault(state)) {
			state = state.setValue(ItemVaultBlock.LARGE, false);
			getLevel().setBlock(worldPosition, state, 22);
		}

		itemCapability.invalidate();
		setChanged();
		sendData();
	}

	@Override
	public void notifyMultiUpdated() {
		BlockState state = getBlockState();
		if (ExplosionProofItemVaultBlock.isVault(state)) {
			level.setBlock(getBlockPos(), state.setValue(ItemVaultBlock.LARGE, radius > 2), 6);
		}
		itemCapability.invalidate();
		setChanged();
	}

	private void initExplosionProofCapability() {
		if (itemCapability.isPresent())
			return;

		if (!isController()) {
			ItemVaultBlockEntity controllerBE = getControllerBE();
			if (!(controllerBE instanceof ExplosionProofItemVaultBlockEntity explosionProofController))
				return;

			explosionProofController.initExplosionProofCapability();
			itemCapability = explosionProofController.itemCapability;
			invId = explosionProofController.invId;
			return;
		}

		boolean alongZ = ExplosionProofItemVaultBlock.getVaultBlockAxis(getBlockState()) == Axis.Z;
		IItemHandlerModifiable[] invs = new IItemHandlerModifiable[length * radius * radius];
		for (int yOffset = 0; yOffset < length; yOffset++) {
			for (int xOffset = 0; xOffset < radius; xOffset++) {
				for (int zOffset = 0; zOffset < radius; zOffset++) {
					BlockPos vaultPos = alongZ ? worldPosition.offset(xOffset, zOffset, yOffset)
						: worldPosition.offset(yOffset, xOffset, zOffset);
					ItemVaultBlockEntity vaultAt =
						ConnectivityHandler.partAt(CBBlockEntityTypes.EXPLOSION_PROOF_ITEM_VAULT.get(), level, vaultPos);
					invs[yOffset * radius * radius + xOffset * radius + zOffset] =
						vaultAt != null ? vaultAt.getInventoryOfBlock() : new ItemStackHandler();
				}
			}
		}

		IItemHandler itemHandler = new VersionedInventoryWrapper(SameSizeCombinedInvWrapper.create(invs));
		itemCapability = LazyOptional.of(() -> itemHandler);

		BlockPos farCorner = alongZ ? worldPosition.offset(radius, radius, length)
			: worldPosition.offset(length, radius, radius);
		invId = new InventoryIdentifier.Bounds(net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(
			worldPosition, farCorner));
	}
}
