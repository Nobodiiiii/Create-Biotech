package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

/** Concurrent persisted orders, each executing its recipe tree from leaves to root. */
public final class GaugeCraftJobs extends SavedData {
	public static final int MAX_ACTIVE_JOBS = 16;
	private static final String NAME = "create_biotech_gauge_craft_jobs";
	private final List<Job> jobs = new ArrayList<>();

	public record Summary(UUID id, ItemStack output, int count, String status, ItemStack currentOutput, int completed, int total,
		String address, boolean canCancel) {}

	public static GaugeCraftJobs get(MinecraftServer server) {
		return server.overworld().getDataStorage()
			.computeIfAbsent(new SavedData.Factory<>(GaugeCraftJobs::new, GaugeCraftJobs::load), NAME);
	}

	public static void onTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		GaugeCraftJobs data = get(server);
		if (server.getTickCount() % 10 != 0)
			return;
		data.collectOutputs(server);
		for (Job job : new ArrayList<>(data.jobs)) {
			if (data.tick(server, job))
				data.setDirty();
		}
	}

	public boolean submit(ServerPlayer player, BlockPos tickerPos, UUID network, ItemStack output, int count,
		String address, GaugeCraftPlan plan) {
		if (!plan.ready() || isFull(network))
			return false;
		Job job = new Job(UUID.randomUUID(), player.getUUID(), player.level().dimension(), tickerPos,
			network, output.copyWithCount(1), count, address, plan.steps());
		plan.stock().forEach(stock -> reserve(job, stock.network(), stock.stack(), stock.count()));
		jobs.add(job);
		setDirty();
		return true;
	}

	public boolean isFull(UUID network) {
		return jobs.stream().filter(job -> job.network.equals(network)).count() >= MAX_ACTIVE_JOBS;
	}

	public List<Summary> summaries(UUID network, UUID player, MinecraftServer server) {
		return jobs.stream().filter(job -> job.network.equals(network)).map(job -> {
			int total = job.steps.stream().mapToInt(GaugeCraftPlan.Step::crafts).sum();
			int completed = job.steps.stream().limit(job.index).mapToInt(GaugeCraftPlan.Step::crafts).sum();
			if (job.index < job.steps.size())
				completed += job.steps.get(job.index).crafts() - job.remaining;
			ItemStack currentOutput = job.index < job.steps.size()
				? job.steps.get(job.index).output().copy() : ItemStack.EMPTY;
			return new Summary(job.id, job.output.copy(), job.count, status(job, server), currentOutput, completed, total,
				job.address, job.owner.equals(player));
		}).toList();
	}

	private String status(Job job, MinecraftServer server) {
		ServerLevel level = server.getLevel(job.dimension);
		if (level == null || !loaded(level, job.tickerPos))
			return "paused_unloaded";
		if (job.index >= job.steps.size())
			return job.address.isBlank() ? "waiting_storage" : "delivering";
		GaugeCraftPlan.Step step = job.steps.get(job.index);
		if (!loaded(level, step.gauge().pos())
			|| step.inputs().stream().anyMatch(input -> !loaded(level, input.source().pos())))
			return "paused_unloaded";
		if (!matches(level, FactoryPanelBehaviour.at(level, step.gauge()), step))
			return "paused_changed";
		return job.waiting ? "waiting_output" : "processing";
	}

	public boolean cancel(UUID network, UUID player, UUID id) {
		boolean removed = jobs.removeIf(job -> job.id.equals(id) && job.network.equals(network)
			&& job.owner.equals(player));
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
				amount = Math.min(GaugeCraftPlan.MAX_REQUEST, amount + step.outputPerCraft() - job.received);
		}
		return amount;
	}

	private static boolean loaded(Level level, BlockPos pos) {
		return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
	}

	public int reservedAmount(UUID network, ItemStack stack, UUID exceptJob) {
		long amount = 0;
		for (Job job : jobs) {
			if (job.id.equals(exceptJob))
				continue;
			for (GaugeCraftPlan.Stock stock : job.reserved)
				if (stock.network().equals(network) && ItemStack.isSameItemSameComponents(stock.stack(), stack))
					amount += stock.count();
		}
		return (int) Math.min(Integer.MAX_VALUE, amount);
	}

	private int available(UUID network, ItemStack stack, Job job) {
		return Math.max(0, LogisticsManager.getSummaryOfNetwork(network, true).getCountOf(stack)
			- reservedAmount(network, stack, job.id));
	}

	private static void reserve(Job job, UUID network, ItemStack stack, int amount) {
		if (amount == 0)
			return;
		for (int i = 0; i < job.reserved.size(); i++) {
			GaugeCraftPlan.Stock stock = job.reserved.get(i);
			if (!stock.network().equals(network) || !ItemStack.isSameItemSameComponents(stock.stack(), stack))
				continue;
			int updated = stock.count() + amount;
			if (updated <= 0)
				job.reserved.remove(i);
			else
				job.reserved.set(i, new GaugeCraftPlan.Stock(network, stock.stack(), updated));
			return;
		}
		if (amount > 0)
			job.reserved.add(new GaugeCraftPlan.Stock(network, stack.copyWithCount(1), amount));
	}

	/** Each positive inventory delta can satisfy only one pending batch, including partial arrivals. */
	public void collectOutputs(MinecraftServer server) {
		List<Job> pending = new ArrayList<>(jobs.stream().filter(job -> job.waiting
			&& job.index < job.steps.size()).toList());
		while (!pending.isEmpty()) {
			Job first = pending.removeFirst();
			GaugeCraftPlan.Step step = first.steps.get(first.index);
			List<Job> group = new ArrayList<>();
			group.add(first);
			pending.removeIf(job -> {
				GaugeCraftPlan.Step other = job.steps.get(job.index);
				if (!step.network().equals(other.network())
					|| !ItemStack.isSameItemSameComponents(step.output(), other.output()))
					return false;
				group.add(job);
				return true;
			});
			if (group.stream().anyMatch(job -> server.getLevel(job.dimension) == null
				|| !loaded(server.getLevel(job.dimension), job.steps.get(job.index).gauge().pos())))
				continue;
			int current = LogisticsManager.getSummaryOfNetwork(step.network(), true).getCountOf(step.output());
			int arrived = Math.max(0, current - first.baseline);
			for (Job job : group) {
				int allocated = Math.min(arrived, job.steps.get(job.index).outputPerCraft() - job.received);
				if (job.baseline != current || allocated > 0)
					setDirty();
				job.baseline = current;
				job.received += allocated;
				arrived -= allocated;
				reserve(job, step.network(), step.output(), allocated);
			}
		}
	}

	/** Internal withdrawals must not hide the next arrival from another order on the same network. */
	private void rebaseOutputs(UUID network, ItemStack stack) {
		for (Job pending : jobs) {
			if (!pending.waiting || pending.index >= pending.steps.size())
				continue;
			GaugeCraftPlan.Step step = pending.steps.get(pending.index);
			if (step.network().equals(network) && ItemStack.isSameItemSameComponents(step.output(), stack))
				pending.baseline = LogisticsManager.getSummaryOfNetwork(network, true).getCountOf(stack);
		}
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
			if (available(job.network, job.output, job) < job.count)
				return false;
			if (!job.address.isBlank() && !deliver(job))
				return false;
			jobs.remove(job);
			ServerPlayer owner = server.getPlayerList().getPlayer(job.owner);
			if (owner != null)
				owner.displayClientMessage(Component.translatable(job.address.isBlank()
					? "create_biotech.gauge_craft.stored" : "create_biotech.gauge_craft.accepted")
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
			if (job.received < step.outputPerCraft())
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
		if (!dispatch(job, step))
			return false;
		job.baseline = LogisticsManager.getSummaryOfNetwork(step.network(), true).getCountOf(step.output());
		job.received = 0;
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

	private boolean deliver(Job job) {
		Multimap<PackagerBlockEntity, PackagingRequest> requests = LogisticsManager.findPackagersForRequest(
			job.network, PackageOrderWithCrafts.simple(List.of(new BigItemStack(job.output, job.count))),
			null, job.address);
		if (requests.values().stream().mapToInt(PackagingRequest::getCount).sum() < job.count
			|| requests.keySet().stream().anyMatch(packager -> packager.isTooBusyFor(RequestType.PLAYER)))
			return false;
		LogisticsManager.performPackageRequests(requests);
		rebaseOutputs(job.network, job.output);
		return true;
	}

	private boolean dispatch(Job job, GaugeCraftPlan.Step step) {
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
				if (available(group.getKey(), input.stack, job) < input.count)
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
		for (GaugeCraftPlan.Input input : step.inputs()) {
			reserve(job, input.network(), input.stack(), -input.amount());
			rebaseOutputs(input.network(), input.stack());
		}
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
		final List<GaugeCraftPlan.Stock> reserved = new ArrayList<>();
		int index, remaining, baseline, received;
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
			tag.putInt("Received", received);
			tag.putBoolean("Waiting", waiting);
			ListTag reservations = new ListTag();
			for (GaugeCraftPlan.Stock stock : reserved) {
				CompoundTag entry = new CompoundTag();
				entry.putUUID("Network", stock.network());
				entry.put("Stack", stock.stack().saveOptional(registries));
				entry.putInt("Count", stock.count());
				reservations.add(entry);
			}
			tag.put("Reserved", reservations);
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
			job.received = tag.getInt("Received");
			job.waiting = tag.getBoolean("Waiting");
			for (Tag raw : tag.getList("Reserved", Tag.TAG_COMPOUND)) {
				CompoundTag entry = (CompoundTag) raw;
				reserve(job, entry.getUUID("Network"), ItemStack.parseOptional(registries, entry.getCompound("Stack")),
					entry.getInt("Count"));
			}
			return job;
		}
	}
}
