package com.nobodiiiii.createbiotech.content.surgery.stats;

import com.google.gson.JsonParser;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.network.CBPackets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** One bounded atomic snapshot includes calibrated parameters and formula definitions. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public record BionicStatsPacket(String json) {
	public static final int MAX_LENGTH = 65536;
	public BionicStatsPacket {
		if (json.length() > MAX_LENGTH) throw new IllegalArgumentException("Bionic stat snapshot exceeds 65536 characters");
	}
	public BionicStatsPacket(RegistryFriendlyByteBuf buffer) { this(buffer.readUtf(MAX_LENGTH)); }
	public void write(RegistryFriendlyByteBuf buffer) { buffer.writeUtf(json, MAX_LENGTH); }
	public void handle() { BionicStats.acceptClient(JsonParser.parseString(json).getAsJsonObject()); }
	@SubscribeEvent public static void onSync(OnDatapackSyncEvent event) {
		var packet = new BionicStatsPacket(BionicStats.exportServer().toString());
		event.getRelevantPlayers().forEach(player -> CBPackets.sendToPlayer(packet, player));
	}
	@SubscribeEvent public static void onStopped(ServerStoppedEvent event) { BionicStats.resetServer(); }
}
