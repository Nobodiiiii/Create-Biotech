package com.nobodiiiii.createbiotech.content.surgery;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.foundation.item.CBItemData;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.content.equipment.toolbox.ToolboxHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SurgicalKitBlockEntity extends BlockEntity {
	private static final String LOAN = "SurgicalKitLoan";
	private static final Map<Level, Set<SurgicalKitBlockEntity>> CLIENT_KITS = new WeakHashMap<>();
	private ItemStack storedKit;
	@Nullable
	private UUID loanId;

	public SurgicalKitBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.SURGICAL_KIT.get(), pos, state);
		storedKit = new ItemStack(state.getBlock());
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if (level != null && level.isClientSide)
			CLIENT_KITS.computeIfAbsent(level, key -> Collections.newSetFromMap(new WeakHashMap<>())).add(this);
	}

	@Override
	public void setRemoved() {
		if (level != null && level.isClientSide) {
			Set<SurgicalKitBlockEntity> kits = CLIENT_KITS.get(level);
			if (kits != null)
				kits.remove(this);
		}
		super.setRemoved();
	}

	@Nullable
	public static SurgicalKitBlockEntity nearest(Player player) {
		return CLIENT_KITS.getOrDefault(player.level(), Set.of()).stream().filter(kit -> kit.inRange(player))
			.min(Comparator.comparingDouble(kit -> ToolboxHandler.distance(player.position(), kit.worldPosition)))
			.orElse(null);
	}

	public boolean inRange(Player player) {
		double range = ToolboxHandler.getMaxRange(player);
		return !isRemoved() && level == player.level()
			&& ToolboxHandler.distance(player.position(), worldPosition) < range * range;
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		loanId = tag.hasUUID("Loan") ? tag.getUUID("Loan") : null;
		storedKit = loanId != null ? ItemStack.EMPTY : tag.contains("Kit")
			? ItemStack.parseOptional(registries, tag.getCompound("Kit")) : new ItemStack(getBlockState().getBlock());
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (loanId != null)
			tag.putUUID("Loan", loanId);
		else
			tag.put("Kit", storedKit.saveOptional(registries));
	}

	@Override
	public boolean onlyOpCanSetNbt() {
		return true;
	}

	void storePlacedStack(ItemStack stack) {
		setComponents(DataComponentMap.EMPTY);
		storedKit = stack.copyWithCount(1);
		loanId = null;
		setChanged();
	}

	ItemStack copyStoredKit() {
		return storedKit.copy();
	}

	void select(ServerPlayer player, int slot, @Nullable SurgicalKitItem.Tool tool) {
		Inventory inventory = player.getInventory();
		if (loanId != null) {
			selectLoanedTool(inventory, slot, tool);
			return;
		}
		if (tool == null) {
			SurgicalKitItem.setSelectedTool(storedKit, null);
			setChanged();
			return;
		}
		if (!SurgicalKitItem.availableTools(storedKit).contains(tool))
			return;
		ItemStack held = inventory.getItem(slot);
		if (!held.isEmpty()) {
			int freeSlot = inventory.getFreeSlot();
			if (freeSlot < 0)
				return;
			inventory.setItem(freeSlot, held);
		}
		loanId = UUID.randomUUID();
		CompoundTag loan = new CompoundTag();
		loan.putUUID("Id", loanId);
		loan.putLong("Pos", worldPosition.asLong());
		loan.putString("Dimension", player.level().dimension().location().toString());
		CBItemData.edit(storedKit, tag -> tag.put(LOAN, loan));
		SurgicalKitItem.setSelectedTool(storedKit, tool);
		inventory.setItem(slot, storedKit);
		storedKit = ItemStack.EMPTY;
		inventory.setChanged();
		setChanged();
	}

	private void selectLoanedTool(Inventory inventory, int slot, @Nullable SurgicalKitItem.Tool tool) {
		for (int index = 0; index < inventory.getContainerSize(); index++) {
			ItemStack stack = inventory.getItem(index);
			CompoundTag loan = loanData(stack);
			if (loan == null || !matchingLoan(loan, stack))
				continue;
			if (tool == null) {
				acceptReturn(inventory.removeItemNoUpdate(index));
			} else if (SurgicalKitItem.availableTools(stack).contains(tool)) {
				SurgicalKitItem.setSelectedTool(stack, tool);
				if (index != slot && (index < Inventory.INVENTORY_SIZE || index == Inventory.SLOT_OFFHAND)) {
					inventory.setItem(index, inventory.getItem(slot));
					inventory.setItem(slot, stack);
				}
			}
			inventory.setChanged();
			return;
		}
	}

	void acceptReturn(ItemStack stack) {
		CBItemData.edit(stack, tag -> tag.remove(LOAN));
		SurgicalKitItem.setSelectedTool(stack, null);
		storedKit = stack;
		loanId = null;
		setChanged();
	}

	static boolean isLoaned(ItemStack stack) {
		return loanData(stack) != null;
	}

	@Nullable
	private static CompoundTag loanData(ItemStack stack) {
		CompoundTag tag = SurgicalKitItem.isKit(stack) ? CBItemData.getReadOnly(stack) : null;
		return tag != null && tag.get(LOAN) instanceof CompoundTag loan ? loan : null;
	}

	private boolean matchingLoan(CompoundTag loan, ItemStack stack) {
		return loan.hasUUID("Id") && loan.getUUID("Id").equals(loanId) && stack.is(getBlockState().getBlock().asItem());
	}

	@Nullable
	static SurgicalKitBlockEntity findLoan(ServerLevel currentLevel, ItemStack stack) {
		CompoundTag loan = loanData(stack);
		if (loan == null)
			return null;
		ResourceLocation dimension = ResourceLocation.tryParse(loan.getString("Dimension"));
		if (dimension == null || !loan.hasUUID("Id")) {
			CBItemData.edit(stack, tag -> tag.remove(LOAN));
			return null;
		}
		ServerLevel source = currentLevel.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
		BlockPos pos = BlockPos.of(loan.getLong("Pos"));
		if (source != null && source.isInWorldBounds(pos) && !source.isLoaded(pos))
			return null;
		if (source != null && source.isInWorldBounds(pos) && source.getBlockEntity(pos) instanceof SurgicalKitBlockEntity kit
			&& kit.matchingLoan(loan, stack))
			return kit;
		CBItemData.edit(stack, tag -> tag.remove(LOAN));
		return null;
	}
}
