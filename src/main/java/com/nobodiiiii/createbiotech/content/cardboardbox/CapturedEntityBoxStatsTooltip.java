package com.nobodiiiii.createbiotech.content.cardboardbox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCombatCalibration;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGait;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalHealthCalibration;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.trait.BionicHeadTraits;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitDonors;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResolver;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult.DetailKind;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult.DisplayEntry;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult.InactiveReason;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult.MissingPart;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitRule;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitScope;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitSet;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitType;
import com.simibubi.create.foundation.item.TooltipModifier;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Create-style Alt expansion for the base attributes of a boxed living entity. */
@OnlyIn(Dist.CLIENT)
public final class CapturedEntityBoxStatsTooltip implements TooltipModifier {
	private static ItemStack cachedStack = ItemStack.EMPTY;
	@Nullable
	private static BoxDetails cachedDetails;
	private static BionicTraitResolver.Revision cachedTraitRevision;
	@Nullable
	private static Level cachedLevel;

	@Override
	public void modify(ItemTooltipEvent context) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null)
			append(context.getItemStack(), minecraft.level, context.getToolTip());
	}

	/** Appends the same expandable section to inventory and surgical-table HUD tooltips. */
	public static void append(ItemStack stack, Level level, List<Component> tooltip) {
		BoxDetails details = details(stack, level);
		if (details == null)
			return;

		boolean expanded = Screen.hasAltDown();
		tooltip.add(Component.translatable("create_biotech.tooltip.hold_for_base_stats",
			Component.literal("Alt").withStyle(expanded ? ChatFormatting.WHITE : ChatFormatting.GRAY))
			.withStyle(ChatFormatting.DARK_GRAY));
		if (!expanded)
			return;

		if (!details.stats().isEmpty()) {
			tooltip.add(CommonComponents.EMPTY);
			tooltip.add(Component.translatable("create_biotech.tooltip.base_stats")
				.withStyle(ChatFormatting.GOLD));
			for (BaseStat stat : details.stats())
				tooltip.add(stat.line());
		}
		appendPropertiesSection(tooltip, details.traits());
		appendDonorFacts(tooltip, details.donorFacts(), details.donorAttacks(), details.deterrenceTargets(), details.huntingTargets());
	}

	private static void appendDonorFacts(List<Component> tooltip, Set<BionicTrait> facts,
		Set<ResourceLocation> attacks, Set<ResourceLocation> targets, Set<ResourceLocation> huntingTargets) {
		if (facts.isEmpty() && attacks.isEmpty())
			return;
		tooltip.add(CommonComponents.EMPTY);
		tooltip.add(Component.translatable("create_biotech.tooltip.donor_carrier_traits")
			.withStyle(ChatFormatting.GOLD));
		for (BionicTrait trait : BionicTrait.values())
			if (facts.contains(trait))
				appendProperty(tooltip, trait == BionicTrait.DETERRENCE ? deterrenceName(targets)
					: trait == BionicTrait.HUNTING_INSTINCT ? targetList(trait, huntingTargets)
					: Component.translatable(trait.descriptionId()));
		if (!attacks.isEmpty()) appendProperty(tooltip, effectList(BionicTrait.EFFECT_ATTACK, attacks));
	}

	/** Head classifications always show their runtime value; other inactive traits show reasons. */
	public static void appendPropertiesSection(List<Component> tooltip, BionicTraitSet traits) {
		tooltip.add(CommonComponents.EMPTY);
		tooltip.add(Component.translatable("create_biotech.tooltip.properties")
			.withStyle(ChatFormatting.GOLD));
		List<Component> classifications = new ArrayList<>();
		Map<BionicTraitScope, List<Component>> groups = new java.util.EnumMap<>(BionicTraitScope.class);
		for (BionicTraitScope scope : BionicTraitScope.values())
			groups.put(scope, new ArrayList<>());
		for (BionicTrait trait : BionicTrait.values()) {
			BionicTraitResult result = traits.result(trait);
			if (trait.isHeadClassification() && !result.active()) {
				Component value = Component.translatable("create_biotech.trait.variant."
					+ BionicHeadTraits.defaultValue(trait).getPath());
				appendProperty(classifications, Component.translatable(trait.descriptionId(), value)
					.append(Component.translatable("create_biotech.trait.default").withStyle(ChatFormatting.DARK_GRAY)));
				continue;
			}
			if (!result.present())
				continue;
			if (trait.valueKind() == BionicTrait.ValueKind.NUMBER && result.active() && result.value() == 0)
				continue;
			List<BionicTraitScope> scopes = trait.scopes().stream().sorted().toList();
			List<Component> lines = trait.isHeadClassification() ? classifications : groups.get(scopes.getFirst());
			if (!result.displayEntries().isEmpty()) {
				Map<DetailGroup, Set<ResourceLocation>> details = new java.util.LinkedHashMap<>();
				result.displayEntries().stream().sorted(java.util.Comparator
					.comparing((DisplayEntry entry) -> !entry.active())
					.thenComparing(DisplayEntry::kind).thenComparing(DisplayEntry::id))
					.forEach(entry -> details.computeIfAbsent(new DetailGroup(entry.kind(), entry.inactiveReason(), entry.missingParts()),
						ignored -> new java.util.HashSet<>()).add(entry.id()));
				details.forEach((group, ids) -> appendTraitLine(lines, trait, scopes, result,
					detailName(trait, group.kind(), ids, group.reason() == null), group.reason(), group.missingParts()));
				continue;
			}
			Component name = Component.translatable(trait.nameDescriptionId());
			if (trait.valueKind() == BionicTrait.ValueKind.NUMBER && result.active())
				name = Component.translatable(trait.descriptionId(),
					String.format(java.util.Locale.ROOT, "%.1f", result.value()
						* (trait == BionicTrait.KNOCKBACK_RESISTANCE ? 100 : 1)));
			appendTraitLine(lines, trait, scopes, result, name, result.inactiveReason(), result.missingParts());
		}
		tooltip.addAll(classifications);
		appendTraitGroup(tooltip, Component.translatable(BionicTraitScope.WHOLE_BODY.descriptionId()),
			groups.remove(BionicTraitScope.WHOLE_BODY));
		MutableComponent headName = Component.translatable(BionicTraitScope.HEAD.descriptionId());
		if (traits.headUsesTorso())
			headName.append(Component.translatable("create_biotech.trait.slot.head_uses_torso")
				.withStyle(ChatFormatting.DARK_GRAY));
		appendTraitGroup(tooltip, headName, groups.remove(BionicTraitScope.HEAD));
		groups.forEach((scope, lines) -> appendTraitGroup(tooltip, Component.translatable(scope.descriptionId()), lines));
	}

	private record DetailGroup(DetailKind kind, @Nullable InactiveReason reason,
		List<List<MissingPart>> missingParts) {}

	/** Each row has one status; only distinct items with the same reason share parentheses. */
	private static void appendTraitLine(List<Component> lines, BionicTrait trait, List<BionicTraitScope> scopes,
		BionicTraitResult result, Component name, @Nullable InactiveReason reason,
		List<List<MissingPart>> missingParts) {
		if (scopes.size() > 1) {
			MutableComponent names = Component.empty();
			for (var scope : scopes) {
				if (!names.getSiblings().isEmpty())
					names.append(Component.translatable("create_biotech.trait.list_separator"));
				names.append(Component.translatable(scope.descriptionId()));
			}
			name = Component.translatable("create_biotech.trait.allowed_scopes", name, names);
		}
		if (reason != null) {
			lines.add(Component.literal(" ").append(name).append(Component.literal(" — "))
				.append(inactiveReason(reason, missingParts)).withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		if (trait.valueKind() == BionicTrait.ValueKind.ABILITY
			&& trait.rule().type() == BionicTraitType.COVERAGE_SCALED
			&& result.strength() < 1.0d - BionicTraitRule.COVERAGE_EPSILON)
			name = Component.translatable("create_biotech.trait.partial", name,
				Long.toString(Math.round(result.strength() * 100.0d)));
		appendProperty(lines, name);
	}

	private static Component inactiveReason(InactiveReason reason, List<List<MissingPart>> missingParts) {
		String key = reason.descriptionId();
		if (reason != InactiveReason.MISSING_REQUIRED_PARTS)
			return Component.translatable(key);
		if (missingParts.isEmpty())
			return Component.translatable(key + ".unknown");
		MutableComponent alternatives = Component.empty();
		for (var missing : missingParts) {
			if (!alternatives.getSiblings().isEmpty())
				alternatives.append(Component.translatable("create_biotech.trait.part.alternative_separator"));
			MutableComponent parts = Component.empty();
			for (var part : missing) {
				if (!parts.getSiblings().isEmpty())
					parts.append(Component.translatable("create_biotech.trait.list_separator"));
				String role = part.role().name().toLowerCase(java.util.Locale.ROOT);
				String specific = "create_biotech.trait.part." + part.species().getNamespace() + "."
					+ part.species().getPath() + "." + role;
				Component species = BuiltInRegistries.ENTITY_TYPE.getOptional(part.species())
					.map(type -> Component.translatable(type.getDescriptionId()))
					.orElseGet(() -> Component.literal(part.species().toString()));
				parts.append(Language.getInstance().has(specific) ? Component.translatable(specific)
					: Component.translatable("create_biotech.trait.part." + role, species));
			}
			alternatives.append(parts);
		}
		return Component.translatable(key, alternatives);
	}

	private static Component deterrenceName(Set<ResourceLocation> targets) {
		return targetList(BionicTrait.DETERRENCE, targets);
	}

	private static Component targetList(BionicTrait trait, Set<ResourceLocation> targets) {
		return detailName(trait, DetailKind.TARGET, targets, true);
	}

	private static Component effectList(BionicTrait trait, Set<ResourceLocation> effects) {
		return detailName(trait, DetailKind.EFFECT, effects, true);
	}

	private static Component detailName(BionicTrait trait, DetailKind kind, Set<ResourceLocation> ids, boolean active) {
		Component base = Component.translatable(trait.nameDescriptionId());
		if (ids.isEmpty()) return base;
		MutableComponent names = Component.empty();
		List<ResourceLocation> sorted = ids.stream().sorted().toList();
		int shown = Math.min(3, sorted.size());
		for (int index = 0; index < shown; index++) {
			if (index > 0) names.append(Component.translatable("create_biotech.trait.list_separator"));
			ResourceLocation id = sorted.get(index);
			Component value = switch (kind) {
			case TARGET -> BuiltInRegistries.ENTITY_TYPE.getOptional(id)
				.map(type -> Component.translatable(type.getDescriptionId()))
				.orElseGet(() -> {
					String key = "create_biotech.trait.hunt_target." + id.getPath();
					return Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(id.toString());
				});
			case EFFECT -> BuiltInRegistries.MOB_EFFECT.getOptional(id)
				.map(effect -> effect.getDisplayName()).orElseGet(() -> Component.literal(id.toString()));
			case SUBTRAIT -> Component.translatable("create_biotech.trait.variant." + id.getPath());
			};
			if (trait == BionicTrait.DISPOSITION && active)
				value = value.copy().withStyle(switch (id.getPath()) {
				case "friendly" -> ChatFormatting.GREEN;
				case "hostile" -> ChatFormatting.RED;
				default -> ChatFormatting.YELLOW;
				});
			names.append(value);
		}
		if (sorted.size() > shown)
			names.append(Component.translatable("create_biotech.trait.list_separator")).append(Component.literal("…"));
		if (trait == BionicTrait.IMMUNE_EFFECTS)
			return Component.translatable("create_biotech.trait.effect_immunity", names);
		if (trait.valueKind() == BionicTrait.ValueKind.SUBTRAIT_SET || kind != DetailKind.SUBTRAIT)
			return Component.translatable(trait.descriptionId(), names);
		return Component.translatable("create_biotech.trait.with_variants", base, names);
	}

	private static void appendTraitGroup(List<Component> tooltip, Component name,
		List<Component> lines) {
		if (lines.isEmpty())
			return;
		tooltip.add(Component.literal(" ").append(name.copy()
			.withStyle(ChatFormatting.DARK_AQUA)));
		for (Component line : lines)
			tooltip.add(Component.literal(" ").append(line));
	}

	private static void appendProperty(List<Component> tooltip, Component property) {
		tooltip.add(Component.literal(" ").append(property.copy().withStyle(ChatFormatting.GRAY)));
	}

	@Nullable
	private static BoxDetails details(ItemStack stack, Level level) {
		BionicTraitResolver.Revision revision = BionicTraitResolver.revision();
		if (level == cachedLevel && revision.equals(cachedTraitRevision)
			&& ItemStack.isSameItemSameComponents(cachedStack, stack))
			return cachedDetails;

		cachedStack = stack.copyWithCount(1);
		cachedLevel = level;
		cachedTraitRevision = revision;
		cachedDetails = captureDetails(stack, level);
		return cachedDetails;
	}

	@Nullable
	private static BoxDetails captureDetails(ItemStack stack, Level level) {
		Entity entity = CapturedEntityBoxHelper.createCapturedEntity(stack, level);
		if (!(entity instanceof LivingEntity living))
			return null;
		if (!(living instanceof SlimeBionicEntity bionic)) {
			MimicProfile profile = MimicProfile.capture(living);
			BionicTraitDonors.Facts facts = profile == null ? BionicTraitDonors.detect(living)
				: BionicTraitDonors.get(profile, level);
			return new BoxDetails(List.of(),
				BionicTraitResolver.donorProperties(facts,
					net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()), level), facts.abilities().stream()
					.filter(trait -> trait != BionicTrait.MILKABLE && !trait.carrier().isAllTissue())
					.collect(java.util.stream.Collectors.toUnmodifiableSet()),
				facts.attackEffects(), facts.deterrenceTargets(),
				com.nobodiiiii.createbiotech.entity.trait.BionicHunting.targets(Set.of(
					net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()))));
		}
		SurgicalAssembly assembly = bionic.getAssembly();
		if (assembly == null)
			return null;

		BionicTraitSet traits = BionicTraitResolver.resolve(assembly, level);
		List<BaseStat> stats = new ArrayList<>();
		addMaximumHealth(stats, bionic, assembly);
		addDps(stats, bionic, assembly);
		addMovementSpeed(stats, assembly);
		add(stats, bionic, Attributes.ARMOR, ValueFormat.DECIMAL, true);
		add(stats, bionic, Attributes.ARMOR_TOUGHNESS, ValueFormat.DECIMAL, false);
		add(stats, bionic, Attributes.KNOCKBACK_RESISTANCE, ValueFormat.PERCENTAGE, true);
		add(stats, bionic, Attributes.ATTACK_KNOCKBACK, ValueFormat.DECIMAL, false);
		addAnatomyCounts(stats, assembly);
		return new BoxDetails(List.copyOf(stats), traits, Set.of(), Set.of(), Set.of(), Set.of());
	}

	private static void addMaximumHealth(List<BaseStat> stats, SlimeBionicEntity bionic,
		SurgicalAssembly assembly) {
		// Client-side preview entities keep their registered attribute default. Use the measured,
		// non-overlapping body volume saved in the assembly so Alt shows the actual result.
		if (!assembly.hasBodyVolume()) {
			add(stats, bionic, Attributes.MAX_HEALTH, ValueFormat.DECIMAL, true);
			return;
		}
		double maximumHealth = SurgicalHealthCalibration.maximumHealth(assembly.bodyVolume());
		stats.add(new BaseStat(Attributes.MAX_HEALTH.value().getDescriptionId(),
			maximumHealth, ValueFormat.DECIMAL));
	}

	private static void add(List<BaseStat> stats, LivingEntity living, Holder<Attribute> attribute,
		ValueFormat format, boolean includeZero) {
		AttributeInstance instance = living.getAttribute(attribute);
		if (instance == null)
			return;
		double value = instance.getBaseValue();
		if (!Double.isFinite(value) || !includeZero && Math.abs(value) < 1.0e-8d)
			return;
		stats.add(new BaseStat(attribute.value().getDescriptionId(), value, format));
	}

	private static void addDps(List<BaseStat> stats, SlimeBionicEntity bionic,
		SurgicalAssembly assembly) {
		AttributeInstance attackDamage = bionic.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attackDamage == null || !Double.isFinite(attackDamage.getBaseValue()))
			return;
		double dps = assembly.attackGeometry() == null
			? attackDamage.getBaseValue() : SurgicalCombatCalibration.nominalDamagePerSecond(
				attackDamage.getBaseValue(), assembly.attackGeometry().arms().stream().filter(arm -> !bionic.isBionicWing(arm)).toList());
		stats.add(new BaseStat("create_biotech.tooltip.stat.dps", dps, ValueFormat.DECIMAL));
	}

	private static void addMovementSpeed(List<BaseStat> stats, SurgicalAssembly assembly) {
		double speed = SurgicalGait.movementSpeed(assembly.bodyBounds());
		stats.add(new BaseStat(Attributes.MOVEMENT_SPEED.value().getDescriptionId(),
			speed, ValueFormat.DECIMAL));
	}

	private static void addAnatomyCounts(List<BaseStat> stats, SurgicalAssembly assembly) {
		List<SurgicalAssembly.Limb> limbs = assembly.effectiveLimbs();
		stats.add(countStat("create_biotech.tooltip.stat.head_count", limbs, SurgicalLimbType.NECK));
		stats.add(countStat("create_biotech.tooltip.stat.arm_count", limbs, SurgicalLimbType.SHOULDER));
		stats.add(countStat("create_biotech.tooltip.stat.leg_count", limbs, SurgicalLimbType.HIP));
	}

	private static BaseStat countStat(String descriptionId, List<SurgicalAssembly.Limb> limbs,
		SurgicalLimbType type) {
		long count = limbs.stream().filter(limb -> limb.type() == type).count();
		return new BaseStat(descriptionId, count, ValueFormat.INTEGER);
	}

	private enum ValueFormat {
		DECIMAL,
		PERCENTAGE,
		INTEGER
	}

	private record BoxDetails(List<BaseStat> stats, BionicTraitSet traits, Set<BionicTrait> donorFacts,
		Set<ResourceLocation> donorAttacks, Set<ResourceLocation> deterrenceTargets, Set<ResourceLocation> huntingTargets) {}

	private record BaseStat(String descriptionId, double value, ValueFormat format) {
		private Component line() {
			String formatted = switch (format) {
			case DECIMAL -> ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(value);
			case PERCENTAGE -> ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(value * 100.0d) + "%";
			case INTEGER -> Long.toString(Math.round(value));
			};
			return Component.literal(" ")
				.append(Component.translatable(descriptionId).withStyle(ChatFormatting.GRAY))
				.append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal(formatted).withStyle(ChatFormatting.AQUA));
		}
	}
}
