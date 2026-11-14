package com.nobodiiiii.createbiotech.content.cardboardbox;

import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.api.bionic.BionicGameplayEvent;
import com.nobodiiiii.createbiotech.entity.trait.BionicGameplayRuntime;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.MapCodec;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointRepair;
import com.nobodiiiii.createbiotech.foundation.item.CBItemData;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.item.ItemHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.entity.PartEntity;

public class CapturedEntityBoxHelper {
	private static final String CAPTURED_ENTITY_TAG = "CapturedEntity";
	private static final String CAPTURED_ENTITY_DESC_ID_TAG = "CapturedEntityDescId";
	private static final String CAPTURED_ENTITY_HEALTH_TAG = "CapturedEntityHealth";
	private static final String DATA_ROOT = CreateBiotech.MOD_ID;
	private static final String AI_DISABLED_BY_MOD_TAG = "AiDisabledByMod";
	private static final String NO_AI_TAG = "NoAI";
	private static final String NEOFORGE_DATA_TAG = "NeoForgeData";
	private static final String FORGE_DATA_TAG = "ForgeData";
	private static final String CHAMBER_MARKED_TAG = "CreeperBlastChamberMarked";
	private static final String CHAMBER_CONTROLLER_POS_TAG = "CreeperBlastChamberControllerPos";
	private static final String CHAMBER_PACKAGER_POS_TAG = "CreeperBlastChamberPackagerPos";
	private static final MapCodec<Dynamic<?>> CAPTURED_ENTITY_FIELD =
		Codec.PASSTHROUGH.fieldOf(CAPTURED_ENTITY_TAG);
	private static final MapCodec<Optional<Float>> CAPTURED_HEALTH_FIELD =
		Codec.FLOAT.optionalFieldOf(CAPTURED_ENTITY_HEALTH_TAG);
	// Values contain only scalar metadata, never the weak component key or its entity NBT.
	private static final LoadingCache<CustomData, CaptureMetadata> CAPTURED_METADATA = CacheBuilder.newBuilder()
		.weakKeys()
		.build(CacheLoader.from(CapturedEntityBoxHelper::readCaptureMetadata));
	// Computed lazily, once per immutable component. Values never retain the weak key or its NBT.
	private static final LoadingCache<CustomData, HashCode> CAPTURE_FINGERPRINTS = CacheBuilder.newBuilder()
		.weakKeys()
		.build(CacheLoader.from(CapturedEntityBoxHelper::readCaptureFingerprint));

	private CapturedEntityBoxHelper() {}

	/** Resolves multipart interaction proxies to the living entity that owns the saved state. */
	@Nullable
	public static LivingEntity resolveLivingTarget(Entity target) {
		Entity resolved = target;
		while (resolved instanceof PartEntity<?> part) {
			Entity parent = part.getParent();
			if (parent == null || parent == resolved)
				return null;
			resolved = parent;
		}
		return resolved instanceof LivingEntity living ? living : null;
	}

	public static void markAiDisabledByMod(Entity entity) {
		if (entity == null)
			return;
		CompoundTag persistentData = entity.getPersistentData();
		CompoundTag data = persistentData.contains(DATA_ROOT, Tag.TAG_COMPOUND)
			? persistentData.getCompound(DATA_ROOT) : new CompoundTag();
		data.putBoolean(AI_DISABLED_BY_MOD_TAG, true);
		persistentData.put(DATA_ROOT, data);
	}

	public static void unmarkAiDisabledByMod(Entity entity) {
		if (entity == null)
			return;
		CompoundTag persistentData = entity.getPersistentData();
		if (!persistentData.contains(DATA_ROOT, Tag.TAG_COMPOUND))
			return;
		CompoundTag data = persistentData.getCompound(DATA_ROOT);
		data.remove(AI_DISABLED_BY_MOD_TAG);
		if (data.isEmpty())
			persistentData.remove(DATA_ROOT);
		else
			persistentData.put(DATA_ROOT, data);
	}

	public static boolean isAiDisabledByMod(Entity entity) {
		if (entity == null)
			return false;
		CompoundTag persistentData = entity.getPersistentData();
		if (!persistentData.contains(DATA_ROOT, Tag.TAG_COMPOUND))
			return false;
		return persistentData.getCompound(DATA_ROOT)
			.getBoolean(AI_DISABLED_BY_MOD_TAG);
	}

