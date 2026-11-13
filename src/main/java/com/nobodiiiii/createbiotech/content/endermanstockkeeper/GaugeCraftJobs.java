package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import com.google.common.collect.Multimap;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.RequestPromise;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;

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

/** Concurrent persisted orders with a bounded set of independent production branches. */
public final class GaugeCraftJobs extends SavedData {
	public static final int MAX_ACTIVE_JOBS = 16;
	public static final int MAX_PARALLEL_STEPS = 8;
	private static final int TICK_INTERVAL = 10;
	private static final String NAME = "create_biotech_gauge_craft_jobs";
	private final List<Job> jobs = new ArrayList<>();
	// Keep a bounded final snapshot so an open tree can show completion after the active job is removed.
	private final Map<UUID, FinishedTree> finishedTrees = new LinkedHashMap<>();
	private record FinishedTree(UUID network, GaugeCraftTree tree) {}

	public record Summary(UUID id, ItemStack output, int count, String status, ItemStack currentOutput, int completed, int total,
		String address, boolean canCancel) {}

	public static GaugeCraftJobs get(MinecraftServer server) {
		return server.overworld().getDataStorage()
			.computeIfAbsent(new SavedData.Factory<>(GaugeCraftJobs::new, GaugeCraftJobs::load), NAME);
	}

