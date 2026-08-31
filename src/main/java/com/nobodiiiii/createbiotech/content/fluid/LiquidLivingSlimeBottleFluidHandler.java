package com.nobodiiiii.createbiotech.content.fluid;

import com.nobodiiiii.createbiotech.registry.CBFluids;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/** Exposes one bottle as an atomic 250 mB liquid living slime container. */
public final class LiquidLivingSlimeBottleFluidHandler implements IFluidHandlerItem {
	public static final int BOTTLE_VOLUME = 250;

	private ItemStack container;

	public LiquidLivingSlimeBottleFluidHandler(ItemStack container) {
		this.container = container;
	}

	@Override
	public ItemStack getContainer() {
		return container;
	}

	@Override
	public int getTanks() {
		return 1;
	}

	@Override
	public FluidStack getFluidInTank(int tank) {
		return tank == 0 && container.is(CBFluids.LIQUID_LIVING_SLIME_BOTTLE.get())
			? new FluidStack(CBFluids.LIQUID_LIVING_SLIME.get(), BOTTLE_VOLUME)
			: FluidStack.EMPTY;
	}

	@Override
	public int getTankCapacity(int tank) {
		return tank == 0 ? BOTTLE_VOLUME : 0;
	}

	@Override
	public boolean isFluidValid(int tank, FluidStack stack) {
		return tank == 0 && stack.is(CBFluids.LIQUID_LIVING_SLIME.get());
	}

	@Override
	public int fill(FluidStack resource, FluidAction action) {
		return 0;
	}

	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {
		if (resource.getAmount() < BOTTLE_VOLUME
			|| !resource.is(CBFluids.LIQUID_LIVING_SLIME.get()))
			return FluidStack.EMPTY;

		return drain(BOTTLE_VOLUME, action);
	}

	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {
		if (container.getCount() != 1 || maxDrain < BOTTLE_VOLUME
			|| !container.is(CBFluids.LIQUID_LIVING_SLIME_BOTTLE.get()))
			return FluidStack.EMPTY;

		FluidStack drained = new FluidStack(CBFluids.LIQUID_LIVING_SLIME.get(), BOTTLE_VOLUME);
		if (action.execute())
			container = new ItemStack(Items.GLASS_BOTTLE);
		return drained;
	}
}
