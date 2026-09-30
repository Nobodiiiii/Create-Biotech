package com.nobodiiiii.createbiotech.content.surgery;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.foundation.item.CBItemData;
import com.nobodiiiii.createbiotech.registry.CBItems;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A chain-and-cartilage joint that pins one surgical cube group to another as a moving limb. */
public class SurgicalJointItem extends Item {
	public static final List<SurgicalLimbType> TYPES = List.of(SurgicalLimbType.NECK, SurgicalLimbType.SHOULDER,
		SurgicalLimbType.ELBOW, SurgicalLimbType.HIP, SurgicalLimbType.KNEE);
	private static final String SELECTED_TYPE_TAG = "SurgicalJointType";
	private final SurgicalLimbType limbType;
	private final boolean selectable;

	public SurgicalJointItem(Properties properties) {
		this(SurgicalLimbType.NECK, properties, true);
	}

	/** Legacy fixed-type items remain loadable and supply the wheel's full-size preview models. */
	public SurgicalJointItem(SurgicalLimbType limbType, Properties properties) {
		this(limbType, properties, false);
	}

	private SurgicalJointItem(SurgicalLimbType limbType, Properties properties, boolean selectable) {
		super(properties);
		this.limbType = limbType;
		this.selectable = selectable;
	}

	public SurgicalLimbType limbType() {
		return limbType;
	}

	public boolean selectable() {
		return selectable;
	}

	public static boolean canSelect(ItemStack stack) {
		return stack.getItem() instanceof SurgicalJointItem joint && joint.selectable;
	}

	@Nullable
	public static SurgicalLimbType selectedType(ItemStack stack) {
		if (!(stack.getItem() instanceof SurgicalJointItem joint))
			return null;
		if (!joint.selectable)
			return joint.limbType;
		// Fresh stacks (including creative and recipe output) have no active joint type.
		CompoundTag tag = CBItemData.getReadOnly(stack);
		return tag == null ? null : SurgicalLimbType.byId(tag.getString(SELECTED_TYPE_TAG));
	}

	public static void select(ItemStack stack, SurgicalLimbType type) {
		if (!canSelect(stack) || type == null)
			return;
		CBItemData.edit(stack, tag -> tag.putString(SELECTED_TYPE_TAG, type.id()));
	}

	public static ItemStack createStack(SurgicalLimbType type) {
		ItemStack stack = new ItemStack(CBItems.SURGICAL_JOINT.get());
		select(stack, type);
		return stack;
	}

	public static ItemStack previewStack(SurgicalLimbType type) {
		return new ItemStack(switch (type) {
		case NECK -> CBItems.NECK_JOINT.get();
		case SHOULDER -> CBItems.SHOULDER_JOINT.get();
		case ELBOW -> CBItems.ELBOW_JOINT.get();
		case HIP -> CBItems.HIP_JOINT.get();
		case KNEE -> CBItems.KNEE_JOINT.get();
		});
	}

	public static Component typeName(SurgicalLimbType type) {
		return Component.translatable("item.create_biotech." + type.id() + "_joint");
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		SurgicalLimbType selected = selectedType(stack);
		if (!selectable || selected == null)
			return;
		tooltip.add(Component.translatable("item.create_biotech.surgical_kit.selected", typeName(selected))
			.withStyle(ChatFormatting.GRAY));
	}
}
