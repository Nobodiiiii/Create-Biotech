package com.nobodiiiii.createbiotech.content.magmacubeburner;

import java.util.List;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

public class MagmaCubeBurnerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

	public static final int TANK_CAPACITY = FluidType.BUCKET_VOLUME;
	public static final int LAVA_PER_RENDER_PIXEL = FluidType.BUCKET_VOLUME / 4;
	static final int BURNING_ANIMATION_PERIOD = 40;
	static final int BURNING_LANDING_TICK = 32;

	private static final String LAVA_TANK_TAG = "LavaTank";
	private static final String BURN_PROGRESS_TAG = "BurnProgress";
	private static final int LAVA_BUCKET_BURN_TIME =
		Math.max(1, Items.LAVA_BUCKET.getDefaultInstance().getBurnTime(null));

	private final FluidTank lavaTank = new FluidTank(TANK_CAPACITY) {
		@Override
		public boolean isFluidValid(FluidStack stack) {
			return stack.getFluid().is(FluidTags.LAVA);
		}

		@Override
		protected void onContentsChanged() {
			setChanged();
			needsClientSync = true;
		}
	};

	private final IFluidHandler fluidHandler = new LavaInputHandler();
	private final LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> fluidHandler);
	private int burnProgress;
	private boolean needsClientSync;
	private int lastSyncedLavaHeight = -1;
	@Nullable
	private HeatLevel lastClientHeatLevel;

	public MagmaCubeBurnerBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.MAGMA_CUBE_BURNER.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, MagmaCubeBurnerBlockEntity blockEntity) {
		blockEntity.tick();
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

	@Override
	public void tick() {
		super.tick();
		if (level == null)
			return;

		if (level.isClientSide) {
			tickClientParticles();
			return;
		}

		consumeLavaForOneTick();
		boolean heatChanged = updateHeatLevel();
		int lavaHeight = getRenderedLavaHeightPixels();
		boolean lavaHeightChanged = lavaHeight != lastSyncedLavaHeight;
		if (heatChanged || lavaHeightChanged || (needsClientSync && level.getGameTime() % 20 == 0)) {
			lastSyncedLavaHeight = lavaHeight;
			needsClientSync = false;
			notifyUpdate();
		}
	}

	private void consumeLavaForOneTick() {
		if (lavaTank.isEmpty()) {
			burnProgress = 0;
			return;
		}

		int bucketBurnTime = getLavaBucketBurnTime();
		// Consume exactly one lava bucket over the burn time reported by the lava bucket item.
		// The remainder keeps sub-millibucket precision without inventing an additional fuel duration.
		burnProgress += FluidType.BUCKET_VOLUME;
		int amountToDrain = burnProgress / bucketBurnTime;
		if (amountToDrain <= 0)
			return;

		FluidStack drained = lavaTank.drain(amountToDrain, IFluidHandler.FluidAction.EXECUTE);
		burnProgress -= drained.getAmount() * bucketBurnTime;
		if (lavaTank.isEmpty())
			burnProgress = 0;
	}

	private boolean updateHeatLevel() {
		BlockState state = getBlockState();
		if (!state.hasProperty(BlazeBurnerBlock.HEAT_LEVEL))
			return false;

		HeatLevel desired = getHeatLevel();
		if (state.getValue(BlazeBurnerBlock.HEAT_LEVEL) == desired)
			return false;

		level.setBlock(worldPosition, state.setValue(BlazeBurnerBlock.HEAT_LEVEL, desired), Block.UPDATE_ALL);
		if (level.getBlockEntity(worldPosition.above()) instanceof BasinBlockEntity basin)
			basin.notifyChangeOfContents();
		return true;
	}

	private HeatLevel getHeatLevel() {
		if (lavaTank.isEmpty())
			return HeatLevel.SMOULDERING;
		boolean lowPercent = (double) getRemainingBurnTime() / getLavaBucketBurnTime() < .0125;
		return lowPercent ? HeatLevel.FADING : HeatLevel.KINDLED;
	}

	private void tickClientParticles() {
		HeatLevel heatLevel = getBlockState().getOptionalValue(BlazeBurnerBlock.HEAT_LEVEL)
			.orElse(HeatLevel.SMOULDERING);
		if (lastClientHeatLevel != null && !MagmaCubeBurnerBlock.isBurning(lastClientHeatLevel)
			&& MagmaCubeBurnerBlock.isBurning(heatLevel))
			spawnStartBurningParticleBurst();
		lastClientHeatLevel = heatLevel;

		if (!MagmaCubeBurnerBlock.isBurning(heatLevel))
			return;
		spawnBurningParticles();
		if (Math.floorMod(level.getGameTime(), BURNING_ANIMATION_PERIOD) == BURNING_LANDING_TICK)
			spawnMagmaCubeLandingParticles();
	}

	private void spawnBurningParticles() {

		RandomSource random = level.getRandom();
		if (random.nextInt(4) != 0)
			return;

		Vec3 center = VecHelper.getCenterOf(worldPosition);
		Vec3 smoke = center.add(VecHelper.offsetRandomly(Vec3.ZERO, random, .125f).multiply(1, 0, 1));
		boolean openAbove = level.getBlockState(worldPosition.above())
			.getCollisionShape(level, worldPosition.above()).isEmpty();
		if (openAbove || random.nextInt(8) == 0)
			level.addParticle(ParticleTypes.LARGE_SMOKE, smoke.x, smoke.y, smoke.z, 0, 0, 0);

		double yMotion = openAbove ? .0625 : random.nextDouble() * .0125;
		Vec3 flame = center.add(VecHelper.offsetRandomly(Vec3.ZERO, random, .5f)
			.multiply(1, .25, 1)
			.normalize()
			.scale((openAbove ? .25 : .5) + random.nextDouble() * .125))
			.add(0, .5, 0);
		level.addParticle(ParticleTypes.FLAME, flame.x, flame.y, flame.z, 0, yMotion, 0);
	}

	private void spawnStartBurningParticleBurst() {
		Vec3 center = VecHelper.getCenterOf(worldPosition);
		RandomSource random = level.getRandom();
		for (int i = 0; i < 20; i++) {
			Vec3 offset = VecHelper.offsetRandomly(Vec3.ZERO, random, .5f)
				.multiply(1, .25f, 1)
				.normalize();
			Vec3 position = center.add(offset.scale(.5 + random.nextDouble() * .125))
				.add(0, .125, 0);
			Vec3 motion = offset.scale(1 / 32f);
			level.addParticle(ParticleTypes.FLAME, position.x, position.y, position.z,
				motion.x, motion.y, motion.z);
		}
	}

	private void spawnMagmaCubeLandingParticles() {
		RandomSource random = level.getRandom();
		float diameter = EntityType.MAGMA_CUBE.getDimensions().width * 2;
		float radius = diameter / 2;
		double centerX = worldPosition.getX() + .5;
		double y = worldPosition.getY() + 2 / 16d;
		double centerZ = worldPosition.getZ() + .5;
		for (int i = 0; i < diameter * 16; i++) {
			float angle = random.nextFloat() * Mth.TWO_PI;
			float distance = random.nextFloat() * .5f + .5f;
			float xOffset = Mth.sin(angle) * radius * distance;
			float zOffset = Mth.cos(angle) * radius * distance;
			level.addParticle(ParticleTypes.FLAME, centerX + xOffset, y, centerZ + zOffset, 0, 0, 0);
		}
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
		if (cap == ForgeCapabilities.FLUID_HANDLER)
			return fluidCapability.cast();
		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		fluidCapability.invalidate();
	}

	public FluidStack getLavaFluidForRender() {
		return lavaTank.getFluid();
	}

	public int getRenderedLavaHeightPixels() {
		int amount = lavaTank.getFluidAmount();
		if (amount <= 0)
			return 0;
		// The visible chamber spans four pixels: 250, 500, 750 and 1000 mB.
		return Math.min(4, (amount + LAVA_PER_RENDER_PIXEL - 1) / LAVA_PER_RENDER_PIXEL);
	}

	public int getRemainingBurnTime() {
		if (lavaTank.isEmpty())
			return 0;
		long numerator = (long) lavaTank.getFluidAmount() * getLavaBucketBurnTime() - burnProgress;
		return (int) Math.max(0, (numerator + FluidType.BUCKET_VOLUME - 1) / FluidType.BUCKET_VOLUME);
	}

	public static int getLavaBucketBurnTime() {
		return LAVA_BUCKET_BURN_TIME;
	}

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		super.write(tag, clientPacket);
		tag.put(LAVA_TANK_TAG, lavaTank.writeToNBT(new CompoundTag()));
		tag.putInt(BURN_PROGRESS_TAG, burnProgress);
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		lavaTank.readFromNBT(tag.getCompound(LAVA_TANK_TAG));
		burnProgress = Math.min(Math.max(0, tag.getInt(BURN_PROGRESS_TAG)), getLavaBucketBurnTime() - 1);
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		tooltip.add(Component.translatable("create_biotech.magma_cube_burner.goggles.burn_time",
			getRemainingBurnTime() / 20).withStyle(ChatFormatting.GOLD));
		return containedFluidTooltip(tooltip, isPlayerSneaking, fluidCapability);
	}

	private class LavaInputHandler implements IFluidHandler {

		@Override
		public int getTanks() {
			return 1;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			return tank == 0 ? lavaTank.getFluid() : FluidStack.EMPTY;
		}

		@Override
		public int getTankCapacity(int tank) {
			return tank == 0 ? lavaTank.getCapacity() : 0;
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return tank == 0 && lavaTank.isFluidValid(stack);
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			return lavaTank.fill(resource, action);
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			return FluidStack.EMPTY;
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			return FluidStack.EMPTY;
		}
	}
}
