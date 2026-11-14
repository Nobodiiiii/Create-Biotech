package com.nobodiiiii.createbiotech.content.creeperblastchamber;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import com.nobodiiiii.createbiotech.content.explosionproofitemvault.ExplosionProofItemVaultBlockEntity;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.registry.CBRecipeTypes;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.foundation.item.ItemHelper;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

/** ChamberProcessing algorithms over the canonical CreeperBlastChamberBlockEntity state. */
final class ChamberProcessing {
	private ChamberProcessing() {}


	static void tickPressProcessing(CreeperBlastChamberBlockEntity operationOwner) {
		if (!operationOwner.structureValid || operationOwner.structureOrigin == null || operationOwner.inputVaultController == null || operationOwner.outputVaultController == null) {
			operationOwner.pressCycleProcessed = false;
			return;
		}

		List<MechanicalPressBlockEntity> presses = ChamberProcessing.getMechanicalPresses(operationOwner);
		if (presses.isEmpty()) {
			operationOwner.pressCycleProcessed = false;
			return;
		}

		if (ChamberProcessing.hasUnworkablePresses(operationOwner, presses)) {
			ChamberProcessing.resetPressProgress(operationOwner, presses);
			return;
		}

		for (MechanicalPressBlockEntity press : presses) {
			if (press.getSpeed() != 0 && !press.getPressingBehaviour().running)
				press.getPressingBehaviour().start(PressingBehaviour.Mode.WORLD);
		}

		MechanicalPressBlockEntity press = ChamberProcessing.getMasterPress(operationOwner, presses);
		if (press == null) {
			operationOwner.pressCycleProcessed = false;
			return;
		}

		PressingBehaviour pressingBehaviour = press.getPressingBehaviour();
		if (press.getSpeed() == 0) {
			if (!pressingBehaviour.running || pressingBehaviour.runningTicks < CreeperBlastChamberBlockEntity.PRESSING_TRIGGER_TICKS)
				operationOwner.pressCycleProcessed = false;
			return;
		}

		if (pressingBehaviour.runningTicks < CreeperBlastChamberBlockEntity.PRESSING_TRIGGER_TICKS) {
			operationOwner.pressCycleProcessed = false;
			return;
		}

		if (operationOwner.pressCycleProcessed)
			return;

		operationOwner.pressCycleProcessed = true;
		CreeperBlastChamberBlockEntity.CreeperCountSummary workableCreepers = ChamberContainment.summarizeWorkableMarkedCreepers(operationOwner);
		if (workableCreepers.totalCount() <= 0)
			return;

		if (ChamberProcessing.applyOverloadFromPress(operationOwner, press, workableCreepers))
			return;

		ChamberProcessing.processMarkedCreepersCycle(operationOwner, workableCreepers);
	}

	static boolean hasUnworkablePresses(CreeperBlastChamberBlockEntity operationOwner, List<MechanicalPressBlockEntity> presses) {
		boolean hasWorkingPress = false;
		boolean hasStoppedPress = false;
		for (MechanicalPressBlockEntity press : presses) {
			if (press.getSpeed() != 0)
				hasWorkingPress = true;
			else
				hasStoppedPress = true;
			if (hasWorkingPress && hasStoppedPress)
				return true;
		}
		return false;
	}

