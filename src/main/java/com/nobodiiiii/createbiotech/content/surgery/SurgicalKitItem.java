package com.nobodiiiii.createbiotech.content.surgery;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.foundation.item.CBItemData;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.glue.SuperGlueItem;
import com.simibubi.create.content.equipment.symmetryWand.SymmetryWandItem;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.common.Tags;

/**
 * A durable, surgical-table-only proxy for every tool or consumable used while editing a subject.
 * The selected logical tool is stored on the stack; no actual tool stack is created or consumed.
 */
public class SurgicalKitItem extends Item {
	public static final int MAX_DURABILITY = 200;
	public static final String OPEN_KEY_TRANSLATION = "key.create_biotech.surgical_kit";
	private static final String SELECTED_TOOL_TAG = "SurgicalKitTool";

	public SurgicalKitItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
		TooltipFlag flag) {
		Tool selected = selectedTool(stack);
		if (selected != null)
			tooltip.add(Component.translatable("item.create_biotech.surgical_kit.selected",
				selected.displayStack().getHoverName()).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.create_biotech.surgical_kit.alt",
			Component.keybind(OPEN_KEY_TRANSLATION))
			.withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("item.create_biotech.surgical_kit.table_only")
			.withStyle(ChatFormatting.DARK_GRAY));
	}

	@Nullable
	public static Tool selectedTool(ItemStack stack) {
		if (!(stack.getItem() instanceof SurgicalKitItem))
			return null;
		CompoundTag tag = CBItemData.getReadOnly(stack);
		if (tag == null || !tag.contains(SELECTED_TOOL_TAG))
			return null;
		return Tool.byId(tag.getString(SELECTED_TOOL_TAG));
	}

	public static void setSelectedTool(ItemStack stack, Tool tool) {
		if (!(stack.getItem() instanceof SurgicalKitItem) || tool == null)
			return;
		CBItemData.edit(stack, tag -> tag.putString(SELECTED_TOOL_TAG, tool.id));
	}

	public static float modelValue(ItemStack stack) {
		Tool tool = selectedTool(stack);
		return tool == null ? 0.0f : tool.ordinal() + 1.0f;
	}

	public static boolean isKit(ItemStack stack) {
		return stack.getItem() instanceof SurgicalKitItem;
	}

	public static boolean matches(ItemStack stack, Item item) {
		if (stack.is(item))
			return true;
		Tool selected = selectedTool(stack);
		return selected != null && selected.displayStack().is(item);
	}

	public static boolean isShears(ItemStack stack) {
		return matches(stack, Items.SHEARS);
	}

	public static boolean isHoneyBottle(ItemStack stack) {
		return matches(stack, Items.HONEY_BOTTLE);
	}

	public static boolean isSlimeBall(ItemStack stack) {
		return matches(stack, Items.SLIME_BALL);
	}

	public static boolean isGlue(ItemStack stack) {
		return stack.getItem() instanceof SuperGlueItem
			|| selectedTool(stack) == Tool.SUPER_GLUE
			|| selectedTool(stack) == Tool.SMART_SUPER_GLUE;
	}

	public static boolean isSmartGlue(ItemStack stack) {
		return stack.is(CBItems.SMART_SUPER_GLUE.get()) || selectedTool(stack) == Tool.SMART_SUPER_GLUE;
	}

	public static boolean isStandardGlue(ItemStack stack) {
		return isGlue(stack) && !isSmartGlue(stack);
	}

	public static boolean isSymmetryWand(ItemStack stack) {
		return stack.getItem() instanceof SymmetryWandItem || selectedTool(stack) == Tool.SYMMETRY_WAND;
	}

	public static boolean isWrench(ItemStack stack) {
		return AllItems.WRENCH.isIn(stack) || stack.is(Tags.Items.TOOLS_WRENCH)
			|| selectedTool(stack) == Tool.WRENCH;
	}

	@Nullable
	public static SurgicalLimbType limbType(ItemStack stack) {
		if (stack.getItem() instanceof SurgicalJointItem joint)
			return joint.limbType();
		Tool selected = selectedTool(stack);
		return selected == null ? null : selected.limbType;
	}

	public static boolean hasDurability(ItemStack stack, int amount) {
		return !isKit(stack) || amount >= 0 && stack.getMaxDamage() - stack.getDamageValue() >= amount;
	}

	public enum Tool {
		SHEARS("shears", () -> new ItemStack(Items.SHEARS), null),
		SUPER_GLUE("super_glue", () -> AllItems.SUPER_GLUE.asStack(), null),
		SMART_SUPER_GLUE("smart_super_glue", () -> new ItemStack(CBItems.SMART_SUPER_GLUE.get()), null),
		HONEY_BOTTLE("honey_bottle", () -> new ItemStack(Items.HONEY_BOTTLE), null),
		SLIME_BALL("slime_ball", () -> new ItemStack(Items.SLIME_BALL), null),
		SYMMETRY_WAND("symmetry_wand", () -> AllItems.WAND_OF_SYMMETRY.asStack(), null),
		WRENCH("wrench", () -> AllItems.WRENCH.asStack(), null),
		NECK_JOINT("neck_joint", () -> new ItemStack(CBItems.NECK_JOINT.get()), SurgicalLimbType.NECK),
		SHOULDER_JOINT("shoulder_joint", () -> new ItemStack(CBItems.SHOULDER_JOINT.get()), SurgicalLimbType.SHOULDER),
		ELBOW_JOINT("elbow_joint", () -> new ItemStack(CBItems.ELBOW_JOINT.get()), SurgicalLimbType.ELBOW),
		HIP_JOINT("hip_joint", () -> new ItemStack(CBItems.HIP_JOINT.get()), SurgicalLimbType.HIP),
		KNEE_JOINT("knee_joint", () -> new ItemStack(CBItems.KNEE_JOINT.get()), SurgicalLimbType.KNEE);

		private final String id;
		private final Supplier<ItemStack> displayStack;
		@Nullable
		private final SurgicalLimbType limbType;

		Tool(String id, Supplier<ItemStack> displayStack, @Nullable SurgicalLimbType limbType) {
			this.id = id;
			this.displayStack = displayStack;
			this.limbType = limbType;
		}

		public String id() {
			return id;
		}

		public ItemStack displayStack() {
			return displayStack.get();
		}

		@Nullable
		private static Tool byId(String id) {
			if (id == null || id.isBlank())
				return null;
			String normalized = id.toLowerCase(Locale.ROOT);
			for (Tool tool : values())
				if (tool.id.equals(normalized))
					return tool;
			return null;
		}
	}
}