	public static void appendHoverText(ItemStack stack, HolderLookup.Provider registries,
		List<Component> tooltipComponents) {
		appendAddressTooltip(stack, tooltipComponents);
		appendContentsTooltip(stack, registries, tooltipComponents);
	}

	public static boolean captureEntity(ItemStack stack, LivingEntity target) {
		if (stack.isEmpty() || stack.getItem() instanceof EmptyCardboardBoxItem || hasCapturedEntity(stack))
			return false;

		BionicGameplayEvent packEvent = target instanceof SlimeBionicEntity bionic
			? BionicGameplayRuntime.post(BionicGameplayRuntime.event(BionicGameplayEvent.Kind.PACK, bionic, CreateBiotech.asResource("pack")).interaction(null, null, stack)) : null;
		if (packEvent != null && packEvent.isCancelled()) return false;
		CompoundTag entityData = new CompoundTag();
		target.saveWithoutId(entityData);

		ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
		if (entityId == null)
			return false;

		entityData.putString("id", entityId.toString());
		restoreAiInSavedEntityData(entityData);

		CompoundTag stackTag = CBItemData.getOrEmpty(stack);
		stackTag.put(CAPTURED_ENTITY_TAG, entityData);
		stackTag.putString(CAPTURED_ENTITY_DESC_ID_TAG, target.getType().getDescriptionId());
		stackTag.putFloat(CAPTURED_ENTITY_HEALTH_TAG, target.getHealth());
		CBItemData.set(stack, stackTag);
		if (packEvent != null) BionicGameplayRuntime.post(packEvent.after());
		return true;
	}

	public static ItemStack createFilledBox(Item boxItem, EntityType<?> entityType) {
		if (boxItem instanceof EmptyCardboardBoxItem emptyBox)
			boxItem = emptyBox.filledBox();
		if (!(boxItem instanceof CapturedEntityBoxItem))
			throw new IllegalArgumentException("Item " + BuiltInRegistries.ITEM.getKey(boxItem)
				+ " is not a captured entity box item");

		ItemStack stack = boxItem.getDefaultInstance();
		ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
		if (entityId == null)
			return stack;

		CompoundTag entityData = new CompoundTag();
		entityData.putString("id", entityId.toString());
		CompoundTag stackTag = CBItemData.getOrEmpty(stack);
		stackTag.put(CAPTURED_ENTITY_TAG, entityData);
		stackTag.putString(CAPTURED_ENTITY_DESC_ID_TAG, entityType.getDescriptionId());
		stackTag.remove(CAPTURED_ENTITY_HEALTH_TAG);
		CBItemData.set(stack, stackTag);
		return stack;
	}

	/**
	 * Copies only the captured-entity payload into a fresh box. Address, package
	 * contents and unrelated item components deliberately do not cross the machine
	 * boundary.
	 */
	public static ItemStack copyCapturedEntityIntoFreshBox(ItemStack source, Item boxItem) {
		ItemStack result = new ItemStack(boxItem);
		CompoundTag sourceData = CBItemData.getReadOnly(source);
		if (sourceData == null || !sourceData.contains(CAPTURED_ENTITY_TAG, Tag.TAG_COMPOUND))
			return ItemStack.EMPTY;

		CompoundTag resultData = new CompoundTag();
		resultData.put(CAPTURED_ENTITY_TAG, sourceData.getCompound(CAPTURED_ENTITY_TAG).copy());
		if (sourceData.contains(CAPTURED_ENTITY_DESC_ID_TAG, Tag.TAG_STRING))
			resultData.putString(CAPTURED_ENTITY_DESC_ID_TAG, sourceData.getString(CAPTURED_ENTITY_DESC_ID_TAG));
		if (sourceData.contains(CAPTURED_ENTITY_HEALTH_TAG, Tag.TAG_ANY_NUMERIC))
			resultData.putFloat(CAPTURED_ENTITY_HEALTH_TAG, sourceData.getFloat(CAPTURED_ENTITY_HEALTH_TAG));
		CBItemData.set(result, resultData);
		clearLegacyCreeperBlastChamberMarkers(result);
		return result;
	}

	/** Removes obsolete chamber ownership tags from both 1.21 and historical data roots. */
	public static void clearLegacyCreeperBlastChamberMarkers(ItemStack stack) {
		CBItemData.edit(stack, root -> {
			if (!root.contains(CAPTURED_ENTITY_TAG, Tag.TAG_COMPOUND))
				return;
			CompoundTag entityData = root.getCompound(CAPTURED_ENTITY_TAG);
			clearLegacyCreeperBlastChamberMarkers(entityData, NEOFORGE_DATA_TAG);
			clearLegacyCreeperBlastChamberMarkers(entityData, FORGE_DATA_TAG);
		});
	}

