package com.nobodiiiii.createbiotech.content.experience;

import java.util.List;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberBlock;
import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberBlockEntity;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

public class ExperiencePumpBlockEntity extends KineticBlockEntity {
	private static final double ATTRACT_HALF_EXTENT = 1.5d; // 3x3x3 default
	private static final double ABSORB_HALF_EXTENT = 0.75d; // 1.5x1.5x1.5
	private static final double NOZZLE_ATTRACT_RPM_DIVISOR = 16.0d;
	private static final double NO_NOZZLE_CENTER_OFFSET = 1.05d;
	private static final double NOZZLE_CENTER_OFFSET = 1.65d;
	private static final double ATTRACT_ACCEL = 0.05d;
	private static final double MAX_ATTRACT_SPEED = 0.45d;

	// Budget can grow up to this so a single cluster (192 XP) can be pulled at low speeds.
	private static final double MAX_BUDGET_BACKLOG = 256.0d;

	// Emission accumulator: spit a single big orb when this threshold is hit,
	// or after holding XP for this many ticks (whichever comes first).
	private static final int EMISSION_LARGE_ORB_THRESHOLD = 64;
	private static final int EMISSION_MAX_HOLD_TICKS = 30;

	private double fractionalXp;
	private int pendingEmissionXp;
	private int pendingEmissionTicks;