	static boolean applyOverloadFromPress(CreeperBlastChamberBlockEntity operationOwner, MechanicalPressBlockEntity press, CreeperBlastChamberBlockEntity.CreeperCountSummary creeperCounts) {
		if (!CreeperBlastChamberBlockEntity.isOverloadExplosionsEnabled()) {
			if (operationOwner.overloadPoints > 0)
				ChamberProcessing.setOverloadPoints(operationOwner, 0);
			return false;
		}

		if (operationOwner.overloadPoints >= CreeperBlastChamberBlockEntity.getOverloadPointsCap()) {
			ChamberProcessing.triggerOverload(operationOwner, creeperCounts);
			return true;
		}

		int rpm = Mth.floor(Math.abs(press.getSpeed()));
		int overloadGain = rpm - CreeperBlastChamberBlockEntity.getOverloadThresholdRpm();
		if (overloadGain <= 0)
			return false;

		int nextOverloadPoints = operationOwner.overloadPoints + overloadGain;
		if (nextOverloadPoints > CreeperBlastChamberBlockEntity.getOverloadPointsCap()) {
			ChamberProcessing.setOverloadPoints(operationOwner, CreeperBlastChamberBlockEntity.getOverloadPointsCap());
			return false;
		}

		ChamberProcessing.setOverloadPoints(operationOwner, nextOverloadPoints);
		return false;
	}

	static void triggerOverload(CreeperBlastChamberBlockEntity operationOwner, CreeperBlastChamberBlockEntity.CreeperCountSummary creeperCounts) {
		Level level = operationOwner.getLevel();
		if (level == null || level.isClientSide || !operationOwner.structureValid || operationOwner.structureOrigin == null)
			return;
		if (!CreeperBlastChamberBlockEntity.isOverloadExplosionsEnabled())
			return;

		ChamberProcessing.setOverloadPoints(operationOwner, 0);
		int effectiveExplosionCount = creeperCounts.getExplosionEquivalentCount();
		if (effectiveExplosionCount <= 0)
			return;

		ChamberProcessing.destroyChamberStructureForOverload(operationOwner, level);
		if (creeperCounts.chargedCount() > 0) {
			ChamberProcessing.triggerHighPressureOverload(operationOwner, level, effectiveExplosionCount);
			return;
		}

		float explosionPower = effectiveExplosionCount * CreeperBlastChamberBlockEntity.getTntExplosionPower();
		ChamberProcessing.explodeOverloadAt(operationOwner, level, ChamberProcessing.getOverloadExplosionCenter(operationOwner), explosionPower);
	}

	static void triggerHighPressureOverload(CreeperBlastChamberBlockEntity operationOwner, Level level, int effectiveExplosionCount) {
		if (operationOwner.structureOrigin == null)
			return;

		float explosionPower = effectiveExplosionCount * CreeperBlastChamberBlockEntity.getOverloadTntEquivalentPerCreeper() * CreeperBlastChamberBlockEntity.getTntExplosionPower();
		double[] xCoords = {operationOwner.structureOrigin.getX() + 0.5d, operationOwner.structureOrigin.getX() + operationOwner.structureSize - 0.5d};
		double[] yCoords = {operationOwner.structureOrigin.getY() + 0.5d, operationOwner.structureOrigin.getY() + 3.5d};
		double[] zCoords = {operationOwner.structureOrigin.getZ() + 0.5d, operationOwner.structureOrigin.getZ() + operationOwner.structureSize - 0.5d};

		for (double x : xCoords)
			for (double y : yCoords)
				for (double z : zCoords)
					level.explode(null, x, y, z, explosionPower, Level.ExplosionInteraction.TNT);

		ChamberProcessing.explodeOverloadAt(operationOwner, level, ChamberProcessing.getOverloadExplosionCenter(operationOwner), explosionPower);
	}

	static Vec3 getOverloadExplosionCenter(CreeperBlastChamberBlockEntity operationOwner) {
		return Vec3.atCenterOf(operationOwner.structureOrigin.offset(operationOwner.structureSize / 2, 1, operationOwner.structureSize / 2));
	}

	static void awardSafeBlastAdvancement(CreeperBlastChamberBlockEntity operationOwner) {
		if (operationOwner.getLevel() == null || operationOwner.structureOrigin == null)
			return;
		CBAdvancements.awardNearby(operationOwner.getLevel(), ChamberProcessing.getOverloadExplosionCenter(operationOwner), 32, CBAdvancements.CREEPER_BLAST_CHAMBER);
	}

