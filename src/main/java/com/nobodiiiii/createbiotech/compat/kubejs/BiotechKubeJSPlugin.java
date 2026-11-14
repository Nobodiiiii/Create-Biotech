package com.nobodiiiii.createbiotech.compat.kubejs;

import com.nobodiiiii.createbiotech.api.bionic.BionicGameplayEvent;
import com.nobodiiiii.createbiotech.api.bionic.BionicGameplayApi;
import com.nobodiiiii.createbiotech.api.bionic.BionicStat;
import com.nobodiiiii.createbiotech.api.bionic.BionicStatApi;

import com.google.gson.JsonObject;
import com.nobodiiiii.createbiotech.api.bionic.BionicTraitApi;
import com.nobodiiiii.createbiotech.api.bionic.BionicTraitEvent;
import dev.latvian.mods.kubejs.event.*;
import dev.latvian.mods.kubejs.generator.KubeDataGenerator;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;

/** Loaded only by KubeJS's optional plugin discovery; common code has no references to this class. */
public final class BiotechKubeJSPlugin implements KubeJSPlugin {
	public static final EventGroup GAMEPLAY_GROUP = EventGroup.of("BionicGameplayEvents");
	private static final java.util.Map<BionicGameplayEvent.Kind, TargetedEventHandler<ResourceLocation>> GAMEPLAY = new java.util.EnumMap<>(BionicGameplayEvent.Kind.class);
	static {
		for (var kind : BionicGameplayEvent.Kind.values()) {
			String name = switch (kind) { case SPECIAL_ATTACK -> "specialAttack"; case TRAIT_CHANGED -> "traitChanged"; default -> kind.name().toLowerCase(java.util.Locale.ROOT); };
			GAMEPLAY.put(kind, GAMEPLAY_GROUP.server(name, () -> Gameplay.class).supportsTarget(EventTargetType.ID).hasResult());
		}
	}
	public static final EventGroup GROUP = EventGroup.of("BionicTraitEvents");
	public static final EventGroup STATS_GROUP = EventGroup.of("BionicStatEvents");
	public static final EventHandler FORMULAS = STATS_GROUP.server("formulas", () -> Formulas.class);
	public static final EventHandler DEFINITIONS = GROUP.server("definitions", () -> Definitions.class);
	public static final TargetedEventHandler<ResourceLocation> TICK = GROUP.server("tick", () -> Runtime.class).supportsTarget(EventTargetType.ID);
	public static final TargetedEventHandler<ResourceLocation> MELEE = GROUP.server("melee", () -> Melee.class).supportsTarget(EventTargetType.ID);
	public static final TargetedEventHandler<ResourceLocation> HURT = GROUP.server("hurt", () -> Hurt.class).supportsTarget(EventTargetType.ID).hasResult();

