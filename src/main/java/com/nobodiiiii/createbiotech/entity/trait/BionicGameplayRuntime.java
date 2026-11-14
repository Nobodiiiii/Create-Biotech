package com.nobodiiiii.createbiotech.entity.trait;

import java.util.*;
import com.nobodiiiii.createbiotech.api.bionic.BionicGameplayEvent;
import com.nobodiiiii.createbiotech.api.bionic.BionicGameplayEvent.Kind;
import com.nobodiiiii.createbiotech.api.bionic.BionicGameplayEvent.Phase;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.neoforged.neoforge.common.NeoForge;

/** Native gameplay and scripted content share output transactions and per-entity persistent timers. */
public final class BionicGameplayRuntime {
	private BionicGameplayRuntime() {}
	public static BionicGameplayEvent event(Kind kind, SlimeBionicEntity entity, ResourceLocation id) {
		return new BionicGameplayEvent(kind, Phase.BEFORE, entity, id, entity.getAssembly());
	}
	public static BionicGameplayEvent post(BionicGameplayEvent event) {
		if (!event.getEntity().level().isClientSide) NeoForge.EVENT_BUS.post(event);
		return event;
	}
	static List<ItemStack> roll(SlimeBionicEntity entity, BionicGameplayRules.Output output, int organs) {
		List<ItemStack> result = new ArrayList<>();
		for (int i = 0; i < Math.min(4096, organs); i++) {
			int count = output.min() + entity.getRandom().nextInt(output.max() - output.min() + 1);
			if (output.item() != null) {
				if (count > 0) result.add(new ItemStack(output.item(), count));
			} else if (entity.level() instanceof ServerLevel level) {
				var params = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, entity)
					.withParameter(LootContextParams.ORIGIN, entity.position()).create(LootContextParamSets.GIFT);
				var table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, output.loot()));
				for (int n = 0; n < count; n++) result.addAll(table.getRandomItems(params));
			}
			if (result.size() > 4096) throw new IllegalArgumentException("Bionic loot output exceeds 4096 stacks");
		}
		return result;
	}
	public static List<ItemStack> output(SlimeBionicEntity entity, BionicTrait trait, String channel,
		List<ItemStack> fallback, int organs) {
		var output = BionicGameplayRules.get(entity.level(), trait).outputs.get(channel);
		var event = post(event(Kind.PRODUCE, entity, ResourceLocation.fromNamespaceAndPath(trait.id().getNamespace(), trait.id().getPath() + "/" + channel))
			.outputs(output == null ? fallback : roll(entity, output, organs)));
		return event.isCancelled() ? List.of() : event.getOutputs();
	}
	public static void produced(SlimeBionicEntity entity, BionicTrait trait, String channel, List<ItemStack> outputs) {
		post(event(Kind.PRODUCE, entity, ResourceLocation.fromNamespaceAndPath(trait.id().getNamespace(), trait.id().getPath() + "/" + channel)).outputs(outputs).after());
	}
	public static boolean drop(SlimeBionicEntity entity, BionicTrait trait, String channel, Item item, int count) {
		List<ItemStack> fallback = new ArrayList<>();
		for (int left = count; left > 0;) {
			var stack = new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())); fallback.add(stack); left -= stack.getCount();
		}
		var outputs = output(entity, trait, channel, fallback, count);
		if (outputs.isEmpty()) return false;
		outputs.forEach(entity::spawnAtLocation); produced(entity, trait, channel, outputs);
		return true;
	}
	public static void tick(SlimeBionicEntity entity) {
		if (entity.level().isClientSide || entity.isNoAi() || !entity.isAlive()) return;
		var timers = entity.getPersistentData().getCompound("BionicGameplayTimers");
		Set<String> retained = new HashSet<>();
		long now = entity.level().getGameTime();
		Set<String> interactionKeys = new HashSet<>();
		for (var trait : entity.getBionicTraits().definitions()) {
			if (!entity.getBionicTraits().has(trait)) continue;
			var rules = BionicGameplayRules.get(entity.level(), trait);
			rules.interactions.forEach(i -> interactionKeys.add(trait.id() + "/" + i.id()));
			for (var cycle : rules.production) {
				String key = trait.id() + "/" + cycle.id(); retained.add(key);
				if (!timers.contains(key)) { timers.putLong(key, now + cycle.min() + entity.getRandom().nextInt(cycle.max() - cycle.min() + 1)); continue; }
				if (timers.getLong(key) > now) continue;
				int organs = cycle.organ() == null ? 1 : BionicTraitRuntime.membersWithRole(entity, trait, cycle.organ()).size();
				var before = post(event(Kind.PRODUCE, entity, ResourceLocation.parse(cycle.id())).outputs(roll(entity, cycle.output(), organs)));
				if (!before.isCancelled()) { before.getOutputs().forEach(entity::spawnAtLocation); post(before.after()); }
				timers.putLong(key, now + cycle.min() + entity.getRandom().nextInt(cycle.max() - cycle.min() + 1));
			}
		}
		for (String key : List.copyOf(timers.getAllKeys())) if (!retained.contains(key)) timers.remove(key);
		entity.getPersistentData().put("BionicGameplayTimers", timers);
		var cooldowns = entity.getPersistentData().getCompound("BionicInteractionCooldowns");
		for (String key : List.copyOf(cooldowns.getAllKeys())) if (!interactionKeys.contains(key) || cooldowns.getLong(key) <= now) cooldowns.remove(key);
		entity.getPersistentData().put("BionicInteractionCooldowns", cooldowns);
	}
	public static InteractionResult interact(SlimeBionicEntity entity, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		var cooldowns = entity.getPersistentData().getCompound("BionicInteractionCooldowns");
		for (var trait : entity.getBionicTraits().definitions()) {
			if (!entity.getBionicTraits().has(trait)) continue;
			for (var interaction : BionicGameplayRules.get(entity.level(), trait).interactions) {
				String key = trait.id() + "/" + interaction.id();
				if (!interaction.ingredient().item(held) || held.getCount() < interaction.consume()
					|| interaction.ownerOnly() && entity.getBionicOwnerId() != null && !entity.getBionicOwnerId().equals(player.getUUID())
					|| cooldowns.getLong(key) > entity.level().getGameTime()) continue;
				if (entity.level().isClientSide) return InteractionResult.SUCCESS;
				var before = post(event(Kind.PRODUCE, entity, ResourceLocation.parse(interaction.id()))
					.interaction(player, hand, held).outputs(roll(entity, interaction.output(), 1)));
				if (before.isCancelled()) return InteractionResult.FAIL;
				if (interaction.damage() > 0) held.hurtAndBreak(interaction.damage(), player, LivingEntity.getSlotForHand(hand));
				held.consume(interaction.consume(), player);
				for (ItemStack stack : before.getOutputs()) if (!player.getInventory().add(stack)) player.drop(stack, false);
				cooldowns.putLong(key, entity.level().getGameTime() + interaction.cooldown());
				entity.getPersistentData().put("BionicInteractionCooldowns", cooldowns); post(before.after());
				return InteractionResult.CONSUME;
			}
		}
		return InteractionResult.PASS;
	}
	public static boolean nativeInteractions(SlimeBionicEntity entity, BionicTrait trait) {
		return BionicGameplayRules.get(entity.level(), trait).interactions.isEmpty();
	}
	public static BionicGameplayRules.Equipment equipment(SlimeBionicEntity entity, BionicTrait trait, ResourceLocation donor) {
		return BionicGameplayRules.get(entity.level(), trait).equipment(donor);
	}
	public static boolean tamingItem(SlimeBionicEntity entity, ItemStack item) {
		var assembly = entity.getAssembly(); if (assembly == null) return false;
		for (var trait : List.of(BionicTrait.TAMEABLE, BionicTrait.LIVESTOCK_TAMEABLE))
			for (var member : entity.getBionicTraits().members(trait)) {
				var rule = equipment(entity, trait, assembly.sources().get(member.source()).profile().entityTypeId());
				if (rule == null || rule.tameItem() == null ? item.is(Items.SLIME_BALL) : rule.tameItem().item(item)) return true;
			}
		return false;
	}
	public static double tamingChance(SlimeBionicEntity entity, ItemStack item) {
		double chance = -1;
		var assembly = entity.getAssembly();
		if (assembly != null) for (var trait : List.of(BionicTrait.TAMEABLE, BionicTrait.LIVESTOCK_TAMEABLE))
			for (var member : entity.getBionicTraits().members(trait)) {
				var rule = equipment(entity, trait, assembly.sources().get(member.source()).profile().entityTypeId());
				if (rule == null || rule.tameItem() == null ? item.is(Items.SLIME_BALL) : rule.tameItem().item(item))
					chance = Math.max(chance, rule != null && rule.chance() >= 0 ? rule.chance() : entity.getBionicTraits().tamingChance());
			}
		return chance < 0 ? entity.getBionicTraits().tamingChance() : chance;
	}
}