	static void explodeOverloadAt(CreeperBlastChamberBlockEntity operationOwner, Level level, Vec3 position, float explosionPower) {
		CBAdvancements.awardNearby(level, position, 32, CBAdvancements.CREEPER_BLAST_CHAMBER_OVERLOAD);
		level.explode(null, position.x, position.y, position.z, explosionPower, Level.ExplosionInteraction.TNT);
	}

	static void destroyChamberStructureForOverload(CreeperBlastChamberBlockEntity operationOwner, Level level) {
		if (!operationOwner.structureValid || operationOwner.structureOrigin == null)
			return;

		List<BlockPos> positionsToClear = new ArrayList<>(operationOwner.structureSize * operationOwner.structureSize * 4);
		for (int x = 0; x < operationOwner.structureSize; x++) {
			for (int y = 0; y <= 3; y++) {
				for (int z = 0; z < operationOwner.structureSize; z++)
					positionsToClear.add(operationOwner.structureOrigin.offset(x, y, z));
			}
		}

		for (BlockPos pos : positionsToClear) {
			if (!level.getBlockState(pos).isAir())
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		}
	}

	static void setOverloadPoints(CreeperBlastChamberBlockEntity operationOwner, int overloadPoints) {
		int clampedOverloadPoints = Mth.clamp(overloadPoints, 0, CreeperBlastChamberBlockEntity.getOverloadPointsCap());
		if (operationOwner.overloadPoints == clampedOverloadPoints)
			return;
		operationOwner.overloadPoints = clampedOverloadPoints;
		operationOwner.setChanged();
		if (operationOwner.getLevel() != null && !operationOwner.getLevel().isClientSide)
			operationOwner.notifyUpdate();
	}

	static void processMarkedCreepersCycle(CreeperBlastChamberBlockEntity operationOwner, CreeperBlastChamberBlockEntity.CreeperCountSummary creeperCounts) {
		CreeperBlastChamberBlockEntity.ChamberCreeperKind chamberKind = ChamberContainment.getHeldCreeperKind(operationOwner);
		boolean highPressure = chamberKind == CreeperBlastChamberBlockEntity.ChamberCreeperKind.CHARGED || chamberKind == CreeperBlastChamberBlockEntity.ChamberCreeperKind.MIXED;
		int operations = highPressure ? creeperCounts.chargedCount() : creeperCounts.totalCount();
		if (operations <= 0)
			return;

		IItemHandler inputHandler = ChamberProcessing.getVaultItemHandler(operationOwner, operationOwner.inputVaultController);
		IItemHandler outputHandler = ChamberProcessing.getVaultItemHandler(operationOwner, operationOwner.outputVaultController);
		if (inputHandler == null || outputHandler == null)
			return;

		int processedOperations = 0;
		for (int i = 0; i < operations; i++) {
			CreeperBlastChamberBlockEntity.ProcessingAttemptResult result = highPressure
				? ChamberProcessing.processNextHighPressureVaultItem(operationOwner, inputHandler, outputHandler)
				: ChamberProcessing.processNextVaultStack(operationOwner, inputHandler, outputHandler);
			if (result == CreeperBlastChamberBlockEntity.ProcessingAttemptResult.PROCESSED) {
				processedOperations++;
				continue;
			}
			break;
		}
		if (processedOperations > 0)
			ChamberProcessing.awardSafeBlastAdvancement(operationOwner);
	}

	static void tickOverloadDecay(CreeperBlastChamberBlockEntity operationOwner, Level level) {
		if (operationOwner.overloadPoints <= 0 || ChamberProcessing.isCurrentlyOverloading(operationOwner) || level.getGameTime() % 20 != 0)
			return;
		ChamberProcessing.setOverloadPoints(operationOwner, operationOwner.overloadPoints - CreeperBlastChamberBlockEntity.getOverloadDecayPointsPerSecond());
	}

