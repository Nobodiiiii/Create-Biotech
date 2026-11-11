package com.nobodiiiii.createbiotech.compat.jei;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.client.BionicTraitClientRules;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitRule;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitScope;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;

/**
 * Header tooltips explain inheritance types; acquisition conditions follow the effect description.
 * Coverage thresholds follow server data packs; effect descriptions use reference values.
 */
final class BionicTraitJeiText {
	private static final String PREFIX = "create_biotech.jei.trait.";

	private BionicTraitJeiText() {}

	static List<FormattedText> description(BionicTrait trait) {
		List<FormattedText> text = new ArrayList<>();
		text.add(tr(trait.id() + ".effect").withStyle(ChatFormatting.BLACK));
		Component condition = condition(trait);
		if (condition != null)
			text.add(tr("condition", condition).withStyle(ChatFormatting.BLACK));
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

	static Component donorTooltip(BionicTrait trait) {
		Component carrier = carrier(trait.carrier());
		if (carrier == null)
			carrier = Component.translatable(BionicTraitScope.WHOLE_BODY.descriptionId());
		return tr("donors.carrier", carrier).withStyle(ChatFormatting.GRAY);
	}

	private static Component field(String label, Component value) {
		return tr(label, value.copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY);
	}

	private static String percentage(double coverage) {
		return BigDecimal.valueOf(coverage).movePointRight(2).stripTrailingZeros().toPlainString();
	}

	/** Null for whole-tissue carriers; paired wings read as one carrier, fallbacks as alternatives. */
	@Nullable
	private static Component carrier(BionicTrait.Carrier carrier) {
		if (carrier.isAllTissue())
			return null;
		return join(Stream.concat(carrier.roles().stream(), carrier.fallback().stream())
			.map(role -> switch (role) {
			case LEFT_WING, RIGHT_WING -> "wing";
			default -> role.name().toLowerCase(Locale.ROOT);
			})
			.distinct()
			.map(role -> tr("carrier." + role))
			.toList(), tr("carrier.separator"));
	}

	@Nullable
	private static Component condition(BionicTrait trait) {
		if (trait.isHeadClassification())
			return tr("condition.head_retention", percentage(BionicTraitClientRules.rule(trait).minCoverage()));
		if (trait.valueKind() == BionicTrait.ValueKind.NUMBER)
			return tr("condition.numeric");
		if (trait == BionicTrait.SHELL_DEFENSE)
			return tr("condition.shell_defense");
		return switch (trait.condition()) {
		case NONE -> null;
		case BODY_VOLUME -> tr("condition.body_volume");
		case BREATHING_REQUIRED -> tr("condition.breathing");
		case WOOL_AND_BODY -> tr("condition.wool_and_body");
		case UDDER_AND_BODY, SHELL_AND_BODY, COMPLETE_CHEST, HEAD_AND_TENDRIL, HEAD_AND_MOUTH ->
			tr("condition." + trait.condition().name().toLowerCase(Locale.ROOT));
		case WING_LIFT -> tr("condition.wings");
		case LIMB_COUNT -> tr(trait == BionicTrait.WING_SLOW_FALL ? "condition.slow_fall_wings" : "condition.legs",
			trait == BionicTrait.AGILE_LANDING || trait == BionicTrait.WALL_CLIMB
				|| trait == BionicTrait.STRONG_JUMP || trait == BionicTrait.WING_SLOW_FALL ? 2 : 1);
		};
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
