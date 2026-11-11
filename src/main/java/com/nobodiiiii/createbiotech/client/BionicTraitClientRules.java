package com.nobodiiiii.createbiotech.client;

import java.util.Map;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitRule;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitRulesPacket;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Server thresholds for JEI, kept separate from the runtime registry in integrated servers. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public final class BionicTraitClientRules {
	private static Map<BionicTrait, Double> minCoverage = Map.of();

	private BionicTraitClientRules() {}

	public static BionicTraitRule rule(BionicTrait trait) {
		return trait.rule().withMinCoverage(minCoverage.getOrDefault(trait, trait.rule().minCoverage()));
	}

	public static void accept(BionicTraitRulesPacket packet) {
		minCoverage = packet.minCoverage();
	}

	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		minCoverage = Map.of();
	}
}
