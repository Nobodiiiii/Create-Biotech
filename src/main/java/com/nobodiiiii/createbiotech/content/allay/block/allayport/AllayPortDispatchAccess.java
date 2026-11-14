package com.nobodiiiii.createbiotech.content.allay.block.allayport;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import com.nobodiiiii.createbiotech.content.allay.logistics.address.AllayAddressRules;
import com.nobodiiiii.createbiotech.content.allay.logistics.courier.AllayCourierDispatchService;
import com.nobodiiiii.createbiotech.content.allay.logistics.courier.AllayCourierTask;
import com.nobodiiiii.createbiotech.content.allay.logistics.courier.AllayCourierTaskManager;
import com.nobodiiiii.createbiotech.content.allay.logistics.courier.AllayCourierTarget;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

final class AllayPortDispatchAccess {

	private final AllayPortBlockEntity port;
	private final AllayPortInventory inventory;

	AllayPortDispatchAccess(AllayPortBlockEntity port, AllayPortInventory inventory) {
		this.port = port;
		this.inventory = inventory;
	}

	@Nullable IItemHandler getItemHandler(@Nullable Direction side) {
		if (side == null) {
			return inventory.combinedHandler();
		}
		return side.getAxis() == Direction.Axis.Y
			? port.getAutomationItemHandler()
			: inventory.carrierInventory();
	}

	boolean tryDispatch() {
		if (!(port.getLevel() instanceof ServerLevel serverLevel)) {
			return false;
		}
		if (!inventory.hasStoredCarrier()) {
			return false;
		}

		DispatchCandidate candidate = findDispatchCandidate(serverLevel);
		if (candidate == null) {
			return false;
		}
		ItemStack extractedPackage = port.inventory.extractItem(candidate.packageSlot(), 1, false);
		ItemStack extractedCarrier = inventory.extractOneCarrier(false);
		if (extractedPackage.isEmpty() || extractedCarrier.isEmpty()) {
			restoreFailedDispatch(candidate.packageSlot(), extractedPackage, extractedCarrier);
			return false;
		}
		if (!AllayCourierTaskManager.addTask(serverLevel.getServer(), candidate.task())) {
			restoreFailedDispatch(candidate.packageSlot(), extractedPackage, extractedCarrier);
			return false;
		}

		port.flap(false);
		port.markPortContentsChanged();
		return true;
	}

	private void restoreFailedDispatch(int packageSlot, ItemStack extractedPackage, ItemStack extractedCarrier) {
		if (!extractedPackage.isEmpty()) {
			ItemStack remainder = port.inventory.insertItem(packageSlot, extractedPackage, false);
			if (!remainder.isEmpty()) {
				port.drop(remainder);
			}
		}
		if (!extractedCarrier.isEmpty()) {
			inventory.returnCarrier(extractedCarrier);
		}
		port.markPortContentsChanged();
	}

	private @Nullable DispatchCandidate findDispatchCandidate(ServerLevel serverLevel) {
		String filterString = port.getFilterString();
		for (int slot = 0; slot < port.inventory.getSlots(); slot++) {
			ItemStack packageInSlot = port.inventory.getStackInSlot(slot);
			if (packageInSlot.isEmpty() || !PackageItem.isPackage(packageInSlot)) {
				continue;
			}
			if (filterString != null && !AllayAddressRules.isBlank(filterString)
				&& AllayAddressRules.matchesPackage(packageInSlot, filterString)) {
				continue;
			}
			AllayCourierTarget target = AllayCourierDispatchService.resolvePackageTarget(serverLevel, packageInSlot,
				port.getCourierSpawnPosition(), serverLevel.dimension(), port.getBlockPos());
			if (target == null) {
				continue;
			}

			ItemStack singlePackage = packageInSlot.copy();
			singlePackage.setCount(1);
			AllayCourierTask task;
			UUID sourceSubLevelId = SubLevelCompat.getSpaceId(serverLevel, port.getBlockPos());
			if (target instanceof AllayCourierTarget.AllayPortTarget allayPort) {
				task = AllayCourierTask.forPackageToAllayPort(
					UUID.randomUUID(), singlePackage, serverLevel, allayPort.dimension(), allayPort.pos(),
					allayPort.subLevelId(),
					port.getCourierSpawnPosition(), port.getCourierLaunchDirection(),
					serverLevel.dimension(), port.getBlockPos(), sourceSubLevelId, null, port.getReturnMode());
			} else if (target instanceof AllayCourierTarget.PlayerTarget player) {
				task = AllayCourierTask.forPackageToPlayer(
					UUID.randomUUID(), singlePackage, serverLevel, player.playerId(), player.dimension(),
					port.getCourierSpawnPosition(), port.getCourierLaunchDirection(),
					serverLevel.dimension(), port.getBlockPos(), sourceSubLevelId, null, port.getReturnMode());
			} else {
				continue;
			}

			return new DispatchCandidate(slot, port.prepareCourierDeparture(task));
		}
		return null;
	}

	private record DispatchCandidate(int packageSlot, AllayCourierTask task) {}
}
