package com.nobodiiiii.createbiotech.entity.ai;

import java.util.HashMap;
import java.util.Map;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.network.CBPackets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/** Keep donor classifications in client tooltips consistent with the server's data packs. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public record BionicHeadDataPacket(Map<ResourceLocation, BionicDisposition> dispositions,
	Map<ResourceLocation, BionicIntelligence> intelligence) {
	private static final int MAX_ENTRIES = 65536;

	public BionicHeadDataPacket {
		dispositions = Map.copyOf(dispositions);
		intelligence = Map.copyOf(intelligence);
		if (dispositions.size() > MAX_ENTRIES || intelligence.size() > MAX_ENTRIES)
			throw new IllegalArgumentException("Too many bionic head classifications");
	}

	public BionicHeadDataPacket(RegistryFriendlyByteBuf buffer) {
		this(read(buffer, BionicDisposition.class), read(buffer, BionicIntelligence.class));
	}

	public void write(RegistryFriendlyByteBuf buffer) {
		write(buffer, dispositions);
		write(buffer, intelligence);
	}

	public void handle() {
		BionicDispositionRegistry.replaceData(dispositions);
		BionicIntelligenceRegistry.replaceData(intelligence);
	}

	@SubscribeEvent
	public static void onDatapackSync(OnDatapackSyncEvent event) {
		BionicHeadDataPacket packet = new BionicHeadDataPacket(BionicDispositionRegistry.data(),
			BionicIntelligenceRegistry.data());
		event.getRelevantPlayers().forEach(player -> CBPackets.sendToPlayer(packet, player));
	}

	private static <T extends Enum<T>> Map<ResourceLocation, T> read(RegistryFriendlyByteBuf buffer,
		Class<T> type) {
		int count = buffer.readVarInt();
		if (count < 0 || count > MAX_ENTRIES)
			throw new IllegalArgumentException("Invalid bionic head classification count");
		Map<ResourceLocation, T> entries = new HashMap<>();
		for (int index = 0; index < count; index++)
			entries.put(buffer.readResourceLocation(), buffer.readEnum(type));
		return entries;
	}

	private static <T extends Enum<T>> void write(RegistryFriendlyByteBuf buffer,
		Map<ResourceLocation, T> entries) {
		buffer.writeVarInt(entries.size());
		entries.forEach((id, value) -> {
			buffer.writeResourceLocation(id);
			buffer.writeEnum(value);
		});
	}
}
