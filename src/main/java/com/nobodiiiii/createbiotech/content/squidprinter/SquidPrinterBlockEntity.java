package com.nobodiiiii.createbiotech.content.squidprinter;

import net.minecraft.core.HolderLookup;

import static com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult.HOLD;
import static com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult.PASS;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.foundation.advancement.PlacedByPlayerAdvancementTracker;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

public class SquidPrinterBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

	public int processingTicks;
	public boolean sendSplash;

	protected SmartFluidTankBehaviour tank;
	protected BeltProcessingBehaviour beltProcessing;
	public FilteringBehaviour filtering;

	private boolean running;
	private ItemStack processingOutput;
	private int idleTicksWhileRunning;
	private float squidPose;
	private float squidPoseOld;
	@Nullable
	private UUID advancementOwner;

	public SquidPrinterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		processingTicks = -1;
		running = false;
		processingOutput = ItemStack.EMPTY;
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		tank = SmartFluidTankBehaviour.single(this, getTankCapacity());
		behaviours.add(tank);

		beltProcessing = new BeltProcessingBehaviour(this).whenItemEnters(this::onItemReceived)
			.whileItemHeld(this::whenItemHeld);
		behaviours.add(beltProcessing);

		filtering = new FilteringBehaviour(this, new SquidPrinterFilterSlot())
			.withPredicate(stack -> true)
			.withCallback(stack -> notifyUpdate());
		behaviours.add(filtering);
	}

	@Override
	protected AABB createRenderBoundingBox() {
		return super.createRenderBoundingBox().expandTowards(0, -2, 0);
	}

	@Override
	public void tick() {
		super.tick();

		if (level == null)
			return;

		if (!level.isClientSide) {
			if (running && ++idleTicksWhileRunning >= 3) {
				clearProcessingState();
				notifyUpdate();
			}
		}

		if (level.isClientSide)
			tickSquidPose();

		if (running && processingTicks > getFinishingTicks())
			processingTicks--;

		if (level.isClientSide && running && processingTicks >= getFinishingTicks() + 3)
			spawnInkParticles();

		if (level.isClientSide && running && processingTicks > getFinishingTicks() && level.getGameTime() % 3 == 0)
			spawnAmbientInk();
	}

	protected ProcessingResult onItemReceived(TransportedItemStack transported,
		TransportedItemStackHandlerBehaviour handler) {
		if (handler.blockEntity.isVirtual())
			return PASS;
		if (!hasApplicableInputRecipe(transported.stack))
			return PASS;
		return HOLD;
	}

	protected ProcessingResult whenItemHeld(TransportedItemStack transported,
		TransportedItemStackHandlerBehaviour handler) {
		if (processingTicks == -1 && !hasApplicableInputRecipe(transported.stack))
			return PASS;

		idleTicksWhileRunning = 0;

		if (processingTicks != -1) {
			if (processingTicks > getFinishingTicks())
				return HOLD;

			ItemStack out = produceResult();
			transported.clearFanProcessingData();
			List<TransportedItemStack> outList = new ArrayList<>();
			TransportedItemStack held = null;
			if (!out.isEmpty()) {
				TransportedItemStack result = transported.copy();
				result.stack = out;
				outList.add(result);
			}
			ItemStack remaining = transported.stack.copy();
			remaining.shrink(1);
			if (!remaining.isEmpty()) {
				held = transported.copy();
				held.stack = remaining;
			}
			handler.handleProcessingOnItem(transported, TransportedResult.convertToAndLeaveHeld(outList, held));
			PlacedByPlayerAdvancementTracker.awardPlacedBy(level, advancementOwner, CBAdvancements.SQUID_PRINTER);

			sendSplash = true;
			clearProcessingState();
			notifyUpdate();
			return HOLD;
		}

		Optional<PreparedRecipe> recipe = findMatchingRecipe(transported.stack);
		if (recipe.isEmpty())
			return HOLD;

		startProcessing(recipe.get());
		notifyUpdate();
		return HOLD;
	}

	private boolean hasApplicableInputRecipe(ItemStack input) {
		if (level == null || input.isEmpty())
			return false;

		ItemStackHandler recipeInventory = new ItemStackHandler(1);
		recipeInventory.setStackInSlot(0, input.copy());
		RecipeWrapper recipeWrapper = new RecipeWrapper(recipeInventory);
		ItemStack template = getTemplate();
		return level.getRecipeManager()
			.getRecipesFor(com.nobodiiiii.createbiotech.registry.CBRecipeTypes.SQUID_PRINTER_TYPE.get(),
				recipeWrapper, level)
			.stream()
			.map(net.minecraft.world.item.crafting.RecipeHolder::value)
			.anyMatch(recipe -> {
				if (recipe.copiesEnchantments()
					&& !SquidPrinterEnchantmentRuleRecipe.isCopyingEnabled(level.getRecipeManager()))
					return false;
				if (template.isEmpty())
					return recipe.copiesEnchantments() || recipe.getTemplateIngredient().isPresent()
						|| recipe.matchesTemplate(template, level);
				return recipe.matchesTemplate(template, level);
			});
	}

	private Optional<PreparedRecipe> findMatchingRecipe(ItemStack input) {
		if (level == null)
			return Optional.empty();

		ItemStack template = getTemplate();
		ItemStackHandler recipeInventory = new ItemStackHandler(1);
		recipeInventory.setStackInSlot(0, input.copy());
		RecipeWrapper recipeWrapper = new RecipeWrapper(recipeInventory);
		return level.getRecipeManager()
			.getRecipesFor(com.nobodiiiii.createbiotech.registry.CBRecipeTypes.SQUID_PRINTER_TYPE.get(), recipeWrapper,
				level)
			.stream()
			.map(net.minecraft.world.item.crafting.RecipeHolder::value)
			.filter(recipe -> recipe.matches(recipeWrapper, level))
			.filter(recipe -> recipe.matchesTemplate(template, level))
			.map(recipe -> new PreparedRecipe(recipe, template.copyWithCount(1)))
			.filter(this::hasRequiredFluid)
			.findFirst();
	}

	private boolean hasRequiredFluid(PreparedRecipe recipe) {
		return recipe.recipe().getRequiredFluid().map(requiredFluid -> {
			FluidStack stored = getFluid();
			return requiredFluid.test(stored)
				&& stored.getAmount() >= recipe.recipe().getRequiredFluidAmount(recipe.template());
		}).orElse(true);
	}

	private void startProcessing(PreparedRecipe recipe) {
		processingOutput = recipe.recipe().rollResult(recipe.template(), level.random);
		processingTicks = recipe.recipe()
			.getRequiredTicks(recipe.template()) + getFinishingTicks();
		running = true;
		idleTicksWhileRunning = 0;
		int requiredFluid = recipe.recipe().getRequiredFluidAmount(recipe.template());
		if (level != null && !level.isClientSide && requiredFluid > 0)
			tank.getPrimaryHandler()
				.drain(requiredFluid, FluidAction.EXECUTE);
	}

	private void clearProcessingState() {
		processingTicks = -1;
		running = false;
		processingOutput = ItemStack.EMPTY;
		idleTicksWhileRunning = 0;
	}

	private ItemStack getTemplate() {
		return filtering == null ? ItemStack.EMPTY : filtering.getFilter();
	}

	private ItemStack produceResult() {
		return processingOutput.copy();
	}

	public FluidStack getFluid() {
		return tank.getPrimaryHandler().getFluid();
	}

	public boolean isRunning() {
		return running;
	}

	float getSquidPose(float partialTicks) {
		return Mth.lerp(partialTicks, squidPoseOld, squidPose);
	}

	private void tickSquidPose() {
		squidPoseOld = squidPose;
		squidPose = Mth.approach(squidPose, running ? 1.0f : 0.0f, SquidPrinterSquidVisual.POSE_SPEED);
	}

	public void setAdvancementOwner(@Nullable LivingEntity placer) {
		advancementOwner = PlacedByPlayerAdvancementTracker.ownerFrom(placer);
		setChanged();
	}

	@FunctionalInterface
	public interface SquidInkParticleEmitter {
		void emit(SquidPrinterInkParticleOption options, double x, double y, double z, double dx, double dy,
			double dz);
	}

	public int getComparatorOutput() {
		FluidStack stored = getFluid();
		return stored.isEmpty() ? 0 : Math.max(1, (int) Math.round(stored.getAmount() * 14.0 / getTankCapacity()) + 1);
	}

	private static int getTankCapacity() {
		return CBConfigs.SERVER.squidPrinter.tankCapacity.get();
	}

	private static int getFinishingTicks() {
		return CBConfigs.SERVER.squidPrinter.finishingTicks.get();
	}

	private void spawnInkParticles() {
		if (level == null)
			return;
		forEachBurstInkParticle(level, worldPosition,
			(options, x, y, z, dx, dy, dz) -> level.addParticle(options, x, y, z, dx, dy, dz));
	}

	private void spawnAmbientInk() {
		if (level == null)
			return;
		forEachAmbientInkParticle(level, worldPosition,
			(options, x, y, z, dx, dy, dz) -> level.addParticle(options, x, y, z, dx, dy, dz));
	}

	/**
	 * Ink from both streams vanishes this far below the printer, so it never sinks
	 * past the depot it is printing onto.
	 */
	private static final double INK_FALL_LIMIT_BELOW_BLOCK = 1.2d;

	/**
	 * Converts the shared depth limit into the per-particle allowance, which
	 * differs between the two streams because they are released at different
	 * heights.
	 */
	private static SquidPrinterInkParticleOption inkOptions(BlockPos pos, double spawnY) {
		return new SquidPrinterInkParticleOption((float) (INK_FALL_LIMIT_BELOW_BLOCK + (spawnY - pos.getY())));
	}

	public static void forEachBurstInkParticle(Level level, BlockPos pos, SquidInkParticleEmitter emitter) {
		double centerX = pos.getX() + 0.5d;
		double centerY = pos.getY() - 0.5d;
		double centerZ = pos.getZ() + 0.5d;
		SquidPrinterInkParticleOption options = inkOptions(pos, centerY);
		for (int i = 0; i < 4; i++) {
			double offsetX = (level.random.nextDouble() - 0.5d) * 0.4d;
			double offsetZ = (level.random.nextDouble() - 0.5d) * 0.4d;
			emitter.emit(options, centerX + offsetX, centerY, centerZ + offsetZ, 0d, -0.05d, 0d);
		}
	}

	public static void forEachAmbientInkParticle(Level level, BlockPos pos, SquidInkParticleEmitter emitter) {
		double centerX = pos.getX() + 0.5d;
		double centerY = pos.getY() + 0.05d;
		double centerZ = pos.getZ() + 0.5d;
		double offsetX = (level.random.nextDouble() - 0.5d) * 0.3d;
		double offsetZ = (level.random.nextDouble() - 0.5d) * 0.3d;
		emitter.emit(inkOptions(pos, centerY), centerX + offsetX, centerY, centerZ + offsetZ, 0d, -0.04d, 0d);
	}

	@Override
	protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(compound, registries, clientPacket);
		compound.putInt("ProcessingTicks", processingTicks);
		compound.putBoolean("Running", running);
		compound.putBoolean("HasProcessingResult", running);
		if (!processingOutput.isEmpty())
			compound.put("ProcessingOutput", processingOutput.saveOptional(registries));
		PlacedByPlayerAdvancementTracker.writeOwner(compound, advancementOwner);
		if (sendSplash && clientPacket) {
			compound.putBoolean("Splash", true);
			sendSplash = false;
		}
	}

	@Override
	protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(compound, registries, clientPacket);
		processingTicks = compound.getInt("ProcessingTicks");
		running = compound.getBoolean("Running");
		boolean hasProcessingResult = compound.getBoolean("HasProcessingResult")
			|| compound.contains("ProcessingOutput");
		processingOutput =
			compound.contains("ProcessingOutput")
				? ItemStack.parseOptional(registries, compound.getCompound("ProcessingOutput"))
				: ItemStack.EMPTY;
		if (running && !hasProcessingResult) {
			processingTicks = -1;
			running = false;
		}
		advancementOwner = PlacedByPlayerAdvancementTracker.readOwner(compound);
	}

	@Nullable
	public IFluidHandler getFluidCapability(Direction side) {
		return side != Direction.DOWN ? tank.getCapability() : null;
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		return containedFluidTooltip(tooltip, isPlayerSneaking, tank.getCapability());
	}

	public void spawnSplashIfPending(ServerLevel level) {
		if (!sendSplash)
			return;
		sendSplash = false;
	}

	private record PreparedRecipe(SquidPrinterRecipe recipe, ItemStack template) {
	}
}
