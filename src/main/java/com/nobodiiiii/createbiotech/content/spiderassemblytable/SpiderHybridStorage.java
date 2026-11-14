package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** SpiderHybridStorage algorithms over the canonical SpiderAssemblyTableBlockEntity state. */
final class SpiderHybridStorage {
	private SpiderHybridStorage() {}


	static boolean isHybridSlotItemOnly(SpiderAssemblyTableBlockEntity operationOwner, int hybridIndex) {
		return operationOwner.fluidTanks[hybridIndex].getFluid().isEmpty();
	}

	static boolean isHybridSlotFluidOnly(SpiderAssemblyTableBlockEntity operationOwner, int hybridIndex) {
		return !operationOwner.fluidTanks[hybridIndex].getFluid().isEmpty();
	}

	static ItemStack getItemLock(SpiderAssemblyTableBlockEntity operationOwner, int hybridIndex) {
		return operationOwner.itemLocks[hybridIndex];
	}

	static FluidStack getFluidLock(SpiderAssemblyTableBlockEntity operationOwner, int hybridIndex) {
		return operationOwner.fluidLocks[hybridIndex];
	}

	static boolean isHybridSlotLocked(SpiderAssemblyTableBlockEntity operationOwner, int hybridIndex) {
		return !operationOwner.itemLocks[hybridIndex].isEmpty() || !operationOwner.fluidLocks[hybridIndex].isEmpty() || operationOwner.slotBlocked[hybridIndex];
	}

	static boolean areAllHybridSlotsLocked(SpiderAssemblyTableBlockEntity operationOwner) {
		for (int i = 0; i < SpiderAssemblyTableBlockEntity.LEG_COUNT; i++)
			if (!SpiderHybridStorage.isHybridSlotLocked(operationOwner, i))
				return false;
		return true;
	}

	static boolean isHybridSlotBlocked(SpiderAssemblyTableBlockEntity operationOwner, int hybridIndex) {
		return operationOwner.slotBlocked[hybridIndex];
	}

	static boolean canHybridSlotAcceptItem(SpiderAssemblyTableBlockEntity operationOwner, int hybridIndex, ItemStack stack) {
		if (hybridIndex < 0 || hybridIndex >= SpiderAssemblyTableBlockEntity.LEG_COUNT)
			return false;
		if (stack.isEmpty())
			return true;
		if (operationOwner.slotBlocked[hybridIndex])
			return false;
		if (!operationOwner.fluidTanks[hybridIndex].getFluid().isEmpty())
			return false;
		if (!operationOwner.fluidLocks[hybridIndex].isEmpty())
			return false;
		ItemStack lock = operationOwner.itemLocks[hybridIndex];
		if (!lock.isEmpty() && !ItemStack.isSameItemSameComponents(lock, stack))
			return false;
		return true;
	}

	static boolean canHybridTankAcceptFluid(SpiderAssemblyTableBlockEntity operationOwner, int hybridIndex, FluidStack stack) {
		if (hybridIndex < 0 || hybridIndex >= SpiderAssemblyTableBlockEntity.LEG_COUNT)
			return false;
		if (stack.isEmpty())
			return true;
		if (operationOwner.slotBlocked[hybridIndex])
			return false;
		if (!operationOwner.inventory.getStackInSlot(SpiderAssemblyTableBlockEntity.HYBRID_SLOT_START + hybridIndex).isEmpty())
			return false;
		if (!operationOwner.itemLocks[hybridIndex].isEmpty())
			return false;
		FluidStack lock = operationOwner.fluidLocks[hybridIndex];
		if (!lock.isEmpty() && !FluidStack.isSameFluidSameComponents(lock, stack))
			return false;
		return operationOwner.fluidTanks[hybridIndex].isFluidValid(stack);
	}