	public static void clearLegacyCreeperBlastChamberMarkers(Entity entity) {
		if (entity == null)
			return;
		clearChamberKeys(entity.getPersistentData());
	}

	private static void clearLegacyCreeperBlastChamberMarkers(CompoundTag entityData, String persistentRoot) {
		if (!entityData.contains(persistentRoot, Tag.TAG_COMPOUND))
			return;
		CompoundTag persistentData = entityData.getCompound(persistentRoot);
		clearChamberKeys(persistentData);
		if (persistentData.isEmpty())
			entityData.remove(persistentRoot);
		else
			entityData.put(persistentRoot, persistentData);
	}

	private static void clearChamberKeys(CompoundTag persistentData) {
		if (!persistentData.contains(DATA_ROOT, Tag.TAG_COMPOUND))
			return;
		CompoundTag modData = persistentData.getCompound(DATA_ROOT);
		modData.remove(CHAMBER_MARKED_TAG);
		modData.remove(CHAMBER_CONTROLLER_POS_TAG);
		modData.remove(CHAMBER_PACKAGER_POS_TAG);
		if (modData.isEmpty())
			persistentData.remove(DATA_ROOT);
		else
			persistentData.put(DATA_ROOT, modData);
	}

	public static boolean captureEntityFromPlayerStack(ItemStack stack, Player player, LivingEntity target) {
		ItemStack filledBox = createCaptureBox(stack);
		if (filledBox.isEmpty())
			return false;
		if (!captureEntity(filledBox, target))
			return false;

		if (stack.getCount() == 1)
			return replacePlayerStack(player, stack, filledBox);

		stack.shrink(1);
		if (!player.getInventory().add(filledBox))
			player.drop(filledBox, false);
		return true;
	}

	/** Builds one package without consuming the empty input; commit only after capture succeeds. */
	public static ItemStack createCaptureBox(ItemStack emptyBox) {
		if (!isEmptyBox(emptyBox))
			return ItemStack.EMPTY;
		Item item = emptyBox.getItem() instanceof EmptyCardboardBoxItem empty
			? empty.filledBox() : emptyBox.getItem();
		ItemStack result = emptyBox.transmuteCopy(item, 1);
		clearPackageRouting(result);
		return result;
	}

	/** Returns the ordinary empty item, preserving names and unrelated custom data. */
	public static ItemStack createEmptyBox(ItemStack source) {
		if (!(source.getItem() instanceof CapturedEntityBoxItem filled))
			return source.copy();
		ItemStack result = source.transmuteCopy(filled.emptyBox());
		clearCapturedEntity(result);
		clearPackageRouting(result);
		return result;
	}

	private static void clearPackageRouting(ItemStack stack) {
		stack.remove(AllDataComponents.PACKAGE_ADDRESS);
		stack.remove(AllDataComponents.PACKAGE_ORDER_DATA);
		stack.remove(AllDataComponents.PACKAGE_ORDER_CONTEXT);
		stack.remove(AllDataComponents.PACKAGE_CONTENTS);
	}

	public static boolean isEmptyBox(ItemStack stack) {
		return !stack.isEmpty()
			&& (stack.getItem() instanceof EmptyCardboardBoxItem
				|| stack.getItem() instanceof CapturedEntityBoxItem)
			&& !hasCapturedEntity(stack)
			&& (stack.getItem() instanceof EmptyCardboardBoxItem
				|| stack.getOrDefault(AllDataComponents.PACKAGE_CONTENTS, ItemContainerContents.EMPTY)
					.stream().allMatch(ItemStack::isEmpty));
	}

	public static boolean isEmptySmallBox(ItemStack stack) {
		return isEmptyBox(stack) && (stack.getItem() instanceof CardboardBoxItem
			|| stack.getItem() instanceof EmptyCardboardBoxItem empty && !empty.isLarge());
	}

	public static boolean isEmptyLargeBox(ItemStack stack) {
		return isEmptyBox(stack) && (stack.getItem() instanceof LargeCardboardBoxItem
			|| stack.getItem() instanceof EmptyCardboardBoxItem empty && empty.isLarge());
	}

