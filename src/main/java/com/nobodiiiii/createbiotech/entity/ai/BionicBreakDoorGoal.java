package com.nobodiiiii.createbiotech.entity.ai;

import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;

/** Keep vanilla's 240-tick breaking animation, checking live anatomy and protection throughout. */
public final class BionicBreakDoorGoal extends BreakDoorGoal {
	private final SlimeBionicEntity bionic;
	public BionicBreakDoorGoal(SlimeBionicEntity bionic) {
		super(bionic, 240, difficulty -> difficulty == Difficulty.HARD);
		this.bionic = bionic;
	}
	private boolean enabled() {
		return bionic.getBionicTraits().has(BionicTrait.DOOR_HANDLING) && !bionic.isOrderedToSit()
			&& bionic.getNavigation() instanceof GroundPathNavigation && EventHooks.canEntityGrief(bionic.level(), bionic);
	}
	@Override public boolean canUse() { return enabled() && super.canUse(); }
	@Override public boolean canContinueToUse() {
		return enabled() && hasDoor && super.canContinueToUse()
			&& CommonHooks.canEntityDestroy(bionic.level(), doorPos, bionic);
	}
	@Override public void tick() {
		if (canContinueToUse()) super.tick();
	}
}
