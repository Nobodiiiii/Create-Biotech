package com.nobodiiiii.createbiotech.content.surgery;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalKitItemRenderer;
import com.nobodiiiii.createbiotech.foundation.item.CBItemData;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.glue.SuperGlueItem;
import com.simibubi.create.content.equipment.symmetryWand.SymmetryWandItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * A durable, surgical-table-only proxy for every tool or consumable used while editing a subject.
 * The selected logical tool is stored on the stack; no actual tool stack is created or consumed.
 */
public class SurgicalKitItem extends Item {
	public static final int MAX_DURABILITY = 200;
	public static final String OPEN_KEY_TRANSLATION = "key.create_biotech.surgical_kit";
	private static final String SELECTED_TOOL_TAG = "SurgicalKitTool";
	private static final String TEMPORARY_MOVE_TAG = "SurgicalKitTemporaryMove";
	private static final String MOVE_DIMENSION_TAG = "Dimension";
	private static final String MOVE_TABLE_POS_TAG = "TablePos";
	private static final String MOVE_ANCHOR_SUBJECT_TAG = "AnchorSubject";
	private static final String MOVE_ANCHOR_CUBE_TAG = "AnchorCube";
	private static final String MOVE_COMPONENTS_TAG = "Components";
	private static final String MOVE_SUBJECT_TAG = "Subject";
	private static final String MOVE_CUBES_TAG = "Cubes";
	private static final String MOVE_SUBJECT_STATE_TAG = "SourceState";
	private static final String MOVE_ASSEMBLY_TAG = "SourceAssembly";
	private static final int TEMPORARY_MOVE_VALIDATION_INTERVAL = 20;

