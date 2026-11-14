package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import static com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult.HOLD;
import static com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult.PASS;
import java.util.List;
import java.util.Optional;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.recipe.RecipeApplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

/** SpiderProcessPlanner algorithms over the canonical SpiderAssemblyTableBlockEntity state. */
final class SpiderProcessPlanner {
	private SpiderProcessPlanner() {}


	static boolean startProcessFor(SpiderAssemblyTableBlockEntity operationOwner, ItemStack input) {
		if (input.isEmpty())
			return false;
		ItemStack singleInput = input.copy();
		singleInput.setCount(1);

		for (int attempt = 0; attempt < SpiderAssemblyTableBlockEntity.LEG_COUNT; attempt++) {
			int slot = (operationOwner.nextSlot + attempt) % SpiderAssemblyTableBlockEntity.LEG_COUNT;
			SpiderAssemblyTableBlockEntity.MachineKind kind = operationOwner.getMachineKind(slot);
			if (kind == null)
				continue;

			Optional<SpiderAssemblyTableBlockEntity.ProcessingPlan> plan = SpiderProcessPlanner.createPlan(operationOwner, slot, kind, singleInput);
			if (plan.isEmpty())
				continue;

			operationOwner.activeSlot = slot;
			operationOwner.activeMachine = kind;
			operationOwner.processingTicksTotal = Math.max(2, plan.get().duration());
			operationOwner.processingTicksRemaining = operationOwner.processingTicksTotal;
			operationOwner.processAppliedThisCycle = false;
			operationOwner.nextSlot = (slot + 1) % SpiderAssemblyTableBlockEntity.LEG_COUNT;
			operationOwner.cachedInput = singleInput;
			operationOwner.setChanged();
			operationOwner.sendData();
			return true;
		}
		return false;
	}

	/**
	 * Waits for missing ingredients only when a leg can accept the next step's
	 * required item or fluid. A partially filled tank keeps waiting for enough fluid.
	 */
	static ProcessingResult noPlanResultFor(SpiderAssemblyTableBlockEntity operationOwner, ItemStack input) {
		if (operationOwner.getLevel() == null || input.isEmpty())
			return PASS;

		if (!SequencedAssemblyRecipe.getRecipes(operationOwner.getLevel(), input, AllRecipeTypes.DEPLOYING.getType(),
			DeployerApplicationRecipe.class, recipe -> SpiderProcessPlanner.hasWaitingDeployerLeg(operationOwner, recipe.value())).isEmpty())
			return HOLD;
		if (!SequencedAssemblyRecipe.getRecipes(operationOwner.getLevel(), input, AllRecipeTypes.FILLING.getType(),
			FillingRecipe.class, recipe -> SpiderProcessPlanner.hasWaitingSpoutLeg(operationOwner, recipe.value())).isEmpty())
			return HOLD;
		return PASS;
	}

	static boolean hasWaitingDeployerLeg(SpiderAssemblyTableBlockEntity operationOwner, DeployerApplicationRecipe recipe) {
		for (int i = 0; i < SpiderAssemblyTableBlockEntity.LEG_COUNT; i++) {
			if (operationOwner.getMachineKind(i) != SpiderAssemblyTableBlockEntity.MachineKind.DEPLOYER || operationOwner.slotBlocked[i])
				continue;
			if (!operationOwner.fluidTanks[i].getFluid().isEmpty() || !operationOwner.fluidLocks[i].isEmpty())
				continue;
			if (!operationOwner.inventory.getStackInSlot(SpiderAssemblyTableBlockEntity.HYBRID_SLOT_START + i).isEmpty())
				continue;
			ItemStack lock = operationOwner.itemLocks[i];
			if (lock.isEmpty() || recipe.getRequiredHeldItem().test(lock))
				return true;
		}
		return false;
	}

	static boolean hasWaitingSpoutLeg(SpiderAssemblyTableBlockEntity operationOwner, FillingRecipe recipe) {
		SizedFluidIngredient requiredFluid = recipe.getRequiredFluid();
		for (int i = 0; i < SpiderAssemblyTableBlockEntity.LEG_COUNT; i++) {
			if (operationOwner.getMachineKind(i) != SpiderAssemblyTableBlockEntity.MachineKind.SPOUT || operationOwner.slotBlocked[i])
				continue;
			if (!operationOwner.inventory.getStackInSlot(SpiderAssemblyTableBlockEntity.HYBRID_SLOT_START + i).isEmpty() || !operationOwner.itemLocks[i].isEmpty())
				continue;
			if (requiredFluid.amount() > operationOwner.fluidTanks[i].getCapacity())
				continue;
			FluidStack lock = operationOwner.fluidLocks[i];
			if (!lock.isEmpty() && !requiredFluid.ingredient().test(lock))
				continue;
			FluidStack available = operationOwner.fluidTanks[i].getFluid();
			if (available.isEmpty())
				return true;
			if (requiredFluid.ingredient().test(available) && available.getAmount() < requiredFluid.amount()
				&& SpiderHybridStorage.canHybridTankAcceptFluid(operationOwner, i, available))
				return true;
		}
		return false;
	}

	static float getWorkpiecePriority(SpiderAssemblyTableBlockEntity operationOwner, ItemStack input) {
		if (input.isEmpty() || !SpiderProcessPlanner.hasAvailableProcess(operationOwner, input))
			return -1;
		if (!input.has(AllDataComponents.SEQUENCED_ASSEMBLY))
			return 0;
		return input.get(AllDataComponents.SEQUENCED_ASSEMBLY).progress();
	}

