package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import static com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult.HOLD;
import static com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult.PASS;
import java.util.ArrayList;
import java.util.List;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.foundation.advancement.PlacedByPlayerAdvancementTracker;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/** SpiderProcessController algorithms over the canonical SpiderAssemblyTableBlockEntity state. */
final class SpiderProcessController {
	private SpiderProcessController() {}


	static void tickAssembly(SpiderAssemblyTableBlockEntity operationOwner) {
		if (operationOwner.activeSlot < 0)
			return;
		if (operationOwner.getSpeed() == 0)
			return;

		if (!operationOwner.processAppliedThisCycle && !operationOwner.cachedInput.isEmpty() && !SpiderProcessController.isCachedInputStillHeld(operationOwner))
			SpiderProcessController.invalidateCachedInput(operationOwner);

		operationOwner.processingTicksRemaining--;
		if (operationOwner.processingTicksRemaining > 0)
			return;

		SpiderProcessController.resetActiveProcess(operationOwner);
		operationOwner.setChanged();
		operationOwner.sendData();
	}

	static ProcessingResult onItemReceived(SpiderAssemblyTableBlockEntity operationOwner, TransportedItemStack transported,
		TransportedItemStackHandlerBehaviour handler) {
		if (handler.blockEntity.isVirtual())
			return PASS;
		if (operationOwner.getSpeed() == 0)
			return PASS;
		if (operationOwner.activeSlot >= 0)
			return HOLD;
		if (transported.stack.isEmpty())
			return PASS;
		if (SpiderProcessController.promotePreferredWorkpiece(operationOwner, transported, handler))
			return HOLD;
		if (SpiderProcessPlanner.startProcessFor(operationOwner, transported.stack))
			return HOLD;
		return SpiderProcessPlanner.noPlanResultFor(operationOwner, transported.stack);
	}

	static ProcessingResult whileItemHeld(SpiderAssemblyTableBlockEntity operationOwner, TransportedItemStack transported,
		TransportedItemStackHandlerBehaviour handler) {
		if (operationOwner.getSpeed() == 0)
			return PASS;

		if (operationOwner.activeSlot < 0) {
			if (transported.stack.isEmpty())
				return PASS;
			if (SpiderProcessController.promotePreferredWorkpiece(operationOwner, transported, handler))
				return HOLD;
			if (SpiderProcessPlanner.startProcessFor(operationOwner, transported.stack))
				return HOLD;
			return SpiderProcessPlanner.noPlanResultFor(operationOwner, transported.stack);
		}

		if (operationOwner.processAppliedThisCycle)
			return HOLD;

		if (!SpiderProcessController.matchesCachedInput(operationOwner, transported.stack)) {
			SpiderProcessController.invalidateCachedInput(operationOwner);
			return HOLD;
		}

		if (operationOwner.processingTicksRemaining > operationOwner.processingTicksTotal / 2)
			return HOLD;

		if (!SpiderProcessController.applyActiveProcessToTransported(operationOwner, transported, handler)) {
			SpiderProcessController.invalidateCachedInput(operationOwner);
			return HOLD;
		}

		return HOLD;
	}

