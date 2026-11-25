package com.nobodiiiii.createbiotech.content.surgery;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBDataComponents;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.content.equipment.toolbox.ToolboxHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SurgicalKitBlockEntity extends BlockEntity {
	private static final String SOURCE_ID_TAG = "ToolSourceId";
	private static final Map<Level, Set<SurgicalKitBlockEntity>> CLIENT_KITS = new WeakHashMap<>();
	private ItemStack storedKit;
	@Nullable
	private UUID sourceId;

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
		sourceId = tag.hasUUID(SOURCE_ID_TAG) ? tag.getUUID(SOURCE_ID_TAG) : null;
		if (tag.contains("Kit", Tag.TAG_COMPOUND))
			storedKit = ItemStack.parseOptional(registries, tag.getCompound("Kit"));
		else if (tag.hasUUID("Loan"))
			storedKit = ItemStack.EMPTY;
		else
			storedKit = new ItemStack(getBlockState().getBlock());
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("Kit", storedKit.saveOptional(registries));
		if (sourceId != null)
			tag.putUUID(SOURCE_ID_TAG, sourceId);
	}

	@Override
	public boolean onlyOpCanSetNbt() {
		return true;
	}

	void storePlacedStack(ItemStack stack) {
		setComponents(DataComponentMap.EMPTY);
		storedKit = stack.copyWithCount(1);
		sourceId = null;
		setChanged();
	}

	ItemStack copyStoredKit() {
		return storedKit.copy();
	}

	boolean matchesSource(UUID id) {
		return id.equals(sourceId);
	}

	void select(ServerPlayer player, int slot, @Nullable SurgicalKitItem.Tool tool) {
		Inventory inventory = player.getInventory();
		SurgicalKitItem borrowedTool = getBlockState().is(CBBlocks.CREATIVE_SURGICAL_KIT.get())
			? CBItems.BORROWED_CREATIVE_SURGICAL_TOOL.get() : CBItems.BORROWED_SURGICAL_TOOL.get();
		ItemStack held = inventory.getItem(slot);
		if (tool == null) {
			if (held.is(borrowedTool)) {
				inventory.setItem(slot, ItemStack.EMPTY);
				inventory.setChanged();
			}
			return;
		}
		ItemStack selected = new ItemStack(borrowedTool);
		if (!SurgicalKitItem.availableTools(selected).contains(tool))
			return;
		if (!held.isEmpty()) {
			int freeSlot = inventory.getFreeSlot();
			if (freeSlot < 0)
				return;
			inventory.setItem(freeSlot, held);
		}
		if (sourceId == null) {
			sourceId = UUID.randomUUID();
			setChanged();
		}
		selected.set(CBDataComponents.SURGICAL_TOOL_SOURCE.get(),
			new SurgicalToolSource(GlobalPos.of(player.level().dimension(), worldPosition), sourceId));
		SurgicalKitItem.setSelectedTool(selected, tool);
		inventory.setItem(slot, selected);
		inventory.setChanged();
	}
}