	static boolean hasAvailableProcess(SpiderAssemblyTableBlockEntity operationOwner, ItemStack input) {
		ItemStack singleInput = input.copy();
		singleInput.setCount(1);
		for (int attempt = 0; attempt < SpiderAssemblyTableBlockEntity.LEG_COUNT; attempt++) {
			int slot = (operationOwner.nextSlot + attempt) % SpiderAssemblyTableBlockEntity.LEG_COUNT;
			SpiderAssemblyTableBlockEntity.MachineKind kind = operationOwner.getMachineKind(slot);
			if (kind != null && SpiderProcessPlanner.createPlan(operationOwner, slot, kind, singleInput).isPresent())
				return true;
		}
		return false;
	}

	static Optional<SpiderAssemblyTableBlockEntity.ProcessingPlan> createPlan(SpiderAssemblyTableBlockEntity operationOwner, int slot, SpiderAssemblyTableBlockEntity.MachineKind kind, ItemStack input) {
		return operationOwner.createPlan(slot, kind, input);
	}

	static Optional<PressingRecipe> findPressingRecipe(SpiderAssemblyTableBlockEntity operationOwner, ItemStack input) {
		return SequencedAssemblyRecipe.getRecipe(operationOwner.getLevel(), input, AllRecipeTypes.PRESSING.getType(),
			PressingRecipe.class).map(RecipeHolder::value);
	}

	static Optional<CuttingRecipe> findCuttingRecipe(SpiderAssemblyTableBlockEntity operationOwner, ItemStack input) {
		return SequencedAssemblyRecipe.getRecipe(operationOwner.getLevel(), input, AllRecipeTypes.CUTTING.getType(), CuttingRecipe.class)
			.map(RecipeHolder::value);
	}

	static Optional<DeployerApplicationRecipe> findDeployingRecipe(SpiderAssemblyTableBlockEntity operationOwner, int slot, ItemStack input) {
		ItemStack held = operationOwner.inventory.getStackInSlot(SpiderAssemblyTableBlockEntity.HYBRID_SLOT_START + slot);
		if (held.isEmpty())
			return Optional.empty();

		ItemStackHandler recipeInventory = new ItemStackHandler(2);
		recipeInventory.setStackInSlot(0, input.copy());
		recipeInventory.setStackInSlot(1, held.copy());
		RecipeWrapper wrapper = new RecipeWrapper(recipeInventory);
		return SequencedAssemblyRecipe.getRecipe(operationOwner.getLevel(), wrapper, AllRecipeTypes.DEPLOYING.getType(),
			DeployerApplicationRecipe.class, recipe -> recipe.value().matches(wrapper, operationOwner.getLevel()))
			.map(RecipeHolder::value);
	}

	static Optional<FillingRecipe> findFillingRecipe(SpiderAssemblyTableBlockEntity operationOwner, int slot, ItemStack input) {
		FluidStack fluid = operationOwner.fluidTanks[slot].getFluid();
		if (fluid.isEmpty())
			return Optional.empty();

		SingleRecipeInput recipeInput = new SingleRecipeInput(input.copy());
		return SequencedAssemblyRecipe.getRecipe(operationOwner.getLevel(), recipeInput, AllRecipeTypes.FILLING.getType(),
			FillingRecipe.class,
			recipe -> recipe.value().matches(recipeInput, operationOwner.getLevel()) && recipe.value().getRequiredFluid().test(fluid))
			.map(RecipeHolder::value);
	}

	static List<ItemStack> processPressing(SpiderAssemblyTableBlockEntity operationOwner, ItemStack input) {
		return SpiderProcessPlanner.findPressingRecipe(operationOwner, input)
			.map(recipe -> RecipeApplier.applyRecipeOn(operationOwner.getLevel(), input.copy(), recipe, true))
			.orElse(List.of());
	}

	static List<ItemStack> processCutting(SpiderAssemblyTableBlockEntity operationOwner, ItemStack input) {
		return SpiderProcessPlanner.findCuttingRecipe(operationOwner, input)
			.map(recipe -> RecipeApplier.applyRecipeOn(operationOwner.getLevel(), input.copy(), recipe, true))
			.orElse(List.of());
	}

	static List<ItemStack> processDeploying(SpiderAssemblyTableBlockEntity operationOwner, int slot, ItemStack input) {
		Optional<DeployerApplicationRecipe> recipe = SpiderProcessPlanner.findDeployingRecipe(operationOwner, slot, input);
		if (recipe.isEmpty())
			return List.of();
		List<ItemStack> outputs = RecipeApplier.applyRecipeOn(operationOwner.getLevel(), input.copy(), recipe.get(), true);
		SpiderProcessController.consumeDeployingItem(operationOwner, slot, recipe.get());
		return outputs;
	}

	static List<ItemStack> processFilling(SpiderAssemblyTableBlockEntity operationOwner, int slot, ItemStack input) {
		Optional<FillingRecipe> recipeOpt = SpiderProcessPlanner.findFillingRecipe(operationOwner, slot, input);
		if (recipeOpt.isEmpty())
			return List.of();
		FillingRecipe recipe = recipeOpt.get();
		SizedFluidIngredient requiredFluid = recipe.getRequiredFluid();
		FluidStack available = operationOwner.fluidTanks[slot].getFluid();
		if (!requiredFluid.test(available))
			return List.of();
		operationOwner.fluidTanks[slot].drain(requiredFluid.amount(), FluidAction.EXECUTE);
		return recipe.rollResults(operationOwner.getLevel().random);
	}
}
