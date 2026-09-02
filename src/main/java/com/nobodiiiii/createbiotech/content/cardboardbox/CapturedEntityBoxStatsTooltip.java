package com.nobodiiiii.createbiotech.content.cardboardbox;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCombatCalibration;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGait;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;
import com.simibubi.create.foundation.item.TooltipModifier;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
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
	private static List<BaseStat> cachedStats;

	@Override
	public void modify(ItemTooltipEvent context) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null)
			append(context.getItemStack(), minecraft.level, context.getToolTip());
	}

	/** Appends the same expandable section to inventory and surgical-table HUD tooltips. */
	public static void append(ItemStack stack, Level level, List<Component> tooltip) {
		List<BaseStat> stats = stats(stack, level);
		if (stats == null || stats.isEmpty())
			return;

		boolean expanded = Screen.hasAltDown();
		tooltip.add(Component.translatable("create_biotech.tooltip.hold_for_base_stats",
			Component.literal("Alt").withStyle(expanded ? ChatFormatting.WHITE : ChatFormatting.GRAY))
			.withStyle(ChatFormatting.DARK_GRAY));
		if (!expanded)
			return;

		tooltip.add(CommonComponents.EMPTY);
		tooltip.add(Component.translatable("create_biotech.tooltip.base_stats")
			.withStyle(ChatFormatting.GOLD));
		for (BaseStat stat : stats)
			tooltip.add(stat.line());
	}

	@Nullable
	private static List<BaseStat> stats(ItemStack stack, Level level) {
		if (ItemStack.isSameItemSameComponents(cachedStack, stack))
			return cachedStats;

		cachedStack = stack.copyWithCount(1);
		cachedStats = captureStats(stack, level);
		return cachedStats;
	}

	@Nullable
	private static List<BaseStat> captureStats(ItemStack stack, Level level) {
		// Ordinary slime mimics deliberately have no numerical expansion. Checking the saved type
		// before constructing the entity keeps their hover path free of attribute/model work.
		if (!CapturedEntityBoxHelper.containsEntityType(stack, CBEntityTypes.SLIME_BIONIC.get()))
			return null;
		Entity entity = CapturedEntityBoxHelper.createCapturedEntity(stack, level);
		if (!(entity instanceof SlimeBionicEntity bionic))
			return null;
		SurgicalAssembly assembly = bionic.getAssembly();
		if (assembly == null)
			return null;

		List<BaseStat> stats = new ArrayList<>();
		add(stats, bionic, Attributes.MAX_HEALTH, ValueFormat.DECIMAL, true);
		addDps(stats, bionic, assembly);
		addMovementSpeed(stats, assembly);
		add(stats, bionic, Attributes.ARMOR, ValueFormat.DECIMAL, true);
		add(stats, bionic, Attributes.ARMOR_TOUGHNESS, ValueFormat.DECIMAL, false);
		add(stats, bionic, Attributes.KNOCKBACK_RESISTANCE, ValueFormat.PERCENTAGE, true);
		add(stats, bionic, Attributes.ATTACK_KNOCKBACK, ValueFormat.DECIMAL, false);
		addAnatomyCounts(stats, assembly);
		return List.copyOf(stats);
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
