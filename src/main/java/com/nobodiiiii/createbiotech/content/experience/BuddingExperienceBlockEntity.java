package com.nobodiiiii.createbiotech.content.experience;

import java.util.ArrayList;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.RegistryObject;

public class BuddingExperienceBlockEntity extends BlockEntity implements IHaveGoggleInformation {

	private final int[] faceFluidAmounts = new int[6];
	private final LazyOptional<IFluidHandler> fluidHandlerCap = LazyOptional.of(() -> new ExperienceInputFluidHandler());

	public BuddingExperienceBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.BUDDING_EXPERIENCE.get(), pos, state);
	}

	public int insertFluid(int amount, boolean simulate) {
		if (amount <= 0 || level == null)
			return 0;
		List<Direction> open = findOpenFaces();
		if (open.isEmpty())
			return 0;
		if (simulate)
			return amount;

		Direction face = open.get(level.random.nextInt(open.size()));
		applyFluidToFace(face, amount);
		return amount;
	}

	public int getFluidSpace() {
		if (level == null)
			return 0;
		return findOpenFaces().isEmpty() ? 0 : Integer.MAX_VALUE / 4;
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

		int newAmount = Math.min(ExperienceConstants.buddingMatureXp(), faceFluidAmounts[idx] + amount);
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
		syncToClient();
	}

	private boolean canPlaceStage(ServerLevel serverLevel, Direction face, int currentStage) {
		BlockPos adjacent = worldPosition.relative(face);
		BlockState state = serverLevel.getBlockState(adjacent);
		if (currentStage == 0) {
			return state.isAir() || state.getFluidState()
				.getType() == Fluids.WATER;
		}
		RegistryObject<? extends Block> expected = blockForStage(currentStage);
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
		RegistryObject<? extends Block> expected = blockForStage(stage);
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
		RegistryObject<? extends Block> blockRef = blockForStage(stage);
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

	private static RegistryObject<? extends Block> blockForStage(int stage) {
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
		syncToClient();
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		for (int i = 0; i < 6; i++)
			tag.putInt("Face" + i, faceFluidAmounts[i]);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		for (int i = 0; i < 6; i++)
			faceFluidAmounts[i] = tag.getInt("Face" + i);
	}

	@Override
	public CompoundTag getUpdateTag() {
		return saveWithoutMetadata();
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

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
		if (cap == ForgeCapabilities.FLUID_HANDLER)
			return fluidHandlerCap.cast();
		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		fluidHandlerCap.invalidate();
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
