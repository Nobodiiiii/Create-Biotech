package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;

/** Material allocation shared by the dependency scheduler and its tree snapshot. */
final class GaugeCraftFlow {

	/** A negative source denotes stock; other sources are earlier execution steps. */
	record Supply(int source, GaugeCraftPlan.Input input, int count) {}
	private final List<List<Supply>> inputs;

	GaugeCraftFlow(List<GaugeCraftPlan.Step> steps) {
		inputs = new ArrayList<>(steps.size());
		int[] available = new int[steps.size()];
		for (int index = 0; index < steps.size(); index++) {
			GaugeCraftPlan.Step step = steps.get(index);
			List<Supply> supplies = new ArrayList<>();
			for (GaugeCraftPlan.Input input : step.inputs()) {
				int needed = input.amount() * step.crafts();
				for (int source = 0; source < index && needed > 0; source++) {
					GaugeCraftPlan.Step producer = steps.get(source);
					if (available[source] == 0 || !producer.network().equals(input.network())
						|| !ItemStack.isSameItemSameComponents(producer.output(), input.stack()))
						continue;
					int taken = Math.min(needed, available[source]);
					available[source] -= taken;
					needed -= taken;
					supplies.add(new Supply(source, input, taken));
				}
				if (needed > 0)
					supplies.add(new Supply(-1, input, needed));
			}
			inputs.add(List.copyOf(supplies));
			available[index] = step.crafts() * step.outputPerCraft();
		}
	}

	List<Supply> inputs(int step) {
		return inputs.get(step);
	}
}
