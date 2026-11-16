package com.nobodiiiii.createbiotech.content.experience;

import net.minecraft.core.HolderLookup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.registries.DeferredHolder;

public class BuddingExperienceBlockEntity extends BlockEntity implements IHaveGoggleInformation {

	private final int[] faceFluidAmounts = new int[6];
	private final long[] faceOverflowAmounts = new long[6];
	private int[] savedGrowthThresholds = new int[0];
	private final IFluidHandler fluidHandler = new ExperienceInputFluidHandler();

	public BuddingExperienceBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.BUDDING_EXPERIENCE.get(), pos, state);
	}

	public int insertFluid(int amount, boolean simulate) {
		if (amount <= 0 || level == null)
			return 0;
		List<Direction> open = findOpenFaces();
		if (open.isEmpty())
			return 0;
		int accepted = Math.min(amount, getFluidSpace());
		if (accepted == 0)
			return 0;
		if (simulate)
			return accepted;

		open.removeIf(face -> getFaceFluidSpace(face) < accepted);
		Direction face = open.get(level.random.nextInt(open.size()));
		applyFluidToFace(face, accepted);
		return accepted;
	}

	public int getFluidSpace() {
		if (level == null)
			return 0;
		long space = 0;
		for (Direction face : findOpenFaces())
			space = Math.max(space, getFaceFluidSpace(face));
		return (int) Math.min(Integer.MAX_VALUE, space);
	}

	private long getFaceFluidSpace(Direction face) {
		int idx = face.get3DDataValue();
		return Math.max(0, Long.MAX_VALUE - faceOverflowAmounts[idx] - faceFluidAmounts[idx]);
	}

	public boolean isFluidInputBlocked() {
		return level == null || findOpenFaces().isEmpty();
	}

	public void naturalTick(ServerLevel serverLevel, BlockPos pos, RandomSource random) {
		if (random.nextInt(ExperienceConstants.buddingGrowthChance()) != 0)
			return;
		Direction face = Direction.values()[random.nextInt(6)];
		int idx = face.get3DDataValue();
		int currentStage = stageOf(faceFluidAmounts[idx]);
		if (!worldMatchesStage(face, currentStage)) {
			faceFluidAmounts[idx] = 0;
			currentStage = 0;
		}
		if (currentStage >= 4)
			return;
		if (faceOverflowAmounts[idx] > 0) {
			applyFluidToFace(face, 0);
			return;
		}
		int targetStage = currentStage + 1;
		if (!canPlaceStage(serverLevel, face, currentStage))
			return;
		placeStageBlock(serverLevel, face, targetStage);
		faceFluidAmounts[idx] = stageThreshold(targetStage);
		syncToClient();
	}

	private void applyFluidToFace(Direction face, int amount) {
		int idx = face.get3DDataValue();
		int currentStage = stageOf(faceFluidAmounts[idx]);
		if (!worldMatchesStage(face, currentStage)) {
			faceFluidAmounts[idx] = 0;
			currentStage = 0;
		}

		if (currentStage == 4) {
			harvestMatureCluster(face);
			faceFluidAmounts[idx] = 0;
			currentStage = 0;
		}

		long available = faceOverflowAmounts[idx] + faceFluidAmounts[idx] + amount;
		int newAmount = (int) Math.min(ExperienceConstants.buddingMatureXp(), available);
		int newStage = stageOf(newAmount);

		while (currentStage < newStage) {
			int targetStage = currentStage + 1;
			if (!(level instanceof ServerLevel serverLevel)
				|| !canPlaceStage(serverLevel, face, currentStage)) {
				newAmount = Math.min(newAmount, stageThreshold(currentStage + 1) - 1);
				break;
			}
			placeStageBlock(serverLevel, face, targetStage);
			currentStage = targetStage;
		}

		faceFluidAmounts[idx] = newAmount;
		faceOverflowAmounts[idx] = available - newAmount;
		syncToClient();
	}

	private boolean canPlaceStage(ServerLevel serverLevel, Direction face, int currentStage) {
		BlockPos adjacent = worldPosition.relative(face);
		BlockState state = serverLevel.getBlockState(adjacent);
		if (currentStage == 0) {
			return state.isAir() || state.getFluidState()
				.getType() == Fluids.WATER;
		}
		DeferredHolder<Block, ? extends Block> expected = blockForStage(currentStage);
		return expected != null && state.is(expected.get()) && state.hasProperty(ExperienceClusterBlock.FACING)
			&& state.getValue(ExperienceClusterBlock.FACING) == face;
	}

	private boolean worldMatchesStage(Direction face, int stage) {
		if (level == null)
			return false;
		BlockState state = level.getBlockState(worldPosition.relative(face));
		if (stage == 0) {
			if (!(state.getBlock() instanceof ExperienceClusterBlock))
				return true;
			return state.getValue(ExperienceClusterBlock.FACING) != face;
		}
		DeferredHolder<Block, ? extends Block> expected = blockForStage(stage);
		if (expected == null)
			return false;
		return state.is(expected.get()) && state.hasProperty(ExperienceClusterBlock.FACING)
			&& state.getValue(ExperienceClusterBlock.FACING) == face;
	}

	private void harvestMatureCluster(Direction face) {
		if (!(level instanceof ServerLevel serverLevel))
			return;
		BlockPos clusterPos = worldPosition.relative(face);
		BlockState clusterState = serverLevel.getBlockState(clusterPos);
		if (!(clusterState.getBlock() instanceof ExperienceClusterBlock))
			return;
		Block.popResource(serverLevel, clusterPos, new ItemStack(CBItems.EXPERIENCE_CLUSTER.get()));
		CBAdvancements.awardNearby(serverLevel, clusterPos, 16, CBAdvancements.EXPERIENCE_CLUSTER);
		boolean waterlogged = clusterState.getValue(ExperienceClusterBlock.WATERLOGGED);
		BlockState replacement = waterlogged ? Fluids.WATER.defaultFluidState()
			.createLegacyBlock() : Blocks.AIR.defaultBlockState();
		serverLevel.setBlock(clusterPos, replacement, Block.UPDATE_ALL);
	}

	private void placeStageBlock(ServerLevel serverLevel, Direction face, int stage) {
		DeferredHolder<Block, ? extends Block> blockRef = blockForStage(stage);
		if (blockRef == null)
			return;
		BlockPos adjacent = worldPosition.relative(face);
		boolean waterlogged = serverLevel.getFluidState(adjacent)
			.getType() == Fluids.WATER;
		BlockState state = blockRef.get()
			.defaultBlockState()
			.setValue(ExperienceClusterBlock.FACING, face)
			.setValue(ExperienceClusterBlock.WATERLOGGED, waterlogged);
		serverLevel.setBlock(adjacent, state, Block.UPDATE_ALL);
	}

	private List<Direction> findOpenFaces() {
		List<Direction> open = new ArrayList<>(6);
		if (level == null)
			return open;
		for (Direction dir : Direction.values()) {
			int idx = dir.get3DDataValue();
			int storedStage = stageOf(faceFluidAmounts[idx]);
			if (worldMatchesStage(dir, storedStage)) {
				if (storedStage > 0) {
					open.add(dir);
					continue;
				}
				BlockState state = level.getBlockState(worldPosition.relative(dir));
				if (state.isAir() || state.getFluidState()
					.getType() == Fluids.WATER)
					open.add(dir);
				continue;
			}
			BlockState state = level.getBlockState(worldPosition.relative(dir));
			if (state.isAir() || state.getFluidState()
				.getType() == Fluids.WATER)
				open.add(dir);
		}
		return open;
	}

	private static int stageOf(int fluidAmount) {
		if (fluidAmount >= ExperienceConstants.buddingMatureXp())
			return 4;
		if (fluidAmount >= ExperienceConstants.largeBudXpValue())
			return 3;
		if (fluidAmount >= ExperienceConstants.mediumBudXpValue())
			return 2;
		if (fluidAmount >= ExperienceConstants.smallBudXpValue())
			return 1;
		return 0;
	}

	private static int stageThreshold(int stage) {
		return switch (stage) {
			case 1 -> ExperienceConstants.smallBudXpValue();
			case 2 -> ExperienceConstants.mediumBudXpValue();
			case 3 -> ExperienceConstants.largeBudXpValue();
			case 4 -> ExperienceConstants.buddingMatureXp();
			default -> 0;
		};
	}

	private static DeferredHolder<Block, ? extends Block> blockForStage(int stage) {
		return switch (stage) {
			case 1 -> CBBlocks.SMALL_EXPERIENCE_BUD;
			case 2 -> CBBlocks.MEDIUM_EXPERIENCE_BUD;
			case 3 -> CBBlocks.LARGE_EXPERIENCE_BUD;
			case 4 -> CBBlocks.EXPERIENCE_CLUSTER;
			default -> null;
		};
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CreateLang.builder()
			.add(Component.translatable("create_biotech.gui.goggles.budding_experience"))
			.forGoggles(tooltip);
		for (Direction dir : Direction.values()) {
			int idx = dir.get3DDataValue();
			int fluidAmount = faceFluidAmounts[idx];
			int stage = stageOf(fluidAmount);
			String stageKey = "create_biotech.gui.goggles.budding_experience.stage." + stage;
			String facingKey = "create_biotech.gui.goggles.budding_experience.facing." + dir.getName();
			CreateLang.builder()
				.add(Component.translatable(facingKey)
					.withStyle(ChatFormatting.GRAY))
				.text(": ")
				.add(Component.translatable(stageKey)
					.withStyle(ChatFormatting.GOLD))
				.text(ChatFormatting.DARK_GRAY,
					" (" + fluidAmount + " / " + ExperienceConstants.buddingMatureXp() + " mB)")
				.forGoggles(tooltip, 1);
		}
		return true;
	}

	public int getFaceFluidAmount(Direction direction) {
		return faceFluidAmounts[direction.get3DDataValue()];
	}

	public void resetFaceState(Direction face) {
		int idx = face.get3DDataValue();
		if (faceFluidAmounts[idx] == 0)
			return;
		faceFluidAmounts[idx] = 0;
		// Harvesting consumes this growth cycle, retaining paid overflow on this face.
		syncToClient();
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if (level == null || level.isClientSide)
			return;
		migrateGrowthProgress();
	}

	private static int[] growthThresholds() {
		return new int[] { stageThreshold(1), stageThreshold(2), stageThreshold(3), stageThreshold(4) };
	}

	private void migrateGrowthProgress() {
		int[] current = growthThresholds();
		if (Arrays.equals(savedGrowthThresholds, current))
			return;
		int[] previous = savedGrowthThresholds;
		if (previous.length != 4) {
			// The published format stored only Face0..Face5, using these old defaults.
			// An unchanged custom threshold retains its original units.
			previous = new int[] { current[0] == 24 ? 16 : current[0], current[1] == 48 ? 32 : current[1],
				current[2] == 96 ? 64 : current[2], current[3] == 192 ? 128 : current[3] };
		}
		for (int i = 0; i < 6; i++) {
			faceFluidAmounts[i] = rebaseGrowthProgress(faceFluidAmounts[i], previous, current);
			faceOverflowAmounts[i] = Math.min(Long.MAX_VALUE - faceFluidAmounts[i], faceOverflowAmounts[i]);
		}
		savedGrowthThresholds = current;
		setChanged();
	}

	private static int rebaseGrowthProgress(int amount, int[] previous, int[] current) {
		if (amount <= 0)
			return 0;
		int oldLower = 0;
		int newLower = 0;
		for (int i = 0; i < 4; i++) {
			if (amount < previous[i]) {
				long offset = (long) (amount - oldLower) * Math.max(0, current[i] - newLower)
					/ Math.max(1, previous[i] - oldLower);
				return (int) Math.max(newLower, Math.min((long) current[i] - 1, newLower + offset));
			}
			oldLower = previous[i];
			newLower = current[i];
		}
		return current[3];
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		for (int i = 0; i < 6; i++)
			tag.putInt("Face" + i, faceFluidAmounts[i]);
		tag.putLongArray("FaceOverflow", faceOverflowAmounts.clone());
		tag.putIntArray("GrowthThresholds", growthThresholds());
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		for (int i = 0; i < 6; i++)
			faceFluidAmounts[i] = Math.max(0, tag.getInt("Face" + i));
		long[] overflow = tag.getLongArray("FaceOverflow");
		for (int i = 0; i < 6; i++)
			faceOverflowAmounts[i] = i < overflow.length
				? Math.min(Long.MAX_VALUE - faceFluidAmounts[i], Math.max(0, overflow[i])) : 0;
		savedGrowthThresholds = tag.getIntArray("GrowthThresholds");
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	private void syncToClient() {
		if (level == null)
			return;
		setChanged();
		BlockState state = getBlockState();
		level.sendBlockUpdated(worldPosition, state, state, 3);
	}

	public IFluidHandler getFluidCapability(Direction side) {
		return fluidHandler;
	}

	private class ExperienceInputFluidHandler implements IFluidHandler {
		@Override
		public int getTanks() {
			return 1;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			return FluidStack.EMPTY;
		}

		@Override
		public int getTankCapacity(int tank) {
			return tank == 0 ? getFluidSpace() : 0;
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return tank == 0 && ExperienceFluidHelper.isExperience(stack);
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			if (!ExperienceFluidHelper.isExperience(resource))
				return 0;
			return insertFluid(resource.getAmount(), action.simulate());
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
