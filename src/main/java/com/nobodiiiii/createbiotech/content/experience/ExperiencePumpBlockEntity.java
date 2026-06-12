package com.nobodiiiii.createbiotech.content.experience;

import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.foundation.advancement.PlacedByPlayerAdvancementTracker;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.content.kinetics.fan.NozzleBlock;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.items.IItemHandler;

public class ExperiencePumpBlockEntity extends PumpBlockEntity {
	private static final double OPEN_INPUT_HALF_EXTENT = 0.75d;
	private static final double NOZZLE_ATTRACTION_SIZE_AT_256_RPM = 33d;
	private static final double NOZZLE_ATTRACTION_ACCELERATION_AT_256_RPM = 0.05d;
	private static final double NOZZLE_ATTRACTION_MAX_SPEED_AT_256_RPM = 0.7d;
	private static final double MIN_ATTRACTION_DISTANCE_SQR = 1.0E-4d;

	@Nullable
	private UUID advancementOwner;
	private final LazyOptional<IFluidHandler> specialSourceCapability;

	public ExperiencePumpBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.EXPERIENCE_PUMP.get(), pos, state);
		specialSourceCapability = LazyOptional.of(() -> new SpecialExperienceSourceHandler());
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(new ExperiencePumpFluidTransferBehaviour(this));
		registerAwardables(behaviours, FluidPropagator.getSharedTriggers());
		registerAwardables(behaviours, AllAdvancements.PUMP);
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide || getSpeed() == 0)
			return;
		Direction output = getFront();
		if (output == null)
			return;
		Direction input = output.getOpposite();
		boolean hasOpenExperienceSource = hasOpenExperienceSource(input);
		if (!hasOpenExperienceSource && !hasExperienceItemEndpoint(input))
			return;

		BlockPos outputPos = worldPosition.relative(output);
		BlockState outputState = level.getBlockState(outputPos);
		FluidTransportBehaviour outputPipe = FluidPropagator.getPipe(level, outputPos);
		if (outputPipe != null && outputPipe.canHaveFlowToward(outputState, output.getOpposite())) {
			if (hasOpenExperienceSource)
				attractOpenInputExperienceOrbs(input);
			return;
		}

		int rate = getFluidPumpRatePerTick();

		BlockEntity targetBE = level.getBlockEntity(outputPos);
		if (targetBE == null)
			return;
		targetBE.getCapability(ForgeCapabilities.FLUID_HANDLER, output.getOpposite())
			.ifPresent(target -> {
				FluidStack offered = ExperienceFluidHelper.experienceStack(rate);
				int accepted = target.fill(offered, IFluidHandler.FluidAction.SIMULATE);
				if (accepted <= 0)
					return;
				if (hasOpenExperienceSource)
					attractOpenInputExperienceOrbs(input);
				FluidStack drained = drainSpecialSource(accepted, IFluidHandler.FluidAction.EXECUTE);
				if (drained.isEmpty())
					return;
				target.fill(drained, IFluidHandler.FluidAction.EXECUTE);
			});
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
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
		if (cap == ForgeCapabilities.FLUID_HANDLER && side != null && canExposeSpecialSource(side))
			return specialSourceCapability.cast();
		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		specialSourceCapability.invalidate();
	}

	@Override
	protected void write(CompoundTag compound, boolean clientPacket) {
		super.write(compound, clientPacket);
		PlacedByPlayerAdvancementTracker.writeOwner(compound, advancementOwner);
	}

	@Override
	protected void read(CompoundTag compound, boolean clientPacket) {
		super.read(compound, clientPacket);
		advancementOwner = PlacedByPlayerAdvancementTracker.readOwner(compound);
	}

	private boolean canExposeSpecialSource(Direction side) {
		Direction output = getFront();
		return output != null && side == output && hasPotentialSpecialSource(output.getOpposite());
	}

	private boolean hasPotentialSpecialSource(Direction input) {
		return hasOpenExperienceSource(input) || hasExperienceItemEndpoint(input);
	}

	private boolean hasOpenExperienceSource(Direction input) {
		if (level == null)
			return false;
		BlockPos inputPos = worldPosition.relative(input);
		if (FluidPropagator.getPipe(level, inputPos) != null)
			return false;
		if (hasExperienceItemEndpoint(input))
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
		if (level == null)
			return false;
		BlockEntity blockEntity = level.getBlockEntity(worldPosition.relative(input));
		if (blockEntity == null)
			return false;
		return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, input.getOpposite())
			.isPresent();
	}

	private FluidStack drainSpecialSource(int maxAmount, IFluidHandler.FluidAction action) {
		if (maxAmount <= 0 || level == null)
			return FluidStack.EMPTY;

		Direction output = getFront();
		if (output == null)
			return FluidStack.EMPTY;
		Direction input = output.getOpposite();
		int taken = hasExperienceItemEndpoint(input)
			? drainItemInput(input, maxAmount, action)
			: drainOpenInput(input, maxAmount, action);
		return ExperienceFluidHelper.experienceStack(taken);
	}

	private void attractOpenInputExperienceOrbs(Direction input) {
		if (!hasInputNozzle(input))
			return;
		Vec3 center = getEndpointCenter(input);
		attractInputOrbs(center, cubeAround(center, OPEN_INPUT_HALF_EXTENT));
	}

	private void attractInputOrbs(Vec3 center, AABB absorbBox) {
		if (level == null || level.isClientSide)
			return;
		double speedScale = Math.abs(getSpeed()) / 256d;
		double attractionHalfExtent = NOZZLE_ATTRACTION_SIZE_AT_256_RPM * speedScale / 2d;
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

		int orbXp = drainExperienceOrbs(absorbBox, remaining, action);
		remaining -= orbXp;
		absorbed += orbXp;

		if (remaining <= 0)
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

	private int drainItemInput(Direction input, int maxAmount, IFluidHandler.FluidAction action) {
		if (maxAmount <= 0 || level == null)
			return 0;
		IItemHandler itemHandler = getItemHandler(input);
		if (itemHandler == null)
			return 0;

		int extracted = 0;
		for (int slot = 0; slot < itemHandler.getSlots() && extracted < maxAmount; slot++) {
			ItemStack peek = getExtractableItemPreview(itemHandler, slot);
			if (peek.isEmpty())
				continue;
			int xpPerItem = xpValueOf(peek);
			if (xpPerItem <= 0)
				continue;
			int missing = maxAmount - extracted;
			int items = Math.min(peek.getCount(), missing / xpPerItem);
			if (items <= 0)
				continue;
			if (action.simulate()) {
				extracted += items * xpPerItem;
				continue;
			}
			ItemStack actual = itemHandler.extractItem(slot, items, false);
			if (actual.isEmpty())
				continue;
			extracted += actual.getCount() * xpPerItem;
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

	private int estimateSpecialSource(int maxAmount) {
		if (maxAmount <= 0)
			return 0;
		Direction input = getFront() == null ? null : getFront().getOpposite();
		if (input == null)
			return 0;
		if (hasExperienceItemEndpoint(input))
			return drainItemInput(input, maxAmount, IFluidHandler.FluidAction.SIMULATE);
		return drainOpenInput(input, maxAmount, IFluidHandler.FluidAction.SIMULATE);
	}

	@Nullable
	private IItemHandler getItemHandler(Direction input) {
		if (level == null)
			return null;
		BlockEntity blockEntity = level.getBlockEntity(worldPosition.relative(input));
		if (blockEntity == null)
			return null;
		return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, input.getOpposite())
			.orElse(null);
	}

	private int getFluidPumpRatePerTick() {
		return Math.max(1, (int) (Math.abs(getSpeed()) / 2f));
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

	private class SpecialExperienceSourceHandler implements IFluidHandler {
		@Override
		public int getTanks() {
			return 1;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			int amount = estimateSpecialSource(Integer.MAX_VALUE / 4);
			return ExperienceFluidHelper.experienceStack(amount);
		}

		@Override
		public int getTankCapacity(int tank) {
			return Integer.MAX_VALUE / 4;
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return ExperienceFluidHelper.isPrimaryExperience(stack);
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			return 0;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			if (!ExperienceFluidHelper.isPrimaryExperience(resource))
				return FluidStack.EMPTY;
			return drain(resource.getAmount(), action);
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			return drainSpecialSource(maxDrain, action);
		}
	}

	class ExperiencePumpFluidTransferBehaviour extends FluidTransportBehaviour {

		public ExperiencePumpFluidTransferBehaviour(SmartBlockEntity blockEntity) {
			super(blockEntity);
		}

		@Override
		public void tick() {
			super.tick();
			for (Entry<Direction, PipeConnection> entry : interfaces.entrySet()) {
				boolean pull = isPullingOnSide(isFront(entry.getKey()));
				Couple<Float> pressure = entry.getValue().getPressure();
				pressure.set(pull, Math.abs(getSpeed()));
				pressure.set(!pull, 0f);
			}
		}

		@Override
		public boolean canHaveFlowToward(BlockState state, Direction direction) {
			return isSideAccessible(direction);
		}

		@Override
		public boolean canPullFluidFrom(FluidStack fluid, BlockState state, Direction direction) {
			return ExperienceFluidHelper.isExperience(fluid);
		}

		@Override
		public AttachmentTypes getRenderedRimAttachment(BlockAndTintGetter world, BlockPos pos, BlockState state,
			Direction direction) {
			AttachmentTypes attachment = super.getRenderedRimAttachment(world, pos, state, direction);
			return attachment == AttachmentTypes.RIM ? AttachmentTypes.NONE : attachment;
		}
	}
}