	static boolean isCurrentlyOverloading(CreeperBlastChamberBlockEntity operationOwner) {
		if (!operationOwner.structureValid || operationOwner.structureOrigin == null)
			return false;

		MechanicalPressBlockEntity masterPress = ChamberProcessing.getMasterPress(operationOwner, ChamberProcessing.getMechanicalPresses(operationOwner));
		return masterPress != null && Math.abs(masterPress.getSpeed()) > CreeperBlastChamberBlockEntity.getOverloadThresholdRpm();
	}

	static CreeperBlastChamberBlockEntity.ProcessingAttemptResult processNextVaultStack(CreeperBlastChamberBlockEntity operationOwner, IItemHandler inputHandler, IItemHandler outputHandler) {
		for (int slot = 0; slot < inputHandler.getSlots(); slot++) {
			ItemStack stackInSlot = inputHandler.getStackInSlot(slot);
			if (stackInSlot.isEmpty())
				continue;
			return ChamberProcessing.processVaultStack(operationOwner, inputHandler, outputHandler, slot, stackInSlot.copy());
		}
		return CreeperBlastChamberBlockEntity.ProcessingAttemptResult.NO_INPUT;
	}

	static CreeperBlastChamberBlockEntity.ProcessingAttemptResult processNextHighPressureVaultItem(CreeperBlastChamberBlockEntity operationOwner, IItemHandler inputHandler, IItemHandler outputHandler) {
		for (int slot = 0; slot < inputHandler.getSlots(); slot++) {
			ItemStack stackInSlot = inputHandler.getStackInSlot(slot);
			if (stackInSlot.isEmpty())
				continue;
			return ChamberProcessing.processHighPressureVaultItem(operationOwner, inputHandler, outputHandler, slot, stackInSlot.copy());
		}
		return CreeperBlastChamberBlockEntity.ProcessingAttemptResult.NO_INPUT;
	}

	static CreeperBlastChamberBlockEntity.ProcessingAttemptResult processVaultStack(CreeperBlastChamberBlockEntity operationOwner, IItemHandler inputHandler, IItemHandler outputHandler, int slot,
		ItemStack stackToProcess) {
		Optional<ProcessingRecipe<RecipeWrapper, ?>> recipe = ChamberProcessing.findCrushingRecipe(operationOwner, stackToProcess);
		if (recipe.isEmpty()) {
			inputHandler.extractItem(slot, stackToProcess.getCount(), false);
			return CreeperBlastChamberBlockEntity.ProcessingAttemptResult.PROCESSED;
		}

		List<ItemStack> outputs = ChamberProcessing.rollProcessingResults(operationOwner, stackToProcess, recipe.get());
		if (!ChamberProcessing.canFullyInsertAll(operationOwner, outputHandler, outputs))
			return CreeperBlastChamberBlockEntity.ProcessingAttemptResult.BLOCKED_OUTPUT;

		ItemStack extracted = inputHandler.extractItem(slot, stackToProcess.getCount(), false);
		if (extracted.getCount() != stackToProcess.getCount())
			return CreeperBlastChamberBlockEntity.ProcessingAttemptResult.NO_INPUT;

		ChamberProcessing.insertAllOutputs(operationOwner, outputHandler, outputs);
		return CreeperBlastChamberBlockEntity.ProcessingAttemptResult.PROCESSED;
	}

