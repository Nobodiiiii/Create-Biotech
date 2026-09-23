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
import com.nobodiiiii.createbiotech.entity.trait.BionicBodyTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicBodyTraitRegistry;
import com.nobodiiiii.createbiotech.entity.trait.BionicBodyTraits;
import com.nobodiiiii.createbiotech.entity.trait.BionicHeadTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicHeadTraitRegistry;
import com.nobodiiiii.createbiotech.entity.trait.BionicHeadTraits;
import com.nobodiiiii.createbiotech.entity.trait.BionicOrganTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicOrganTraitRegistry;
import com.nobodiiiii.createbiotech.entity.trait.BionicOrganTraits;
import com.nobodiiiii.createbiotech.entity.trait.BionicAnatomyRegistry;
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
	private static long cachedTraitGeneration = -1L;

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
			&& details.headTraits().isEmpty() && details.organTraits().isEmpty()
			&& details.donorHeadFacts().isEmpty() && details.donorOrganFacts().isEmpty()
			&& details.inactiveHeadReasons().isEmpty()
			&& details.inactiveOrganReasons().isEmpty()
			&& disposition == null)
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
		appendPropertiesSection(tooltip, disposition, details.traits(),
			details.headTraits(), details.organTraits());
		appendInactiveHeadReasons(tooltip, details.inactiveHeadReasons());
		appendInactiveOrganReasons(tooltip, details.inactiveOrganReasons());
		appendDonorFacts(tooltip, details.donorHeadFacts(), details.donorOrganFacts());
	}

	public static void appendInactiveOrganReasons(List<Component> tooltip,
		Map<BionicOrganTrait, BionicOrganTraitRegistry.InactiveReason> reasons) {
		if (reasons.isEmpty())
			return;
		tooltip.add(CommonComponents.EMPTY);
		tooltip.add(Component.translatable("create_biotech.tooltip.inactive_organ_traits")
			.withStyle(ChatFormatting.GOLD));
		for (BionicOrganTrait trait : BionicOrganTrait.values()) {
			BionicOrganTraitRegistry.InactiveReason reason = reasons.get(trait);
			if (reason != null)
				appendProperty(tooltip, Component.translatable(trait.descriptionId())
					.append(Component.literal(" — "))
					.append(Component.translatable("create_biotech.trait.inactive."
						+ reason.name().toLowerCase(java.util.Locale.ROOT))));
		}
	}

	public static void appendInactiveHeadReasons(List<Component> tooltip,
		Map<BionicHeadTrait, BionicHeadTraitRegistry.InactiveReason> reasons) {
		if (reasons.isEmpty())
			return;
		tooltip.add(CommonComponents.EMPTY);
		tooltip.add(Component.translatable("create_biotech.tooltip.inactive_head_traits")
			.withStyle(ChatFormatting.GOLD));
		for (BionicHeadTrait trait : BionicHeadTrait.values()) {
			BionicHeadTraitRegistry.InactiveReason reason = reasons.get(trait);
			if (reason != null)
				appendProperty(tooltip, Component.translatable(trait.descriptionId())
					.append(Component.literal(" — "))
					.append(Component.translatable("create_biotech.trait.inactive."
						+ reason.name().toLowerCase(java.util.Locale.ROOT))));
		}
	}

	private static void appendDonorFacts(List<Component> tooltip,
		Set<BionicHeadTrait> heads, Set<BionicOrganTrait> organs) {
		if (heads.isEmpty() && organs.isEmpty())
			return;
		tooltip.add(CommonComponents.EMPTY);
		tooltip.add(Component.translatable("create_biotech.tooltip.donor_special_traits")
			.withStyle(ChatFormatting.GOLD));
		for (BionicHeadTrait trait : BionicHeadTrait.values())
			if (heads.contains(trait))
				appendProperty(tooltip, Component.translatable(trait.descriptionId()));
		for (BionicOrganTrait trait : BionicOrganTrait.values())
			if (organs.contains(trait))
				appendProperty(tooltip, Component.translatable(trait.descriptionId()));
	}

	/** Adds the shared Create-style property heading and its disposition value. */
	public static void appendDispositionSection(List<Component> tooltip, BionicDisposition disposition) {
		appendPropertiesSection(tooltip, disposition, BionicBodyTraits.EMPTY);
	}

	/** Adds one shared property section for head disposition and whole-tissue donor traits. */
	public static void appendPropertiesSection(List<Component> tooltip,
		@Nullable BionicDisposition disposition, BionicBodyTraits traits) {
		appendPropertiesSection(tooltip, disposition, traits, BionicHeadTraits.EMPTY);
	}

	public static void appendPropertiesSection(List<Component> tooltip,
		@Nullable BionicDisposition disposition, BionicBodyTraits traits,
		BionicHeadTraits headTraits) {
		appendPropertiesSection(tooltip, disposition, traits, headTraits, BionicOrganTraits.EMPTY);
	}

	public static void appendPropertiesSection(List<Component> tooltip,
		@Nullable BionicDisposition disposition, BionicBodyTraits traits,
		BionicHeadTraits headTraits, BionicOrganTraits organTraits) {
		if (disposition == null && (traits == null || traits.isEmpty())
			&& (headTraits == null || headTraits.isEmpty())
			&& (organTraits == null || organTraits.isEmpty()))
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
		if (headTraits != null)
			for (BionicHeadTrait trait : BionicHeadTrait.values())
				if (headTraits.has(trait))
					appendProperty(tooltip, Component.translatable(trait.descriptionId()));
		if (organTraits != null)
			for (BionicOrganTrait trait : BionicOrganTrait.values())
				if (organTraits.has(trait))
					appendProperty(tooltip, Component.translatable(trait.descriptionId()));
		if (traits == null)
			return;
		for (BionicBodyTrait trait : BionicBodyTrait.values()) {
			double coverage = traits.coverage(trait);
			if (coverage <= 0.0d)
				continue;
			Component name = Component.translatable(trait.descriptionId());
			if (coverage < 1.0d - 1.0e-8d)
				name = Component.translatable("create_biotech.trait.partial", name,
					Long.toString(Math.round(coverage * 100.0d)));
			appendProperty(tooltip, name);
		}
		appendEffectImmunities(tooltip, traits);
		if (traits.naturalArmor() > 1.0e-8d)
			appendProperty(tooltip, Component.translatable("create_biotech.trait.natural_armor",
				ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(traits.naturalArmor())));
		if (traits.knockbackResistance() > 1.0e-8d)
			appendProperty(tooltip, Component.translatable("create_biotech.trait.knockback_resistance",
				ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(
					traits.knockbackResistance() * 100.0d)));
		if (traits.passiveRegeneration() > 1.0e-8d)
			appendProperty(tooltip, Component.translatable("create_biotech.trait.passive_regeneration",
				ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(
					traits.passiveRegeneration())));
	}

	private static void appendEffectImmunities(List<Component> tooltip, BionicBodyTraits traits) {
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
		long generation = (BionicBodyTraitRegistry.generation() * 31L
			+ BionicHeadTraitRegistry.generation()) * 31L
			+ BionicOrganTraitRegistry.generation() * 31L
			+ BionicAnatomyRegistry.generation();
		if (cachedTraitGeneration == generation && ItemStack.isSameItemSameComponents(cachedStack, stack))
			return cachedDetails;

		cachedStack = stack.copyWithCount(1);
		cachedTraitGeneration = generation;
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
			return new BoxDetails(List.of(), null, BionicDispositionRegistry.get(living),
				profile == null ? BionicBodyTraitRegistry.detect(living)
					: BionicBodyTraitRegistry.get(profile, level), BionicHeadTraits.EMPTY,
				BionicOrganTraits.EMPTY,
				profile == null ? Set.of() : BionicHeadTraitRegistry.donorFacts(profile, level),
				profile == null ? Set.of() : BionicOrganTraitRegistry.donorFacts(profile),
				Map.of(), Map.of());
		}
		SurgicalAssembly assembly = bionic.getAssembly();
		if (assembly == null)
			return null;

		List<BaseStat> stats = new ArrayList<>();
		addMaximumHealth(stats, bionic, assembly);
		addDps(stats, bionic, assembly);
		addMovementSpeed(stats, assembly);
		add(stats, bionic, Attributes.ARMOR, ValueFormat.DECIMAL, true);
		add(stats, bionic, Attributes.ARMOR_TOUGHNESS, ValueFormat.DECIMAL, false);
		add(stats, bionic, Attributes.KNOCKBACK_RESISTANCE, ValueFormat.PERCENTAGE, true);
		add(stats, bionic, Attributes.ATTACK_KNOCKBACK, ValueFormat.DECIMAL, false);
		addAnatomyCounts(stats, assembly);
		return new BoxDetails(List.copyOf(stats), assembly, null,
			BionicBodyTraitRegistry.resolve(assembly, level),
			BionicHeadTraitRegistry.resolve(assembly, level),
			BionicOrganTraitRegistry.resolve(assembly), Set.of(), Set.of(),
			BionicHeadTraitRegistry.inactiveReasons(assembly, level),
			BionicOrganTraitRegistry.inactiveReasons(assembly));
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
		@Nullable BionicDisposition disposition, BionicBodyTraits traits,
		BionicHeadTraits headTraits, BionicOrganTraits organTraits,
		Set<BionicHeadTrait> donorHeadFacts, Set<BionicOrganTrait> donorOrganFacts,
		Map<BionicHeadTrait, BionicHeadTraitRegistry.InactiveReason> inactiveHeadReasons,
		Map<BionicOrganTrait, BionicOrganTraitRegistry.InactiveReason> inactiveOrganReasons) {}

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