	static void handleLockButton(SpiderAssemblyTableBlockEntity operationOwner, int hybridIndex, ItemStack carried) {
		if (hybridIndex < 0 || hybridIndex >= SpiderAssemblyTableBlockEntity.LEG_COUNT)
			return;

		if (!carried.isEmpty()) {
			IFluidHandlerItem fluidHandler = carried.getCapability(Capabilities.FluidHandler.ITEM);
			FluidStack containerFluid = fluidHandler != null && fluidHandler.getTanks() > 0
				? fluidHandler.getFluidInTank(0)
				: FluidStack.EMPTY;
			if (!containerFluid.isEmpty()) {
				if (!operationOwner.inventory.getStackInSlot(SpiderAssemblyTableBlockEntity.HYBRID_SLOT_START + hybridIndex).isEmpty())
					return;
				FluidStack currentFluid = operationOwner.fluidTanks[hybridIndex].getFluid();
				if (!currentFluid.isEmpty() && !FluidStack.isSameFluidSameComponents(currentFluid, containerFluid))
					return;
				FluidStack lock = containerFluid.copy();
				lock.setAmount(1);
				operationOwner.fluidLocks[hybridIndex] = lock;
				operationOwner.itemLocks[hybridIndex] = ItemStack.EMPTY;
				operationOwner.slotBlocked[hybridIndex] = false;
			} else {
				if (!operationOwner.fluidTanks[hybridIndex].getFluid().isEmpty())
					return;
				ItemStack slotItem = operationOwner.inventory.getStackInSlot(SpiderAssemblyTableBlockEntity.HYBRID_SLOT_START + hybridIndex);
				if (!slotItem.isEmpty() && !ItemStack.isSameItemSameComponents(slotItem, carried))
					return;
				ItemStack lock = carried.copy();
				lock.setCount(1);
				operationOwner.itemLocks[hybridIndex] = lock;
				operationOwner.fluidLocks[hybridIndex] = FluidStack.EMPTY;
				operationOwner.slotBlocked[hybridIndex] = false;
			}
		} else {
			if (SpiderHybridStorage.isHybridSlotLocked(operationOwner, hybridIndex)) {
				operationOwner.itemLocks[hybridIndex] = ItemStack.EMPTY;
				operationOwner.fluidLocks[hybridIndex] = FluidStack.EMPTY;
				operationOwner.slotBlocked[hybridIndex] = false;
			} else {
				ItemStack slotItem = operationOwner.inventory.getStackInSlot(SpiderAssemblyTableBlockEntity.HYBRID_SLOT_START + hybridIndex);
				FluidStack slotFluid = operationOwner.fluidTanks[hybridIndex].getFluid();
				if (!slotItem.isEmpty()) {
					ItemStack lock = slotItem.copy();
					lock.setCount(1);
					operationOwner.itemLocks[hybridIndex] = lock;
				} else if (!slotFluid.isEmpty()) {
					FluidStack lock = slotFluid.copy();
					lock.setAmount(1);
					operationOwner.fluidLocks[hybridIndex] = lock;
				} else {
					operationOwner.slotBlocked[hybridIndex] = true;
				}
			}
		}

		operationOwner.setChanged();
		operationOwner.sendData();
	}

	static void handleLockAllButton(SpiderAssemblyTableBlockEntity operationOwner) {
		boolean anyUnlocked = false;
		for (int i = 0; i < SpiderAssemblyTableBlockEntity.LEG_COUNT; i++) {
			if (!SpiderHybridStorage.isHybridSlotLocked(operationOwner, i)) {
				anyUnlocked = true;
				break;
			}
		}

		if (anyUnlocked) {
			for (int i = 0; i < SpiderAssemblyTableBlockEntity.LEG_COUNT; i++) {
				if (SpiderHybridStorage.isHybridSlotLocked(operationOwner, i))
					continue;
				ItemStack slotItem = operationOwner.inventory.getStackInSlot(SpiderAssemblyTableBlockEntity.HYBRID_SLOT_START + i);
				FluidStack slotFluid = operationOwner.fluidTanks[i].getFluid();
				if (!slotItem.isEmpty()) {
					ItemStack lock = slotItem.copy();
					lock.setCount(1);
					operationOwner.itemLocks[i] = lock;
				} else if (!slotFluid.isEmpty()) {
					FluidStack lock = slotFluid.copy();
					lock.setAmount(1);
					operationOwner.fluidLocks[i] = lock;
				} else {
					operationOwner.slotBlocked[i] = true;
				}
			}
		} else {
			for (int i = 0; i < SpiderAssemblyTableBlockEntity.LEG_COUNT; i++) {
				operationOwner.itemLocks[i] = ItemStack.EMPTY;
				operationOwner.fluidLocks[i] = FluidStack.EMPTY;
				operationOwner.slotBlocked[i] = false;
			}
		}

		operationOwner.setChanged();
		operationOwner.sendData();
	}