	static CreeperBlastChamberBlockEntity.ProcessingAttemptResult processHighPressureVaultItem(CreeperBlastChamberBlockEntity operationOwner, IItemHandler inputHandler, IItemHandler outputHandler,
		int slot, ItemStack stackToProcess) {
		if (stackToProcess.isEmpty())
			return CreeperBlastChamberBlockEntity.ProcessingAttemptResult.NO_INPUT;

		ItemStack singleInput = stackToProcess.copy();
		singleInput.setCount(1);

		Optional<CreeperBlastChamberHighPressureRecipe> recipe = ChamberProcessing.findHighPressureRecipe(operationOwner, singleInput);
		List<ItemStack> outputs = recipe.map(CreeperBlastChamberHighPressureRecipe::rollResults)
			.orElse(List.of());
		if (!ChamberProcessing.canFullyInsertAll(operationOwner, outputHandler, outputs))
			return CreeperBlastChamberBlockEntity.ProcessingAttemptResult.BLOCKED_OUTPUT;

		ItemStack extracted = inputHandler.extractItem(slot, 1, false);
		if (extracted.getCount() != 1)
			return CreeperBlastChamberBlockEntity.ProcessingAttemptResult.NO_INPUT;

		ChamberProcessing.insertAllOutputs(operationOwner, outputHandler, outputs);
		return CreeperBlastChamberBlockEntity.ProcessingAttemptResult.PROCESSED;
	}

	static Optional<ProcessingRecipe<RecipeWrapper, ?>> findCrushingRecipe(CreeperBlastChamberBlockEntity operationOwner, ItemStack stack) {
		Level level = operationOwner.getLevel();
		if (level == null || stack.isEmpty())
			return Optional.empty();

		CreeperBlastChamberBlockEntity.CRUSHING_RECIPE_INVENTORY.setStackInSlot(0, stack);
		Optional<RecipeHolder<StandardProcessingRecipe<RecipeWrapper>>> recipe =
			AllRecipeTypes.CRUSHING.find(CreeperBlastChamberBlockEntity.CRUSHING_RECIPE_WRAPPER, level);
		if (recipe.isEmpty())
			recipe = AllRecipeTypes.MILLING.find(CreeperBlastChamberBlockEntity.CRUSHING_RECIPE_WRAPPER, level);
		return recipe.<ProcessingRecipe<RecipeWrapper, ?>>map(RecipeHolder::value);
	}

	static List<ItemStack> rollProcessingResults(CreeperBlastChamberBlockEntity operationOwner, ItemStack input, ProcessingRecipe<RecipeWrapper, ?> recipe) {
		List<ItemStack> outputs = new ArrayList<>();
		for (int roll = 0; roll < input.getCount(); roll++) {
			for (ItemStack rolledResult : recipe.rollResults(operationOwner.getLevel().random))
				ItemHelper.addToList(rolledResult, outputs);
		}
		if (input.hasCraftingRemainingItem())
			ItemHelper.addToList(input.getCraftingRemainingItem(), outputs);
		return outputs;
	}

	static Optional<CreeperBlastChamberHighPressureRecipe> findHighPressureRecipe(CreeperBlastChamberBlockEntity operationOwner, ItemStack stack) {
		Level level = operationOwner.getLevel();
		if (level == null || stack.isEmpty())
			return Optional.empty();

		CreeperBlastChamberBlockEntity.HIGH_PRESSURE_RECIPE_INVENTORY.setStackInSlot(0, stack);
		return level.getRecipeManager()
			.getRecipeFor(CBRecipeTypes.CREEPER_BLAST_CHAMBER_HIGH_PRESSURE_TYPE.get(), CreeperBlastChamberBlockEntity.HIGH_PRESSURE_RECIPE_WRAPPER,
				level)
			.map(RecipeHolder::value);
	}

