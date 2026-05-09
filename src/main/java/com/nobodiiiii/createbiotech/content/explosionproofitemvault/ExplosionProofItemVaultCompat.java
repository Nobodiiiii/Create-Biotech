package com.nobodiiiii.createbiotech.content.explosionproofitemvault;

import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.AllMountedStorageTypes;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType;
import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntity;

public class ExplosionProofItemVaultCompat {

	private ExplosionProofItemVaultCompat() {}

	public static void register() {
		MountedItemStorageType.REGISTRY.register(CBBlocks.EXPLOSION_PROOF_ITEM_VAULT.get(), AllMountedStorageTypes.VAULT.get());
		InventoryIdentifier.REGISTRY.register(CBBlocks.EXPLOSION_PROOF_ITEM_VAULT.get(), (level, state, face) -> {
			BlockEntity blockEntity = level.getBlockEntity(face.getPos());
			return blockEntity instanceof ItemVaultBlockEntity vault ? vault.getInvId() : null;
		});
	}
}
