package com.nobodiiiii.createbiotech.content.experience;

import net.minecraft.core.HolderLookup;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.foundation.advancement.PlacedByPlayerAdvancementTracker;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.nobodiiiii.createbiotech.mixin.PipeConnectionAccessor;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FlowSource;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.content.fluids.pipes.VanillaFluidTargets;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.content.kinetics.fan.NozzleBlock;
import com.simibubi.create.foundation.ICapabilityProvider;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.IItemHandler;

public class ExperiencePumpBlockEntity extends PumpBlockEntity {
	private static final double OPEN_INPUT_HALF_EXTENT = 0.75d;
	private static final double NOZZLE_ATTRACTION_ACCELERATION_AT_256_RPM = 0.05d;
	private static final double NOZZLE_ATTRACTION_MAX_SPEED_AT_256_RPM = 0.7d;
	private static final double MIN_ATTRACTION_DISTANCE_SQR = 1.0E-4d;

	@Nullable
	private UUID advancementOwner;
	private final IFluidHandler specialSourceCapability;
	private long cachedExperience;
	private long cacheDrainTick = Long.MIN_VALUE;
	private int cacheDrainedThisTick;

	public ExperiencePumpBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.EXPERIENCE_PUMP.get(), pos, state);
		specialSourceCapability = new SpecialExperienceSourceHandler();
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		for (BlockEntityBehaviour behaviour : behaviours) {
			if (behaviour instanceof FluidTransportBehaviour transport)
				transport.interfaces = new ExperiencePipeConnections(transport);
		}
	}

	@Override
	public void tick() {
		Direction output = getFront();
		if (level != null && !level.isClientSide && getSpeed() != 0 && output != null)
			refillExperienceCache(output.getOpposite());
		if (level != null && !level.isClientSide && output != null)
			updateSpecialInputSource(output.getOpposite());
		// All output, including adjacent tanks and open pipe ends, uses Create's network.
		super.tick();
	}

	private void updateSpecialInputSource(Direction input) {
		FluidTransportBehaviour transport = getBehaviour(FluidTransportBehaviour.TYPE);
		if (transport == null)
			return;
		boolean useCache = usesExperienceCache(input);
		for (Direction side : Direction.values()) {
			PipeConnection connection = transport.getConnection(side);
			if (connection == null)
				continue;
			PipeConnectionAccessor access = (PipeConnectionAccessor) connection;
			boolean cachedSource = access.createBiotech$getSource().orElse(null) instanceof CachedExperienceSource;
			if (side == input && useCache) {
				if (cachedSource)
					continue;
				access.createBiotech$setSource(Optional.of(new CachedExperienceSource(side)));
			} else if (cachedSource) {
				access.createBiotech$setSource(Optional.empty());
			} else {
				continue;
			}
			// A retained FluidNetwork captures its original source supplier.
			access.createBiotech$setNetwork(Optional.empty());
		}
	}

	private void refillExperienceCache(Direction input) {
		if (hasOrdinaryFluidInput(input))
			return;
		if (hasOpenExperienceSource(input))
			attractOpenInputExperienceOrbs(input);
		int missing = (int) Math.max(0, getFluidPumpRatePerTick() - cachedExperience);
		if (missing == 0)
			return;
		long absorbed = hasExperienceItemEndpoint(input) ? drainItemInput(input, missing)
			: hasOpenExperienceSource(input) ? drainOpenInput(input, missing, FluidAction.EXECUTE) : 0;
		if (absorbed > 0) {
			cachedExperience += absorbed;
			setChanged();
		}
	}

	@Override
	public void onSpeedChanged(float previousSpeed) {
		super.onSpeedChanged(previousSpeed);
		if (Math.abs(previousSpeed) == Math.abs(getSpeed()))
			return;
		if (getSpeed() != 0)
			PlacedByPlayerAdvancementTracker.awardPlacedBy(level, advancementOwner, CBAdvancements.EXPERIENCE_PUMP);
	}

	public void setAdvancementOwner(@Nullable LivingEntity placer) {
		advancementOwner = PlacedByPlayerAdvancementTracker.ownerFrom(placer);
		setChanged();
	}

	@Override
	protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(compound, registries, clientPacket);
		PlacedByPlayerAdvancementTracker.writeOwner(compound, advancementOwner);
		compound.putLong("CachedExperience", cachedExperience);
	}

	@Override
	protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(compound, registries, clientPacket);
		advancementOwner = PlacedByPlayerAdvancementTracker.readOwner(compound);
		cachedExperience = Math.max(0, compound.getLong("CachedExperience"));
	}

	private boolean hasOrdinaryFluidInput(Direction input) {
		if (level == null)
			return true;
		BlockPos inputPos = worldPosition.relative(input);
		if (!level.isLoaded(inputPos))
			return true;
		FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, inputPos);
		return pipe != null && pipe.canHaveFlowToward(level.getBlockState(inputPos), input.getOpposite())
			|| FluidPropagator.hasFluidCapability(level, inputPos, input.getOpposite())
			|| !level.getFluidState(inputPos).isEmpty()
			|| VanillaFluidTargets.canProvideFluidWithoutCapability(level.getBlockState(inputPos));
	}

	private boolean usesExperienceCache(Direction input) {
		return !hasOrdinaryFluidInput(input)
			&& (cachedExperience > 0 || hasOpenExperienceSource(input) || hasExperienceItemEndpoint(input));
	}

	private boolean hasOpenExperienceSource(Direction input) {
		if (level == null || !canDrainOpenExperience() || hasOrdinaryFluidInput(input))
			return false;
		BlockPos inputPos = worldPosition.relative(input);
		if (FluidPropagator.getPipe(level, inputPos) != null)
			return false;
		if (hasItemEndpoint(input))
			return false;
		if (FluidPropagator.hasFluidCapability(level, inputPos, input.getOpposite()))
			return false;
		return hasInputNozzle(input) || FluidPropagator.isOpenEnd(level, worldPosition, input);
	}

	private boolean hasInputNozzle(Direction input) {
		if (level == null)
			return false;
		BlockState inputState = level.getBlockState(worldPosition.relative(input));
		return AllBlocks.NOZZLE.has(inputState) && inputState.getValue(NozzleBlock.FACING) == input.getOpposite();
	}

	private boolean hasExperienceItemEndpoint(Direction input) {
		return !hasOrdinaryFluidInput(input) && CBConfigs.SERVER.experiencePump.allowItemXpDrain.get()
			&& hasItemEndpoint(input);
	}

	private boolean hasItemEndpoint(Direction input) {
		if (level == null)
			return false;
		return level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.relative(input),
			input.getOpposite()) != null;
	}

	private void attractOpenInputExperienceOrbs(Direction input) {
		if (!CBConfigs.SERVER.experiencePump.allowOrbDrain.get() || !hasInputNozzle(input))
			return;
		Vec3 center = getEndpointCenter(input);
		attractInputOrbs(center, cubeAround(center, OPEN_INPUT_HALF_EXTENT));
	}

	private void attractInputOrbs(Vec3 center, AABB absorbBox) {
		if (level == null || level.isClientSide)
			return;
		double speedScale = Math.abs(getSpeed()) / 256d;
		double attractionHalfExtent = CBConfigs.SERVER.experiencePump.nozzleAttractionRangeAt256Rpm.get()
			* speedScale;
		if (attractionHalfExtent <= OPEN_INPUT_HALF_EXTENT)
			return;

		AABB attractionBox = cubeAround(center, attractionHalfExtent);
		double acceleration = NOZZLE_ATTRACTION_ACCELERATION_AT_256_RPM * speedScale;
		double maxSpeed = NOZZLE_ATTRACTION_MAX_SPEED_AT_256_RPM * speedScale;
		double maxSpeedSqr = maxSpeed * maxSpeed;

		List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class, attractionBox,
			orb -> orb.isAlive() && !absorbBox.contains(orb.position()));
		for (ExperienceOrb orb : orbs) {
			Vec3 toCenter = center.subtract(orb.position());
			if (toCenter.lengthSqr() < MIN_ATTRACTION_DISTANCE_SQR)
				continue;
			Vec3 motion = orb.getDeltaMovement()
				.add(toCenter.normalize()
					.scale(acceleration));
			if (motion.lengthSqr() > maxSpeedSqr)
				motion = motion.normalize()
					.scale(maxSpeed);
			orb.setDeltaMovement(motion);
			orb.hurtMarked = true;
		}
	}

	private int drainOpenInput(Direction input, int maxAmount, IFluidHandler.FluidAction action) {
		if (maxAmount <= 0 || level == null || level.isClientSide)
			return 0;

		Vec3 center = getEndpointCenter(input);
		AABB absorbBox = cubeAround(center, OPEN_INPUT_HALF_EXTENT);
		int remaining = maxAmount;
		int absorbed = 0;

		if (CBConfigs.SERVER.experiencePump.allowOrbDrain.get()) {
			int orbXp = drainExperienceOrbs(absorbBox, remaining, action);
			remaining -= orbXp;
			absorbed += orbXp;
		}

		if (remaining <= 0 || !CBConfigs.SERVER.experiencePump.allowPlayerXpDrain.get())
			return absorbed;

		List<Player> players = level.getEntitiesOfClass(Player.class, absorbBox,
			player -> player.isAlive() && !player.isSpectator());
		for (Player player : players) {
			if (remaining <= 0)
				break;
			int drained = action.simulate() ? Math.min(remaining, Math.max(0, player.totalExperience))
				: ExperienceHelper.drainPlayerExperience(player, remaining);
			remaining -= drained;
			absorbed += drained;
		}

		return absorbed;
	}

	private int drainExperienceOrbs(AABB absorbBox, int maxAmount, IFluidHandler.FluidAction action) {
		if (maxAmount <= 0 || level == null || level.isClientSide)
			return 0;

		int remaining = maxAmount;
		int absorbed = 0;
		List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class, absorbBox, ExperienceOrb::isAlive);
		for (ExperienceOrb orb : orbs) {
			if (remaining <= 0)
				break;
			int value = orb.getValue();
			if (value <= 0)
				continue;
			int taken = Math.min(value, remaining);
			if (action.execute()) {
				orb.discard();
				if (value > taken)
					ExperienceHelper.spawnExperience(level, orb.position(), value - taken);
			}
			remaining -= taken;
			absorbed += taken;
		}
		return absorbed;
	}

	private long drainItemInput(Direction input, int maxAmount) {
		if (maxAmount <= 0 || level == null)
			return 0;
		IItemHandler itemHandler = getItemHandler(input);
		if (itemHandler == null)
			return 0;

		long extracted = 0;
		for (int slot = 0; slot < itemHandler.getSlots() && extracted < maxAmount; slot++) {
			while (extracted < maxAmount) {
				ItemStack peek = getExtractableItemPreview(itemHandler, slot);
				if (peek.isEmpty())
					break;
				int xpPerItem = xpValueOf(peek);
				if (xpPerItem <= 0)
					break;
				long missing = maxAmount - extracted;
				int items = (int) Math.min(peek.getCount(), (missing + xpPerItem - 1) / xpPerItem);
				ItemStack actual = itemHandler.extractItem(slot, items, false);
				if (actual.isEmpty())
					break;
				long value = (long) actual.getCount() * xpValueOf(actual);
				if (value <= 0)
					break;
				extracted += value;
			}
		}
		return extracted;
	}

	private ItemStack getExtractableItemPreview(IItemHandler itemHandler, int slot) {
		ItemStack preview = itemHandler.getStackInSlot(slot);
		if (preview.isEmpty())
			return ItemStack.EMPTY;
		ItemStack extracted = itemHandler.extractItem(slot, 1, true);
		return extracted.isEmpty() ? ItemStack.EMPTY : preview;
	}

	@Nullable
	private IItemHandler getItemHandler(Direction input) {
		if (level == null)
			return null;
		return level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.relative(input),
			input.getOpposite());
	}

	private int getFluidPumpRatePerTick() {
		return limitTransferAmount(Math.max(1, (int) (Math.abs(getSpeed()) / 2f)));
	}

	private static boolean canDrainOpenExperience() {
		return CBConfigs.SERVER.experiencePump.allowOrbDrain.get()
			|| CBConfigs.SERVER.experiencePump.allowPlayerXpDrain.get();
	}

	private static int limitTransferAmount(int requested) {
		int maximum = CBConfigs.SERVER.experiencePump.maxTransferPerTick.get();
		return maximum > 0 ? Math.min(requested, maximum) : requested;
	}

	private Vec3 getEndpointCenter(Direction side) {
		return Vec3.atCenterOf(worldPosition.relative(side));
	}

	private static AABB cubeAround(Vec3 center, double halfExtent) {
		return new AABB(center.x - halfExtent, center.y - halfExtent, center.z - halfExtent, center.x + halfExtent,
			center.y + halfExtent, center.z + halfExtent);
	}

	private static int xpValueOf(ItemStack stack) {
		if (stack.is(AllItems.EXP_NUGGET.get()))
			return ExperienceConstants.xpPerNugget();
		if (stack.getItem() instanceof ExperienceClusterBlockItem cluster)
			return cluster.getXpValue();
		return 0;
	}

	private static class ExperiencePipeConnections extends EnumMap<Direction, PipeConnection> {
		ExperiencePipeConnections(FluidTransportBehaviour transport) {
			super(Direction.class);
			for (Direction side : Direction.values()) {
				if (transport.canHaveFlowToward(transport.blockEntity.getBlockState(), side))
					put(side, new ExperiencePipeConnection(side));
			}
		}

		@Override
		public PipeConnection computeIfAbsent(Direction side,
			Function<? super Direction, ? extends PipeConnection> factory) {
			// Create adds faces here during NBT reads and pressure rebuilds after rotation.
			return super.computeIfAbsent(side, ExperiencePipeConnection::new);
		}
	}

	private static class ExperiencePipeConnection extends PipeConnection {
		ExperiencePipeConnection(Direction side) {
			super(side);
		}

		@Override
		public boolean manageFlows(Level world, BlockPos pos, FluidStack internalFluid,
			Predicate<FluidStack> extractionPredicate) {
			return super.manageFlows(world, pos, internalFluid,
				fluid -> ExperienceFluidHelper.isExperience(fluid) && extractionPredicate.test(fluid));
		}
	}

	private class SpecialExperienceSourceHandler implements IFluidHandler {
		@Override
		public int getTanks() {
			return 1;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			return tank == 0 ? ExperienceFluidHelper.experienceStack((int) Math.min(Integer.MAX_VALUE, cachedExperience))
				: FluidStack.EMPTY;
		}

		@Override
		public int getTankCapacity(int tank) {
			return tank == 0 ? Integer.MAX_VALUE : 0;
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return tank == 0 && ExperienceFluidHelper.isGeneratedExperience(stack);
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			return 0;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			if (!ExperienceFluidHelper.isGeneratedExperience(resource))
				return FluidStack.EMPTY;
			return drain(resource.getAmount(), action);
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			if (level == null || getSpeed() == 0)
				return FluidStack.EMPTY;
			int remaining = getFluidPumpRatePerTick()
				- (cacheDrainTick == level.getGameTime() ? cacheDrainedThisTick : 0);
			int drained = (int) Math.min(Math.max(0, Math.min(maxDrain, remaining)), cachedExperience);
			if (drained > 0 && action.execute()) {
				if (cacheDrainTick != level.getGameTime()) {
					cacheDrainTick = level.getGameTime();
					cacheDrainedThisTick = 0;
				}
				cacheDrainedThisTick += drained;
				cachedExperience -= drained;
				setChanged();
			}
			return ExperienceFluidHelper.experienceStack(drained);
		}
	}

	private class CachedExperienceSource extends FlowSource {
		private final ICapabilityProvider<IFluidHandler> capability = ICapabilityProvider.of(specialSourceCapability);

		CachedExperienceSource(Direction input) {
			super(new BlockFace(worldPosition, input));
		}

		@Override
		public boolean isEndpoint() {
			return true;
		}

		@Override
		public ICapabilityProvider<IFluidHandler> provideHandler() {
			return capability;
		}
	}

}