	public static void onTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		GaugeCraftJobs data = get(server);
		if (server.getTickCount() % TICK_INTERVAL != 0)
			return;
		data.collectOutputs();
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
			int completed = job.executions.stream().mapToInt(state -> state.step.crafts() - state.remaining).sum();
			ItemStack currentOutput = job.executions.stream().filter(Execution::active)
				.findFirst().map(state -> state.step.output().copy()).orElse(ItemStack.EMPTY);
			return new Summary(job.id, job.output.copy(), job.count, status(job, server), currentOutput, completed, total,
				job.address, job.owner.equals(player));
		}).toList();
	}

	public GaugeCraftTree tree(UUID network, UUID id, MinecraftServer server) {
		for (Job job : jobs)
			if (job.network.equals(network) && job.id.equals(id))
				return tree(job, status(job, server), false, server);
		FinishedTree finished = finishedTrees.get(id);
		return finished != null && finished.network().equals(network) ? finished.tree() : GaugeCraftTree.unavailable();
	}

	private static GaugeCraftTree tree(Job job, String status, boolean terminal, MinecraftServer server) {
		ServerLevel level = server == null ? null : server.getLevel(job.dimension);
		List<GaugeCraftTree.Progress> progress = new ArrayList<>();
		for (int index = 0; index < job.executions.size(); index++) {
			Execution execution = job.executions.get(index);
			GaugeCraftTree.State state = GaugeCraftTree.State.WAITING;
			if (execution.remaining == 0)
				state = GaugeCraftTree.State.COMPLETE;
			else if (terminal || level == null || !loaded(level, job.tickerPos)
				|| (execution.active() || job.dependenciesReady(index)) && !pauseReason(level, execution.step).isEmpty())
				state = GaugeCraftTree.State.PAUSED;
			else if (execution.active())
				state = GaugeCraftTree.State.CRAFTING;
			progress.add(new GaugeCraftTree.Progress(
				execution.step.crafts() - execution.remaining + execution.inFlight,
				(execution.step.crafts() - execution.remaining) * execution.step.outputPerCraft() + execution.received, state));
		}
		return GaugeCraftTree.snapshot(job.steps, job.flow, job.count, progress, status, terminal);
	}

	private void finish(Job job, String status) {
		finishedTrees.put(job.id, new FinishedTree(job.network, tree(job, status, true, null)));
		while (finishedTrees.size() > MAX_ACTIVE_JOBS * 4)
			finishedTrees.remove(finishedTrees.keySet().iterator().next());
	}

	private String status(Job job, MinecraftServer server) {
		ServerLevel level = server.getLevel(job.dimension);
		if (level == null || !loaded(level, job.tickerPos))
			return "paused_unloaded";
		if (job.complete())
			return job.address.isBlank() ? "waiting_storage" : "delivering";
		String paused = "";
		boolean waiting = false;
		for (int index = 0; index < job.executions.size(); index++) {
			Execution state = job.executions.get(index);
			if (state.remaining == 0 || !job.dependenciesReady(index))
				continue;
			String reason = pauseReason(level, state.step);
			if (!reason.isEmpty()) {
				if (paused.isEmpty())
					paused = reason;
			} else if (state.remaining > state.inFlight) {
				return "processing";
			} else {
				waiting = true;
			}
		}
		return waiting ? "waiting_output" : paused.isEmpty() ? "processing" : paused;
	}

	private static String pauseReason(ServerLevel level, GaugeCraftPlan.Step step) {
		if (!loaded(level, step.gauge().pos())
			|| Create.LOGISTICS.getUnloadedLinkCount(step.network()) > 0
			|| step.inputs().stream().anyMatch(input -> !loaded(level, input.source().pos())
				|| Create.LOGISTICS.getUnloadedLinkCount(input.network()) > 0))
			return "paused_unloaded";
		FactoryPanelBehaviour gauge = FactoryPanelBehaviour.at(level, step.gauge());
		if (!matches(level, gauge, step))
			return "paused_changed";
		return gauge.redstonePowered ? "paused_redstone" : "";
	}

	public boolean cancel(UUID network, UUID player, UUID id) {
		// Dispatched materials keep travelling, so their native promises must remain
		// until Create fulfils or expires them. Never clear another order's promises.
		boolean removed = jobs.removeIf(job -> {
			if (!job.id.equals(id) || !job.network.equals(network) || !job.owner.equals(player))
				return false;
			finish(job, "cancelled");
			return true;
		});
		if (removed)
			setDirty();
		return removed;
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

	/** Allocate actual arrivals to jobs; Create separately fulfils the native promises at each packager. */
	public void collectOutputs() {
		List<PendingOutput> pending = new ArrayList<>();
		for (Job job : jobs)
			for (Execution state : job.executions)
				if (state.inFlight > 0)
					pending.add(new PendingOutput(job, state));
		while (!pending.isEmpty()) {
			PendingOutput first = pending.removeFirst();
			GaugeCraftPlan.Step step = first.state.step;
			List<PendingOutput> group = new ArrayList<>();
			group.add(first);
			pending.removeIf(output -> {
				GaugeCraftPlan.Step other = output.state.step;
				if (!step.network().equals(other.network())
					|| !ItemStack.isSameItemSameComponents(step.output(), other.output()))
					return false;
				group.add(output);
				return true;
			});
			// Monitor the receiving network, not the producer chunks: a paused branch
			// can still receive packages and must not block its siblings' accounting.
			if (Create.LOGISTICS.getQueuedPromises(step.network()) == null
				|| Create.LOGISTICS.getUnloadedLinkCount(step.network()) > 0)
				continue;
			int current = LogisticsManager.getSummaryOfNetwork(step.network(), true).getCountOf(step.output());
			int arrived = Math.max(0, current - first.state.baseline);
			for (PendingOutput output : group) {
				Execution state = output.state;
				int allocated = Math.min(arrived, state.outstandingOutput());
				if (state.baseline != current || allocated > 0)
					setDirty();
				state.baseline = current;
				state.received += allocated;
				arrived -= allocated;
				reserve(output.job, step.network(), step.output(), allocated);
			}
		}
	}

	private record PendingOutput(Job job, Execution state) {}

	/** Internal withdrawals must not hide the next arrival from another order on the same network. */
	private void rebaseOutputs(UUID network, ItemStack stack) {
		for (Job job : jobs)
			for (Execution state : job.executions)
				if (state.inFlight > 0 && state.step.network().equals(network)
					&& ItemStack.isSameItemSameComponents(state.step.output(), stack))
					state.baseline = LogisticsManager.getSummaryOfNetwork(network, true).getCountOf(stack);
	}

	private boolean tick(MinecraftServer server, Job job) {
		ServerLevel level = server.getLevel(job.dimension);
		if (level == null || !loaded(level, job.tickerPos))
			return false;
		if (!(level.getBlockEntity(job.tickerPos) instanceof StockTickerBlockEntity ticker)
			|| !job.network.equals(ticker.behaviour.freqId)) {
			finish(job, "failed");
			jobs.remove(job);
			ServerPlayer owner = server.getPlayerList().getPlayer(job.owner);
			if (owner != null)
				owner.displayClientMessage(Component.translatable("create_biotech.gauge_craft.failed")
					.withStyle(ChatFormatting.RED), false);
			return true;
		}
		if (job.complete()) {
			if (available(job.network, job.output, job) < job.count)
				return false;
			if (!job.address.isBlank() && !deliver(job))
				return false;
			finish(job, "complete");
			jobs.remove(job);
			ServerPlayer owner = server.getPlayerList().getPlayer(job.owner);
			if (owner != null)
				owner.displayClientMessage(Component.translatable(job.address.isBlank()
					? "create_biotech.gauge_craft.stored" : "create_biotech.gauge_craft.accepted")
					.withStyle(ChatFormatting.GREEN), false);
			return true;
		}
		boolean changed = false;
		// Settle every branch before counting occupied slots or unlocking consumers.
		for (Execution state : job.executions) {
			if (state.migratePromise) {
				RequestPromiseQueue promises = Create.LOGISTICS.getQueuedPromises(state.step.network());
				if (promises != null) {
					if (state.outstandingOutput() > 0)
						promises.add(new RequestPromise(new BigItemStack(state.step.output(), state.outstandingOutput())));
					state.migratePromise = false;
					changed = true;
				}
			}
			if (state.received >= state.step.outputPerCraft()) {
				int completed = state.received / state.step.outputPerCraft();
				state.received %= state.step.outputPerCraft();
				state.inFlight -= completed;
				state.remaining -= completed;
				changed = true;
			}
		}
		changed |= job.schedule(state -> tickExecution(level, job, state));
		return changed;
	}

	private boolean tickExecution(ServerLevel level, Job job, Execution state) {
		if (!pauseReason(level, state.step).isEmpty())
			return false;
		if (state.requestCooldown > 0) {
			state.requestCooldown = Math.max(0,
				Math.min(state.requestCooldown, AllConfigs.server().logistics.factoryGaugeTimer.get()) - TICK_INTERVAL);
			if (state.requestCooldown > 0)
				return true;
		}
		state.requestCooldown = AllConfigs.server().logistics.factoryGaugeTimer.get();
		RequestPromiseQueue promises = Create.LOGISTICS.getQueuedPromises(state.step.network());
		if (promises == null || !dispatch(job, state.step))
			return true;
		if (state.inFlight == 0)
			state.baseline = LogisticsManager.getSummaryOfNetwork(state.step.network(), true).getCountOf(state.step.output());
		state.inFlight++;
		promises.add(new RequestPromise(new BigItemStack(state.step.output(), state.step.outputPerCraft())));
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

	private static final class Execution {
		final GaugeCraftPlan.Step step;
		// remaining includes inFlight; received has not yet been settled into full rounds.
		int remaining, baseline, received, inFlight, requestCooldown;
		boolean migratePromise;

		Execution(GaugeCraftPlan.Step step) {
			this.step = step;
			remaining = step.crafts();
		}

		boolean active() {
			return remaining > 0 && (inFlight > 0 || remaining < step.crafts());
		}

		int outstandingOutput() {
			return inFlight * step.outputPerCraft() - received;
		}

		void save(CompoundTag tag) {
			tag.putInt("Remaining", remaining);
			tag.putInt("Baseline", baseline);
			tag.putInt("Received", received);
			tag.putInt("InFlight", inFlight);
			tag.putInt("RequestCooldown", requestCooldown);
			tag.putBoolean("MigratePromise", migratePromise);
		}

		void load(CompoundTag tag) {
			if (!tag.contains("InFlight", Tag.TAG_INT))
				throw new IllegalArgumentException("Missing gauge crafting in-flight progress");
			remaining = tag.getInt("Remaining");
			baseline = tag.getInt("Baseline");
			received = tag.getInt("Received");
			inFlight = tag.getInt("InFlight");
			requestCooldown = tag.getInt("RequestCooldown");
			migratePromise = tag.getBoolean("MigratePromise");
			validateProgress();
		}

		void loadBeta2(CompoundTag tag) {
			remaining = tag.getInt("Remaining");
			baseline = tag.getInt("Baseline");
			inFlight = tag.getBoolean("Waiting") ? 1 : 0;
			migratePromise = inFlight > 0;
			validateProgress();
		}

		private void validateProgress() {
			if (remaining < 0 || remaining > step.crafts() || inFlight < 0 || inFlight > remaining
				|| received < 0 || received > inFlight * step.outputPerCraft() || requestCooldown < 0)
				throw new IllegalArgumentException("Invalid gauge crafting step progress");
		}
	}

	private static final class Job {
		final UUID id, owner, network;
		final ResourceKey<Level> dimension;
		final BlockPos tickerPos;
		final ItemStack output;
		final int count;
		final String address;
		final List<GaugeCraftPlan.Step> steps;
		final List<Execution> executions;
		final GaugeCraftFlow flow;
		final List<GaugeCraftPlan.Stock> reserved = new ArrayList<>();

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
			this.executions = steps.stream().map(Execution::new).toList();
			this.flow = new GaugeCraftFlow(steps);
		}

		boolean complete() {
			return executions.stream().allMatch(state -> state.remaining == 0);
		}

		boolean dependenciesReady(int index) {
			for (GaugeCraftFlow.Supply supply : flow.inputs(index))
				if (supply.source() >= 0 && executions.get(supply.source()).remaining > 0)
					return false;
			return true;
		}

		boolean schedule(Predicate<Execution> tick) {
			int active = (int) executions.stream().filter(Execution::active).count();
			boolean changed = false;
			for (int index = 0; index < executions.size(); index++) {
				Execution state = executions.get(index);
				// Completed output + in-flight output covers this finite target.
				// Permanent gauge stock targets must not cancel a player's new order.
				if (state.remaining <= state.inFlight || state.migratePromise || !dependenciesReady(index))
					continue;
				boolean wasActive = state.active();
				if (!wasActive && active >= MAX_PARALLEL_STEPS)
					continue;
				changed |= tick.test(state);
				if (!wasActive && state.active())
					active++;
			}
			return changed;
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
			tag.putInt("ProgressVersion", 1);
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
			for (Execution state : executions) {
				GaugeCraftPlan.Step step = state.step;
				CompoundTag s = new CompoundTag();
				state.save(s);
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
			if (tag.getInt("ProgressVersion") >= 1) {
				ListTag savedSteps = tag.getList("Steps", Tag.TAG_COMPOUND);
				for (int index = 0; index < job.executions.size(); index++)
					job.executions.get(index).load(savedSteps.getCompound(index));
			} else {
				// beta.2 saved one serial step at the job root.
				int current = tag.getInt("Index");
				if (current < 0 || current > job.executions.size())
					throw new IllegalArgumentException("Invalid legacy gauge crafting index");
				for (int index = 0; index < job.executions.size(); index++) {
					Execution state = job.executions.get(index);
					if (index < current)
						state.remaining = 0;
					else if (index == current)
						state.loadBeta2(tag);
				}
			}
			for (Tag raw : tag.getList("Reserved", Tag.TAG_COMPOUND)) {
				CompoundTag entry = (CompoundTag) raw;
				reserve(job, entry.getUUID("Network"), ItemStack.parseOptional(registries, entry.getCompound("Stack")),
					entry.getInt("Count"));
			}
			return job;
		}
	}
}