	static boolean applyActiveProcessToTransported(SpiderAssemblyTableBlockEntity operationOwner, TransportedItemStack transported,
		TransportedItemStackHandlerBehaviour handler) {
		if (operationOwner.activeMachine == null || operationOwner.activeSlot < 0 || transported.stack.isEmpty())
			return false;

		ItemStack currentInput = transported.stack.copy();
		currentInput.setCount(1);
		if (SpiderProcessPlanner.createPlan(operationOwner, operationOwner.activeSlot, operationOwner.activeMachine, currentInput).isEmpty())
			return false;

		ItemStack impactItem = operationOwner.resolveImpactItem().copy();
		List<ItemStack> outputs = SpiderProcessController.applyActiveProcess(operationOwner, currentInput);
		List<TransportedItemStack> outList = new ArrayList<>();
		for (ItemStack out : outputs) {
			if (out.isEmpty())
				continue;
			TransportedItemStack copy = transported.copy();
			copy.stack = out;
			copy.locked = true;
			outList.add(copy);
		}

		ItemStack remaining = transported.stack.copy();
		remaining.shrink(1);
		TransportedItemStack held = null;
		if (!remaining.isEmpty()) {
			held = transported.copy();
			held.stack = remaining;
		}

		transported.clearFanProcessingData();

		if (outList.isEmpty() && held == null) {
			handler.handleProcessingOnItem(transported, TransportedResult.removeItem());
		} else if (held == null) {
			handler.handleProcessingOnItem(transported, TransportedResult.convertTo(outList));
		} else if (outList.isEmpty()) {
			handler.handleProcessingOnItem(transported, TransportedResult.convertTo(List.of(held)));
		} else {
			handler.handleProcessingOnItem(transported, TransportedResult.convertToAndLeaveHeld(outList, held));
		}

		if (operationOwner.containsCompletedSequencedAssembly(outputs))
			PlacedByPlayerAdvancementTracker.awardPlacedBy(operationOwner.getLevel(), operationOwner.advancementOwner, CBAdvancements.SPIDER_ASSEMBLY_TABLE);

		operationOwner.processAppliedThisCycle = true;
		operationOwner.triggerImpact(impactItem);
		operationOwner.setChanged();
		return true;
	}

	static void resetActiveProcess(SpiderAssemblyTableBlockEntity operationOwner) {
		operationOwner.activeSlot = -1;
		operationOwner.activeMachine = null;
		operationOwner.processingTicksRemaining = 0;
		operationOwner.processingTicksTotal = 0;
		operationOwner.processAppliedThisCycle = false;
		operationOwner.cachedInput = ItemStack.EMPTY;
	}

	static boolean isCachedInputStillHeld(SpiderAssemblyTableBlockEntity operationOwner) {
		if (operationOwner.getLevel() == null || operationOwner.cachedInput.isEmpty())
			return false;

		TransportedItemStackHandlerBehaviour handler =
			BlockEntityBehaviour.get(operationOwner.getLevel(), operationOwner.getBlockPos().below(2), TransportedItemStackHandlerBehaviour.TYPE);
		if (handler == null)
			return false;

		boolean[] found = { false };
		handler.handleCenteredProcessingOnAllItems(.51f, transported -> {
			if (SpiderProcessController.matchesCachedInput(operationOwner, transported.stack))
				found[0] = true;
			return TransportedResult.doNothing();
		});
		return found[0];
	}

	static boolean matchesCachedInput(SpiderAssemblyTableBlockEntity operationOwner, ItemStack input) {
		return !operationOwner.cachedInput.isEmpty()
			&& !input.isEmpty()
			&& ItemStack.isSameItemSameComponents(operationOwner.cachedInput, input);
	}

	static void invalidateCachedInput(SpiderAssemblyTableBlockEntity operationOwner) {
		if (operationOwner.cachedInput.isEmpty())
			return;
		operationOwner.cachedInput = ItemStack.EMPTY;
		operationOwner.setChanged();
		operationOwner.sendData();
	}

	static boolean promotePreferredWorkpiece(SpiderAssemblyTableBlockEntity operationOwner, TransportedItemStack transported,
		TransportedItemStackHandlerBehaviour handler) {
		if (operationOwner.getLevel() == null || transported.stack.isEmpty())
			return false;

		BlockPos depotPos = operationOwner.getBlockPos().below(2);
		if (!(operationOwner.getLevel().getBlockEntity(depotPos) instanceof DepotBlockEntity))
			return false;

		IItemHandler depotInventory = operationOwner.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, depotPos, null);
		if (depotInventory == null || depotInventory.getSlots() < 2)
			return false;

		float bestPriority = SpiderProcessPlanner.getWorkpiecePriority(operationOwner, transported.stack);
		int bestSlot = -1;
		for (int slot = 1; slot < depotInventory.getSlots(); slot++) {
			ItemStack candidate = depotInventory.getStackInSlot(slot);
			float priority = SpiderProcessPlanner.getWorkpiecePriority(operationOwner, candidate);
			if (priority <= bestPriority)
				continue;
			bestPriority = priority;
			bestSlot = slot;
		}
		if (bestSlot < 0)
			return false;

