package com.nobodiiiii.createbiotech.api.bionic;

import java.util.List;
import java.util.ArrayList;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

/** Server-thread transaction events. Only BEFORE is cancellable/mutable; AFTER observes committed work. */
public final class BionicGameplayEvent extends Event {
	public enum Kind { INTERACT, PRODUCE, SPECIAL_ATTACK, ASSEMBLY, PACK, RELEASE, TAME, EQUIPMENT, TRAIT_CHANGED }
	public enum Phase { BEFORE, AFTER }
	private final Kind kind;
	private final Phase phase;
	private final SlimeBionicEntity entity;
	private final ResourceLocation id;
	private final SurgicalAssembly assembly;
	private Player player;
	private LivingEntity target;
	private InteractionHand hand;
	private ItemStack item = ItemStack.EMPTY;
	private List<ItemStack> outputs = List.of();
	private double amount = 1;
	private boolean cancelled;
	private InteractionResult result = InteractionResult.PASS;
	public BionicGameplayEvent(Kind kind, Phase phase, SlimeBionicEntity entity, ResourceLocation id, SurgicalAssembly assembly) {
		this.kind = kind; this.phase = phase; this.entity = entity; this.id = id; this.assembly = assembly;
	}
	public Kind getKind() { return kind; }
	public Phase getPhase() { return phase; }
	public SlimeBionicEntity getEntity() { return entity; }
	public ResourceLocation getId() { return id; }
	public SurgicalAssembly getAssembly() { return assembly; }
	public Player getPlayer() { return player; }
	public LivingEntity getTarget() { return target; }
	public InteractionHand getHand() { return hand; }
	public ItemStack getItem() { return item.copy(); }
	public List<ItemStack> getOutputs() { return outputs.stream().map(ItemStack::copy).toList(); }
	public double getAmount() { return amount; }
	public boolean isCancelled() { return cancelled; }
	public InteractionResult getResult() { return result; }
	public BionicGameplayEvent interaction(Player player, InteractionHand hand, ItemStack item) {
		if (phase != Phase.BEFORE) throw new IllegalStateException("AFTER event is read-only");
		this.player = player; this.hand = hand; this.item = item.copy(); return this;
	}
	public BionicGameplayEvent target(LivingEntity target) {
		if (phase != Phase.BEFORE) throw new IllegalStateException("AFTER event is read-only");
		this.target = target; return this;
	}
	public BionicGameplayEvent outputs(List<ItemStack> outputs) { setOutputs(outputs); return this; }
	public BionicGameplayEvent amount(double value) { setAmount(value); return this; }
	public void setOutputs(List<ItemStack> values) {
		if (phase != Phase.BEFORE) throw new IllegalStateException("AFTER event is read-only");
		if (values.size() > 4096 || values.stream().anyMatch(s -> s == null || s.getCount() < 0 || s.getCount() > 4096))
			throw new IllegalArgumentException("Invalid bionic outputs");
		List<ItemStack> normalized = new ArrayList<>();
		for (var value : values) for (int left = value.isEmpty() ? 0 : value.getCount(); left > 0;) {
			int count = Math.min(left, value.getMaxStackSize()); normalized.add(value.copyWithCount(count)); left -= count;
			if (normalized.size() > 4096) throw new IllegalArgumentException("Too many normalized bionic output stacks");
		}
		outputs = List.copyOf(normalized);
	}
	public void setAmount(double value) {
		if (phase != Phase.BEFORE) throw new IllegalStateException("AFTER event is read-only");
		if (!Double.isFinite(value) || value < 0 || value > 4096) throw new IllegalArgumentException("Invalid event amount");
		amount = value;
	}
	public void setResult(InteractionResult result) {
		if (phase != Phase.BEFORE) throw new IllegalStateException("AFTER event is read-only");
		this.result = java.util.Objects.requireNonNull(result);
	}
	public void cancel() {
		if (phase != Phase.BEFORE) throw new IllegalStateException("AFTER event cannot cancel committed work");
		cancelled = true;
	}
	public BionicGameplayEvent after() {
		var event = new BionicGameplayEvent(kind, Phase.AFTER, entity, id, assembly);
		event.player = player; event.target = target; event.hand = hand; event.item = item.copy();
		event.outputs = getOutputs(); event.amount = amount; event.result = result;
		return event;
	}
}
