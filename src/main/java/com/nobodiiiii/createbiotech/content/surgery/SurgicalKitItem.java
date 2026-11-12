package com.nobodiiiii.createbiotech.content.surgery;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.foundation.item.CBItemData;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.AllBlocks;
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
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.Tags;

/** Base item for kits which store a selected logical tool without creating or consuming its display stack. */
public class SurgicalKitItem extends Item {
	public static final int MAX_DURABILITY = 200;
	public static final String OPEN_KEY_TRANSLATION = "key.create_biotech.surgical_kit";
	private static final List<Tool> STANDARD_TOOLS = List.of(Tool.SHEARS, Tool.SHOVEL,
		Tool.SMART_SUPER_GLUE, Tool.HONEY_BOTTLE, Tool.SLIME_BALL, Tool.SYMMETRY_WAND,
		Tool.WRENCH, Tool.TEMPORARY_BOX);
	private static final String SELECTED_TOOL_TAG = "SurgicalKitTool";
	private static final String TEMPORARY_MOVE_TAG = "SurgicalKitTemporaryMove";
	private static final String MOVE_FORMAT_VERSION_TAG = "Version";
	private static final int MOVE_FORMAT_VERSION = 1;
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
	private static final MapCodec<Optional<String>> SELECTED_TOOL_FIELD =
		Codec.STRING.optionalFieldOf(SELECTED_TOOL_TAG);
	// The selected tool is read by several per-frame predicates, so it is decoded straight from the
	// component by identity instead of through the full custom-data snapshot. Values are enum
	// constants only, never the weak component key or the body a kit may carry.
	private static final LoadingCache<CustomData, Optional<Tool>> SELECTED_TOOLS = CacheBuilder.newBuilder()
		.weakKeys()
		.build(CacheLoader.from(SurgicalKitItem::readSelectedTool));
	private final List<Tool> availableTools;

	public SurgicalKitItem(Properties properties) {
		this(properties, STANDARD_TOOLS);
	}

