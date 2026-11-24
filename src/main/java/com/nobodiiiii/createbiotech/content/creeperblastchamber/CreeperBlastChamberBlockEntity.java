package com.nobodiiiii.createbiotech.content.creeperblastchamber;

import net.minecraft.core.HolderLookup;



import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerBlockEntity;
import com.nobodiiiii.createbiotech.foundation.item.DeferredExtractionPreviewProvider;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.foundation.blockEntity.SyncedBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import org.slf4j.Logger;


public class CreeperBlastChamberBlockEntity extends SyncedBlockEntity implements IHaveGoggleInformation {
	static final Logger LOGGER = LogUtils.getLogger();

	static final String DATA_ROOT = CreateBiotech.MOD_ID;
	static final String MARKED_CREEPER_TAG = "CreeperBlastChamberMarked";
	static final String CONTROLLER_POS_TAG = "CreeperBlastChamberControllerPos";
	static final String PACKAGER_POS_TAG = "CreeperBlastChamberPackagerPos";
	public static final String PONDER_COMPRESSION_TAG = "PonderCompression";
	public static final String PONDER_COMPRESSION_ANIM_TAG = "PonderCompressionAnim";
	private static final String PENDING_UNPACKS_TAG = "PendingUnpacks";
	private static final String PENDING_PACKAGINGS_TAG = "PendingPackagings";
	private static final String PENDING_APPEARANCES_TAG = "PendingAppearances";
	private static final String READY_OUTPUTS_TAG = "ReadyOutputs";
	private static final String MARKED_CREEPERS_TAG = "MarkedCreepers";
	private static final String CONTAINED_DATA_VERSION_TAG = "ContainedCreeperDataVersion";
	private static final String STORED_CREEPERS_TAG = "StoredCreepers";
	static final int CONTAINED_DATA_VERSION = 2;
	static final int STRUCTURE_RECHECK_INTERVAL_TICKS = 20;
	private static final String INPUT_VAULT_CONTROLLER_TAG = "InputVaultController";
	private static final String OUTPUT_VAULT_CONTROLLER_TAG = "OutputVaultController";
	private static final String CONFIGURED_INPUT_VAULT_CONTROLLER_TAG = "ConfiguredInputVaultController";
	private static final String CREEPER_FACE_VISIBLE_TAG = "CreeperFaceVisible";
	private static final String OVERLOAD_POINTS_TAG = "OverloadPoints";
	static final int OUTPUT_REQUEST_KEEPALIVE_TICKS = 2;
	/** Visual transition lengths stay independent of the configurable bio-packager cycle. */
	static final int CREEPER_ENTRY_ANIMATION_TICKS = 10;
	static final int CREEPER_EXIT_ANIMATION_TICKS = 10;
	/** Equal window counts use this stable order; facing never depends on a random render seed. */
	static final Direction[] CREEPER_WINDOW_FACING_PRIORITY = {
		Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
	};
	static final int APPEARANCE_PUFF_COUNT = 6;
	static final double APPEARANCE_PUFF_RADIUS = .32d;
	static final int PRESSING_TRIGGER_TICKS = PressingBehaviour.CYCLE / 2;
	static final float CLIENT_PRESS_EFFECT_START_OFFSET = 0.4f;
	static final float CLIENT_RETURN_EFFECT_ARM_THRESHOLD = 0.95f;
	static final float CLIENT_PRESS_RETURN_EPSILON = 0.001f;
	static final double CLIENT_RETURN_EFFECT_Y_OFFSET = 2d;
	static final double CLIENT_RETURN_BASE_EXPLOSION_JITTER = 0.2d;
	static final int CLIENT_RETURN_EXTRA_EXPLOSION_MIN = 1;
	static final int CLIENT_RETURN_EXTRA_EXPLOSION_MAX = 9;
	static final double CLIENT_RETURN_EXTRA_EXPLOSION_RADIUS = 1d;
	static final double CLIENT_RETURN_EXPLOSION_SIZE_PARAM_MIN = 0.15d;
	static final double CLIENT_RETURN_EXPLOSION_SIZE_PARAM_MAX = 0.95d;
	static final ItemStackHandler CRUSHING_RECIPE_INVENTORY = new ItemStackHandler(1);
	static final RecipeWrapper CRUSHING_RECIPE_WRAPPER = new RecipeWrapper(CRUSHING_RECIPE_INVENTORY);
	static final ItemStackHandler HIGH_PRESSURE_RECIPE_INVENTORY = new ItemStackHandler(1);
	static final RecipeWrapper HIGH_PRESSURE_RECIPE_WRAPPER = new RecipeWrapper(HIGH_PRESSURE_RECIPE_INVENTORY);
	static final Map<Level, Set<BlockPos>> CLIENT_LOADED_CHAMBERS = new WeakHashMap<>();

