package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.google.common.collect.Multimap;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Persisted, sequential factory requests. A sent batch is never sent twice after a restart. */
public final class GaugeCraftJobs extends SavedData {
	private static final String NAME = "create_biotech_gauge_craft_jobs";
	private final List<Job> jobs = new ArrayList<>();

	public static GaugeCraftJobs get(MinecraftServer server) {
		return server.overworld().getDataStorage()
			.computeIfAbsent(new SavedData.Factory<>(GaugeCraftJobs::new, GaugeCraftJobs::load), NAME);
	}

	public static void onTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		GaugeCraftJobs data = get(server);
		if (server.getTickCount() % 10 != 0)
			return;
		for (Job job : new ArrayList<>(data.jobs)) {
			if (data.tick(server, job))
				data.setDirty();
		}
	}

	public boolean submit(ServerPlayer player, BlockPos tickerPos, UUID network, ItemStack output, int count,
		String address, GaugeCraftPlan plan) {
		Set<UUID> involved = networks(network, plan.steps());
		if (address.isBlank() || !plan.ready() || jobs.stream().anyMatch(job ->
			job.networks().stream().anyMatch(involved::contains)))
			return false;
		jobs.add(new Job(UUID.randomUUID(), player.getUUID(), player.level().dimension(), tickerPos,
			network, output.copyWithCount(1), count, address, plan.steps()));
		setDirty();
		return true;
	}

	public boolean hasActive(UUID network) {
		return jobs.stream().anyMatch(job -> job.networks().contains(network));
	}

	public boolean ownedBy(UUID network, UUID player) {
		return jobs.stream().anyMatch(job -> job.network.equals(network) && job.owner.equals(player));
	}

	public String status(UUID network, UUID player, MinecraftServer server) {
		Job job = jobs.stream().filter(existing -> existing.network.equals(network)
			&& existing.owner.equals(player)).findFirst().orElse(null);
		if (job == null)
			return "busy";
		if (job.index >= job.steps.size())
			return "delivering";
		ServerLevel level = server.getLevel(job.dimension);
		GaugeCraftPlan.Step step = job.steps.get(job.index);
		if (level == null || !loaded(level, step.gauge().pos())
			|| step.inputs().stream().anyMatch(input -> !loaded(level, input.source().pos())))
			return "paused_unloaded";
		if (!matches(level, FactoryPanelBehaviour.at(level, step.gauge()), step))
			return "paused_changed";
		return job.waiting ? "waiting_output" : "processing";
	}

	public boolean cancel(UUID network, UUID player) {
		boolean removed = jobs.removeIf(job -> job.network.equals(network) && job.owner.equals(player));
		if (removed)
			setDirty();
		return removed;
	}

	public int heldAmount(UUID network, ItemStack stack) {
		int amount = 0;
		for (Job job : jobs) {
			if (!job.waiting || job.index >= job.steps.size())
				continue;
			GaugeCraftPlan.Step step = job.steps.get(job.index);
			if (network.equals(step.network()) && ItemStack.isSameItemSameComponents(stack, step.output()))
				amount = Math.min(GaugeCraftPlan.MAX_REQUEST, amount + step.outputPerCraft());
		}
		return amount;
	}

	private static boolean loaded(Level level, BlockPos pos) {
		return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
	}

	private static Set<UUID> networks(UUID root, List<GaugeCraftPlan.Step> steps) {
		Set<UUID> result = new HashSet<>();
		result.add(root);
		for (GaugeCraftPlan.Step step : steps) {
			result.add(step.network());
			step.inputs().forEach(input -> result.add(input.network()));
		}
		return result;
	}

	private boolean tick(MinecraftServer server, Job job) {
		ServerLevel level = server.getLevel(job.dimension);
		if (level == null || !loaded(level, job.tickerPos))
			return false;
		if (!(level.getBlockEntity(job.tickerPos) instanceof StockTickerBlockEntity ticker)
			|| !job.network.equals(ticker.behaviour.freqId)) {
			jobs.remove(job);
			ServerPlayer owner = server.getPlayerList().getPlayer(job.owner);
			if (owner != null)
				owner.displayClientMessage(Component.translatable("create_biotech.gauge_craft.failed")
					.withStyle(ChatFormatting.RED), false);
			return true;
		}
		if (job.index >= job.steps.size()) {
			if (LogisticsManager.getSummaryOfNetwork(job.network, true).getCountOf(job.output) < job.count)
				return false;
			if (!ticker.broadcastPackageRequest(RequestType.PLAYER,
				PackageOrderWithCrafts.simple(List.of(new BigItemStack(job.output, job.count))), null, job.address))
				return false;
			jobs.remove(job);
			ServerPlayer owner = server.getPlayerList().getPlayer(job.owner);
			if (owner != null)
				owner.displayClientMessage(Component.translatable("create_biotech.gauge_craft.accepted")
					.withStyle(ChatFormatting.GREEN), false);
			return true;
		}
		GaugeCraftPlan.Step step = job.steps.get(job.index);
		if (!loaded(level, step.gauge().pos()))
			return false;
		FactoryPanelBehaviour gauge = FactoryPanelBehaviour.at(level, step.gauge());
		if (!matches(level, gauge, step))
			return false;
		if (job.waiting) {
			int current = LogisticsManager.getSummaryOfNetwork(step.network(), true).getCountOf(step.output());
			if (current < (long) job.baseline + step.outputPerCraft())
				return false;
			job.waiting = false;
			job.remaining--;
			if (job.remaining == 0) {
				job.index++;
				if (job.index < job.steps.size())
					job.remaining = job.steps.get(job.index).crafts();
			}
			return true;
		}
		if (job.remaining <= 0)
			return false;
		if (!dispatch(step))
			return false;
		job.baseline = LogisticsManager.getSummaryOfNetwork(step.network(), true).getCountOf(step.output());
		job.waiting = true;
		return true;
	}

	private static boolean matches(Level level, FactoryPanelBehaviour gauge, GaugeCraftPlan.Step step) {
		if (gauge == null || !gauge.isActive() || !step.network().equals(gauge.network)
			|| !ItemStack.isSameItemSameComponents(step.output(), gauge.getFilter())
			|| step.outputPerCraft() != gauge.recipeOutput || !step.address().equals(gauge.recipeAddress)
			|| gauge.activeCraftingArrangement.size() != step.arrangement().size()
			|| gauge.targetedBy.size() != step.inputs().size())
			return false;
		for (int i = 0; i < step.arrangement().size(); i++)
			if (!ItemStack.isSameItemSameComponents(step.arrangement().get(i), gauge.activeCraftingArrangement.get(i)))
				return false;
		for (GaugeCraftPlan.Input input : step.inputs()) {
			FactoryPanelConnection connection = gauge.targetedBy.get(input.source());
			if (connection == null || connection.amount != input.amount() || !loaded(level, input.source().pos()))
				return false;
			FactoryPanelBehaviour source = FactoryPanelBehaviour.at(level, input.source());
			if (source == null || !input.network().equals(source.network)
				|| !ItemStack.isSameItemSameComponents(input.stack(), source.getFilter()))
				return false;
		}
		return true;
	}

	private static boolean dispatch(GaugeCraftPlan.Step step) {
		Map<UUID, List<BigItemStack>> grouped = new HashMap<>();
		for (GaugeCraftPlan.Input input : step.inputs()) {
			List<BigItemStack> entries = grouped.computeIfAbsent(input.network(), ignored -> new ArrayList<>());
			BigItemStack existing = entries.stream()
				.filter(entry -> ItemStack.isSameItemSameComponents(entry.stack, input.stack())).findFirst().orElse(null);
			if (existing == null)
				entries.add(new BigItemStack(input.stack(), input.amount()));
			else
				existing.count += input.amount();
		}
		for (Map.Entry<UUID, List<BigItemStack>> group : grouped.entrySet())
			for (BigItemStack input : group.getValue())
				if (LogisticsManager.getSummaryOfNetwork(group.getKey(), true).getCountOf(input.stack) < input.count)
					return false;
		PackageOrderWithCrafts context = step.arrangement().isEmpty()
			? PackageOrderWithCrafts.empty()
			: PackageOrderWithCrafts.singleRecipe(step.arrangement().stream()
				.map(stack -> new BigItemStack(stack.copyWithCount(1))).toList());
		List<Multimap<PackagerBlockEntity, PackagingRequest>> requests = new ArrayList<>();
		for (Map.Entry<UUID, List<BigItemStack>> group : grouped.entrySet()) {
			PackageOrderWithCrafts order = new PackageOrderWithCrafts(new PackageOrder(group.getValue()),
				context.orderedCrafts());
			Multimap<PackagerBlockEntity, PackagingRequest> found =
				LogisticsManager.findPackagersForRequest(group.getKey(), order, null, step.address());
			for (BigItemStack input : group.getValue()) {
				int foundCount = found.values().stream()
					.filter(request -> ItemStack.isSameItemSameComponents(request.item(), input.stack))
					.mapToInt(PackagingRequest::getCount).sum();
				if (foundCount < input.count)
					return false;
			}
			requests.add(found);
		}
		for (Multimap<PackagerBlockEntity, PackagingRequest> request : requests)
			for (PackagerBlockEntity packager : request.keySet())
				if (packager.isTooBusyFor(RequestType.RESTOCK))
					return false;
		requests.forEach(LogisticsManager::performPackageRequests);
		return true;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		jobs.forEach(job -> list.add(job.save(registries)));
		tag.put("Jobs", list);
		return tag;
	}

	private static GaugeCraftJobs load(CompoundTag tag, HolderLookup.Provider registries) {
		GaugeCraftJobs data = new GaugeCraftJobs();
		for (Tag value : tag.getList("Jobs", Tag.TAG_COMPOUND)) {
			try {
				data.jobs.add(Job.load((CompoundTag) value, registries));
			} catch (RuntimeException ignored) {}
		}
		return data;
	}

	private static CompoundTag position(FactoryPanelPosition position) {
		CompoundTag tag = new CompoundTag();
		tag.putLong("Pos", position.pos().asLong());
		tag.putString("Slot", position.slot().name());
		return tag;
	}

	private static FactoryPanelPosition position(CompoundTag tag) {
		return new FactoryPanelPosition(BlockPos.of(tag.getLong("Pos")), PanelSlot.valueOf(tag.getString("Slot")));
	}

	private static final class Job {
		final UUID id, owner, network;
		final ResourceKey<Level> dimension;
		final BlockPos tickerPos;
		final ItemStack output;
		final int count;
		final String address;
		final List<GaugeCraftPlan.Step> steps;
		int index, remaining, baseline;
		boolean waiting;

		Job(UUID id, UUID owner, ResourceKey<Level> dimension, BlockPos tickerPos, UUID network,
			ItemStack output, int count, String address, List<GaugeCraftPlan.Step> steps) {
			this.id = id;
			this.owner = owner;
			this.dimension = dimension;
			this.tickerPos = tickerPos;
			this.network = network;
			this.output = output;
			this.count = count;
			this.address = address;
			this.steps = List.copyOf(steps);
			this.remaining = steps.isEmpty() ? 0 : steps.getFirst().crafts();
		}

		Set<UUID> networks() {
			return GaugeCraftJobs.networks(network, steps);
		}

		CompoundTag save(HolderLookup.Provider registries) {
			CompoundTag tag = new CompoundTag();
			tag.putUUID("Id", id);
			tag.putUUID("Owner", owner);
			tag.putUUID("Network", network);
			tag.putString("Dimension", dimension.location().toString());
			tag.putLong("Ticker", tickerPos.asLong());
			tag.put("Output", output.saveOptional(registries));
			tag.putInt("Count", count);
			tag.putString("Address", address);
			tag.putInt("Index", index);
			tag.putInt("Remaining", remaining);
			tag.putInt("Baseline", baseline);
			tag.putBoolean("Waiting", waiting);
			ListTag stepList = new ListTag();
			for (GaugeCraftPlan.Step step : steps) {
				CompoundTag s = new CompoundTag();
				s.put("Gauge", position(step.gauge()));
				s.putUUID("Network", step.network());
				s.put("Output", step.output().saveOptional(registries));
				s.putInt("PerCraft", step.outputPerCraft());
				s.putInt("Crafts", step.crafts());
				s.putString("Address", step.address());
				ListTag arrangement = new ListTag();
				step.arrangement().forEach(stack -> arrangement.add(stack.saveOptional(registries)));
				s.put("Arrangement", arrangement);
				ListTag inputs = new ListTag();
				for (GaugeCraftPlan.Input input : step.inputs()) {
					CompoundTag i = new CompoundTag();
					i.put("Source", position(input.source()));
					i.putUUID("Network", input.network());
					i.put("Stack", input.stack().saveOptional(registries));
					i.putInt("Amount", input.amount());
					inputs.add(i);
				}
				s.put("Inputs", inputs);
				stepList.add(s);
			}
			tag.put("Steps", stepList);
			return tag;
		}

		static Job load(CompoundTag tag, HolderLookup.Provider registries) {
			List<GaugeCraftPlan.Step> steps = new ArrayList<>();
			for (Tag value : tag.getList("Steps", Tag.TAG_COMPOUND)) {
				CompoundTag s = (CompoundTag) value;
				List<ItemStack> arrangement = new ArrayList<>();
				for (Tag raw : s.getList("Arrangement", Tag.TAG_COMPOUND))
					arrangement.add(ItemStack.parseOptional(registries, (CompoundTag) raw));
				List<GaugeCraftPlan.Input> inputs = new ArrayList<>();
				for (Tag raw : s.getList("Inputs", Tag.TAG_COMPOUND)) {
					CompoundTag i = (CompoundTag) raw;
					inputs.add(new GaugeCraftPlan.Input(position(i.getCompound("Source")), i.getUUID("Network"),
						ItemStack.parseOptional(registries, i.getCompound("Stack")), i.getInt("Amount")));
				}
				steps.add(new GaugeCraftPlan.Step(position(s.getCompound("Gauge")), s.getUUID("Network"),
					ItemStack.parseOptional(registries, s.getCompound("Output")), s.getInt("PerCraft"),
					s.getInt("Crafts"), s.getString("Address"), List.copyOf(arrangement), List.copyOf(inputs)));
			}
			Job job = new Job(tag.getUUID("Id"), tag.getUUID("Owner"),
				ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dimension"))),
				BlockPos.of(tag.getLong("Ticker")), tag.getUUID("Network"),
				ItemStack.parseOptional(registries, tag.getCompound("Output")), tag.getInt("Count"),
				tag.getString("Address"), steps);
			job.index = tag.getInt("Index");
			job.remaining = tag.getInt("Remaining");
			job.baseline = tag.getInt("Baseline");
			job.waiting = tag.getBoolean("Waiting");
			return job;
		}
	}
}
