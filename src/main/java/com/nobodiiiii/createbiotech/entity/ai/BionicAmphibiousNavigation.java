package com.nobodiiiii.createbiotech.entity.ai;

import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.AmphibiousNodeEvaluator;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;

/** Retain strong-jump land paths for bodies that also have swimming tissue. */
public final class BionicAmphibiousNavigation extends AmphibiousPathNavigation {
	public BionicAmphibiousNavigation(SlimeBionicEntity mob, Level level) { super(mob, level); }

	@Override protected PathFinder createPathFinder(int visitedNodes) {
		nodeEvaluator = new AmphibiousNodeEvaluator(false) {
			@Override public int getNeighbors(Node[] neighbors, Node node) {
				SlimeBionicEntity bionic = (SlimeBionicEntity) mob;
				bionic.setEvaluatingJumpPath(true);
				try { return super.getNeighbors(neighbors, node); }
				finally { bionic.setEvaluatingJumpPath(false); }
			}
		};
		nodeEvaluator.setCanPassDoors(true);
		return new PathFinder(nodeEvaluator, visitedNodes);
	}
}