	public LerpedFloat gauge = LerpedFloat.linear();
	public LerpedFloat displayGauge = LerpedFloat.linear();
	boolean structureValid;
	int structureSize;
	BlockPos structureOrigin;
	BlockPos bottomCenter;
	BlockPos inputVaultController;
	BlockPos outputVaultController;
	BlockPos configuredInputVaultController;
	Axis structurePressAxis;
	int overloadPoints;
	boolean pressCycleProcessed;
	boolean pausedByUnloadedChunks;
	int recheckTimer;
	final List<PendingUnpack> pendingUnpacks = new ArrayList<>();
	final List<PendingAppearance> pendingAppearances = new ArrayList<>();
	final List<PendingPackaging> pendingPackagings = new ArrayList<>();
	final List<ReadyOutput> readyOutputs = new ArrayList<>();
	final Map<BlockPos, StoredCreeper> storedCreepers = new LinkedHashMap<>();
	final Map<UUID, BlockPos> legacyMarkedCreepers = new LinkedHashMap<>();
	/** Stable geometry derived from the formed structure; rebuilt only when its origin or size changes. */
	final List<BlockPos> cachedPackagerPositions = new ArrayList<>();
	final List<BlockPos> cachedPressPositions = new ArrayList<>();
	final Map<Long, BlockPos> cachedPressByPackager = new HashMap<>();
	int containedDataVersion = CONTAINED_DATA_VERSION;
	boolean controllerOutputRequested;
	int controllerOutputRequestTicks;
	boolean creeperFaceVisible = true;
	final Map<Long, Float> clientPressOffsets = new HashMap<>();
	final Set<Long> clientReturnEffectsArmed = new HashSet<>();

	/** Reused by the render and client-tick paths so neither allocates a fresh list every frame. */
	final List<RenderManagedCreeper> workingRenderCreepers = new ArrayList<>();
	final List<RenderCreeperAnimation> renderAnimations = new ArrayList<>();
	final Map<Long, Float> clientNextPressOffsets = new HashMap<>();
	final Set<Long> clientActivePackagers = new HashSet<>();
	final Set<Long> clientAppearanceEffectsSpawned = new HashSet<>();
	private final ChamberInputHandler inputHandler = new ChamberInputHandler();

