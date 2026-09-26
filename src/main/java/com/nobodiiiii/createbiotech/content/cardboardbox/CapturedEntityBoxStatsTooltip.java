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
import com.nobodiiiii.createbiotech.entity.ai.BionicDisposition;
import com.nobodiiiii.createbiotech.entity.ai.BionicDispositionRegistry;
import com.nobodiiiii.createbiotech.entity.ai.BionicMind;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitDonors;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResolver;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitScope;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitSet;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitType;
import com.simibubi.create.foundation.item.TooltipModifier;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
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
		BionicDisposition disposition = details.disposition();
		if (details.assembly() != null) {
			BionicMind mind = BionicMind.resolve(details.assembly(), level);
			disposition = mind.hasRecognizedHead() ? mind.disposition() : null;
		}
		if (details.stats().isEmpty() && details.traits().isEmpty()
			&& details.donorFacts().isEmpty() && disposition == null)
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
		appendPropertiesSection(tooltip, disposition, details.traits());
		appendDonorFacts(tooltip, details.donorFacts());
	}

	private static void appendDonorFacts(List<Component> tooltip, Set<BionicTrait> facts) {
		if (facts.isEmpty())
			return;
		tooltip.add(CommonComponents.EMPTY);
		tooltip.add(Component.translatable("create_biotech.tooltip.donor_carrier_traits")
			.withStyle(ChatFormatting.GOLD));
		for (BionicTrait trait : BionicTrait.values())
			if (facts.contains(trait))
				appendProperty(tooltip, Component.translatable(trait.descriptionId()));
	}

	/** Adds the shared Create-style property heading and its disposition value. */
	public static void appendDispositionSection(List<Component> tooltip, BionicDisposition disposition) {
		appendPropertiesSection(tooltip, disposition, BionicTraitSet.EMPTY);
	}

	/** Both active properties and their inactive reasons come from the entity's evaluation model. */
	public static void appendPropertiesSection(List<Component> tooltip,
		@Nullable BionicDisposition disposition, BionicTraitSet traits) {
		if (disposition == null && traits.isEmpty())
			return;
		tooltip.add(CommonComponents.EMPTY);
		tooltip.add(Component.translatable("create_biotech.tooltip.properties")
			.withStyle(ChatFormatting.GOLD));
		if (disposition != null) {
			ChatFormatting color = switch (disposition) {
			case FRIENDLY -> ChatFormatting.GREEN;
			case NEUTRAL -> ChatFormatting.YELLOW;
			case HOSTILE -> ChatFormatting.RED;
			};
			tooltip.add(Component.literal(" ")
				.append(Component.translatable("create_biotech.disposition."
					+ disposition.name().toLowerCase(java.util.Locale.ROOT)).withStyle(color)));
		}
		Map<BionicTraitScope, List<Component>> groups = new java.util.EnumMap<>(BionicTraitScope.class);
		for (BionicTraitScope scope : BionicTraitScope.values())
			groups.put(scope, new ArrayList<>());
		for (BionicTrait trait : BionicTrait.values()) {
			BionicTraitResult result = traits.result(trait);
			if (!result.present())
				continue;
			List<BionicTraitScope> scopes = trait.scopes().stream().sorted().toList();
			List<Component> lines = groups.get(scopes.getFirst());
			Component name = Component.translatable(trait.descriptionId());
			if (trait.valueKind() == BionicTrait.ValueKind.NUMBER)
				name = Component.translatable(trait.descriptionId(),
					ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(result.value()
						* (trait == BionicTrait.KNOCKBACK_RESISTANCE ? 100 : 1)));
			if (scopes.size() > 1) {
				MutableComponent names = Component.empty();
				for (int index = 0; index < scopes.size(); index++) {
					if (index > 0)
						names.append(Component.translatable("create_biotech.trait.list_separator"));
					names.append(Component.translatable(scopes.get(index).descriptionId()));
				}
				name = Component.translatable("create_biotech.trait.allowed_scopes", name, names);
			}
			if (!result.active()) {
				lines.add(Component.literal(" ").append(name).append(Component.literal(" — "))
					.append(Component.translatable(result.inactiveReason().descriptionId()))
					.withStyle(ChatFormatting.DARK_GRAY));
			} else if (trait.valueKind() == BionicTrait.ValueKind.EFFECT_SET) {
				appendEffectImmunities(lines, traits);
			} else {
				if (trait.valueKind() == BionicTrait.ValueKind.ABILITY
					&& trait.rule().type() == BionicTraitType.COVERAGE_SCALED && !traits.hasFullEffect(trait))
					name = Component.translatable("create_biotech.trait.partial", name,
						Long.toString(Math.round(result.strength() * 100.0d)));
				appendProperty(lines, name);
			}
		}
		groups.forEach((scope, lines) -> appendTraitGroup(tooltip, scope.descriptionId(), lines));
	}

	private static void appendTraitGroup(List<Component> tooltip, String descriptionId,
		List<Component> lines) {
		if (lines.isEmpty())
			return;
		tooltip.add(Component.literal(" ").append(Component.translatable(descriptionId)
			.withStyle(ChatFormatting.DARK_AQUA)));
		for (Component line : lines)
			tooltip.add(Component.literal(" ").append(line));
	}

	private static void appendEffectImmunities(List<Component> tooltip, BionicTraitSet traits) {
		List<ResourceLocation> effects = traits.immuneEffects().stream().sorted().toList();
		if (effects.isEmpty())
			return;
		MutableComponent names = Component.empty();
		int shown = Math.min(3, effects.size());
		for (int index = 0; index < shown; index++) {
			if (index > 0)
				names.append(Component.translatable("create_biotech.trait.list_separator"));
			var effect = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
				.getOptional(effects.get(index)).orElse(null);
			names.append(effect == null ? Component.literal(effects.get(index).toString())
				: effect.getDisplayName());
		}
		if (effects.size() > shown) {
			names.append(Component.translatable("create_biotech.trait.list_separator"));
			names.append(Component.literal("…"));
		}
		appendProperty(tooltip, Component.translatable("create_biotech.trait.effect_immunity", names));
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
			return new BoxDetails(List.of(), null, BionicDispositionRegistry.get(living),
				BionicTraitResolver.donorProperties(facts), facts.abilities().stream()
					.filter(trait -> !trait.carrier().isAllTissue()).collect(java.util.stream.Collectors.toUnmodifiableSet()));
		}
		SurgicalAssembly assembly = bionic.getAssembly();
		if (assembly == null)
			return null;

		BionicTraitSet traits = bionic.getBionicTraits();
		List<BaseStat> stats = new ArrayList<>();
		addMaximumHealth(stats, bionic, assembly);
		addDps(stats, bionic, assembly);
		addMovementSpeed(stats, assembly);
		add(stats, bionic, Attributes.ARMOR, ValueFormat.DECIMAL, true);
		add(stats, bionic, Attributes.ARMOR_TOUGHNESS, ValueFormat.DECIMAL, false);
		add(stats, bionic, Attributes.KNOCKBACK_RESISTANCE, ValueFormat.PERCENTAGE, true);
		add(stats, bionic, Attributes.ATTACK_KNOCKBACK, ValueFormat.DECIMAL, false);
		addAnatomyCounts(stats, assembly);
		return new BoxDetails(List.copyOf(stats), assembly, null, traits, Set.of());
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
		double dps = SurgicalCombatCalibration.nominalDamagePerSecond(
			attackDamage.getBaseValue(), assembly.attackGeometry());
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

	private record BoxDetails(List<BaseStat> stats, @Nullable SurgicalAssembly assembly,
		@Nullable BionicDisposition disposition, BionicTraitSet traits, Set<BionicTrait> donorFacts) {}

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