	@Override public void registerEvents(EventGroupRegistry registry) { registry.register(GROUP); registry.register(STATS_GROUP); registry.register(GAMEPLAY_GROUP); }
	@Override public void registerBindings(BindingRegistry bindings) {
		bindings.add("BionicTraits", BionicTraitApi.class);
		bindings.add("BionicGameplay", BionicGameplayApi.class);
		bindings.add("BionicStats", BionicStatApi.class);
	}
	@Override public void init() {
		NeoForge.EVENT_BUS.addListener(BiotechKubeJSPlugin::gameplay);
		NeoForge.EVENT_BUS.addListener(BiotechKubeJSPlugin::tick);
		NeoForge.EVENT_BUS.addListener(BiotechKubeJSPlugin::melee);
		NeoForge.EVENT_BUS.addListener(BiotechKubeJSPlugin::hurt);
	}
	@Override public void generateData(KubeDataGenerator generator) {
		if (DEFINITIONS.hasListeners()) DEFINITIONS.post(new Definitions(generator));
		if (FORMULAS.hasListeners()) FORMULAS.post(new Formulas(generator));
	}
	private static void gameplay(BionicGameplayEvent event) {
		var handler = GAMEPLAY.get(event.getKind());
		if (handler.hasListeners(event.getId()) && handler.post(new Gameplay(event), event.getId()).interruptFalse()
			&& event.getPhase() == BionicGameplayEvent.Phase.BEFORE) event.cancel();
	}
	public static final class Gameplay implements KubeEvent {
		private final BionicGameplayEvent event;
		public Gameplay(BionicGameplayEvent event) { this.event = event; }
		public String getPhase() { return event.getPhase().name().toLowerCase(java.util.Locale.ROOT); }
		public ResourceLocation getId() { return event.getId(); }
		public com.nobodiiiii.createbiotech.entity.SlimeBionicEntity getEntity() { return event.getEntity(); }
		public com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly getAssembly() { return event.getAssembly(); }
		public net.minecraft.world.entity.player.Player getPlayer() { return event.getPlayer(); }
		public net.minecraft.world.entity.LivingEntity getTarget() { return event.getTarget(); }
		public net.minecraft.world.InteractionHand getHand() { return event.getHand(); }
		public net.minecraft.world.item.ItemStack getItem() { return event.getItem(); }
		public java.util.List<net.minecraft.world.item.ItemStack> getOutputs() { return event.getOutputs(); }
		public void setOutputs(java.util.List<net.minecraft.world.item.ItemStack> outputs) { event.setOutputs(outputs); }
		public double getAmount() { return event.getAmount(); }
		public void setAmount(double value) { event.setAmount(value); }
		public net.minecraft.world.InteractionResult getResult() { return event.getResult(); }
		public void setResult(net.minecraft.world.InteractionResult result) { event.setResult(result); }
	}
	public static final class Formulas implements KubeEvent {
		private final KubeDataGenerator generator;
		private final java.util.Set<String> written = new java.util.HashSet<>();
		public Formulas(KubeDataGenerator generator) { this.generator = generator; }
		public void set(String stat, String expression) {
			JsonObject json = new JsonObject(); json.addProperty("expression", expression); define(stat, json);
		}
		public void define(String stat, JsonObject definition) {
			String path = stat.startsWith("create_biotech:") ? stat.substring("create_biotech:".length()) : stat;
			var key = BionicStat.valueOf(path.toUpperCase(java.util.Locale.ROOT));
			if (!written.add(key.id())) throw new IllegalArgumentException("Duplicate scripted bionic stat: " + stat);
			generator.json(ResourceLocation.parse("create_biotech:bionic_stats/" + key.id() + ".json"), definition.deepCopy());
		}
	}
	private static void tick(BionicTraitEvent.Tick event) {
		if (TICK.hasListeners(event.getTrait().id())) TICK.post(new Runtime(event), event.getTrait().id());
	}
	private static void melee(BionicTraitEvent.Melee event) {
		if (MELEE.hasListeners(event.getTrait().id())) MELEE.post(new Melee(event), event.getTrait().id());
	}
	private static void hurt(BionicTraitEvent.Hurt event) {
		if (HURT.hasListeners(event.getTrait().id()) && HURT.post(new Hurt(event), event.getTrait().id()).interruptFalse()) event.setAmount(0);
	}

	public static final class Definitions implements KubeEvent {
		private final KubeDataGenerator generator;
		private final java.util.Set<ResourceLocation> written = new java.util.HashSet<>();
		public Definitions(KubeDataGenerator generator) { this.generator = generator; }
		public void gameplay(ResourceLocation trait, JsonObject rules) {
			JsonObject patch = new JsonObject(); patch.add("gameplay", rules.deepCopy());
			JsonObject modifier = new JsonObject(); modifier.addProperty("trait", trait.toString()); modifier.add("patch", patch);
			modify(trait.withSuffix("_gameplay"), modifier);
		}
		public void register(ResourceLocation id, JsonObject definition) { write("bionic_traits", id, definition); }
		public void bind(ResourceLocation id, JsonObject binding) { write("bionic_trait_bindings", id, binding); }
		public void anatomy(ResourceLocation id, JsonObject definition) { write("bionic_anatomy", id, definition); }
		public void modify(ResourceLocation id, JsonObject modifier) { write("bionic_trait_modifiers", id, modifier); }
		private void write(String directory, ResourceLocation id, JsonObject json) {
			var resource = id.withPath(directory + "/" + id.getPath() + ".json");
			if (!written.add(resource)) throw new IllegalArgumentException("Duplicate scripted bionic resource: " + resource);
			generator.json(resource, json.deepCopy());
		}
	}
	public static class Runtime implements KubeEvent {
		protected final BionicTraitEvent event;
		public Runtime(BionicTraitEvent event) { this.event = event; }
		public com.nobodiiiii.createbiotech.entity.SlimeBionicEntity getEntity() { return event.getEntity(); }
		public ResourceLocation getId() { return event.getTrait().id(); }
		public com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult getResult() { return event.getResult(); }
	}
	public static final class Melee extends Runtime {
		public Melee(BionicTraitEvent.Melee event) { super(event); }
		public net.minecraft.world.entity.LivingEntity getTarget() { return ((BionicTraitEvent.Melee)event).getTarget(); }
		public java.util.Set<com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.CombinationMember> getArm() { return ((BionicTraitEvent.Melee)event).getArm(); }
	}
	public static final class Hurt extends Runtime {
		public Hurt(BionicTraitEvent.Hurt event) { super(event); }
		public net.minecraft.world.damagesource.DamageSource getSource() { return ((BionicTraitEvent.Hurt)event).getSource(); }
		public float getAmount() { return ((BionicTraitEvent.Hurt)event).getAmount(); }
		public void setAmount(float amount) { ((BionicTraitEvent.Hurt)event).setAmount(amount); }
	}
}
