package com.nobodiiiii.createbiotech.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.nobodiiiii.createbiotech.entity.trait.BionicAnatomyRole;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitScope;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitType;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;

/** Default reference text; assembly tooltips remain the source of live, data-pack-dependent values. */
final class BionicTraitJeiText {
	private static final String PREFIX = "create_biotech.jei.trait.";

	private BionicTraitJeiText() {}

	static List<FormattedText> description(BionicTrait trait) {
		List<FormattedText> text = new ArrayList<>();
		text.add(BionicTraitJeiIngredient.name(trait).copy().withStyle(ChatFormatting.BOLD));
		text.add(Component.empty());
		text.add(tr(trait.id() + ".description"));
		text.add(Component.empty());
		text.add(tr("scope", join(trait.scopes().stream().sorted()
			.map(scope -> Component.translatable(scope.descriptionId())).toList())));
		text.add(tr("carrier", carrier(trait)));
		text.add(tr("rule", rule(trait)));
		if (trait.rule().type() != BionicTraitType.PRESENCE) {
			boolean perLimb = trait.scopes().stream().anyMatch(scope ->
				scope == BionicTraitScope.HEAD || scope == BionicTraitScope.ARM || scope == BionicTraitScope.LEG);
			text.add(tr(perLimb ? "coverage.limb" : "coverage.region"));
		}
		switch (trait.condition()) {
		case NONE -> {}
		case BREATHING_REQUIRED -> text.add(tr("condition.breathing"));
		case WING_LIFT -> text.add(tr("condition.wings"));
		case LIMB_COUNT -> text.add(tr("condition.legs",
			trait == BionicTrait.AGILE_LANDING || trait == BionicTrait.WALL_CLIMB ? 2 : 1));
		}
		text.add(Component.empty());
		text.add(tr("defaults"));
		return List.copyOf(text);
	}

	private static Component carrier(BionicTrait trait) {
		BionicTrait.Carrier carrier = trait.carrier();
		if (carrier.isAllTissue())
			return tr("carrier.all");
		Component roles = roles(carrier.roles());
		return carrier.fallback().isEmpty() ? roles : tr("carrier.fallback", roles, roles(carrier.fallback()));
	}

	private static Component roles(List<BionicAnatomyRole> roles) {
		return join(roles.stream().map(role -> tr("carrier." + role.name().toLowerCase(Locale.ROOT))).toList());
	}

	private static Component rule(BionicTrait trait) {
		String percentage = String.format(Locale.ROOT, "%.0f", trait.rule().minCoverage() * 100.0d);
		if (trait.valueKind() == BionicTrait.ValueKind.NUMBER)
			return tr("rule.numeric", percentage);
		if (trait.valueKind() == BionicTrait.ValueKind.EFFECT_SET)
			return tr("rule.effects", percentage);
		return switch (trait.rule().type()) {
		case PRESENCE -> tr("rule.presence");
		case COVERAGE_THRESHOLD -> tr("rule.threshold", percentage);
		case COVERAGE_SCALED -> tr("rule.scaled");
		};
	}

	private static Component join(List<? extends Component> components) {
		MutableComponent result = Component.empty();
		for (Component component : components) {
			if (!result.getSiblings().isEmpty())
				result.append(Component.translatable("create_biotech.trait.list_separator"));
			result.append(component);
		}
		return result;
	}

	private static MutableComponent tr(String key, Object... arguments) {
		return Component.translatable(PREFIX + key, arguments);
	}
}