	public ExperiencePumpBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.EXPERIENCE_PUMP.get(), pos, state);
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide)
			return;

		float speed = Math.abs(getSpeed());
		if (speed > 0) {
			fractionalXp += speed * ExperienceConstants.PUMP_XP_PER_RPM_PER_SECOND / 20.0d;
			int budget = (int) Math.floor(fractionalXp);
			if (budget > 0) {
				int transferred = transferExperience(budget);
				if (transferred > 0)
					fractionalXp -= transferred;
			}
			if (fractionalXp > MAX_BUDGET_BACKLOG)
				fractionalXp = MAX_BUDGET_BACKLOG;
		}

		tickPendingEmission();
	}

	private int transferExperience(int budget) {
		Direction outputSide = getOutputSide();
		OutputTarget output = resolveOutput(outputSide, budget);
		if (output.blocked() || output.capacity() <= 0)
			return 0;

		int outputBudget = Math.min(budget, output.capacity());
		int extracted = extractFromInput(getInputSide(), outputBudget);
		if (extracted <= 0)
			return 0;
		int accepted = output.accept(extracted);
		return Math.max(0, accepted);
	}

	private int extractFromInput(Direction inputSide, int budget) {
		BlockPos inputPos = worldPosition.relative(inputSide);
		BlockState inputState = level.getBlockState(inputPos);
		BlockEntity inputBE = level.getBlockEntity(inputPos);

		if (inputBE instanceof ExperienceSource source)
			return source.extractExperience(budget, false);

		int fromContainer = extractFromContainer(inputBE, inputSide.getOpposite(), budget);
		if (fromContainer > 0)
			return fromContainer;

		if (isOpenEndpoint(inputPos, inputState, inputSide))
			return suctionOpenWorld(inputSide, budget, isNozzleFacing(inputPos, inputSide));

		return 0;
	}

	private int extractFromContainer(@Nullable BlockEntity inputBE, Direction sideForContainer, int budget) {
		if (inputBE == null || budget <= 0)
			return 0;
		IItemHandler handler = inputBE.getCapability(ForgeCapabilities.ITEM_HANDLER, sideForContainer)
			.orElse(null);
		if (handler == null)
			return 0;

		int totalXp = 0;
		int remaining = budget;
		for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
			ItemStack peek = handler.extractItem(slot, handler.getSlotLimit(slot), true);
			if (peek.isEmpty())
				continue;
			int xpPerItem = xpValueOf(peek);
			if (xpPerItem <= 0 || xpPerItem > remaining)
				continue;
			int maxItems = Math.min(peek.getCount(), remaining / xpPerItem);
			if (maxItems <= 0)
				continue;
			ItemStack actual = handler.extractItem(slot, maxItems, false);
			if (actual.isEmpty())
				continue;
			int gained = actual.getCount() * xpPerItem;
			totalXp += gained;
			remaining -= gained;
		}
		return totalXp;
	}

	private static int xpValueOf(ItemStack stack) {
		if (stack.is(AllItems.EXP_NUGGET.get()))
			return ExperienceConstants.XP_PER_NUGGET;
		if (stack.getItem() instanceof ExperienceClusterBlockItem cluster)
			return cluster.getXpNuggetValue() * ExperienceConstants.XP_PER_NUGGET;
		return 0;
	}

	private int suctionOpenWorld(Direction inputSide, int budget, boolean nozzle) {
		double offset = nozzle ? NOZZLE_CENTER_OFFSET : NO_NOZZLE_CENTER_OFFSET;
		Vec3 center = Vec3.atCenterOf(worldPosition)
			.add(Vec3.atLowerCornerOf(inputSide.getNormal()).scale(offset));
		double speedMag = Math.abs(getSpeed());
		double normalizedSpeed = Math.min(speedMag / ExperienceConstants.SPEED_NORMALIZATION_RPM, 1.0d);
		double attractHalf = nozzle
			? Math.max(ABSORB_HALF_EXTENT, ATTRACT_HALF_EXTENT * speedMag / NOZZLE_ATTRACT_RPM_DIVISOR)
			: ATTRACT_HALF_EXTENT;
		AABB attractionBox = cubeAround(center, attractHalf);
		AABB absorbBox = cubeAround(center, ABSORB_HALF_EXTENT);
		int remaining = budget;
		int absorbed = 0;

		List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class, attractionBox, orb -> orb.isAlive());
		for (ExperienceOrb orb : orbs) {
			if (!absorbBox.contains(orb.position())) {
				attractOrb(orb, center, normalizedSpeed);
				continue;
			}
			if (remaining <= 0)
				continue;
			int value = orb.getValue();
			if (value <= 0)
				continue;
			int taken = Math.min(value, remaining);
			orb.discard();
			if (value > taken)
				ExperienceHelper.spawnExperience(level, orb.position(), value - taken);
			remaining -= taken;
			absorbed += taken;
		}

		if (remaining > 0) {
			List<Player> players = level.getEntitiesOfClass(Player.class, absorbBox,
				player -> player.isAlive() && !player.isSpectator());
			for (Player player : players) {
				if (remaining <= 0)
					break;
				int drained = ExperienceHelper.drainPlayerExperience(player, remaining);
				remaining -= drained;
				absorbed += drained;
			}
		}

		return absorbed;
	}

	private void attractOrb(ExperienceOrb orb, Vec3 center, double normalizedSpeed) {
		Vec3 delta = center.subtract(orb.position());
		double distSqr = delta.lengthSqr();
		if (distSqr < 1.0E-4d)
			return;
		double speedScale = 0.4d + 0.6d * normalizedSpeed;
		double accel = ATTRACT_ACCEL * speedScale;
		double maxSpeed = MAX_ATTRACT_SPEED * speedScale;
		Vec3 dir = delta.normalize();
		Vec3 next = orb.getDeltaMovement().add(dir.scale(accel));
		double speedSqr = next.lengthSqr();
		if (speedSqr > maxSpeed * maxSpeed)
			next = next.normalize().scale(maxSpeed);
		orb.setDeltaMovement(next);
		// ExperienceOrb's EntityType uses updateInterval(20); without forcing velocity sync,
		// clients only see position packets every second and the orb appears to teleport.
		orb.hurtMarked = true;
	}

	private OutputTarget resolveOutput(Direction outputSide, int budget) {
		BlockPos outputPos = worldPosition.relative(outputSide);
		BlockState outputState = level.getBlockState(outputPos);
		ExperienceSink sink = findExperienceSink(outputPos, outputState);
		if (sink != null) {
			int accepted = sink.insertExperience(budget, true);
			return accepted <= 0 ? OutputTarget.blockedTarget() : OutputTarget.sink(sink, accepted);
		}

		if (isOpenEndpoint(outputPos, outputState, outputSide))
			return OutputTarget.open(this, outputSide, isNozzleFacing(outputPos, outputSide), budget);

		return OutputTarget.blockedTarget();
	}

	@Nullable
	private ExperienceSink findExperienceSink(BlockPos pos, BlockState state) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof ExperienceSink sink)
			return sink;
		if (state.is(com.nobodiiiii.createbiotech.registry.CBBlocks.EVOKER_ENCHANTING_CHAMBER.get())
			&& state.getValue(EvokerEnchantingChamberBlock.HALF) == DoubleBlockHalf.UPPER) {
			BlockEntity lower = level.getBlockEntity(pos.below());
			if (lower instanceof EvokerEnchantingChamberBlockEntity chamber)
				return chamber;
		}
		return null;
	}

	private boolean isOpenEndpoint(BlockPos pos, BlockState state, Direction side) {
		if (state.isAir())
			return true;
		if (isNozzleFacing(pos, side))
			return true;
		return state.getCollisionShape(level, pos)
			.isEmpty();
	}

	private boolean isNozzleFacing(BlockPos pos, Direction side) {
		BlockState state = level.getBlockState(pos);
		// Our nozzle FACING is flipped from vanilla: it points toward the pump (== side.getOpposite()).
		return AllBlocks.NOZZLE.has(state) && state.hasProperty(BlockStateProperties.FACING)
			&& state.getValue(BlockStateProperties.FACING) == side.getOpposite();
	}

	private Direction getOutputSide() {
		return getBlockState().getValue(ExperiencePumpBlock.FACING);
	}

	private Direction getInputSide() {
		return getOutputSide().getOpposite();
	}

	private void bufferEmission(int amount) {
		if (amount <= 0)
			return;
		pendingEmissionXp += amount;
	}

	private void tickPendingEmission() {
		if (pendingEmissionXp <= 0) {
			pendingEmissionTicks = 0;
			return;
		}
		pendingEmissionTicks++;
		if (pendingEmissionXp >= EMISSION_LARGE_ORB_THRESHOLD || pendingEmissionTicks >= EMISSION_MAX_HOLD_TICKS)
			flushPendingEmission();
	}

	private void flushPendingEmission() {
		if (pendingEmissionXp <= 0 || level == null || level.isClientSide)
			return;
		Direction outputSide = getOutputSide();
		BlockPos outputPos = worldPosition.relative(outputSide);
		boolean nozzle = isNozzleFacing(outputPos, outputSide);
		// Spawn regardless of whether the output is still open — if a block was placed
		// in front after we buffered, physics will push the orb back into open space.
		// Discarding instead would silently lose XP.
		int amount = pendingEmissionXp;
		pendingEmissionXp = 0;
		pendingEmissionTicks = 0;
		spawnLargeOrb(outputSide, nozzle, amount);
	}

	private void spawnLargeOrb(Direction outputSide, boolean nozzle, int amount) {
		Vec3 direction = Vec3.atLowerCornerOf(outputSide.getNormal());
		Vec3 base = Vec3.atCenterOf(worldPosition)
			.add(direction.scale(nozzle ? 1.8d : 1.05d));
		double normalizedSpeed = Math.min(Math.abs(getSpeed()) / ExperienceConstants.SPEED_NORMALIZATION_RPM, 1.0d);
		ExperienceOrb orb = new ExperienceOrb(level, base.x, base.y, base.z, amount);
		if (nozzle)
			orb.setDeltaMovement(direction.scale(0.08d + normalizedSpeed * 0.18d));
		// ExperienceOrb's EntityType uses updateInterval(20); force velocity sync so the
		// initial motion isn't visually delayed by ~1s of position-only packets.
		orb.hurtMarked = true;
		level.addFreshEntity(orb);
	}

	private static AABB cubeAround(Vec3 center, double halfExtent) {
		return new AABB(center.x - halfExtent, center.y - halfExtent, center.z - halfExtent, center.x + halfExtent,
			center.y + halfExtent, center.z + halfExtent);
	}

	@Override
	protected void write(CompoundTag compound, boolean clientPacket) {
		super.write(compound, clientPacket);
		compound.putDouble("FractionalXp", fractionalXp);
		compound.putInt("PendingEmissionXp", pendingEmissionXp);
		compound.putInt("PendingEmissionTicks", pendingEmissionTicks);
	}

	@Override
	protected void read(CompoundTag compound, boolean clientPacket) {
		super.read(compound, clientPacket);
		fractionalXp = compound.getDouble("FractionalXp");
		pendingEmissionXp = compound.getInt("PendingEmissionXp");
		pendingEmissionTicks = compound.getInt("PendingEmissionTicks");
	}

	private record OutputTarget(boolean blocked, int capacity, @Nullable ExperienceSink sink,
		@Nullable ExperiencePumpBlockEntity pump, @Nullable Direction openSide, boolean nozzle) {

		static OutputTarget blockedTarget() {
			return new OutputTarget(true, 0, null, null, null, false);
		}

		static OutputTarget sink(ExperienceSink sink, int capacity) {
			return new OutputTarget(false, capacity, sink, null, null, false);
		}

		static OutputTarget open(ExperiencePumpBlockEntity pump, Direction side, boolean nozzle, int capacity) {
			return new OutputTarget(false, capacity, null, pump, side, nozzle);
		}

		int accept(int amount) {
			if (sink != null)
				return sink.insertExperience(amount, false);
			if (pump != null && openSide != null) {
				pump.bufferEmission(amount);
				return amount;
			}
			return 0;
		}
	}
}