	static boolean canFullyInsertAll(CreeperBlastChamberBlockEntity operationOwner, IItemHandler outputHandler, List<ItemStack> outputs) {
		List<ItemStack> simulatedSlots = new ArrayList<>(outputHandler.getSlots());
		for (int slot = 0; slot < outputHandler.getSlots(); slot++)
			simulatedSlots.add(outputHandler.getStackInSlot(slot).copy());

		for (ItemStack output : outputs) {
			ItemStack remainder = output.copy();
			for (int slot = 0; slot < simulatedSlots.size() && !remainder.isEmpty(); slot++) {
				ItemStack simulatedStack = simulatedSlots.get(slot);
				int slotLimit = Math.min(outputHandler.getSlotLimit(slot), remainder.getMaxStackSize());

				if (simulatedStack.isEmpty()) {
					int inserted = Math.min(slotLimit, remainder.getCount());
					if (inserted <= 0)
						continue;
					ItemStack insertedStack = remainder.copy();
					insertedStack.setCount(inserted);
					simulatedSlots.set(slot, insertedStack);
					remainder.shrink(inserted);
					continue;
				}

				if (!ItemStack.isSameItemSameComponents(simulatedStack, remainder))
					continue;

				int space = Math.min(slotLimit, simulatedStack.getMaxStackSize()) - simulatedStack.getCount();
				if (space <= 0)
					continue;

				int inserted = Math.min(space, remainder.getCount());
				simulatedStack.grow(inserted);
				remainder.shrink(inserted);
			}

			if (!remainder.isEmpty())
				return false;
		}

		return true;
	}

	static void insertAllOutputs(CreeperBlastChamberBlockEntity operationOwner, IItemHandler outputHandler, List<ItemStack> outputs) {
		for (ItemStack output : outputs) {
			ItemStack remainder = output.copy();
			for (int slot = 0; slot < outputHandler.getSlots() && !remainder.isEmpty(); slot++)
				remainder = outputHandler.insertItem(slot, remainder, false);
		}
	}

	static @Nullable
	IItemHandler getVaultItemHandler(CreeperBlastChamberBlockEntity operationOwner, @Nullable BlockPos controllerPos) {
		if (controllerPos == null || operationOwner.getLevel() == null)
			return null;
		BlockEntity blockEntity = operationOwner.getLevel().getBlockEntity(controllerPos);
		if (!(blockEntity instanceof ExplosionProofItemVaultBlockEntity vault))
			return null;
		return vault.getItemCapability(null);
	}

	static @Nullable
	MechanicalPressBlockEntity getMasterPress(CreeperBlastChamberBlockEntity operationOwner, List<MechanicalPressBlockEntity> presses) {
		if (presses.isEmpty())
			return null;
		MechanicalPressBlockEntity centerPress = ChamberProcessing.getMechanicalPress(operationOwner, operationOwner.structureOrigin.offset(operationOwner.structureSize / 2, 3,
			operationOwner.structureSize / 2));
		if (centerPress != null && centerPress.getSpeed() != 0)
			return centerPress;
		for (MechanicalPressBlockEntity press : presses)
			if (press.getSpeed() != 0)
				return press;
		if (centerPress != null)
			return centerPress;
		for (MechanicalPressBlockEntity press : presses)
			if (press.getPressingBehaviour().running)
				return press;
		return presses.get(0);
	}

	static MechanicalPressBlockEntity getMechanicalPress(CreeperBlastChamberBlockEntity operationOwner, BlockPos pressPos) {
		if (operationOwner.getLevel() == null)
			return null;
		BlockEntity blockEntity = operationOwner.getLevel().getBlockEntity(pressPos);
		return blockEntity instanceof MechanicalPressBlockEntity press ? press : null;
	}

	static List<MechanicalPressBlockEntity> getMechanicalPresses(CreeperBlastChamberBlockEntity operationOwner) {
		operationOwner.resolvedMechanicalPresses.clear();
		if (!operationOwner.structureValid)
			return operationOwner.resolvedMechanicalPresses;

		for (BlockPos pressPos : operationOwner.cachedPressPositions) {
			MechanicalPressBlockEntity press = ChamberProcessing.getMechanicalPress(operationOwner, pressPos);
			if (press != null)
				operationOwner.resolvedMechanicalPresses.add(press);
		}
		return operationOwner.resolvedMechanicalPresses;
	}

	static float getRenderedPressHeadOffset(CreeperBlastChamberBlockEntity operationOwner, BlockPos packagerPos, float partialTicks) {
		BlockPos pressPos = operationOwner.cachedPressByPackager.get(packagerPos.asLong());
		MechanicalPressBlockEntity press = pressPos == null ? null : ChamberProcessing.getMechanicalPress(operationOwner, pressPos);
		return ChamberClientEffects.getSynchronizedPressHeadOffset(press, partialTicks);
	}

