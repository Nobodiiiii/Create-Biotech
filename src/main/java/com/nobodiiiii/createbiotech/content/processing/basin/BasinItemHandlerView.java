package com.nobodiiiii.createbiotech.content.processing.basin;

import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * Boundary view over a basin inventory.
 *
 * <p>The internal captured-slime stack is invisible to ordinary capabilities. Create funnels get
 * a second view which may extract it so their established output paths can materialize a live
 * slime, but even that view cannot insert an already-materialized control stack.</p>
 */
public final class BasinItemHandlerView implements IItemHandlerModifiable {
	private final IItemHandlerModifiable delegate;
	private final BasinItemHandlerAccess access;

	public BasinItemHandlerView(IItemHandlerModifiable delegate, BasinItemHandlerAccess access) {
		this.delegate = Objects.requireNonNull(delegate, "delegate");
		this.access = Objects.requireNonNull(access, "access");
		if (access == BasinItemHandlerAccess.INTERNAL)
			throw new IllegalArgumentException("The internal basin handler must not be wrapped as a boundary view");
	}

	@Override
	public int getSlots() {
		return delegate.getSlots();
	}

	@Override
	public @NotNull ItemStack getStackInSlot(int slot) {
		ItemStack stack = delegate.getStackInSlot(slot);
		return hides(stack) ? ItemStack.EMPTY : stack;
	}

	@Override
	public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
		if (BasinEntityProcessing.isCapturedSmallSlimeItem(stack))
			return stack;
		return delegate.insertItem(slot, stack, simulate);
	}

	@Override
	public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
		ItemStack current = delegate.getStackInSlot(slot);
		if (hides(current))
			return ItemStack.EMPTY;
		return delegate.extractItem(slot, amount, simulate);
	}

	@Override
	public int getSlotLimit(int slot) {
		return delegate.getSlotLimit(slot);
	}

	@Override
	public boolean isItemValid(int slot, @NotNull ItemStack stack) {
		return !BasinEntityProcessing.isCapturedSmallSlimeItem(stack)
			&& delegate.isItemValid(slot, stack);
	}

	@Override
	public void setStackInSlot(int slot, @NotNull ItemStack stack) {
		ItemStack current = delegate.getStackInSlot(slot);
		if (BasinEntityProcessing.isCapturedSmallSlimeItem(current)
			|| BasinEntityProcessing.isCapturedSmallSlimeItem(stack))
			return;
		delegate.setStackInSlot(slot, stack);
	}

	private boolean hides(ItemStack stack) {
		return access == BasinItemHandlerAccess.EXTERNAL
			&& BasinEntityProcessing.isCapturedSmallSlimeItem(stack);
	}
}
