package com.nobodiiiii.createbiotech.entity.inventory;

import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.trait.BionicEquipment.InventoryView;
import com.nobodiiiii.createbiotech.registry.CBMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class BionicCargoMenu extends AbstractContainerMenu {
	public static final int SADDLES_PER_PAGE = 3, CARGO_PER_PAGE = 15;
	private static final int INVENTORY_START = SADDLES_PER_PAGE + CARGO_PER_PAGE;
	private final SlimeBionicEntity entity;
	@Nullable private final InventoryView view;
	private final Container allSaddles, allCargo;
	private final DataSlot page = DataSlot.standalone();
	public final int saddleCount, capacity;

	public BionicCargoMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
		this(id, inventory, inventory.player.level().getEntity(buffer.readInt()) instanceof SlimeBionicEntity b ? b : null,
			null, Mth.clamp(buffer.readVarInt(), 0, SurgicalAssembly.MAX_CUBES),
			Mth.clamp(buffer.readVarInt(), 0, SurgicalAssembly.MAX_CUBES * 54), buffer.readVarInt());
	}
	public BionicCargoMenu(int id, Inventory inventory, SlimeBionicEntity entity, InventoryView view, int page) {
		this(id, inventory, entity, view, view.saddleMembers().size(), view.cargo().getContainerSize(), page);
	}
	private BionicCargoMenu(int id, Inventory inventory, SlimeBionicEntity entity, @Nullable InventoryView view,
		int saddleCount, int capacity, int initialPage) {
		super(CBMenuTypes.BIONIC_CARGO.get(), id);
		this.entity = entity;
		this.view = view;
		this.saddleCount = saddleCount;
		this.capacity = capacity;
		page.set(Mth.clamp(initialPage, 0, pageCount() - 1));
		addDataSlot(page);
		allSaddles = view == null ? new SimpleContainer(SADDLES_PER_PAGE) : view.saddles();
		allCargo = view == null ? new SimpleContainer(CARGO_PER_PAGE) : view.cargo();
		allSaddles.startOpen(inventory.player);
		allCargo.startOpen(inventory.player);
		Container saddles = view == null ? allSaddles : BionicInventoryContainer.page(allSaddles, this::saddleOffset, SADDLES_PER_PAGE);
		Container cargo = view == null ? allCargo : BionicInventoryContainer.page(allCargo, this::cargoOffset, CARGO_PER_PAGE);
		for (int slot = 0; slot < SADDLES_PER_PAGE; slot++) {
			final int local = slot;
			addSlot(new Slot(saddles, slot, 8, 18 + slot * 18) {
				@Override public boolean isActive() { return local < visibleSaddles(); }
				@Override public boolean mayPlace(ItemStack stack) { return isActive() && saddles.canPlaceItem(local, stack) && !hasItem(); }
				@Override public boolean mayPickup(Player player) { return isActive(); }
				@Override public int getMaxStackSize() { return 1; }
			});
		}
		for (int slot = 0; slot < CARGO_PER_PAGE; slot++) {
			final int local = slot;
			addSlot(new Slot(cargo, slot, 80 + slot % 5 * 18, 18 + slot / 5 * 18) {
				@Override public boolean isActive() { return local < visibleCargo(); }
				@Override public boolean mayPlace(ItemStack stack) { return isActive(); }
				@Override public boolean mayPickup(Player player) { return isActive(); }
			});
		}
		for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
			addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 84 + row * 18));
		for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));
	}
	public SlimeBionicEntity entity() { return entity; }
	public int page() { return page.get(); }
	public int pageCount() { return Math.max(1, Math.max((saddleCount + 2) / SADDLES_PER_PAGE, (capacity + 14) / CARGO_PER_PAGE)); }
	public int saddleOffset() { return Math.min(page(), Math.max(0, (saddleCount - 1) / SADDLES_PER_PAGE)) * SADDLES_PER_PAGE; }
	public int cargoOffset() { return Math.min(page(), Math.max(0, (capacity - 1) / CARGO_PER_PAGE)) * CARGO_PER_PAGE; }
	public int visibleSaddles() { return Math.min(SADDLES_PER_PAGE, saddleCount - saddleOffset()); }
	public int visibleCargo() { return Math.min(CARGO_PER_PAGE, capacity - cargoOffset()); }
	@Override public boolean clickMenuButton(Player player, int button) {
		int next = page() + (button == 0 ? -1 : button == 1 ? 1 : 0);
		if (next == page() || next < 0 || next >= pageCount()) return false;
		page.set(next);
		broadcastChanges();
		return true;
	}
	@Override public void removed(Player player) {
		super.removed(player);
		allSaddles.stopOpen(player);
		allCargo.stopOpen(player);
	}
	@Override public boolean stillValid(Player player) {
		return entity != null && entity.isAlive() && player.canInteractWithEntity(entity, 4.0)
			&& view != null && view.stillValid();
	}
	@Override public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.isActive() || !slot.hasItem()) return ItemStack.EMPTY;
		ItemStack stack = slot.getItem(), previous = stack.copy();
		if (index < INVENTORY_START) {
			if (!moveItemStackTo(stack, INVENTORY_START, slots.size(), true)) return ItemStack.EMPTY;
		} else if (!moveToEquipment(stack)) {
			int hotbarStart = INVENTORY_START + 27;
			if (index < hotbarStart) {
				if (!moveItemStackTo(stack, hotbarStart, slots.size(), false)) return ItemStack.EMPTY;
			} else if (!moveItemStackTo(stack, INVENTORY_START, hotbarStart, false)) return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
		slot.onTake(player, stack);
		return previous;
	}
	/** Shift transfer visits the complete inventory, including boxes on other pages. */
	private boolean moveToEquipment(ItemStack stack) {
		int saddles = view == null ? visibleSaddles() : allSaddles.getContainerSize();
		for (int slot = 0; slot < saddles; slot++) if (allSaddles.getItem(slot).isEmpty() && allSaddles.canPlaceItem(slot, stack)) {
				allSaddles.setItem(slot, stack.split(1));
				return true;
			}
		int before = stack.getCount();
		int cargo = view == null ? visibleCargo() : allCargo.getContainerSize();
		// Vanilla moveItemStackTo order: merge compatible stacks, then fill empty slots.
		for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++)
			for (int slot = 0; slot < cargo && !stack.isEmpty(); slot++) {
				ItemStack stored = allCargo.getItem(slot);
				if (!allCargo.canPlaceItem(slot, stack)) continue;
				int limit = Math.min(allCargo.getMaxStackSize(), stack.getMaxStackSize());
				if (pass == 0 && !stored.isEmpty() && ItemStack.isSameItemSameComponents(stack, stored)) {
					int count = Math.min(stack.getCount(), limit - stored.getCount());
					if (count <= 0) continue;
					stored.grow(count); stack.shrink(count); allCargo.setItem(slot, stored);
				} else if (pass == 1 && stored.isEmpty()) allCargo.setItem(slot, stack.split(Math.min(limit, stack.getCount())));
			}
		return stack.getCount() != before;
	}
}
