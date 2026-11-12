package com.nobodiiiii.createbiotech.compat.jei;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.client.BionicTraitClientRules;
import com.nobodiiiii.createbiotech.client.BionicTraitDonorIndex;
import com.nobodiiiii.createbiotech.entity.trait.BionicAnatomyRole;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitOverrides;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitRule;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitScope;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.EntityType;

/**
 * Header tooltips explain inheritance types; optional conditions and replacements follow the effect.
 * Displayed thresholds, limb counts, lift limits and replacements follow server data packs.
 */
final class BionicTraitJeiText {
	private static final String PREFIX = "create_biotech.jei.trait.";

	private BionicTraitJeiText() {}

	static List<FormattedText> description(BionicTrait trait) {
		List<FormattedText> text = new ArrayList<>();
		text.add(tr(trait.id() + ".effect").withStyle(ChatFormatting.BLACK));
		Component condition = condition(trait);
		if (condition != null)
			text.add(tr("condition", condition).withStyle(ChatFormatting.GRAY));
		List<Component> overrides = BionicTraitOverrides.clientTargets(trait).stream().sorted()
			.map(BionicTraitJeiIngredient::name).toList();
		if (!overrides.isEmpty())
			text.add(tr("overrides", join(overrides, tr("overrides.separator"))).withStyle(ChatFormatting.GRAY));
		return List.copyOf(text);
	}

	static Component scopeTooltip(BionicTraitScope scope) {
		return field("scope", Component.translatable(scope.descriptionId()));
	}

	static List<Component> ruleTooltip(BionicTrait trait) {
		BionicTraitRule rule = BionicTraitClientRules.rule(trait);
		List<Component> text = new ArrayList<>();
		String type = switch (rule.type()) {
		case PRESENCE -> "presence";
		case COVERAGE_THRESHOLD -> "threshold";
		case COVERAGE_SCALED -> "scaled";
		case SPECIAL -> "special";
		};
		text.add(field("rule", tr("rule." + type)));
		switch (rule.type()) {
		case PRESENCE, COVERAGE_SCALED -> text.add(tr("rule." + type + ".description").withStyle(ChatFormatting.GRAY));
		case COVERAGE_THRESHOLD -> text.add(tr("rule.threshold.description", percentage(rule.minCoverage()))
			.withStyle(ChatFormatting.GRAY));
		case SPECIAL -> { }
		}
		return List.copyOf(text);
	}

	static Component donorTooltip(BionicTrait trait, EntityType<?> donor) {
		Component carrier = carrier(trait, BionicTraitDonorIndex.carriers(trait, donor));
		return (carrier == null ? tr("donors.whole_body") : tr("donors.carrier", carrier))
			.withStyle(ChatFormatting.GRAY);
	}

	private static Component field(String label, Component value) {
		return tr(label, value.copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY);
	}

	private static String percentage(double coverage) {
		return BigDecimal.valueOf(coverage).movePointRight(2).stripTrailingZeros().toPlainString();
	}

	/** Keep coarse anatomical labels while distinguishing required groups from alternatives. */
	@Nullable
	private static Component carrier(BionicTrait trait, Set<BionicAnatomyRole> roles) {
		if (roles.isEmpty())
			return null;
		boolean requiredTogether = switch (trait.condition()) {
		case WOOL_AND_BODY, UDDER_AND_BODY, SHELL_AND_BODY, COMPLETE_CHEST -> true;
		default -> false;
		};
		return join(roles.stream().sorted()
			.map(role -> switch (role) {
			case LEFT_WING, RIGHT_WING -> "wing";
			case LEFT_RIBCAGE, RIGHT_RIBCAGE -> "ribcage";
			case HEAD_CORE -> "head";
			case MOUTH -> roles.contains(BionicAnatomyRole.HEAD) || roles.contains(BionicAnatomyRole.HEAD_CORE)
				? "head" : "mouth";
			default -> role.name().toLowerCase(Locale.ROOT);
			})
			.distinct()
			.map(role -> tr("carrier." + role))
			.toList(), tr(requiredTogether ? "carrier.required_separator" : "carrier.separator"));
	}

	@Nullable
	private static Component condition(BionicTrait trait) {
		List<Component> conditions = new ArrayList<>();
		boolean legs = trait.scopes().contains(BionicTraitScope.LEG);
		boolean wings = switch (trait.condition()) {
		case WING_LIFT, LIMB_COUNT -> true;
		default -> false;
		};
		if (legs || wings) {
			int minimum = BionicTraitClientRules.minLimbs(trait);
			// One qualifying chain is implicit in the normal regional acquisition rule.
			if (minimum != 1)
				conditions.add(tr(legs ? "condition.legs" : "condition.arms", minimum));
		}
		if (trait.condition() == BionicTrait.Condition.WING_LIFT)
			conditions.add(tr("condition.wing_lift", BigDecimal.valueOf(
				BionicTraitClientRules.maxBodyVolumePerWingVolume(trait)).stripTrailingZeros().toPlainString()));
		return conditions.isEmpty() ? null : join(conditions, tr("condition.separator"));
	}

	private static Component join(List<? extends Component> components, Component separator) {
		MutableComponent result = Component.empty();
		for (Component component : components) {
			if (!result.getSiblings().isEmpty())
				result.append(separator);
			result.append(component);
		}
		return result;
	}

	private static MutableComponent tr(String key, Object... arguments) {
		return Component.translatable(PREFIX + key, arguments);
	}
}