	protected SurgicalKitItem(Properties properties, List<Tool> availableTools) {
		super(properties);
		if (availableTools == null || availableTools.isEmpty())
			throw new IllegalArgumentException("A surgical kit must contain at least one tool");
		this.availableTools = List.copyOf(availableTools);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean selected) {
		if (!availableTools.contains(Tool.TEMPORARY_BOX) || !(level instanceof ServerLevel currentLevel)
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
	}

	@Nullable
	public static Tool selectedTool(ItemStack stack) {
		if (!(stack.getItem() instanceof SurgicalKitItem kit))
			return null;
		CustomData component = CBItemData.getReadOnlyComponent(stack);
		if (component == null)
			return null;
		Tool tool = SELECTED_TOOLS.getUnchecked(component).orElse(null);
		return tool != null && kit.availableTools.contains(tool) ? tool : null;
	}

	private static Optional<Tool> readSelectedTool(CustomData component) {
		return component.read(SELECTED_TOOL_FIELD).result().flatMap(id -> id).map(Tool::byId);
	}

	public static void setSelectedTool(ItemStack stack, Tool tool) {
		if (!(stack.getItem() instanceof SurgicalKitItem kit) || tool == null
			|| !kit.availableTools.contains(tool))
			return;
		CBItemData.edit(stack, tag -> tag.putString(SELECTED_TOOL_TAG, tool.id));
	}

	public static List<Tool> availableTools(ItemStack stack) {
		return stack.getItem() instanceof SurgicalKitItem kit ? kit.availableTools : List.of();
	}

	public static float modelValue(ItemStack stack) {
		Tool tool = selectedTool(stack);
		if (tool == null)
			return 0.0f;
		float value = availableTools(stack).indexOf(tool) + 1.0f;
		return (tool == Tool.TEMPORARY_BOX || tool == Tool.CUBE_COPIER)
			&& CapturedEntityBoxHelper.hasCapturedEntity(stack)
			? value + 0.5f : value;
	}

	public static boolean isKit(ItemStack stack) {
		return stack.getItem() instanceof SurgicalKitItem;
	}

	/** Whether the kit has its logical shears, honey bottle or slime ball selected. */
	public static boolean isShears(ItemStack stack) {
		return stack.is(Items.SHEARS) || selectedTool(stack) == Tool.SHEARS;
	}

	public static boolean isHoneyBottle(ItemStack stack) {
		return stack.is(Items.HONEY_BOTTLE) || selectedTool(stack) == Tool.HONEY_BOTTLE;
	}

	public static boolean isSlimeBall(ItemStack stack) {
		return stack.is(Items.SLIME_BALL) || selectedTool(stack) == Tool.SLIME_BALL;
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

	public static boolean isSymmetryWand(ItemStack stack) {
		return stack.getItem() instanceof SymmetryWandItem || selectedTool(stack) == Tool.SYMMETRY_WAND;
	}

	public static boolean isWrench(ItemStack stack) {
		return AllItems.WRENCH.isIn(stack) || stack.is(Tags.Items.TOOLS_WRENCH)
			|| selectedTool(stack) == Tool.WRENCH;
	}

	@Nullable
	public static SurgicalLimbType limbType(ItemStack stack) {
		return SurgicalJointItem.selectedType(stack);
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

	public static boolean isCubeCopier(ItemStack stack) {
		return selectedTool(stack) == Tool.CUBE_COPIER;
	}

	public static boolean isEmptyCubeCopier(ItemStack stack) {
		return isCubeCopier(stack) && !CapturedEntityBoxHelper.hasCapturedEntity(stack);
	}

	/** Only the selected tool can offer its stored body to table placement. */
	public static boolean hasPlacementCapture(ItemStack stack) {
		return hasTemporaryCapture(stack)
			|| isCubeCopier(stack) && CapturedEntityBoxHelper.hasCapturedEntity(stack);
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
		if (!encoded.contains(MOVE_FORMAT_VERSION_TAG, Tag.TAG_ANY_NUMERIC)
			|| encoded.getInt(MOVE_FORMAT_VERSION_TAG) != MOVE_FORMAT_VERSION || dimension == null
			|| !encoded.contains(MOVE_TABLE_POS_TAG, Tag.TAG_LONG)
			|| !encoded.hasUUID(MOVE_ANCHOR_SUBJECT_TAG)
			|| !encoded.contains(MOVE_ANCHOR_CUBE_TAG, Tag.TAG_ANY_NUMERIC)
			|| !encoded.contains(MOVE_COMPONENTS_TAG, Tag.TAG_LIST)
			|| !encoded.contains(MOVE_ASSEMBLY_TAG, Tag.TAG_COMPOUND))
			return null;
		ListTag encodedComponents = (ListTag) encoded.get(MOVE_COMPONENTS_TAG);
		if (!encodedComponents.isEmpty() && encodedComponents.getElementType() != Tag.TAG_COMPOUND)
			return null;
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
			anchorSubject, anchorCube, Map.copyOf(components), Map.copyOf(sourceSubjects),
			encoded.getCompound(MOVE_ASSEMBLY_TAG));
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
		encoded.putInt(MOVE_FORMAT_VERSION_TAG, MOVE_FORMAT_VERSION);
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
			// The caller hands over freshly saved tags, and writing the component copies the root.
			component.put(MOVE_SUBJECT_STATE_TAG, sourceState);
			encodedComponents.add(component);
		});
		if (encodedComponents.size() != components.size())
			return;
		encoded.put(MOVE_COMPONENTS_TAG, encodedComponents);
		encoded.put(MOVE_ASSEMBLY_TAG, sourceAssembly);
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
		TEMPORARY_BOX("temporary_box", () -> new ItemStack(CBItems.EMPTY_LARGE_CARDBOARD_BOX.get()),
			"item.create_biotech.surgical_kit.temporary_box"),
		MIMIC_INDUCER("mimic_inducer", () -> new ItemStack(CBItems.BIONIC_MECHANISM.get()),
			"item.create_biotech.creative_surgical_kit.mimic_inducer"),
		MIMIC_RESTORATIVE("mimic_restorative",
			() -> PotionContents.createItemStack(Items.POTION, Potions.REGENERATION),
			"item.create_biotech.creative_surgical_kit.mimic_restorative"),
		MIMIC_POSSESSOR("mimic_possessor", () -> new ItemStack(Items.ECHO_SHARD),
			"item.create_biotech.creative_surgical_kit.mimic_possessor"),
		CUBE_COPIER("cube_copier", () -> AllBlocks.CLIPBOARD.asStack(),
			"item.create_biotech.creative_surgical_kit.cube_copier");

		private final String id;
		private final Supplier<ItemStack> displayStack;
		@Nullable
		private final String displayNameKey;

		private static final Map<String, Tool> BY_ID = java.util.Arrays.stream(values())
			.collect(java.util.stream.Collectors.toUnmodifiableMap(tool -> tool.id, tool -> tool));

		Tool(String id, Supplier<ItemStack> displayStack) {
			this(id, displayStack, null);
		}

		Tool(String id, Supplier<ItemStack> displayStack, @Nullable String displayNameKey) {
			this.id = id;
			this.displayStack = displayStack;
			this.displayNameKey = displayNameKey;
		}

		public String id() {
			return id;
		}

		public ItemStack displayStack() {
			return displayStack.get();
		}

		public Component displayName() {
			return displayNameKey == null ? displayStack().getHoverName() : Component.translatable(displayNameKey);
		}

		@Nullable
		private static Tool byId(String id) {
			if (id == null || id.isBlank())
				return null;
			Tool tool = BY_ID.get(id);
			// Hand-edited data may carry a mixed-case id; the enum's own ids are lower case.
			return tool != null ? tool : BY_ID.get(id.toLowerCase(Locale.ROOT));
		}
	}

	/**
	 * A parsed view of a kit's stored temporary move. The tags belong to the stack's read-only
	 * custom-data snapshot and the cube sets to this record: every reader only compares or looks them
	 * up, so nothing here is copied again. Treat all of it as read-only.
	 */
	public record TemporaryMove(ResourceLocation dimension, BlockPos tablePos,
		UUID anchorSubject, int anchorCube,
		Map<UUID, BitSet> components, Map<UUID, CompoundTag> sourceSubjects,
		CompoundTag sourceAssembly) {
		@Nullable
		public BitSet component(UUID subject) {
			return components.get(subject);
		}

		@Nullable
		public CompoundTag sourceSubject(UUID subject) {
			return sourceSubjects.get(subject);
		}
	}
}