		ItemStack candidate = depotInventory.getStackInSlot(bestSlot).copy();
		ItemStack simulated = depotInventory.extractItem(bestSlot, candidate.getCount(), true);
		if (!ItemStack.matches(candidate, simulated))
			return false;
		ItemStack extracted = depotInventory.extractItem(bestSlot, candidate.getCount(), false);
		if (extracted.isEmpty())
			return false;

		TransportedItemStack displaced = transported.copy();
		displaced.clearFanProcessingData();
		TransportedItemStack preferred = transported.copy();
		preferred.stack = extracted;
		preferred.locked = true;
		preferred.clearFanProcessingData();
		handler.handleProcessingOnItem(transported,
			TransportedResult.convertToAndLeaveHeld(List.of(displaced), preferred));
		return true;
	}

	static List<ItemStack> applyActiveProcess(SpiderAssemblyTableBlockEntity operationOwner, ItemStack input) {
		return operationOwner.applyActiveProcess(input);
	}

	static void consumeDeployingItem(SpiderAssemblyTableBlockEntity operationOwner, int slot, DeployerApplicationRecipe recipe) {
		if (recipe.shouldKeepHeldItem())
			return;

		int hybridSlot = SpiderAssemblyTableBlockEntity.HYBRID_SLOT_START + slot;
		ItemStack held = operationOwner.inventory.getStackInSlot(hybridSlot);
		if (held.isEmpty())
			return;

		ItemStack remainder = ItemStack.EMPTY;
		if (held.isDamageableItem()) {
			held.setDamageValue(held.getDamageValue() + 1);
			if (held.getDamageValue() >= held.getMaxDamage())
				held.shrink(1);
		} else {
			remainder = held.getCraftingRemainingItem();
			held.shrink(1);
		}

		boolean depleted = held.isEmpty();
		operationOwner.inventory.setStackInSlot(hybridSlot, held);
		if (!remainder.isEmpty())
			SpiderProcessController.insertIntoItemCacheOrDrop(operationOwner, slot, remainder);
		if (depleted && operationOwner.getLevel() != null)
			operationOwner.getLevel().playSound(null, operationOwner.getBlockPos(), SoundEvents.ITEM_BREAK,
				SoundSource.BLOCKS, 0.25f, 1);
	}

	static void insertIntoItemCacheOrDrop(SpiderAssemblyTableBlockEntity operationOwner, int preferredSlot, ItemStack stack) {
		if (stack.isEmpty())
			return;

		int slot = SpiderAssemblyTableBlockEntity.HYBRID_SLOT_START + preferredSlot;
		if (SpiderHybridStorage.canHoldItemInHybrid(operationOwner, preferredSlot)) {
			ItemStack existing = operationOwner.inventory.getStackInSlot(slot);
			if (existing.isEmpty()) {
				operationOwner.inventory.setStackInSlot(slot, stack);
				return;
			}
			if (ItemStack.isSameItemSameComponents(existing, stack)) {
				int moved = Math.min(existing.getMaxStackSize() - existing.getCount(), stack.getCount());
				existing.grow(moved);
				stack.shrink(moved);
				operationOwner.inventory.setStackInSlot(slot, existing);
				if (stack.isEmpty())
					return;
			}
		}

		for (int i = 0; i < SpiderAssemblyTableBlockEntity.LEG_COUNT; i++) {
			if (!SpiderHybridStorage.canHoldItemInHybrid(operationOwner, i))
				continue;
			int hybridSlot = SpiderAssemblyTableBlockEntity.HYBRID_SLOT_START + i;
			ItemStack remainder = operationOwner.inventory.insertItem(hybridSlot, stack, false);
			if (remainder.isEmpty())
				return;
			stack = remainder;
		}

		SpiderHybridStorage.dropStackNearTable(operationOwner, stack);
	}
}
