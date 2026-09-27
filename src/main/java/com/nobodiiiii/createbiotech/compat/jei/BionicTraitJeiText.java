package com.nobodiiiii.createbiotech.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitType;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;

/**
 * One fixed template per trait: scope, carrier rule, optional condition, then a short effect line.
 * Default reference values only; assembly tooltips remain the source of live, data-pack-dependent values.
 */
final class BionicTraitJeiText {
	private static final String PREFIX = "create_biotech.jei.trait.";

	private BionicTraitJeiText() {}

	static List<FormattedText> description(BionicTrait trait) {
		List<FormattedText> text = new ArrayList<>();
		text.add(field("scope", join(trait.scopes().stream().sorted()
			.map(scope -> Component.translatable(scope.descriptionId())).toList(),
			Component.translatable("create_biotech.trait.list_separator"))));
		text.add(field("rule", rule(trait)));
		Component condition = condition(trait);
		if (condition != null)
			text.add(field("condition", condition));
		text.add(field("effect", tr(trait.id() + ".effect")));
		return List.copyOf(text);
	}

	private static Component field(String label, Component value) {
		return tr(label, value.copy().withStyle(ChatFormatting.BLACK)).withStyle(ChatFormatting.DARK_GRAY);
	}

	private static Component rule(BionicTrait trait) {
		Component type = tr("rule." + switch (trait.rule().type()) {
		case PRESENCE -> "presence";
		case COVERAGE_THRESHOLD -> "threshold";
		case COVERAGE_SCALED -> "scaled";
		});
		Component carrier = carrier(trait.carrier());
		Component detail = carrier;
		if (trait.rule().type() == BionicTraitType.COVERAGE_THRESHOLD) {
			String percentage = String.format(Locale.ROOT, "%.0f", trait.rule().minCoverage() * 100.0d);
			detail = carrier == null ? tr("rule.coverage", percentage) : tr("rule.carrier_coverage", carrier, percentage);
		}
		return detail == null ? type : tr("rule.detail", type, detail);
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
		return switch (trait.condition()) {
		case NONE -> null;
		case BREATHING_REQUIRED -> tr("condition.breathing");
		case WING_LIFT -> tr("condition.wings");
		case LIMB_COUNT -> tr("condition.legs",
			trait == BionicTrait.AGILE_LANDING || trait == BionicTrait.WALL_CLIMB ? 2 : 1);
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