	public CreeperBlastChamberBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.CREEPER_BLAST_CHAMBER.get(), pos, state);
		gauge.startWithValue(0);
		displayGauge.startWithValue(0);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, CreeperBlastChamberBlockEntity be) {
		if (level.isClientSide) {
			float overloadTarget = be.structureValid ? be.getOverloadFraction() : 0f;
			be.gauge.chase(overloadTarget, 0.125f, Chaser.EXP);
			be.gauge.tickChaser();
			be.displayGauge.chase(overloadTarget, 0.125f, Chaser.EXP);
			be.displayGauge.tickChaser();
			ChamberClientEffects.tickClientAnimations(be);
			return;
		}

		if (ChamberStructure.updatePausedByUnloadedChunks(be)) {
			ChamberStructure.syncFormedBlockState(be);
			return;
		}

		ChamberContainment.migrateLegacyContainedCreepers(be);
		ChamberContainment.tickPendingUnpacks(be);
		ChamberContainment.tickPendingAppearances(be);
		ChamberContainment.tickPendingPackagings(be);
		ChamberContainment.tickControllerOutputRequest(be);
		ChamberContainment.tickReadyOutputs(be);
		ChamberProcessing.tickPressProcessing(be);
		ChamberProcessing.tickOverloadDecay(be, level);
		ChamberStructure.syncFormedBlockState(be);

		if (be.recheckTimer > 0) {
			be.recheckTimer--;
			return;
		}
		be.recheckTimer = STRUCTURE_RECHECK_INTERVAL_TICKS;

		ChamberStructure.tryDetectStructure(be);
	}

	public AABB getRenderBoundingBox() {
		if (structureValid && structureOrigin != null)
			return AABB.encapsulatingFullBlocks(structureOrigin,
				structureOrigin.offset(structureSize - 1, 3, structureSize - 1)).inflate(1);
		return new AABB(worldPosition).inflate(1);
	}

	public IItemHandler getItemCapability(@Nullable Direction side) {
		return inputHandler;
	}

	@Override
	public void onLoad() {
		super.onLoad();
		ChamberClientEffects.registerClientLoadedChamber(this);
	}

	@Override
	public void setRemoved() {
		ChamberClientEffects.unregisterClientLoadedChamber(this);
		super.setRemoved();
	}

	public void clearCurrentVaultRoleBindings() {
		ChamberStructure.clearCurrentVaultRoleBindings(this);
	}

	public void onControllerRemoved() {
		ChamberStructure.onControllerRemoved(this);
	}

	public void configureVaultRole(BlockPos vaultControllerPos, CreeperBlastChamberVaultRole role) {
		ChamberStructure.configureVaultRole(this, vaultControllerPos, role);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putBoolean("StructureValid", structureValid);
		tag.putInt("StructureSize", structureSize);
		ListTag pendingList = new ListTag();
		for (PendingUnpack pending : pendingUnpacks)
			pendingList.add(pending.write(registries));
		tag.put(PENDING_UNPACKS_TAG, pendingList);
		ListTag pendingPackagingList = new ListTag();
		for (PendingPackaging pending : pendingPackagings)
			pendingPackagingList.add(pending.write(registries));
		tag.put(PENDING_PACKAGINGS_TAG, pendingPackagingList);
		ListTag pendingAppearanceList = new ListTag();
		for (PendingAppearance pending : pendingAppearances)
			pendingAppearanceList.add(pending.write());
		tag.put(PENDING_APPEARANCES_TAG, pendingAppearanceList);
		ListTag readyOutputList = new ListTag();
		for (ReadyOutput readyOutput : readyOutputs)
			readyOutputList.add(readyOutput.write(registries));
		tag.put(READY_OUTPUTS_TAG, readyOutputList);
		tag.putInt(CONTAINED_DATA_VERSION_TAG, containedDataVersion);
		ListTag storedCreeperList = new ListTag();
		for (StoredCreeper stored : storedCreepers.values())
			storedCreeperList.add(stored.write(registries));
		tag.put(STORED_CREEPERS_TAG, storedCreeperList);
		ListTag markedCreeperList = new ListTag();
		for (Map.Entry<UUID, BlockPos> entry : legacyMarkedCreepers.entrySet())
			markedCreeperList.add(new TrackedMarkedCreeper(entry.getKey(), entry.getValue()).write());
		if (!markedCreeperList.isEmpty())
			tag.put(MARKED_CREEPERS_TAG, markedCreeperList);
		tag.putBoolean(CREEPER_FACE_VISIBLE_TAG, creeperFaceVisible);
		tag.putInt(OVERLOAD_POINTS_TAG, overloadPoints);
		if (structureOrigin != null) {
			tag.putInt("OriginX", structureOrigin.getX());
			tag.putInt("OriginY", structureOrigin.getY());
			tag.putInt("OriginZ", structureOrigin.getZ());
		}
		if (bottomCenter != null) {
			tag.putInt("BottomX", bottomCenter.getX());
			tag.putInt("BottomY", bottomCenter.getY());
			tag.putInt("BottomZ", bottomCenter.getZ());
		}
		if (inputVaultController != null)
			tag.putLong(INPUT_VAULT_CONTROLLER_TAG, inputVaultController.asLong());
		if (outputVaultController != null)
			tag.putLong(OUTPUT_VAULT_CONTROLLER_TAG, outputVaultController.asLong());
		if (configuredInputVaultController != null)
			tag.putLong(CONFIGURED_INPUT_VAULT_CONTROLLER_TAG, configuredInputVaultController.asLong());
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		boolean previousStructureValid = structureValid;
		int previousStructureSize = structureSize;
		BlockPos previousStructureOrigin = structureOrigin;
		super.loadAdditional(tag, registries);
		structureValid = tag.getBoolean("StructureValid");
		structureSize = tag.getInt("StructureSize");
		pausedByUnloadedChunks = false;
		pendingUnpacks.clear();
		for (Tag pendingTag : tag.getList(PENDING_UNPACKS_TAG, Tag.TAG_COMPOUND))
			pendingUnpacks.add(PendingUnpack.read((CompoundTag) pendingTag, registries));
		pendingPackagings.clear();
		for (Tag pendingTag : tag.getList(PENDING_PACKAGINGS_TAG, Tag.TAG_COMPOUND))
			pendingPackagings.add(PendingPackaging.read((CompoundTag) pendingTag, registries));
		pendingAppearances.clear();
		for (Tag pendingTag : tag.getList(PENDING_APPEARANCES_TAG, Tag.TAG_COMPOUND))
			pendingAppearances.add(PendingAppearance.read((CompoundTag) pendingTag));
		readyOutputs.clear();
		for (Tag readyOutputTag : tag.getList(READY_OUTPUTS_TAG, Tag.TAG_COMPOUND))
			readyOutputs.add(ReadyOutput.read((CompoundTag) readyOutputTag, registries));
		containedDataVersion = tag.contains(CONTAINED_DATA_VERSION_TAG, Tag.TAG_INT)
			? tag.getInt(CONTAINED_DATA_VERSION_TAG) : 1;
		storedCreepers.clear();
		for (Tag storedTag : tag.getList(STORED_CREEPERS_TAG, Tag.TAG_COMPOUND)) {
			StoredCreeper stored = StoredCreeper.read((CompoundTag) storedTag, registries);
			if (!stored.normalizedPayloadBox().isEmpty())
				storedCreepers.put(stored.packagerPos(), stored);
		}
		legacyMarkedCreepers.clear();
		for (Tag markedCreeperTag : tag.getList(MARKED_CREEPERS_TAG, Tag.TAG_COMPOUND)) {
			TrackedMarkedCreeper tracked = TrackedMarkedCreeper.read((CompoundTag) markedCreeperTag);
			legacyMarkedCreepers.put(tracked.creeperUuid, tracked.packagerPos);
		}
		if (containedDataVersion >= CONTAINED_DATA_VERSION)
			legacyMarkedCreepers.clear();
		creeperFaceVisible = !tag.contains(CREEPER_FACE_VISIBLE_TAG) || tag.getBoolean(CREEPER_FACE_VISIBLE_TAG);
		if (tag.contains("OriginX")) {
			structureOrigin = new BlockPos(
				tag.getInt("OriginX"), tag.getInt("OriginY"), tag.getInt("OriginZ"));
		} else {
			structureOrigin = null;
		}
		if (tag.contains("BottomX")) {
			bottomCenter = new BlockPos(
				tag.getInt("BottomX"), tag.getInt("BottomY"), tag.getInt("BottomZ"));
		} else {
			bottomCenter = null;
		}
		inputVaultController = tag.contains(INPUT_VAULT_CONTROLLER_TAG)
			? BlockPos.of(tag.getLong(INPUT_VAULT_CONTROLLER_TAG))
			: null;
		outputVaultController = tag.contains(OUTPUT_VAULT_CONTROLLER_TAG)
			? BlockPos.of(tag.getLong(OUTPUT_VAULT_CONTROLLER_TAG))
			: null;
		configuredInputVaultController = tag.contains(CONFIGURED_INPUT_VAULT_CONTROLLER_TAG)
			? BlockPos.of(tag.getLong(CONFIGURED_INPUT_VAULT_CONTROLLER_TAG))
			: inputVaultController;
		overloadPoints = Mth.clamp(tag.getInt(OVERLOAD_POINTS_TAG), 0, getOverloadPointsCap());
		structurePressAxis = structureValid ? getStoredPressAxis() : null;
		pressCycleProcessed = false;
		if (previousStructureValid != structureValid || previousStructureSize != structureSize
			|| !Objects.equals(previousStructureOrigin, structureOrigin))
			ChamberStructure.rebuildStructurePositionCache(this);
		ChamberClientEffects.invalidatePressRenderStates(this);
	}

	public void forceStructureCheck() {
		ChamberStructure.tryDetectStructure(this);
		recheckTimer = STRUCTURE_RECHECK_INTERVAL_TICKS;
	}

	public boolean isStructureValid() {
		return structureValid;
	}

	public int getStructureSize() {
		return structureSize;
	}

	@Nullable
	public BlockPos getStructureOrigin() {
		return structureOrigin;
	}

	@Nullable
	public BlockPos getBottomCenter() {
		return bottomCenter;
	}

	public boolean shouldRenderCreeperFace() {
		return structureValid && creeperFaceVisible;
	}

	float getRenderedPressHeadOffset(BlockPos packagerPos, float partialTicks) {
		return ChamberProcessing.getRenderedPressHeadOffset(this, packagerPos, partialTicks);
	}

	float getRenderedCreeperEffectPressOffset(BlockPos packagerPos, float partialTicks) {
		return ChamberProcessing.getRenderedCreeperEffectPressOffset(this, packagerPos, partialTicks);
	}

	float getWorkingCreeperCompression(BlockPos packagerPos, float partialTicks) {
		return ChamberProcessing.getWorkingCreeperCompression(this, packagerPos, partialTicks);
	}

	public static boolean shouldMutePressActivationSound(MechanicalPressBlockEntity press) {
		return ChamberStructure.shouldMutePressActivationSound(press);
	}

	@Nullable
	public static BlockPos findGoggleInformationSource(Level level, BlockPos structurePos) {
		return ChamberStructure.findGoggleInformationSource(level, structurePos);
	}

	@Nullable
	public static CreeperBlastChamberBlockEntity findStructureController(Level level, BlockPos structurePos) {
		return ChamberStructure.findStructureController(level, structurePos);
	}
	public static InteractionResult onStructureCasingWrenched(Level level, BlockPos clickedPos, @Nullable Player player) {
		return ChamberStructure.onStructureCasingWrenched(level, clickedPos, player);
	}

	public float getOverloadFraction() {
		return overloadPoints / (float) getOverloadPointsCap();
	}

	static int getMinSize() {
		return CBConfigs.SERVER.creeperBlastChamber.minSize.get();
	}

	static int getMaxSize() {
		return Math.max(getMinSize(), CBConfigs.SERVER.creeperBlastChamber.maxSize.get());
	}

	static int getOverloadThresholdRpm() {
		return CBConfigs.SERVER.creeperBlastChamber.overloadThresholdRpm.get();
	}

	static int getOverloadPointsCap() {
		return Math.max(1, CBConfigs.SERVER.creeperBlastChamber.overloadPointsCap.get());
	}

	static int getOverloadDecayPointsPerSecond() {
		return CBConfigs.SERVER.creeperBlastChamber.overloadDecayPointsPerSecond.get();
	}

	static int getOverloadTntEquivalentPerCreeper() {
		return CBConfigs.SERVER.creeperBlastChamber.overloadTntEquivalentPerCreeper.get();
	}

	private static int getChargedCreeperEquivalentMultiplier() {
		return CBConfigs.SERVER.creeperBlastChamber.chargedCreeperEquivalentMultiplier.get();
	}

	static float getTntExplosionPower() {
		return CBConfigs.SERVER.creeperBlastChamber.tntExplosionPower.get()
			.floatValue();
	}

	static int getReadyOutputTimeout() {
		return CBConfigs.SERVER.creeperBlastChamber.readyOutputTimeout.get();
	}

	static boolean isOverloadExplosionsEnabled() {
		return CBConfigs.SERVER.creeperBlastChamber.enableOverloadExplosions.get();
	}

	static boolean areExplosionParticlesEnabled() {
		return CBConfigs.CLIENT.creeperBlastChamber.enableExplosionParticles.get();
	}

	public int getOverloadPercent() {
		return Mth.clamp(Math.round(getOverloadFraction() * 100f), 0, 100);
	}

	private Component getStatusComponent() {
		return Component.translatable(getStatusTranslationKey())
			.withStyle(getStatusColor());
	}

	private String getStatusTranslationKey() {
		if (ChamberStructure.isPausedForPartialChunkUnload(this))
			return "create_biotech.creeper_blast_chamber.tooltip.status.chunk_unloaded";

		List<MechanicalPressBlockEntity> presses = ChamberProcessing.getMechanicalPresses(this);
		if (ChamberProcessing.hasUnworkablePresses(this, presses))
			return "create_biotech.creeper_blast_chamber.tooltip.status.blocked_press";

		if (ChamberProcessing.isCurrentlyOverloading(this))
			return "create_biotech.creeper_blast_chamber.tooltip.status.overloading";

		MechanicalPressBlockEntity masterPress = ChamberProcessing.getMasterPress(this, presses);
		if (masterPress == null || masterPress.getSpeed() == 0)
			return "create_biotech.creeper_blast_chamber.tooltip.status.insufficient_stress";

		return "create_biotech.creeper_blast_chamber.tooltip.status.working";
	}

	private ChatFormatting getStatusColor() {
		if (ChamberStructure.isPausedForPartialChunkUnload(this))
			return ChatFormatting.YELLOW;

		if (ChamberProcessing.hasUnworkablePresses(this, ChamberProcessing.getMechanicalPresses(this)))
			return ChatFormatting.RED;

		if (ChamberProcessing.isCurrentlyOverloading(this))
			return ChatFormatting.DARK_RED;

		MechanicalPressBlockEntity masterPress = ChamberProcessing.getMasterPress(this, ChamberProcessing.getMechanicalPresses(this));
		if (masterPress == null || masterPress.getSpeed() == 0)
			return ChatFormatting.GOLD;

		return ChatFormatting.GREEN;
	}

	private ChatFormatting getOverloadDisplayColor() {
		float overloadFraction = getOverloadFraction();
		if (overloadFraction >= 1f)
			return ChatFormatting.DARK_RED;
		if (overloadFraction >= 0.75f)
			return ChatFormatting.RED;
		if (overloadFraction >= 0.5f)
			return ChatFormatting.GOLD;
		if (overloadFraction >= 0.25f)
			return ChatFormatting.YELLOW;
		return ChatFormatting.GREEN;
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		if (!structureValid)
			return false;

		CreateLang.builder()
			.add(Component.translatable("create_biotech.creeper_blast_chamber.tooltip.status", getStatusComponent()))
			.forGoggles(tooltip);

		Component overloadPercent = Component.literal(getOverloadPercent() + "%")
			.withStyle(getOverloadDisplayColor());
		CreateLang.builder()
			.add(Component.translatable("create_biotech.creeper_blast_chamber.tooltip.overload", overloadPercent))
			.forGoggles(tooltip);
		return true;
	}

	@Nullable
	private Axis getStoredPressAxis() {
		if (!structureValid || structureOrigin == null || level == null)
			return null;

		BlockState pressState = level.getBlockState(structureOrigin.offset(structureSize / 2, 3, structureSize / 2));
		return AllBlocks.MECHANICAL_PRESS.has(pressState) ? ChamberStructure.getPressShaftAxis(pressState) : null;
	}

	boolean isPackagerReserved(BlockPos packagerPos) {
		for (PendingUnpack pending : pendingUnpacks) {
			if (pending.packagerPos.equals(packagerPos))
				return true;
		}
		return false;
	}

	AABB getMarkedCreeperSearchBounds() {
		if (structureOrigin == null)
			return new AABB(getBlockPos()).inflate(32);
		return AABB.encapsulatingFullBlocks(structureOrigin,
			structureOrigin.offset(structureSize - 1, 3, structureSize - 1)).inflate(32);
	}

	boolean isMarkedCreeperForThisChamber(Creeper creeper, @Nullable BlockPos packagerPos) {
		CompoundTag data = getExistingCreateBiotechData(creeper);
		if (data == null || !data.getBoolean(MARKED_CREEPER_TAG))
			return false;
		if (data.getLong(CONTROLLER_POS_TAG) != getBlockPos().asLong())
			return false;
		return packagerPos == null || data.getLong(PACKAGER_POS_TAG) == packagerPos.asLong();
	}

	boolean isPackagerAppearing(BlockPos packagerPos) {
		for (PendingAppearance pending : pendingAppearances) {
			if (pending.packagerPos.equals(packagerPos))
				return true;
		}
		return false;
	}

	@Nullable
	BioPackagerBlockEntity getPackager(BlockPos packagerPos) {
		Level level = getLevel();
		if (level == null || !level.getBlockState(packagerPos).is(CBBlocks.BIO_PACKAGER.get()))
			return null;
		BlockEntity blockEntity = level.getBlockEntity(packagerPos);
		return blockEntity instanceof BioPackagerBlockEntity packager ? packager : null;
	}

	/**
	 * Returns a list reused between calls, so consume it before asking for another. Both this and
	 * {@link #getRenderAnimations()} run every frame for every visible chamber.
	 */
	List<RenderManagedCreeper> getWorkingRenderCreepers() {
		return ChamberClientEffects.getWorkingRenderCreepers(this);
	}

	/** @see #getWorkingRenderCreepers() for the reuse contract. */
	List<RenderCreeperAnimation> getRenderAnimations() {
		return ChamberClientEffects.getRenderAnimations(this);
	}

	public static float getClientWorkingCreeperCompression(Creeper creeper, float partialTicks) {
		return ChamberClientEffects.getClientWorkingCreeperCompression(creeper, partialTicks);
	}

	public static float getSynchronizedPressHeadProgress(@Nullable MechanicalPressBlockEntity press, float partialTicks) {
		return ChamberClientEffects.getSynchronizedPressHeadProgress(press, partialTicks);
	}

	public static float getSynchronizedPressHeadOffset(@Nullable MechanicalPressBlockEntity press, float partialTicks) {
		return ChamberClientEffects.getSynchronizedPressHeadOffset(press, partialTicks);
	}

	static CompoundTag getExistingCreateBiotechData(Entity entity) {
		CompoundTag persistentData = entity.getPersistentData();
		return persistentData.contains(DATA_ROOT) ? persistentData.getCompound(DATA_ROOT) : null;
	}

	static class PendingUnpack {
		final BlockPos packagerPos;
		final ItemStack boxStack;
		int ticksRemaining;
		boolean transitioned;
		final boolean returnBox;

		PendingUnpack(BlockPos packagerPos, ItemStack boxStack, int ticksRemaining, boolean transitioned,
			boolean returnBox) {
			this.packagerPos = packagerPos;
			this.boxStack = boxStack;
			this.ticksRemaining = ticksRemaining;
			this.transitioned = transitioned;
			this.returnBox = returnBox;
		}

		private CompoundTag write(HolderLookup.Provider registries) {
			CompoundTag tag = new CompoundTag();
			tag.putLong("PackagerPos", packagerPos.asLong());
			tag.put("Box", boxStack.save(registries));
			tag.putInt("TicksRemaining", ticksRemaining);
			tag.putBoolean("Transitioned", transitioned);
			tag.putBoolean("ReturnBox", returnBox);
			return tag;
		}

		private static PendingUnpack read(CompoundTag tag, HolderLookup.Provider registries) {
			return new PendingUnpack(
				BlockPos.of(tag.getLong("PackagerPos")),
				ItemStack.parseOptional(registries, tag.getCompound("Box")),
				tag.getInt("TicksRemaining"),
				tag.getBoolean("Transitioned"),
				tag.getBoolean("ReturnBox"));
		}
	}

	abstract static class TimedAnimation {
		protected int ticksRemaining;
		protected final int totalTicks;

		private TimedAnimation(int ticksRemaining, int totalTicks) {
			this.ticksRemaining = ticksRemaining;
			this.totalTicks = totalTicks;
		}
	}

	static class PendingAppearance extends TimedAnimation {
		final BlockPos packagerPos;

		PendingAppearance(BlockPos packagerPos, int ticksRemaining) {
			super(ticksRemaining, CREEPER_ENTRY_ANIMATION_TICKS);
			this.packagerPos = packagerPos;
		}

		private CompoundTag write() {
			CompoundTag tag = new CompoundTag();
			tag.putLong("PackagerPos", packagerPos.asLong());
			tag.putInt("TicksRemaining", ticksRemaining);
			return tag;
		}

		private static PendingAppearance read(CompoundTag tag) {
			return new PendingAppearance(
				BlockPos.of(tag.getLong("PackagerPos")),
				tag.getInt("TicksRemaining"));
		}
	}

	static class PendingPackaging extends TimedAnimation {
		final BlockPos packagerPos;
		final ItemStack boxStack;

		PendingPackaging(BlockPos packagerPos, ItemStack boxStack, int ticksRemaining) {
			super(ticksRemaining, BioPackagerBlockEntity.getCycleTicks() * 2);
			this.packagerPos = packagerPos;
			this.boxStack = boxStack;
		}

		private CompoundTag write(HolderLookup.Provider registries) {
			CompoundTag tag = new CompoundTag();
			tag.putLong("PackagerPos", packagerPos.asLong());
			tag.put("Box", boxStack.save(registries));
			tag.putInt("TicksRemaining", ticksRemaining);
			return tag;
		}

		private static PendingPackaging read(CompoundTag tag, HolderLookup.Provider registries) {
			return new PendingPackaging(
				BlockPos.of(tag.getLong("PackagerPos")),
				ItemStack.parseOptional(registries, tag.getCompound("Box")),
				tag.getInt("TicksRemaining"));
		}
	}

	static class ReadyOutput {
		private final BlockPos packagerPos;
		final ItemStack boxStack;
		int ticksRemaining;

		ReadyOutput(BlockPos packagerPos, ItemStack boxStack, int ticksRemaining) {
			this.packagerPos = packagerPos;
			this.boxStack = boxStack;
			this.ticksRemaining = ticksRemaining;
		}

		private CompoundTag write(HolderLookup.Provider registries) {
			CompoundTag tag = new CompoundTag();
			tag.putLong("PackagerPos", packagerPos.asLong());
			tag.put("Box", boxStack.save(registries));
			tag.putInt("TicksRemaining", ticksRemaining);
			return tag;
		}

		private static ReadyOutput read(CompoundTag tag, HolderLookup.Provider registries) {
			return new ReadyOutput(
				BlockPos.of(tag.getLong("PackagerPos")),
				ItemStack.parseOptional(registries, tag.getCompound("Box")),
				tag.getInt("TicksRemaining"));
		}
	}

	enum ChamberCreeperKind {
		NONE,
		NORMAL,
		CHARGED,
		MIXED;

		ChamberCreeperKind merge(ChamberCreeperKind other) {
			if (other == NONE || other == this)
				return this;
			if (this == NONE)
				return other;
			return MIXED;
		}
	}

	record CreeperCountSummary(int normalCount, int chargedCount) {
		int totalCount() {
			return normalCount + chargedCount;
		}

		int getExplosionEquivalentCount() {
			return normalCount + chargedCount * getChargedCreeperEquivalentMultiplier();
		}
	}

	static class InsertResult {
		private final boolean accepted;
		private final ItemStack remainder;

		InsertResult(boolean accepted, ItemStack remainder) {
			this.accepted = accepted;
			this.remainder = remainder;
		}

		private boolean accepted() {
			return accepted;
		}

		private ItemStack remainder() {
			return remainder;
		}
	}

	enum ProcessingAttemptResult {
		PROCESSED,
		NO_INPUT,
		BLOCKED_OUTPUT
	}

	private class ChamberInputHandler implements IItemHandler, DeferredExtractionPreviewProvider {
		@Override
		public int getSlots() {
			return 1;
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			validateSlot(slot);
			ItemStack output = ChamberContainment.getActualControllerOutput(CreeperBlastChamberBlockEntity.this);
			return output == null ? ItemStack.EMPTY : output;
		}

		@Override
		public ItemStack getDeferredExtractionPreview(int slot) {
			validateSlot(slot);
			return ChamberContainment.getDeferredControllerOutputPreview(CreeperBlastChamberBlockEntity.this);
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			validateSlot(slot);
			if (stack.isEmpty())
				return ItemStack.EMPTY;

			InsertResult result = ChamberContainment.tryInsertLargeCreeperBox(CreeperBlastChamberBlockEntity.this, stack, simulate, true);
			return result.accepted() ? result.remainder() : stack;
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			validateSlot(slot);
			if (amount <= 0)
				return ItemStack.EMPTY;

			ChamberContainment.requestControllerOutput(CreeperBlastChamberBlockEntity.this);
			ItemStack extracted = ChamberContainment.extractReadyOutput(CreeperBlastChamberBlockEntity.this, simulate);
			if (extracted != null)
				return extracted;

			if (!simulate)
				ChamberContainment.tickControllerOutputRequest(CreeperBlastChamberBlockEntity.this);
			return ItemStack.EMPTY;
		}

		@Override
		public int getSlotLimit(int slot) {
			validateSlot(slot);
			return 1;
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			validateSlot(slot);
			return ChamberContainment.isValidLargeCreeperBox(CreeperBlastChamberBlockEntity.this, stack);
		}

		private void validateSlot(int slot) {
			if (slot != 0)
				throw new RuntimeException("Slot " + slot + " not in valid range - [0,1)");
		}
	}

	record RenderCreeperAnimation(BlockPos packagerPos, ItemStack payload, long renderSeed, float defaultYaw,
		int ticksRemaining, int totalTicks, boolean exiting) {}

	record RenderManagedCreeper(BlockPos packagerPos, ItemStack payload, long renderSeed, float defaultYaw) {}

	private record TrackedMarkedCreeper(UUID creeperUuid, BlockPos packagerPos) {
		private CompoundTag write() {
			CompoundTag tag = new CompoundTag();
			tag.putUUID("CreeperUuid", creeperUuid);
			tag.putLong("PackagerPos", packagerPos.asLong());
			return tag;
		}

		private static TrackedMarkedCreeper read(CompoundTag tag) {
			return new TrackedMarkedCreeper(tag.getUUID("CreeperUuid"), BlockPos.of(tag.getLong("PackagerPos")));
		}
	}


	record StoredCreeper(BlockPos packagerPos, ItemStack normalizedPayloadBox, boolean charged,
		long renderSeed, float defaultYaw) {
		private CompoundTag write(HolderLookup.Provider registries) {
			CompoundTag tag = new CompoundTag();
			tag.putLong("PackagerPos", packagerPos.asLong());
			tag.put("Payload", normalizedPayloadBox.save(registries));
			tag.putBoolean("Charged", charged);
			tag.putLong("RenderSeed", renderSeed);
			tag.putFloat("DefaultYaw", defaultYaw);
			return tag;
		}

		private static StoredCreeper read(CompoundTag tag, HolderLookup.Provider registries) {
			float defaultYaw = tag.contains("DefaultYaw", Tag.TAG_FLOAT)
				? tag.getFloat("DefaultYaw") : Direction.NORTH.toYRot();
			if (!Float.isFinite(defaultYaw))
				defaultYaw = Direction.NORTH.toYRot();
			return new StoredCreeper(BlockPos.of(tag.getLong("PackagerPos")),
				ItemStack.parseOptional(registries, tag.getCompound("Payload")), tag.getBoolean("Charged"),
				tag.getLong("RenderSeed"), Mth.wrapDegrees(defaultYaw));
		}
	}

	record StoredCreeperTarget(BlockPos packagerPos, StoredCreeper stored) {}

	record VaultRoleAssignment(BlockPos inputVaultController, BlockPos outputVaultController) {}

	record StructureScanResult(int size, BlockPos origin, BlockPos inputVaultController,
		BlockPos outputVaultController) {}
}
