package com.nobodiiiii.createbiotech.client;

import com.nobodiiiii.createbiotech.content.surgery.stats.BionicStats;

import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitRegistry;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitOverrides;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitRule;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitRulesPacket;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Stages catalog chunks without exposing an incomplete client definition graph. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public final class BionicTraitClientRules {
	private static final Map<ResourceLocation, JsonElement> pending = new LinkedHashMap<>();
	private static long pendingGeneration = -1;
	private static int pendingTotal, pendingBytes;
	private BionicTraitClientRules() {}
	public static BionicTraitRule rule(BionicTrait trait) { return BionicTraitRegistry.clientRule(trait); }
	public static int minLimbs(BionicTrait trait) {
		boolean wings = trait.condition() == BionicTrait.Condition.WING_LIFT || trait.condition() == BionicTrait.Condition.LIMB_COUNT;
		return (int) BionicTraitRegistry.clientParameter(trait, wings ? "min_wings" : "min_legs", wings ? 2 : 1);
	}
	public static double maxBodyVolumePerWingVolume(BionicTrait trait) {
		return BionicTraitRegistry.clientParameter(trait, "max_body_volume_per_wing_volume", 16);
	}
	public static void accept(BionicTraitRulesPacket packet) {
		if (packet.offset() == 0) {
			pending.clear(); pendingBytes = 0;
			pendingGeneration = packet.generation(); pendingTotal = packet.total();
		}
		if (packet.generation() != pendingGeneration || packet.total() != pendingTotal || packet.offset() != pending.size())
			throw new IllegalArgumentException("Out-of-order bionic trait catalog");
		for (var entry : packet.entries()) {
			pendingBytes += entry.encodedSize();
			if (pendingBytes > BionicTraitRulesPacket.MAX_SNAPSHOT_BYTES || pending.put(entry.id(), JsonParser.parseString(entry.json())) != null)
				throw new IllegalArgumentException("Invalid bionic trait catalog");
		}
		if (pending.size() == pendingTotal) {
			BionicTraitRegistry.acceptClient(pendingGeneration, pending);
			BionicTraitOverrides.acceptClientRules(BionicTraitRegistry.clientOverrides());
			BionicTraitDonorIndex.catalogChanged();
			pending.clear(); pendingGeneration = -1;
		}
	}
	@SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		pending.clear(); pendingGeneration = -1; pendingTotal = pendingBytes = 0;
		BionicTraitRegistry.resetClient();
		BionicStats.resetClient();
		BionicTraitOverrides.resetClientRules();
	}
}