	static int[] computeItemInsertOrder(SpiderAssemblyTableBlockEntity operationOwner) {
		int[] order = new int[SpiderAssemblyTableBlockEntity.LEG_COUNT];
		int idx = 0;
		for (int i = 0; i < SpiderAssemblyTableBlockEntity.LEG_COUNT; i++)
			if (operationOwner.getMachineKind(i) == SpiderAssemblyTableBlockEntity.MachineKind.DEPLOYER)
				order[idx++] = i;
		for (int i = 0; i < SpiderAssemblyTableBlockEntity.LEG_COUNT; i++)
			if (operationOwner.getMachineKind(i) != SpiderAssemblyTableBlockEntity.MachineKind.DEPLOYER)
				order[idx++] = i;
		return order;
	}

	static int[] computeItemExtractOrder(SpiderAssemblyTableBlockEntity operationOwner) {
		int[] order = new int[SpiderAssemblyTableBlockEntity.LEG_COUNT];
		int idx = 0;
		for (int i = 0; i < SpiderAssemblyTableBlockEntity.LEG_COUNT; i++)
			if (operationOwner.getMachineKind(i) != SpiderAssemblyTableBlockEntity.MachineKind.DEPLOYER)
				order[idx++] = i;
		for (int i = 0; i < SpiderAssemblyTableBlockEntity.LEG_COUNT; i++)
			if (operationOwner.getMachineKind(i) == SpiderAssemblyTableBlockEntity.MachineKind.DEPLOYER)
				order[idx++] = i;
		return order;
	}

	static SpiderAssemblyTableBlockEntity.FluidExchangeResult exchangeFluidWithItem(SpiderAssemblyTableBlockEntity operationOwner, int hybridIndex, ItemStack singleItem) {
		if (hybridIndex < 0 || hybridIndex >= SpiderAssemblyTableBlockEntity.LEG_COUNT)
			return SpiderAssemblyTableBlockEntity.FluidExchangeResult.failure();

		ItemStack slotItem = operationOwner.inventory.getStackInSlot(SpiderAssemblyTableBlockEntity.HYBRID_SLOT_START + hybridIndex);
		if (!slotItem.isEmpty())
			return SpiderAssemblyTableBlockEntity.FluidExchangeResult.failure();

		IFluidHandlerItem itemHandler = singleItem.getCapability(Capabilities.FluidHandler.ITEM);
		if (itemHandler == null)
			return SpiderAssemblyTableBlockEntity.FluidExchangeResult.failure();

		FluidTank tank = operationOwner.fluidTanks[hybridIndex];
		boolean changed = false;

		FluidStack itemFluid = itemHandler.getTanks() > 0 ? itemHandler.getFluidInTank(0).copy() : FluidStack.EMPTY;
		if (!itemFluid.isEmpty() && SpiderHybridStorage.canHybridTankAcceptFluid(operationOwner, hybridIndex, itemFluid)) {
			int canFill = tank.fill(itemFluid, FluidAction.SIMULATE);
			if (canFill > 0) {
				FluidStack drained = itemHandler.drain(canFill, FluidAction.EXECUTE);
				if (!drained.isEmpty()) {
					tank.fill(drained, FluidAction.EXECUTE);
					changed = true;
				}
			}
		}

		if (!changed && !tank.getFluid().isEmpty()) {
			FluidStack tankFluid = tank.getFluid().copy();
			int filled = itemHandler.fill(tankFluid, FluidAction.EXECUTE);
			if (filled > 0) {
				tank.drain(filled, FluidAction.EXECUTE);
				changed = true;
			}
		}

		if (!changed)
			return SpiderAssemblyTableBlockEntity.FluidExchangeResult.failure();

		operationOwner.setChanged();
		operationOwner.sendData();
		return SpiderAssemblyTableBlockEntity.FluidExchangeResult.success(itemHandler.getContainer());
	}

	static boolean canHoldItemInHybrid(SpiderAssemblyTableBlockEntity operationOwner, int hybridIndex) {
		return operationOwner.fluidTanks[hybridIndex].getFluid().isEmpty();
	}

	static void dropStackNearTable(SpiderAssemblyTableBlockEntity operationOwner, ItemStack stack) {
		if (operationOwner.getLevel() == null || stack.isEmpty())
			return;
		ItemEntity entity = new ItemEntity(operationOwner.getLevel(), operationOwner.getBlockPos().getX() + 0.5d, operationOwner.getBlockPos().getY() + 0.5d,
			operationOwner.getBlockPos().getZ() + 0.5d, stack.copy());
		entity.setDefaultPickUpDelay();
		operationOwner.getLevel().addFreshEntity(entity);
	}
}
