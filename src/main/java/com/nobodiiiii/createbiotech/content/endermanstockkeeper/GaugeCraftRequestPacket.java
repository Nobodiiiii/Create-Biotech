package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.nobodiiiii.createbiotech.network.CBPackets;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

/** Preview and final confirmation are separate server-validated actions. */
public record GaugeCraftRequestPacket(boolean confirm, UUID token, ItemStack stack, int count, String address) {
	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	public static void clearSession(UUID player) {
		SESSIONS.remove(player);
	}

	public GaugeCraftRequestPacket(RegistryFriendlyByteBuf buffer) {
		this(buffer.readBoolean(), buffer.readUUID(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
			buffer.readVarInt(), buffer.readUtf(64));
	}

	public void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeBoolean(confirm);
		buffer.writeUUID(token);
		ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
		buffer.writeVarInt(count);
		buffer.writeUtf(address, 64);
	}

	public void handle(ServerPlayer player) {
		if (!(player.containerMenu instanceof EndermanStockKeeperRequestMenu menu) || !menu.stillValid(player)
			|| !(menu.contentHolder instanceof StockTickerBlockEntity ticker)
			|| !ticker.behaviour.mayInteract(player))
			return;
		UUID network = ticker.behaviour.freqId;
		if (network == null)
			return;
		if (GaugeCraftJobs.get(player.server).isFull(network)) {
			clearSession(player.getUUID());
			CBPackets.sendToPlayer(new GaugeCraftPreviewPacket(token, "busy", java.util.List.of()), player);
			return;
		}
		if (confirm) {
			Session session = SESSIONS.remove(player.getUUID());
			if (session == null || !session.token.equals(token)
				|| session.server != player.server || player.server.getTickCount() - session.createdTick > 1200
				|| !session.network.equals(network) || !session.tickerPos.equals(ticker.getBlockPos()))
				return;
			GaugeCraftPlan fresh = GaugeCraftPlan.build(player.level(), network, session.stack, session.count);
			String signature = signature(fresh);
			if (!signature.equals(session.signature)) {
				preview(player, ticker, session.stack, session.count, session.address, fresh);
				return;
			}
			boolean accepted = fresh.ready() && GaugeCraftJobs.get(player.server)
				.submit(player, ticker.getBlockPos(), network, session.stack, session.count, session.address, fresh);
			if (accepted)
				player.displayClientMessage(
					net.minecraft.network.chat.Component.translatable("create_biotech.gauge_craft.queued"), false);
			CBPackets.sendToPlayer(new GaugeCraftPreviewPacket(token, accepted ? "accepted" : "busy",
				fresh.lines()), player);
			return;
		}
		if (count <= 0 || count > GaugeCraftPlan.MAX_REQUEST || stack.isEmpty()
			|| address.length() > 25
			|| menu.getGaugeOutputs().stream()
				.noneMatch(output -> ItemStack.isSameItemSameComponents(output, stack)))
			return;
		GaugeCraftPlan plan = GaugeCraftPlan.build(player.level(), network, stack, count);
		preview(player, ticker, stack.copyWithCount(1), count, address, plan);
	}

	private static void preview(ServerPlayer player, StockTickerBlockEntity ticker, ItemStack stack,
		int count, String address, GaugeCraftPlan plan) {
		UUID token = UUID.randomUUID();
		SESSIONS.put(player.getUUID(), new Session(token, player.server, player.server.getTickCount(),
			ticker.getBlockPos(), ticker.behaviour.freqId, stack, count, address, signature(plan)));
		GaugeCraftJobs jobs = GaugeCraftJobs.get(player.server);
		String status = jobs.isFull(ticker.behaviour.freqId) ? "busy"
			: !plan.error().isEmpty() ? plan.error() : plan.missing() > 0 ? "missing" : "ready";
		CBPackets.sendToPlayer(new GaugeCraftPreviewPacket(token, status,
			plan.lines()), player);
	}

	private static String signature(GaugeCraftPlan plan) {
		return plan.error() + ":" + plan.missing() + ":" + plan.lines() + ":" + plan.steps() + ":" + plan.stock();
	}

	private record Session(UUID token, MinecraftServer server, int createdTick, BlockPos tickerPos,
		UUID network, ItemStack stack, int count, String address, String signature) {}
}