	public SurgicalKitItem(Properties properties) {
		super(properties);
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void initializeClient(Consumer<IClientItemExtensions> consumer) {
		consumer.accept(new IClientItemExtensions() {
			private final SurgicalKitItemRenderer renderer = new SurgicalKitItemRenderer();

			@Override
			public SurgicalKitItemRenderer getCustomRenderer() {
				return renderer;
			}
		});
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean selected) {
		if (!(level instanceof ServerLevel currentLevel)
			|| Math.floorMod(level.getGameTime() + entity.getId() + slotId,
				TEMPORARY_MOVE_VALIDATION_INTERVAL) != 0)
			return;
		CompoundTag root = CBItemData.getReadOnly(stack);
		boolean hasMoveData = root != null && root.contains(TEMPORARY_MOVE_TAG, Tag.TAG_COMPOUND);
		boolean hasCapture = CapturedEntityBoxHelper.hasCapturedEntity(stack);
		if (!hasMoveData && !hasCapture)
			return;
		TemporaryMove move = temporaryMove(stack);
		if (!hasMoveData || !hasCapture || move == null) {
			clearTemporaryCapture(stack);
			return;
		}
		ServerLevel sourceLevel = currentLevel.getServer().getLevel(
			ResourceKey.create(Registries.DIMENSION, move.dimension()));
		if (sourceLevel == null) {
			clearTemporaryCapture(stack);
			return;
		}
		// An unloaded source is only temporarily unavailable. Keep the move until its chunk can be
		// checked; once loaded, stale subject identity, topology or placement invalidates it.
		if (sourceLevel.isLoaded(move.tablePos())
			&& !SurgicalTableBlockEntity.isTemporaryMoveValid(sourceLevel, move))
			clearTemporaryCapture(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
		TooltipFlag flag) {
		Tool selected = selectedTool(stack);
		if (selected != null)
			tooltip.add(Component.translatable("item.create_biotech.surgical_kit.selected",
				selected.displayName()).withStyle(ChatFormatting.GRAY));
		if (selected == Tool.TEMPORARY_BOX && CapturedEntityBoxHelper.hasCapturedEntity(stack))
			CapturedEntityBoxHelper.appendHoverText(stack, tooltip);
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
		if (tool == null)
			return 0.0f;
		float value = tool.ordinal() + 1.0f;
		return tool == Tool.TEMPORARY_BOX && CapturedEntityBoxHelper.hasCapturedEntity(stack)
			? value + 0.5f : value;
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
			|| selectedTool(stack) == Tool.SMART_SUPER_GLUE;
	}

	public static boolean isShovel(ItemStack stack) {
		return stack.is(ItemTags.SHOVELS) || selectedTool(stack) == Tool.SHOVEL;
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
		return null;
	}

	public static boolean isTemporaryBox(ItemStack stack) {
		return selectedTool(stack) == Tool.TEMPORARY_BOX;
	}

	public static boolean hasTemporaryCapture(ItemStack stack) {
		return isTemporaryBox(stack) && CapturedEntityBoxHelper.hasCapturedEntity(stack)
			&& temporaryMove(stack) != null;
	}

	public static boolean isEmptyTemporaryBox(ItemStack stack) {
		return isTemporaryBox(stack) && !CapturedEntityBoxHelper.hasCapturedEntity(stack)
			&& temporaryMove(stack) == null;
	}

	@Nullable
	public static TemporaryMove temporaryMove(ItemStack stack) {
		if (!isKit(stack))
			return null;
		CompoundTag root = CBItemData.getReadOnly(stack);
		if (root == null || !root.contains(TEMPORARY_MOVE_TAG, Tag.TAG_COMPOUND))
			return null;
		CompoundTag encoded = root.getCompound(TEMPORARY_MOVE_TAG);
		ResourceLocation dimension = ResourceLocation.tryParse(encoded.getString(MOVE_DIMENSION_TAG));
		if (dimension == null || !encoded.contains(MOVE_TABLE_POS_TAG, Tag.TAG_LONG)
			|| !encoded.hasUUID(MOVE_ANCHOR_SUBJECT_TAG)
			|| !encoded.contains(MOVE_ANCHOR_CUBE_TAG, Tag.TAG_ANY_NUMERIC)
			|| !encoded.contains(MOVE_COMPONENTS_TAG, Tag.TAG_LIST)
			|| !encoded.contains(MOVE_ASSEMBLY_TAG, Tag.TAG_COMPOUND))
			return null;
		ListTag encodedComponents = encoded.getList(MOVE_COMPONENTS_TAG, Tag.TAG_COMPOUND);
		if (encodedComponents.isEmpty() || encodedComponents.size() > SurgicalAssembly.MAX_SOURCES)
			return null;
		Map<UUID, BitSet> components = new HashMap<>();
		Map<UUID, CompoundTag> sourceSubjects = new HashMap<>();
		for (int index = 0; index < encodedComponents.size(); index++) {
			CompoundTag component = encodedComponents.getCompound(index);
			if (!component.hasUUID(MOVE_SUBJECT_TAG) || !component.contains(MOVE_CUBES_TAG, Tag.TAG_LONG_ARRAY)
				|| !component.contains(MOVE_SUBJECT_STATE_TAG, Tag.TAG_COMPOUND))
				return null;
			UUID subject = component.getUUID(MOVE_SUBJECT_TAG);
			BitSet cubes = BitSet.valueOf(component.getLongArray(MOVE_CUBES_TAG));
			if (cubes.isEmpty() || cubes.length() > SurgicalAssembly.MAX_CUBES
				|| components.putIfAbsent(subject, cubes) != null
				|| sourceSubjects.putIfAbsent(subject, component.getCompound(MOVE_SUBJECT_STATE_TAG)) != null)
				return null;
		}
		UUID anchorSubject = encoded.getUUID(MOVE_ANCHOR_SUBJECT_TAG);
		int anchorCube = encoded.getInt(MOVE_ANCHOR_CUBE_TAG);
		BitSet anchorComponent = components.get(anchorSubject);
		if (anchorComponent == null || anchorCube < 0 || !anchorComponent.get(anchorCube))
			return null;
		return new TemporaryMove(dimension, BlockPos.of(encoded.getLong(MOVE_TABLE_POS_TAG)),
			anchorSubject, anchorCube, components, sourceSubjects, encoded.getCompound(MOVE_ASSEMBLY_TAG));
	}

	public static void setTemporaryMove(ItemStack stack, ResourceLocation dimension, BlockPos tablePos,
		UUID anchorSubject, int anchorCube, Map<UUID, BitSet> components,
		Map<UUID, CompoundTag> sourceSubjects, CompoundTag sourceAssembly) {
		if (!isTemporaryBox(stack) || dimension == null || tablePos == null || components == null
			|| components.isEmpty() || sourceSubjects == null
			|| !sourceSubjects.keySet().equals(components.keySet())
			|| anchorSubject == null || anchorCube < 0
			|| !components.containsKey(anchorSubject) || !components.get(anchorSubject).get(anchorCube)
			|| sourceAssembly == null || sourceAssembly.isEmpty())
			return;
		CompoundTag encoded = new CompoundTag();
		encoded.putString(MOVE_DIMENSION_TAG, dimension.toString());
		encoded.putLong(MOVE_TABLE_POS_TAG, tablePos.asLong());
		encoded.putUUID(MOVE_ANCHOR_SUBJECT_TAG, anchorSubject);
		encoded.putInt(MOVE_ANCHOR_CUBE_TAG, anchorCube);
		ListTag encodedComponents = new ListTag();
		components.forEach((subject, cubes) -> {
			CompoundTag sourceState = sourceSubjects.get(subject);
			if (sourceState == null || sourceState.isEmpty())
				return;
			CompoundTag component = new CompoundTag();
			component.putUUID(MOVE_SUBJECT_TAG, subject);
			component.putLongArray(MOVE_CUBES_TAG, cubes.toLongArray());
			component.put(MOVE_SUBJECT_STATE_TAG, sourceState.copy());
			encodedComponents.add(component);
		});
		if (encodedComponents.size() != components.size())
			return;
		encoded.put(MOVE_COMPONENTS_TAG, encodedComponents);
		encoded.put(MOVE_ASSEMBLY_TAG, sourceAssembly.copy());
		CBItemData.edit(stack, root -> root.put(TEMPORARY_MOVE_TAG, encoded));
	}

	public static void clearTemporaryMove(ItemStack stack) {
		CBItemData.edit(stack, root -> root.remove(TEMPORARY_MOVE_TAG));
	}

	public static void clearTemporaryCapture(ItemStack stack) {
		CapturedEntityBoxHelper.clearCapturedEntity(stack);
		clearTemporaryMove(stack);
	}

	public static boolean hasDurability(ItemStack stack, int amount) {
		return !isKit(stack) || amount >= 0 && stack.getMaxDamage() - stack.getDamageValue() >= amount;
	}

	public enum Tool {
		SHEARS("shears", () -> new ItemStack(Items.SHEARS)),
		SHOVEL("shovel", () -> new ItemStack(Items.WOODEN_SHOVEL)),
		SMART_SUPER_GLUE("smart_super_glue", () -> new ItemStack(CBItems.SMART_SUPER_GLUE.get())),
		HONEY_BOTTLE("honey_bottle", () -> new ItemStack(Items.HONEY_BOTTLE)),
		SLIME_BALL("slime_ball", () -> new ItemStack(Items.SLIME_BALL)),
		SYMMETRY_WAND("symmetry_wand", () -> AllItems.WAND_OF_SYMMETRY.asStack()),
		WRENCH("wrench", () -> AllItems.WRENCH.asStack()),
		TEMPORARY_BOX("temporary_box", () -> new ItemStack(CBItems.LARGE_CARDBOARD_BOX.get()));

		private final String id;
		private final Supplier<ItemStack> displayStack;

		Tool(String id, Supplier<ItemStack> displayStack) {
			this.id = id;
			this.displayStack = displayStack;
		}

		public String id() {
			return id;
		}

		public ItemStack displayStack() {
			return displayStack.get();
		}

		public Component displayName() {
			return this == TEMPORARY_BOX
				? Component.translatable("item.create_biotech.surgical_kit.temporary_box")
				: displayStack().getHoverName();
		}

		@Nullable
		private static Tool byId(String id) {
			if (id == null || id.isBlank())
				return null;
			String normalized = id.toLowerCase(Locale.ROOT);
			// Migrate kits that had the replaced ordinary-super-glue wheel slot selected.
			if ("super_glue".equals(normalized))
				return SHOVEL;
			for (Tool tool : values())
				if (tool.id.equals(normalized))
					return tool;
			return null;
		}
	}

	public record TemporaryMove(ResourceLocation dimension, BlockPos tablePos,
		UUID anchorSubject, int anchorCube,
		Map<UUID, BitSet> components, Map<UUID, CompoundTag> sourceSubjects,
		CompoundTag sourceAssembly) {
		public TemporaryMove {
			Map<UUID, BitSet> frozen = new HashMap<>();
			components.forEach((subject, cubes) -> frozen.put(subject, (BitSet) cubes.clone()));
			components = Map.copyOf(frozen);
			Map<UUID, CompoundTag> frozenSubjects = new HashMap<>();
			sourceSubjects.forEach((subject, state) -> frozenSubjects.put(subject, state.copy()));
			sourceSubjects = Map.copyOf(frozenSubjects);
			sourceAssembly = sourceAssembly.copy();
		}

		@Override
		public Map<UUID, BitSet> components() {
			Map<UUID, BitSet> copy = new HashMap<>();
			components.forEach((subject, cubes) -> copy.put(subject, (BitSet) cubes.clone()));
			return Map.copyOf(copy);
		}

		@Override
		public CompoundTag sourceAssembly() {
			return sourceAssembly.copy();
		}

		@Override
		public Map<UUID, CompoundTag> sourceSubjects() {
			Map<UUID, CompoundTag> copy = new HashMap<>();
			sourceSubjects.forEach((subject, state) -> copy.put(subject, state.copy()));
			return Map.copyOf(copy);
		}

		@Nullable
		public BitSet component(UUID subject) {
			BitSet cubes = components.get(subject);
			return cubes == null ? null : (BitSet) cubes.clone();
		}

		@Nullable
		public CompoundTag sourceSubject(UUID subject) {
			CompoundTag state = sourceSubjects.get(subject);
			return state == null ? null : state.copy();
		}
	}
}
