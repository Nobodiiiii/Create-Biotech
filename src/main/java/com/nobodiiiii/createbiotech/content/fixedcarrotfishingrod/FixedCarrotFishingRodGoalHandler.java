package com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod;

import net.minecraft.world.entity.animal.Animal;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

public class FixedCarrotFishingRodGoalHandler {

	private static final int GOAL_PRIORITY = 4;

	private FixedCarrotFishingRodGoalHandler() {}

	public static void register() {
		MinecraftForge.EVENT_BUS.addListener(FixedCarrotFishingRodGoalHandler::onEntityJoinLevel);
	}

	private static void onEntityJoinLevel(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide())
			return;
		if (!(event.getEntity() instanceof Animal animal))
			return;
		if (hasFixedCarrotFishingRodGoal(animal))
			return;

		animal.goalSelector.addGoal(GOAL_PRIORITY, new FixedCarrotFishingRodTemptGoal(animal));
	}

	private static boolean hasFixedCarrotFishingRodGoal(Animal animal) {
		return animal.goalSelector.getAvailableGoals()
			.stream()
			.anyMatch(goal -> goal.getGoal() instanceof FixedCarrotFishingRodTemptGoal);
	}
}