	public static boolean replacePlayerStack(Player player, ItemStack source, ItemStack replacement) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (player.getInventory().getItem(slot) != source)
				continue;
			player.getInventory().setItem(slot, replacement);
			player.getInventory().setChanged();
			return true;
		}
		return false;
	}

	private static void restoreAiInSavedEntityData(CompoundTag entityData) {
		if (!entityData.contains(NEOFORGE_DATA_TAG, Tag.TAG_COMPOUND))
			return;
		CompoundTag neoForgeData = entityData.getCompound(NEOFORGE_DATA_TAG);
		if (!neoForgeData.contains(DATA_ROOT, Tag.TAG_COMPOUND))
			return;
		CompoundTag modData = neoForgeData.getCompound(DATA_ROOT);
		if (!modData.getBoolean(AI_DISABLED_BY_MOD_TAG))
			return;

		entityData.remove(NO_AI_TAG);
		modData.remove(AI_DISABLED_BY_MOD_TAG);
		if (modData.isEmpty())
			neoForgeData.remove(DATA_ROOT);
		else
			neoForgeData.put(DATA_ROOT, modData);
		if (neoForgeData.isEmpty())
			entityData.remove(NEOFORGE_DATA_TAG);
		else
			entityData.put(NEOFORGE_DATA_TAG, neoForgeData);
	}

	public static boolean releaseCapturedEntity(UseOnContext context) {
		ItemStack stack = context.getItemInHand();
		Level level = context.getLevel();
		Entity entity = createCapturedEntity(stack, level);
		if (entity == null)
			return false;

		BlockPos clickedPos = context.getClickedPos();
		Direction face = context.getClickedFace();
		BlockState clickedState = level.getBlockState(clickedPos);
		BlockPos spawnPos = clickedState.getCollisionShape(level, clickedPos).isEmpty()
			? clickedPos : clickedPos.relative(face);

		entity.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
		BionicGameplayEvent releaseEvent = entity instanceof SlimeBionicEntity bionic
			? BionicGameplayRuntime.post(BionicGameplayRuntime.event(BionicGameplayEvent.Kind.RELEASE, bionic, CreateBiotech.asResource("release")).interaction(context.getPlayer(), context.getHand(), stack)) : null;
		if (releaseEvent != null && releaseEvent.isCancelled()) return false;
		if (!level.addFreshEntity(entity))
			return false;

		clearCapturedEntity(stack);
		if (context.getPlayer() != null)
			context.getPlayer().setItemInHand(context.getHand(), createEmptyBox(stack));
		if (releaseEvent != null) BionicGameplayRuntime.post(releaseEvent.after());
		return true;
	}

	public static boolean releaseCapturedEntity(ItemStack stack, Level level, Vec3 spawnPos, Vec3 motion) {
		Entity entity = createCapturedEntity(stack, level);
		if (entity == null)
			return false;

		entity.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, entity.getYRot(), entity.getXRot());
		entity.setDeltaMovement(motion);
		entity.fallDistance = 0;
		BionicGameplayEvent releaseEvent = entity instanceof SlimeBionicEntity bionic
			? BionicGameplayRuntime.post(BionicGameplayRuntime.event(BionicGameplayEvent.Kind.RELEASE, bionic, CreateBiotech.asResource("release")).interaction(null, null, stack)) : null;
		if (releaseEvent != null && releaseEvent.isCancelled()) return false;
		if (!level.addFreshEntity(entity))
			return false;

		clearCapturedEntity(stack);
		if (releaseEvent != null) BionicGameplayRuntime.post(releaseEvent.after());
		return true;
	}

	public static boolean containsEntityType(ItemStack stack, EntityType<?> entityType) {
		ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
		return entityId != null && entityId.toString().equals(captureMetadata(stack).entityId());
	}

	/** Resolves the cached species ID without loading the entity or copying its NBT. */
	@Nullable
	public static EntityType<?> getCapturedEntityType(ItemStack stack) {
		ResourceLocation entityId = ResourceLocation.tryParse(captureMetadata(stack).entityId());
		return entityId == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
	}

	/**
	 * Zero-copy view of the entity NBT held by the box, or null when the box is empty or holds
	 * a different entity type. Callers must not mutate the returned tag.
	 */
	public static CompoundTag getCapturedEntityData(ItemStack stack, EntityType<?> entityType) {
		return containsEntityType(stack, entityType) ? getCapturedEntityData(stack) : null;
	}

	public static Entity createCapturedEntity(ItemStack stack, Level level) {
		CapturedEntityRenderData renderData = getCapturedEntityRenderData(stack);
		if (renderData == null)
			return null;
		return createCapturedEntity(renderData, level);
	}

	/** Reads the immutable body for planning without constructing an entity and its runtime systems. */
	@Nullable
	public static SurgicalAssembly getCapturedAssembly(ItemStack stack) {
		CompoundTag entityData = getCapturedEntityData(stack, CBEntityTypes.SLIME_BIONIC.get());
		if (entityData == null || !entityData.contains("SurgicalAssembly", Tag.TAG_COMPOUND))
			return null;
		SurgicalAssembly assembly = SurgicalAssembly.loadCached(entityData.getCompound("SurgicalAssembly"));
		return assembly != null && assembly.isReadyForEntity() ? assembly : null;
	}

	/** Called by the owning item's load hook, never from a tooltip/render/tick loop. */
	public static void migrateReleasedAssembly(ItemStack stack) {
		CustomData component = CBItemData.getReadOnlyComponent(stack);
		if (component == null) return;
		CompoundTag entity = capturedEntityTag(component);
		if (entity == null || !entity.getString("id").equals(CreateBiotech.MOD_ID + ":slime_bionic")
			|| !entity.contains("SurgicalAssembly", Tag.TAG_COMPOUND)) return;
		CompoundTag encoded = entity.getCompound("SurgicalAssembly");
		if (!SurgicalAssembly.needsReleaseMigration(encoded)) return;
		SurgicalAssembly assembly = SurgicalAssembly.loadCached(encoded);
		if (assembly == null || !assembly.isReadyForEntity()) return;
		CompoundTag upgraded = assembly.save();
		// CustomData.update copies once; the component and its shared read-only snapshots stay intact.
		stack.set(DataComponents.CUSTOM_DATA, component.update(root ->
			root.getCompound(CAPTURED_ENTITY_TAG).put("SurgicalAssembly", upgraded)));
	}

	static Entity createCapturedEntity(CapturedEntityRenderData renderData, Level level) {
		CompoundTag entityDataForLoad = capturedEntityTag(renderData.component()).copy();
		if (level instanceof ServerLevel serverLevel
			&& hasUuidCollision(serverLevel.getServer(), entityDataForLoad))
			reseedEntityTree(entityDataForLoad);

		Entity entity = EntityType.loadEntityRecursive(entityDataForLoad, level, Function.identity());
		if (entity == null)
			return null;

		if (entity instanceof LivingEntity living
			&& renderData.hasCapturedHealth())
			living.setHealth(Math.min(living.getMaxHealth(), renderData.capturedHealth()));

		return entity;
	}

	private static boolean hasUuidCollision(MinecraftServer server, CompoundTag entityData) {
		if (entityData.hasUUID("UUID")) {
			UUID uuid = entityData.getUUID("UUID");
			for (ServerLevel level : server.getAllLevels())
				if (level.getEntity(uuid) != null)
					return true;
		}

		ListTag passengers = entityData.getList("Passengers", Tag.TAG_COMPOUND);
		for (int i = 0; i < passengers.size(); i++)
			if (hasUuidCollision(server, passengers.getCompound(i)))
				return true;

		return false;
	}

	private static void reseedEntityTree(CompoundTag entityData) {
		entityData.putUUID("UUID", UUID.randomUUID());

		ListTag passengers = entityData.getList("Passengers", Tag.TAG_COMPOUND);
		for (int i = 0; i < passengers.size(); i++)
			reseedEntityTree(passengers.getCompound(i));
	}

	public static void clearCapturedEntity(ItemStack stack) {
		CompoundTag tag = CBItemData.getOrEmpty(stack);

		tag.remove(CAPTURED_ENTITY_TAG);
		tag.remove(CAPTURED_ENTITY_DESC_ID_TAG);
		tag.remove(CAPTURED_ENTITY_HEALTH_TAG);
		UniversalJointRepair.clearSelection(tag);
		CBItemData.set(stack, tag);
	}

	public static boolean hasCapturedEntity(ItemStack stack) {
		return captureMetadata(stack).captured();
	}

	/**
	 * Applies a name-tag name to the creature held by the box, writing it into the
	 * captured entity's NBT so the beast is released already named.
	 * The box must already hold a creature; otherwise {@code false} is returned and the
	 * stack is left unchanged.
	 *
	 * <p>Only {@code CustomName} is updated. {@code CustomNameVisible} is left unchanged,
	 * matching an ordinary name tag applied directly to the creature.</p>
	 */
	public static boolean applyNameToCapturedEntity(ItemStack stack, RegistryAccess registryAccess, Component name) {
		if (name == null || name.getString().isBlank())
			return false;
		if (!hasCapturedEntity(stack))
			return false;

		CBItemData.edit(stack, root -> {
			CompoundTag entityData = root.getCompound(CAPTURED_ENTITY_TAG);
			String json = Component.Serializer.toJson(name, registryAccess);
			entityData.putString("CustomName", json);
			root.put(CAPTURED_ENTITY_TAG, entityData);
		});
		return true;
	}

	/** Describes the icon without copying or weighing the captured entity's NBT. */
	static CapturedEntityRenderData getCapturedEntityRenderData(ItemStack stack) {
		CustomData component = CBItemData.getReadOnlyComponent(stack);
		if (component == null)
			return null;

		CaptureMetadata metadata = CAPTURED_METADATA.getUnchecked(component);
		if (metadata.entityId().isEmpty())
			return null;

		return new CapturedEntityRenderData(component, metadata.entityId(), metadata.prototype(),
			metadata.health().isPresent(), metadata.health().orElse(0.0f));
	}

	private static CaptureMetadata captureMetadata(ItemStack stack) {
		CustomData component = CBItemData.getReadOnlyComponent(stack);
		return component == null ? CaptureMetadata.EMPTY : CAPTURED_METADATA.getUnchecked(component);
	}

	static HashCode captureFingerprint(CapturedEntityRenderData renderData) {
		return CAPTURE_FINGERPRINTS.getUnchecked(renderData.component());
	}

	private static HashCode readCaptureFingerprint(CustomData component) {
		try {
			HashingOutputStream hash = new HashingOutputStream(Hashing.sha256(), OutputStream.nullOutputStream());
			DataOutputStream output = new DataOutputStream(hash);
			writeCanonicalTag(capturedEntityTag(component), output);
			CaptureMetadata metadata = CAPTURED_METADATA.getUnchecked(component);
			output.writeBoolean(metadata.health().isPresent());
			if (metadata.health().isPresent())
				output.writeFloat(metadata.health().get());
			return hash.hash();
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	/** Compound insertion order must not make equal captures miss their icon cache. */
	private static void writeCanonicalTag(Tag tag, DataOutputStream output) throws IOException {
		output.writeByte(tag.getId());
		if (tag instanceof CompoundTag compound) {
			List<String> keys = new ArrayList<>(compound.getAllKeys());
			keys.sort(String::compareTo);
			output.writeInt(keys.size());
			for (String key : keys) {
				output.writeUTF(key);
				writeCanonicalTag(compound.get(key), output);
			}
		} else if (tag instanceof ListTag list) {
			output.writeInt(list.size());
			for (Tag child : list)
				writeCanonicalTag(child, output);
		} else {
			tag.write(output);
		}
	}

	private static CaptureMetadata readCaptureMetadata(CustomData component) {
		CompoundTag entityData = capturedEntityTag(component);
		if (entityData == null)
			return CaptureMetadata.EMPTY;
		Optional<Float> health = component.read(CAPTURED_HEALTH_FIELD).result().orElse(Optional.empty());
		boolean prototype = entityData.size() == 1 && health.isEmpty()
			&& component.size() == (component.contains(CAPTURED_ENTITY_DESC_ID_TAG) ? 2 : 1);
		return new CaptureMetadata(true, entityData.getString("id"), prototype, health);
	}

	/** Internal read-only view; only entity loading copies it for mutation. */
	@Nullable
	private static CompoundTag capturedEntityTag(CustomData component) {
		Object value = component.read(CAPTURED_ENTITY_FIELD).result().map(Dynamic::getValue).orElse(null);
		return value instanceof CompoundTag tag ? tag : null;
	}

	private record CaptureMetadata(boolean captured, String entityId, boolean prototype, Optional<Float> health) {
		private static final CaptureMetadata EMPTY = new CaptureMetadata(false, "", false, Optional.empty());
	}

	static record CapturedEntityRenderData(CustomData component, String entityId, boolean prototype,
		boolean hasCapturedHealth, float capturedHealth) {}

	public static ItemStackHandler applyVirtualSelfFallbackContents(ItemStack box, ItemStackHandler contents) {
		if (!CapturedEntityBoxItem.isBox(box) || hasAnyPackageContents(contents))
			return contents;

		contents.setStackInSlot(0, (isEmptyBox(box) ? createEmptyBox(box) : box).copyWithCount(1));
		return contents;
	}

	public static ItemStackHandler getVisiblePackageContents(ItemStack box) {
		ItemStackHandler contents = new ItemStackHandler(PackageItem.SLOTS);
		if (PackageItem.isPackage(box)) {
			// Only stored cargo may be removed through the courier UI. The virtual
			// fallback also returns a new empty item for legacy boxes, but consuming
			// that output is safe only when unpacking consumes the outer box too.
			ItemHelper.fillItemStackHandler(
				box.getOrDefault(AllDataComponents.PACKAGE_CONTENTS, ItemContainerContents.EMPTY), contents);
		}
		return contents;
	}

	private static CompoundTag getCapturedEntityData(ItemStack stack) {
		CompoundTag tag = CBItemData.getReadOnly(stack);
		if (tag == null)
			return null;
		if (!tag.contains(CAPTURED_ENTITY_TAG, Tag.TAG_COMPOUND))
			return null;
		return tag.getCompound(CAPTURED_ENTITY_TAG);
	}

	private static boolean hasAnyPackageContents(ItemStackHandler contents) {
		for (int slot = 0; slot < contents.getSlots(); slot++)
			if (!contents.getStackInSlot(slot).isEmpty())
				return true;
		return false;
	}

	private static void appendAddressTooltip(ItemStack stack, List<Component> tooltipComponents) {
		String address = PackageItem.getAddress(stack);
		if (address.isBlank())
			return;

		tooltipComponents.add(Component.literal("\u2192 " + address)
			.withStyle(ChatFormatting.GOLD));
	}

	private static void appendContentsTooltip(ItemStack stack, HolderLookup.Provider registries,
		List<Component> tooltipComponents) {
		List<TooltipEntry> entries = new ArrayList<>();
		collectPackageEntries(stack, entries);
		collectCapturedEntityEntry(stack, registries, entries);

		int visibleNames = 0;
		int skippedNames = 0;
		for (TooltipEntry entry : entries) {
			if (visibleNames > 2) {
				skippedNames++;
				continue;
			}

			visibleNames++;
			tooltipComponents.add(entry.toTooltipLine());
		}

		if (skippedNames > 0)
			tooltipComponents.add(Component.translatable("container.shulkerBox.more", skippedNames)
				.withStyle(ChatFormatting.ITALIC));
	}

	private static void collectPackageEntries(ItemStack stack, List<TooltipEntry> entries) {
		ItemStackHandler contents = getVisiblePackageContents(stack);
		for (int slot = 0; slot < contents.getSlots(); slot++) {
			ItemStack contentStack = contents.getStackInSlot(slot);
			if (contentStack.isEmpty())
				continue;
			if (contentStack.getItem() instanceof SpawnEggItem)
				continue;

			entries.add(new TooltipEntry(contentStack.getHoverName(), contentStack.getCount()));
		}
	}

	private static void collectCapturedEntityEntry(ItemStack stack, HolderLookup.Provider registries,
		List<TooltipEntry> entries) {
		CompoundTag tag = CBItemData.getReadOnly(stack);
		if (tag == null)
			return;

		CompoundTag entityData = tag.getCompound(CAPTURED_ENTITY_TAG);
		if (entityData.contains("CustomName", Tag.TAG_STRING)) {
			try {
				Component customName = Component.Serializer.fromJson(entityData.getString("CustomName"), registries);
				if (customName != null) {
					entries.add(new TooltipEntry(customName, 1));
					return;
				}
			} catch (RuntimeException ignored) {
				// Damaged or legacy custom-name data falls back to the entity type below.
			}
		}

		if (!tag.contains(CAPTURED_ENTITY_DESC_ID_TAG, Tag.TAG_STRING))
			return;

		String entityDescId = tag.getString(CAPTURED_ENTITY_DESC_ID_TAG);
		if (entityDescId.isEmpty())
			return;

		entries.add(new TooltipEntry(Component.translatable(entityDescId), 1));
	}

	private record TooltipEntry(Component name, int count) {
		private Component toTooltipLine() {
			return name.copy()
				.append(" x")
				.append(String.valueOf(count))
				.withStyle(ChatFormatting.GRAY);
		}
	}
}