	/**
	 * Resolves the press whose cycle every animation in this chamber follows, memoized for the current
	 * tick. Which press is the master depends only on block state and speed, so it is stable within a
	 * tick while the head progress derived from it still interpolates per frame.
	 */
	static void resolveRenderPressState(CreeperBlastChamberBlockEntity operationOwner) {
		int tick = AnimationTickHolder.getTicks();
		if (operationOwner.renderPressStateTick == tick && (operationOwner.renderMasterPress == null || !operationOwner.renderMasterPress.isRemoved()))
			return;

		operationOwner.renderPressStateTick = tick;
		operationOwner.renderMasterPress = null;
		operationOwner.renderPressesUnworkable = false;
		if (!operationOwner.structureValid || operationOwner.structureOrigin == null)
			return;
		List<MechanicalPressBlockEntity> presses = ChamberProcessing.getMechanicalPresses(operationOwner);
		if (presses.isEmpty())
			return;
		operationOwner.renderPressesUnworkable = ChamberProcessing.hasUnworkablePresses(operationOwner, presses);
		operationOwner.renderMasterPress = ChamberProcessing.getMasterPress(operationOwner, presses);
	}

	static float getRenderedCreeperEffectPressOffset(CreeperBlastChamberBlockEntity operationOwner, BlockPos packagerPos, float partialTicks) {
		ChamberProcessing.resolveRenderPressState(operationOwner);
		if (operationOwner.renderMasterPress == null || operationOwner.renderMasterPress.getSpeed() == 0)
			return 0f;
		if (!operationOwner.renderPressesUnworkable)
			return ChamberClientEffects.getLocalPressHeadProgress(operationOwner.renderMasterPress.getPressingBehaviour(), partialTicks)
				* PressingBehaviour.Mode.WORLD.headOffset;

		// Only the uncommon mixed-speed fallback needs to resolve the press above this particular slot.
		BlockPos pressPos = operationOwner.cachedPressByPackager.get(packagerPos.asLong());
		MechanicalPressBlockEntity press = pressPos == null ? null : ChamberProcessing.getMechanicalPress(operationOwner, pressPos);
		if (press == null)
			return 0f;
		PressingBehaviour ownBehaviour = press.getPressingBehaviour();
		if (ownBehaviour.mode == null)
			return 0f;
		return ChamberClientEffects.getLocalPressHeadProgress(ownBehaviour, partialTicks) * ownBehaviour.mode.headOffset;
	}

	static float getWorkingCreeperCompression(CreeperBlastChamberBlockEntity operationOwner, BlockPos packagerPos, float partialTicks) {
		return ChamberClientEffects.getCompressionFromPressOffset(ChamberProcessing.getRenderedCreeperEffectPressOffset(operationOwner, packagerPos, partialTicks));
	}

	static void resetPressProgress(CreeperBlastChamberBlockEntity operationOwner, List<MechanicalPressBlockEntity> presses) {
		for (MechanicalPressBlockEntity press : presses) {
			PressingBehaviour pressingBehaviour = press.getPressingBehaviour();
			boolean changed = pressingBehaviour.running || pressingBehaviour.finished || pressingBehaviour.prevRunningTicks != 0
				|| pressingBehaviour.runningTicks != 0 || !pressingBehaviour.particleItems.isEmpty();
			if (!changed)
				continue;
			pressingBehaviour.running = false;
			pressingBehaviour.finished = false;
			pressingBehaviour.prevRunningTicks = 0;
			pressingBehaviour.runningTicks = 0;
			pressingBehaviour.particleItems.clear();
			press.setChanged();
			press.sendData();
		}
		operationOwner.pressCycleProcessed = false;
	}
}
