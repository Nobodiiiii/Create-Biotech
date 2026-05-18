package com.nobodiiiii.createbiotech.content.experience;

import java.util.List;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

public class ExperienceCrystallizerBlockEntity extends BlockEntity implements ExperienceSink, IHaveGoggleInformation {
	private static final int MAX_STACK = 64;

	private ItemStack output = ItemStack.EMPTY;
	private int bufferedXp;
	private final LazyOptional<IItemHandler> itemHandlerCap;

	public ExperienceCrystallizerBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.EXPERIENCE_CRYSTALLIZER.get(), pos, state);
		itemHandlerCap = LazyOptional.of(this::createItemHandler);
	}

	@Override
	public int insertExperience(int amount, boolean simulate) {
		if (amount <= 0)
			return 0;
		int accepted = Math.min(amount, getExperienceSpace());
		if (simulate || accepted <= 0)
			return accepted;

		bufferedXp += accepted;
		while (bufferedXp >= ExperienceConstants.XP_PER_NUGGET && getOutputCount() < MAX_STACK) {
			bufferedXp -= ExperienceConstants.XP_PER_NUGGET;
			addNugget();
		}
		syncToClient();
		return accepted;
	}

	@Override
	public int getExperienceSpace() {
		if (getOutputCount() >= MAX_STACK)
			return 0;
		return (MAX_STACK - getOutputCount()) * ExperienceConstants.XP_PER_NUGGET
			+ (ExperienceConstants.XP_PER_NUGGET - 1 - bufferedXp);
	}

	@Override
	public boolean isExperienceInputBlocked() {
		return getOutputCount() >= MAX_STACK;
	}

	public boolean hasOutput() {
		return !output.isEmpty();
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CreateLang.builder()
			.add(Component.translatable("create_biotech.gui.goggles.experience_buffer"))
			.forGoggles(tooltip);

		String unitKey = "create_biotech.generic.unit.experience_points";
		CreateLang.builder()
			.add(CreateLang.number(bufferedXp)
				.add(Component.translatable(unitKey))
				.style(ChatFormatting.GOLD))
			.text(ChatFormatting.GRAY, " / ")
			.add(CreateLang.number(ExperienceConstants.XP_PER_NUGGET)
				.add(Component.translatable(unitKey))
				.style(ChatFormatting.DARK_GRAY))
			.forGoggles(tooltip, 1);

		if (!output.isEmpty()) {
			CreateLang.builder()
				.add(Component.translatable("create_biotech.gui.goggles.crystallized_nuggets"))
				.forGoggles(tooltip);
			CreateLang.builder()
				.add(CreateLang.number(output.getCount())
					.style(ChatFormatting.GOLD))
				.text(ChatFormatting.GRAY, " / ")
				.add(CreateLang.number(MAX_STACK)
					.style(ChatFormatting.DARK_GRAY))
				.forGoggles(tooltip, 1);
		}

		return true;
	}

	public ItemStack extractOutput() {
		if (output.isEmpty())
			return ItemStack.EMPTY;
		ItemStack extracted = output.copy();
		output = ItemStack.EMPTY;
		syncToClient();
		return extracted;
	}

	private int getOutputCount() {
		return output.isEmpty() ? 0 : output.getCount();
	}

	private void addNugget() {
		if (output.isEmpty()) {
			output = new ItemStack(AllItems.EXP_NUGGET.get());
			return;
		}
		output.grow(1);
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putInt("BufferedXp", bufferedXp);
		if (!output.isEmpty())
			tag.put("Output", output.serializeNBT());
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		bufferedXp = tag.getInt("BufferedXp");
		output = tag.contains("Output") ? ItemStack.of(tag.getCompound("Output")) : ItemStack.EMPTY;
	}

	@Override
	public CompoundTag getUpdateTag() {
		return saveWithoutMetadata();
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
		if (cap == ForgeCapabilities.ITEM_HANDLER)
			return itemHandlerCap.cast();
		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		itemHandlerCap.invalidate();
	}

	private void syncToClient() {
		if (level == null)
			return;
		setChanged();
		BlockState state = getBlockState();
		level.sendBlockUpdated(worldPosition, state, state, 3);
	}

	private IItemHandler createItemHandler() {
		return new IItemHandler() {
			@Override
			public int getSlots() {
				return 1;
			}

			@Override
			public ItemStack getStackInSlot(int slot) {
				return slot == 0 ? output : ItemStack.EMPTY;
			}

			@Override
			public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
				return stack;
			}

			@Override
			public ItemStack extractItem(int slot, int amount, boolean simulate) {
				if (slot != 0 || amount <= 0 || output.isEmpty())
					return ItemStack.EMPTY;
				int extractedAmount = Math.min(amount, output.getCount());
				ItemStack extracted = output.copy();
				extracted.setCount(extractedAmount);
				if (!simulate) {
					output.shrink(extractedAmount);
					if (output.isEmpty())
						output = ItemStack.EMPTY;
					syncToClient();
				}
				return extracted;
			}

			@Override
			public int getSlotLimit(int slot) {
				return MAX_STACK;
			}

			@Override
			public boolean isItemValid(int slot, ItemStack stack) {
				return false;
			}
		};
	}
}
