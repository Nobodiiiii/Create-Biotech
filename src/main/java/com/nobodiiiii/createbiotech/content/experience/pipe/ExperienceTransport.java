package com.nobodiiiii.createbiotech.content.experience.pipe;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberBlock;
import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberBlockEntity;
import com.nobodiiiii.createbiotech.content.experience.ExperienceClusterBlockItem;
import com.nobodiiiii.createbiotech.content.experience.ExperienceConstants;
import com.nobodiiiii.createbiotech.content.experience.ExperienceSink;
import com.nobodiiiii.createbiotech.content.experience.ExperienceSource;
import com.simibubi.create.AllItems;

import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

public final class ExperienceTransport {

	private ExperienceTransport() {}

	@Nullable
	public static ExperienceEndpoint findEndpoint(LevelAccessor world, BlockFace location) {
		BlockPos targetPos = location.getConnectedPos();
		BlockState targetState = world.getBlockState(targetPos);
		BlockEntity blockEntity = world.getBlockEntity(targetPos);
		Direction side = location.getOppositeFace();

		ExperienceSource source = resolveSource(blockEntity);
		ExperienceSink sink = resolveSink(world, targetPos, targetState, blockEntity);
		if (source != null || sink != null)
			return new DirectEndpoint(source, sink);

		IItemHandler itemHandler = resolveItemHandler(blockEntity, side);
		if (itemHandler != null)
			return new ItemHandlerEndpoint(itemHandler);

		return null;
	}

	public static boolean hasEndpoint(BlockGetter world, BlockPos pos, Direction side) {
		if (!(world instanceof LevelAccessor levelAccessor))
			return false;
		return findEndpoint(levelAccessor, new BlockFace(pos.relative(side), side.getOpposite())) != null;
	}

	@Nullable
	private static ExperienceSource resolveSource(@Nullable BlockEntity blockEntity) {
		return blockEntity instanceof ExperienceSource source ? source : null;
	}

	@Nullable
	private static ExperienceSink resolveSink(LevelAccessor world, BlockPos pos, BlockState state,
		@Nullable BlockEntity blockEntity) {
		if (blockEntity instanceof ExperienceSink sink)
			return sink;
		if (!state.getBlock()
			.equals(com.nobodiiiii.createbiotech.registry.CBBlocks.EVOKER_ENCHANTING_CHAMBER.get()))
			return null;
		if (!state.hasProperty(EvokerEnchantingChamberBlock.HALF)
			|| state.getValue(EvokerEnchantingChamberBlock.HALF) != DoubleBlockHalf.UPPER)
			return null;
		BlockEntity lower = world.getBlockEntity(pos.below());
		return lower instanceof EvokerEnchantingChamberBlockEntity chamber ? chamber : null;
	}

	@Nullable
	private static IItemHandler resolveItemHandler(@Nullable BlockEntity blockEntity, Direction side) {
		if (blockEntity == null)
			return null;
		return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side)
			.orElse(null);
	}

	private static int xpValueOf(ItemStack stack) {
		if (stack.is(AllItems.EXP_NUGGET.get()))
			return ExperienceConstants.XP_PER_NUGGET;
		if (stack.getItem() instanceof ExperienceClusterBlockItem cluster)
			return cluster.getXpNuggetValue() * ExperienceConstants.XP_PER_NUGGET;
		return 0;
	}

	private record DirectEndpoint(@Nullable ExperienceSource source, @Nullable ExperienceSink sink)
		implements ExperienceEndpoint {

		@Override
		public int extract(int maxAmount, boolean simulate) {
			return source == null ? 0 : source.extractExperience(maxAmount, simulate);
		}

		@Override
		public int insert(int amount, boolean simulate) {
			return sink == null ? 0 : sink.insertExperience(amount, simulate);
		}
	}

	private record ItemHandlerEndpoint(IItemHandler itemHandler) implements ExperienceEndpoint {

		@Override
		public int extract(int maxAmount, boolean simulate) {
			if (maxAmount <= 0)
				return 0;
			int totalXp = 0;
			int remaining = maxAmount;
			for (int slot = 0; slot < itemHandler.getSlots() && remaining > 0; slot++) {
				ItemStack peek = itemHandler.extractItem(slot, itemHandler.getSlotLimit(slot), true);
				if (peek.isEmpty())
					continue;
				int xpPerItem = xpValueOf(peek);
				if (xpPerItem <= 0 || xpPerItem > remaining)
					continue;
				int maxItems = Math.min(peek.getCount(), remaining / xpPerItem);
				if (maxItems <= 0)
					continue;
				ItemStack actual = itemHandler.extractItem(slot, maxItems, simulate);
				if (actual.isEmpty())
					continue;
				int gained = actual.getCount() * xpPerItem;
				totalXp += gained;
				remaining -= gained;
			}
			return totalXp;
		}

		@Override
		public boolean canExtract() {
			for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
				ItemStack peek = itemHandler.extractItem(slot, itemHandler.getSlotLimit(slot), true);
				if (peek.isEmpty())
					continue;
				if (xpValueOf(peek) > 0)
					return true;
			}
			return false;
		}

		@Override
		public int insert(int amount, boolean simulate) {
			return 0;
		}
	}
}
