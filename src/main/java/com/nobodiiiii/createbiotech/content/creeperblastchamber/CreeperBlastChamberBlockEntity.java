package com.nobodiiiii.createbiotech.content.creeperblastchamber;

import net.minecraft.core.HolderLookup;

import net.minecraft.core.registries.Registries;

import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.client.ClientParticleBudget;
import com.nobodiiiii.createbiotech.client.CreeperBlastChamberClientSoundHandler;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerBlockEntity;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.explosionproofitemvault.ExplosionProofItemVaultBlock;
import com.nobodiiiii.createbiotech.content.explosionproofitemvault.ExplosionProofItemVaultBlockEntity;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.foundation.item.CBItemData;
import com.nobodiiiii.createbiotech.foundation.item.DeferredExtractionPreviewProvider;
import com.nobodiiiii.createbiotech.mixin.MobAccessor;
import com.nobodiiiii.createbiotech.network.ContainedEntityHandoffPacket;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.registry.CBRecipeTypes;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.chainDrive.ChainDriveBlock;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.foundation.blockEntity.SyncedBlockEntity;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import net.createmod.catnip.data.Iterate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import org.slf4j.Logger;


public class CreeperBlastChamberBlockEntity extends SyncedBlockEntity implements IHaveGoggleInformation {
	private static final Logger LOGGER = LogUtils.getLogger();

	private static final String DATA_ROOT = CreateBiotech.MOD_ID;
	private static final String MARKED_CREEPER_TAG = "CreeperBlastChamberMarked";
	private static final String CONTROLLER_POS_TAG = "CreeperBlastChamberControllerPos";
	private static final String PACKAGER_POS_TAG = "CreeperBlastChamberPackagerPos";
	public static final String PONDER_COMPRESSION_TAG = "PonderCompression";
	public static final String PONDER_COMPRESSION_ANIM_TAG = "PonderCompressionAnim";
	private static final String PENDING_UNPACKS_TAG = "PendingUnpacks";
	private static final String PENDING_PACKAGINGS_TAG = "PendingPackagings";
	private static final String PENDING_APPEARANCES_TAG = "PendingAppearances";
	private static final String READY_OUTPUTS_TAG = "ReadyOutputs";
	private static final String MARKED_CREEPERS_TAG = "MarkedCreepers";
	private static final String CONTAINED_DATA_VERSION_TAG = "ContainedCreeperDataVersion";
	private static final String STORED_CREEPERS_TAG = "StoredCreepers";
	private static final int CONTAINED_DATA_VERSION = 2;
	private static final int STRUCTURE_RECHECK_INTERVAL_TICKS = 20;
	private static final String INPUT_VAULT_CONTROLLER_TAG = "InputVaultController";
	private static final String OUTPUT_VAULT_CONTROLLER_TAG = "OutputVaultController";
	private static final String CONFIGURED_INPUT_VAULT_CONTROLLER_TAG = "ConfiguredInputVaultController";
	private static final String CREEPER_FACE_VISIBLE_TAG = "CreeperFaceVisible";
	private static final String OVERLOAD_POINTS_TAG = "OverloadPoints";
	private static final int OUTPUT_REQUEST_KEEPALIVE_TICKS = 2;
	/**
	 * Length of the pop-in a creeper plays on arrival. Deliberately independent of the packager
	 * cycle that drives the outward animation: this one only has to sell the appearance.
	 */
	private static final int CREEPER_ENTRY_ANIMATION_TICKS = 8;
	/** Equal window counts use this stable order; facing never depends on a random render seed. */
	private static final Direction[] CREEPER_WINDOW_FACING_PRIORITY = {
		Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
	};
	private static final int APPEARANCE_PUFF_COUNT = 6;
	private static final double APPEARANCE_PUFF_RADIUS = .32d;
	private static final int PRESSING_TRIGGER_TICKS = PressingBehaviour.CYCLE / 2;
	private static final float CLIENT_PRESS_EFFECT_START_OFFSET = 0.4f;
	private static final float CLIENT_RETURN_EFFECT_ARM_THRESHOLD = 0.95f;
	private static final float CLIENT_PRESS_RETURN_EPSILON = 0.001f;
	private static final double CLIENT_RETURN_EFFECT_Y_OFFSET = 2d;
	private static final double CLIENT_RETURN_BASE_EXPLOSION_JITTER = 0.2d;
	private static final int CLIENT_RETURN_EXTRA_EXPLOSION_MIN = 1;
	private static final int CLIENT_RETURN_EXTRA_EXPLOSION_MAX = 9;
	private static final double CLIENT_RETURN_EXTRA_EXPLOSION_RADIUS = 1d;
	private static final double CLIENT_RETURN_EXPLOSION_SIZE_PARAM_MIN = 0.15d;
	private static final double CLIENT_RETURN_EXPLOSION_SIZE_PARAM_MAX = 0.95d;
	private static final ItemStackHandler CRUSHING_RECIPE_INVENTORY = new ItemStackHandler(1);
	private static final RecipeWrapper CRUSHING_RECIPE_WRAPPER = new RecipeWrapper(CRUSHING_RECIPE_INVENTORY);
	private static final ItemStackHandler HIGH_PRESSURE_RECIPE_INVENTORY = new ItemStackHandler(1);
	private static final RecipeWrapper HIGH_PRESSURE_RECIPE_WRAPPER = new RecipeWrapper(HIGH_PRESSURE_RECIPE_INVENTORY);
	private static final Map<Long, BlockPos> CLIENT_PRESS_CONTROLLERS = new HashMap<>();
	@Nullable
	private static ResourceKey<Level> clientPressControllerDimension;
	private static final Map<Level, Set<BlockPos>> CLIENT_LOADED_CHAMBERS = new WeakHashMap<>();

	public LerpedFloat gauge = LerpedFloat.linear();
	public LerpedFloat displayGauge = LerpedFloat.linear();
	private boolean structureValid;
	private int structureSize;
	private BlockPos structureOrigin;
	private BlockPos bottomCenter;
	private BlockPos inputVaultController;
	private BlockPos outputVaultController;
	private BlockPos configuredInputVaultController;
	private Axis structurePressAxis;
	private int overloadPoints;
	private boolean pressCycleProcessed;
	private boolean pausedByUnloadedChunks;
	private int recheckTimer;
	private final List<PendingUnpack> pendingUnpacks = new ArrayList<>();
	private final List<PendingAppearance> pendingAppearances = new ArrayList<>();
	private final List<PendingPackaging> pendingPackagings = new ArrayList<>();
	private final List<ReadyOutput> readyOutputs = new ArrayList<>();
	private final Map<BlockPos, StoredCreeper> storedCreepers = new LinkedHashMap<>();
	private final Map<UUID, BlockPos> legacyMarkedCreepers = new LinkedHashMap<>();
	/** Stable geometry derived from the formed structure; rebuilt only when its origin or size changes. */
	private final List<BlockPos> cachedPackagerPositions = new ArrayList<>();
	private final List<BlockPos> cachedPressPositions = new ArrayList<>();
	private final Map<Long, BlockPos> cachedPressByPackager = new HashMap<>();
	/** Reused view of the currently loaded press block entities at {@link #cachedPressPositions}. */
	private final List<MechanicalPressBlockEntity> resolvedMechanicalPresses = new ArrayList<>();
	private int containedDataVersion = CONTAINED_DATA_VERSION;
	private boolean controllerOutputRequested;
	private int controllerOutputRequestTicks;
	private boolean creeperFaceVisible = true;
	private final Map<Long, Float> clientPressOffsets = new HashMap<>();
	private final Set<Long> clientReturnEffectsArmed = new HashSet<>();
	private final Set<Long> clientTrackedPressPositions = new HashSet<>();

	/**
	 * Per-tick memo of the press that drives every synchronized animation in this chamber. Both the
	 * contained-creeper renderer and every press head in the structure ask for it once per frame, and
	 * resolving it from scratch rescans the whole press layer each time.
	 */
	private int renderPressStateTick = Integer.MIN_VALUE;
	@Nullable
	private MechanicalPressBlockEntity renderMasterPress;
	private boolean renderPressesUnworkable;

	/** Reused by the render and client-tick paths so neither allocates a fresh list every frame. */
	private final List<RenderManagedCreeper> workingRenderCreepers = new ArrayList<>();
	private final List<RenderCreeperAnimation> renderAnimations = new ArrayList<>();
	private final Map<Long, Float> clientNextPressOffsets = new HashMap<>();
	private final Set<Long> clientActivePackagers = new HashSet<>();
	private final Set<Long> clientActivePressPositions = new HashSet<>();
	private final Set<Long> clientAppearanceEffectsSpawned = new HashSet<>();
	private boolean clientPressLayoutDirty;
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
			be.tickClientAnimations();
			return;
		}

		if (be.updatePausedByUnloadedChunks()) {
			be.syncFormedBlockState();
			return;
		}

		be.migrateLegacyContainedCreepers();
		be.tickPendingUnpacks();
		be.tickPendingAppearances();
		be.tickPendingPackagings();
		be.tickControllerOutputRequest();
		be.tickReadyOutputs();
		be.tickPressProcessing();
		be.tickOverloadDecay(level);
		be.syncFormedBlockState();

		if (be.recheckTimer > 0) {
			be.recheckTimer--;
			return;
		}
		be.recheckTimer = STRUCTURE_RECHECK_INTERVAL_TICKS;

		be.tryDetectStructure();
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
		registerClientLoadedChamber();
	}

	@Override
	public void setRemoved() {
		unregisterClientLoadedChamber();
		clearClientTrackedPresses();
		super.setRemoved();
	}

	private void registerClientLoadedChamber() {
		Level level = getLevel();
		if (level == null || !level.isClientSide)
			return;
		CLIENT_LOADED_CHAMBERS.computeIfAbsent(level, ignored -> new HashSet<>())
			.add(getBlockPos().immutable());
	}

	private void unregisterClientLoadedChamber() {
		Level level = getLevel();
		if (level == null || !level.isClientSide)
			return;
		Set<BlockPos> chambers = CLIENT_LOADED_CHAMBERS.get(level);
		if (chambers == null)
			return;
		chambers.remove(getBlockPos());
		if (chambers.isEmpty())
			CLIENT_LOADED_CHAMBERS.remove(level);
	}

	private void tryDetectStructure() {
		Level level = getLevel();
		if (level == null)
			return;
		BlockPos pos = getBlockPos();
		if (structureValid && structureOrigin != null) {
			StructureScanResult current = scanStructure(level, structureOrigin, structureSize);
			if (current != null) {
				applyDetectedStructure(level, current);
				return;
			}
		}

		for (int s = getMinSize(); s <= getMaxSize(); s++) {
			StructureScanResult result = findStructure(level, pos, s);
			if (result != null) {
				applyDetectedStructure(level, result);
				return;
			}
		}
		boolean wasValid = structureValid;
		int oldSize = structureSize;
		BlockPos oldOrigin = structureOrigin;
		Axis oldPressAxis = structurePressAxis;
		setStructure(level, false, 0, null, null, null);
		if (wasValid)
			onStructureBroken(level, oldSize, oldOrigin, oldPressAxis);
	}

	private void applyDetectedStructure(Level level, StructureScanResult result) {
		boolean wasValid = structureValid;
		int oldSize = structureSize;
		BlockPos oldOrigin = structureOrigin;
		BlockPos oldInputVault = inputVaultController;
		BlockPos oldOutputVault = outputVaultController;
		Axis oldPressAxis = structurePressAxis;
		setStructure(level, true, result.size, result.origin, result.inputVaultController,
			result.outputVaultController);
		if (!wasValid || oldSize != result.size || !Objects.equals(result.origin, oldOrigin)
			|| !Objects.equals(inputVaultController, oldInputVault)
			|| !Objects.equals(outputVaultController, oldOutputVault) || structurePressAxis != oldPressAxis)
			onStructureFormed(level, result.size, result.origin);
	}

	@Nullable
	private StructureScanResult findStructure(Level level, BlockPos controllerPos, int size) {
		for (int dx = 0; dx < size; dx++) {
			for (int dz = 0; dz < size; dz++) {
				if (dx > 0 && dx < size - 1 && dz > 0 && dz < size - 1)
					continue;

				BlockPos origin = controllerPos.offset(-dx, 0, -dz);
				StructureScanResult result = scanStructure(level, origin, size);
				if (result != null)
					return result;
			}
		}
		return null;
	}

	@Nullable
	private StructureScanResult scanStructure(Level level, BlockPos origin, int size) {
		int innerMin = 1;
		int innerMax = size - 2;
		BlockState centerPressState = level.getBlockState(origin.offset(size / 2, 3, size / 2));

		if (!AllBlocks.MECHANICAL_PRESS.has(centerPressState))
			return null;
		Axis pressShaftAxis = getPressShaftAxis(centerPressState);
		Map<Long, BlockPos> vaultControllers = new LinkedHashMap<>();
		int controllerCount = 0;
		int remainingSpecialSlots = 14 * size - 12;

		for (int y = 0; y < 4; y++) {
			for (int x = 0; x < size; x++) {
				for (int z = 0; z < size; z++) {
					BlockPos pos = origin.offset(x, y, z);
					BlockState state = level.getBlockState(pos);
					boolean isInnerX = (x >= innerMin && x <= innerMax);
					boolean isInnerZ = (z >= innerMin && z <= innerMax);
					boolean isCenter = isInnerX && isInnerZ;

					if (isCenter && y == 3) {
						if (!isMechanicalPressOnAxis(state, pressShaftAxis))
							return null;
					} else if (isCenter && (y == 1 || y == 2)) {
						if (!state.isAir())
							return null;
					} else if (isCenter && y == 0) {
						if (!state.is(CBBlocks.BIO_PACKAGER.get()))
							return null;
					} else {
						boolean isController = state.getBlock() instanceof CreeperBlastChamberBlock;
						boolean isCasing = state.is(CBBlocks.EXPLOSION_PROOF_CASING.get());
						boolean isChainDrive = isBlastProofChainDriveOnAxis(state, pressShaftAxis);
						boolean isVault = ExplosionProofItemVaultBlock.isVault(state);
						boolean isVerticalEdge = !isInnerX && !isInnerZ;
						boolean isTopOrBottom = (y == 0 || y == 3);
						boolean chainDriveReserved = isReservedChainDrivePosition(x, y, z, size, pressShaftAxis);

						if (!chainDriveReserved)
							remainingSpecialSlots--;

						if (isController) {
							controllerCount++;
							if (controllerCount > 1)
								return null;
						}

						if (isVault) {
							if (chainDriveReserved)
								return null;
							ExplosionProofItemVaultBlockEntity controller = resolveVaultController(level, pos);
							if (controller == null || !isWholeVaultInsideStructure(level, controller, origin, size))
								return null;
							vaultControllers.putIfAbsent(controller.getBlockPos().asLong(), controller.getBlockPos());
							if (vaultControllers.size() > 2)
								return null;
							if (controllerCount + vaultControllers.size() + remainingSpecialSlots < 3)
								return null;
							continue;
						}

						if (isController && chainDriveReserved)
							return null;

						if (isChainDrive && !chainDriveReserved)
							return null;

						if (isTopOrBottom || isVerticalEdge) {
							if (!isController && !isCasing && !isChainDrive)
								return null;
							if (controllerCount + vaultControllers.size() + remainingSpecialSlots < 3)
								return null;
							continue;
						}

						boolean isGlass = state.is(CBBlocks.BLAST_PROOF_GLASS.get())
							|| state.is(CBBlocks.BLAST_PROOF_FRAMED_GLASS.get());
						if (!isController && !isCasing && !isGlass)
							return null;

						if (controllerCount + vaultControllers.size() + remainingSpecialSlots < 3)
							return null;
					}
				}
			}
		}

		if (controllerCount != 1)
			return null;

		if (vaultControllers.size() != 2)
			return null;

		List<BlockPos> orderedControllers = new ArrayList<>(vaultControllers.values());
		return new StructureScanResult(size, origin, orderedControllers.get(0), orderedControllers.get(1));
	}

	private static Axis getPressShaftAxis(BlockState state) {
		return state.getValue(BlockStateProperties.HORIZONTAL_FACING)
			.getAxis();
	}

	private static boolean isMechanicalPressOnAxis(BlockState state, Axis axis) {
		return AllBlocks.MECHANICAL_PRESS.has(state) && getPressShaftAxis(state) == axis;
	}

	private static boolean isBlastProofChainDriveOnAxis(BlockState state, Axis axis) {
		return state.is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get())
			&& state.hasProperty(BlockStateProperties.AXIS)
			&& state.getValue(BlockStateProperties.AXIS) == axis;
	}

	private boolean isReservedChainDrivePosition(int x, int y, int z, int size, Axis pressShaftAxis) {
		if (y != 3)
			return false;
		if (pressShaftAxis == Axis.X)
			return (x == 0 || x == size - 1) && z >= 1 && z < size - 1;
		return (z == 0 || z == size - 1) && x >= 1 && x < size - 1;
	}

	@Nullable
	private ExplosionProofItemVaultBlockEntity resolveVaultController(Level level, BlockPos pos) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (!(blockEntity instanceof ExplosionProofItemVaultBlockEntity vault))
			return null;
		if (vault.isController())
			return vault;
		return vault.getControllerBE() instanceof ExplosionProofItemVaultBlockEntity controller ? controller : null;
	}

	private boolean isWholeVaultInsideStructure(Level level, ExplosionProofItemVaultBlockEntity controller, BlockPos origin,
		int size) {
		BlockPos controllerPos = controller.getBlockPos();
		Axis axis = controller.getMainConnectionAxis();
		int width = controller.getWidth();
		int height = controller.getHeight();

		for (int yOffset = 0; yOffset < height; yOffset++) {
			for (int xOffset = 0; xOffset < width; xOffset++) {
				for (int zOffset = 0; zOffset < width; zOffset++) {
					BlockPos vaultPos = axis == Axis.Z ? controllerPos.offset(xOffset, zOffset, yOffset)
						: controllerPos.offset(yOffset, xOffset, zOffset);
					if (!isWithinStructureVolume(vaultPos, origin, size))
						return false;
					if (!ExplosionProofItemVaultBlock.isVault(level.getBlockState(vaultPos)))
						return false;
					ExplosionProofItemVaultBlockEntity vaultPart =
						ConnectivityHandler.partAt(CBBlockEntityTypes.EXPLOSION_PROOF_ITEM_VAULT.get(), level, vaultPos);
					if (vaultPart == null)
						return false;
					if (!(vaultPart.getControllerBE() instanceof ExplosionProofItemVaultBlockEntity partController)
						|| !controllerPos.equals(partController.getBlockPos()))
						return false;
				}
			}
		}
		return true;
	}

	private static boolean isWithinStructureVolume(BlockPos pos, BlockPos origin, int size) {
		return pos.getX() >= origin.getX() && pos.getX() < origin.getX() + size
			&& pos.getY() >= origin.getY() && pos.getY() < origin.getY() + 4
			&& pos.getZ() >= origin.getZ() && pos.getZ() < origin.getZ() + size;
	}

	private void setStructure(Level level, boolean valid, int size, @Nullable BlockPos origin,
		@Nullable BlockPos firstVaultController, @Nullable BlockPos secondVaultController) {
		boolean previousValid = structureValid;
		int previousSize = structureSize;
		BlockPos previousOrigin = structureOrigin;
		BlockPos previousInput = inputVaultController;
		BlockPos previousOutput = outputVaultController;
		BlockPos previousConfiguredInput = configuredInputVaultController;
		Axis previousPressAxis = structurePressAxis;
		int previousOverloadPoints = overloadPoints;
		structureValid = valid;
		structureSize = size;
		structureOrigin = origin;
		if (valid && firstVaultController != null && secondVaultController != null) {
			VaultRoleAssignment assignment = resolveVaultRoleAssignment(firstVaultController, secondVaultController);
			inputVaultController = assignment.inputVaultController();
			outputVaultController = assignment.outputVaultController();
			configuredInputVaultController = inputVaultController;
			BlockState pressState = level.getBlockState(origin.offset(size / 2, 3, size / 2));
			structurePressAxis = AllBlocks.MECHANICAL_PRESS.has(pressState) ? getPressShaftAxis(pressState) : null;
		} else {
			inputVaultController = null;
			outputVaultController = null;
			structurePressAxis = null;
			overloadPoints = 0;
		}
		bottomCenter = valid && origin != null
			? origin.offset(size / 2, 0, size / 2)
			: null;
		pausedByUnloadedChunks = valid && isStructureAreaPartiallyUnloaded(level, origin, size);
		if (!valid)
			pressCycleProcessed = false;
		boolean geometryChanged = previousValid != structureValid || previousSize != structureSize
			|| !Objects.equals(previousOrigin, structureOrigin);
		if (geometryChanged)
			rebuildStructurePositionCache();
		syncFormedBlockState();
		boolean vaultBindingsChanged = previousValid != structureValid
			|| !Objects.equals(previousInput, inputVaultController)
			|| !Objects.equals(previousOutput, outputVaultController);
		if (vaultBindingsChanged)
			syncVaultRoleBindings(level, previousInput, previousOutput);
		boolean stateChanged = geometryChanged || vaultBindingsChanged
			|| !Objects.equals(previousConfiguredInput, configuredInputVaultController)
			|| previousPressAxis != structurePressAxis || previousOverloadPoints != overloadPoints;
		if (stateChanged)
			notifyUpdate();
	}

	private void rebuildStructurePositionCache() {
		cachedPackagerPositions.clear();
		cachedPressPositions.clear();
		cachedPressByPackager.clear();
		resolvedMechanicalPresses.clear();
		renderPressStateTick = Integer.MIN_VALUE;
		renderMasterPress = null;
		renderPressesUnworkable = false;
		clientPressLayoutDirty = true;
		if (!structureValid || structureOrigin == null || structureSize <= 2)
			return;

		for (int x = 1; x < structureSize - 1; x++) {
			for (int z = 1; z < structureSize - 1; z++) {
				BlockPos packagerPos = structureOrigin.offset(x, 0, z);
				BlockPos pressPos = structureOrigin.offset(x, 3, z);
				cachedPackagerPositions.add(packagerPos);
				cachedPressPositions.add(pressPos);
				cachedPressByPackager.put(packagerPos.asLong(), pressPos);
			}
		}
	}

	private boolean updatePausedByUnloadedChunks() {
		Level level = getLevel();
		boolean paused = level != null
			&& !level.isClientSide
			&& structureValid
			&& structureOrigin != null
			&& isStructureAreaPartiallyUnloaded(level, structureOrigin, structureSize);
		if (pausedByUnloadedChunks == paused)
			return paused;
		pausedByUnloadedChunks = paused;
		setChanged();
		notifyUpdate();
		return paused;
	}

	private boolean isStructureAreaPartiallyUnloaded(Level level, @Nullable BlockPos origin, int size) {
		if (origin == null || size <= 0)
			return false;

		int minChunkX = origin.getX() >> 4;
		int maxChunkX = (origin.getX() + size - 1) >> 4;
		int minChunkZ = origin.getZ() >> 4;
		int maxChunkZ = (origin.getZ() + size - 1) >> 4;
		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				if (!level.hasChunk(chunkX, chunkZ))
					return true;
			}
		}
		return false;
	}

	private boolean isPausedForPartialChunkUnload() {
		return structureValid && pausedByUnloadedChunks;
	}

	private void syncFormedBlockState() {
		if (level == null || level.isClientSide)
			return;
		BlockState state = getBlockState();
		if (!state.hasProperty(CreeperBlastChamberBlock.FORMED))
			return;
		boolean formed = structureValid;
		if (state.getValue(CreeperBlastChamberBlock.FORMED) == formed)
			return;
		level.setBlock(worldPosition, state.setValue(CreeperBlastChamberBlock.FORMED, formed), 3);
	}

	private VaultRoleAssignment resolveVaultRoleAssignment(BlockPos firstVaultController, BlockPos secondVaultController) {
		if (configuredInputVaultController != null) {
			if (configuredInputVaultController.equals(firstVaultController))
				return new VaultRoleAssignment(firstVaultController, secondVaultController);
			if (configuredInputVaultController.equals(secondVaultController))
				return new VaultRoleAssignment(secondVaultController, firstVaultController);
		}

		if (inputVaultController != null) {
			if (inputVaultController.equals(firstVaultController))
				return new VaultRoleAssignment(firstVaultController, secondVaultController);
			if (inputVaultController.equals(secondVaultController))
				return new VaultRoleAssignment(secondVaultController, firstVaultController);
		}

		return new VaultRoleAssignment(firstVaultController, secondVaultController);
	}

	private void syncVaultRoleBindings(Level level, @Nullable BlockPos previousInput, @Nullable BlockPos previousOutput) {
		if (level.isClientSide)
			return;

		if (previousInput != null && (!previousInput.equals(inputVaultController) || !structureValid))
			setVaultRoleBinding(previousInput, null);
		if (previousOutput != null && (!previousOutput.equals(outputVaultController) || !structureValid))
			setVaultRoleBinding(previousOutput, null);

		if (inputVaultController != null)
			setVaultRoleBinding(inputVaultController, CreeperBlastChamberVaultRole.INPUT);
		if (outputVaultController != null)
			setVaultRoleBinding(outputVaultController, CreeperBlastChamberVaultRole.OUTPUT);
	}

	public void clearCurrentVaultRoleBindings() {
		Level level = getLevel();
		if (level == null || level.isClientSide)
			return;
		setVaultRoleBinding(inputVaultController, null);
		setVaultRoleBinding(outputVaultController, null);
	}

	public void onControllerRemoved() {
		Level level = getLevel();
		if (level == null || level.isClientSide || !structureValid || structureOrigin == null)
			return;
		onStructureBroken(level, structureSize, structureOrigin, structurePressAxis);
	}

	private void setVaultRoleBinding(@Nullable BlockPos controllerPos, @Nullable CreeperBlastChamberVaultRole role) {
		if (controllerPos == null || level == null)
			return;
		BlockEntity blockEntity = level.getBlockEntity(controllerPos);
		if (!(blockEntity instanceof ExplosionProofItemVaultBlockEntity vault))
			return;
		if (role == null) {
			vault.clearBlastChamberBinding(getBlockPos());
			return;
		}
		vault.bindToBlastChamber(getBlockPos(), role);
	}

	public void configureVaultRole(BlockPos vaultControllerPos, CreeperBlastChamberVaultRole role) {
		if (level == null || level.isClientSide || !structureValid || inputVaultController == null
			|| outputVaultController == null)
			return;
		if (!vaultControllerPos.equals(inputVaultController) && !vaultControllerPos.equals(outputVaultController))
			return;

		BlockPos previousInput = inputVaultController;
		BlockPos previousOutput = outputVaultController;
		configuredInputVaultController = role == CreeperBlastChamberVaultRole.INPUT
			? vaultControllerPos
			: vaultControllerPos.equals(inputVaultController) ? outputVaultController : inputVaultController;

		VaultRoleAssignment assignment = resolveVaultRoleAssignment(previousInput, previousOutput);
		inputVaultController = assignment.inputVaultController();
		outputVaultController = assignment.outputVaultController();
		if (Objects.equals(previousInput, inputVaultController) && Objects.equals(previousOutput, outputVaultController))
			return;

		syncVaultRoleBindings(level, previousInput, previousOutput);
		notifyUpdate();
	}

	private void onStructureFormed(Level level, int size, BlockPos origin) {
		BlockPos pressPos = origin.offset(size / 2, 3, size / 2);
		BlockState pressState = level.getBlockState(pressPos);
		Direction shaftFacing = pressState.getValue(BlockStateProperties.HORIZONTAL_FACING);
		resetPressProgress(getMechanicalPresses());
		setPackagerOutputsDown(level, origin, size);
		// Chain drives sit on the two side edges that line up with the press shaft.
		Axis axis = getPressShaftAxis(pressState);
		Direction alongEdge = shaftFacing.getClockWise();
		Axis alongEdgeAxis = alongEdge.getAxis();
		boolean alongFirst = axis == Axis.Z && alongEdgeAxis == Axis.X;
		BlockState chainState = CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get().defaultBlockState()
			.setValue(BlockStateProperties.AXIS, axis)
			.setValue(ChainDriveBlock.CONNECTED_ALONG_FIRST_COORDINATE, alongFirst);
		if ((size & 1) == 0) {
			int topY = 3;
			if (axis == Axis.X) {
				for (int z = 1; z < size - 1; z++) {
					BlockPos westEdgePos = origin.offset(0, topY, z);
					BlockPos eastEdgePos = origin.offset(size - 1, topY, z);
					if (!level.getBlockState(westEdgePos).is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get()))
						level.setBlock(westEdgePos, chainState, 3);
					if (!level.getBlockState(eastEdgePos).is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get()))
						level.setBlock(eastEdgePos, chainState, 3);
				}
				for (int z = 1; z < size - 1; z++) {
					updateChainDriveState(level, origin.offset(0, topY, z), axis, alongEdgeAxis);
					updateChainDriveState(level, origin.offset(size - 1, topY, z), axis, alongEdgeAxis);
				}
				refreshStructureKinetics(level, origin, size);
				return;
			}

			for (int x = 1; x < size - 1; x++) {
				BlockPos northEdgePos = origin.offset(x, topY, 0);
				BlockPos southEdgePos = origin.offset(x, topY, size - 1);
				if (!level.getBlockState(northEdgePos).is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get()))
					level.setBlock(northEdgePos, chainState, 3);
				if (!level.getBlockState(southEdgePos).is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get()))
					level.setBlock(southEdgePos, chainState, 3);
			}

			for (int x = 1; x < size - 1; x++) {
				updateChainDriveState(level, origin.offset(x, topY, 0), axis, alongEdgeAxis);
				updateChainDriveState(level, origin.offset(x, topY, size - 1), axis, alongEdgeAxis);
			}
			refreshStructureKinetics(level, origin, size);
			return;
		}

		int perSide = size - 2;
		// Edge 1: the edge in the shaft direction from press
		// Edge 2: the edge in the opposite shaft direction from press
		Direction towardEdge1 = shaftFacing;
		Direction towardEdge2 = shaftFacing.getOpposite();
		int distFromPressToEdge = size / 2;
		for (int i = -perSide / 2; i <= perSide / 2; i++) {
			BlockPos edge1Pos = pressPos.relative(towardEdge1, distFromPressToEdge)
				.relative(alongEdge, i);
			BlockPos edge2Pos = pressPos.relative(towardEdge2, distFromPressToEdge)
				.relative(alongEdge, i);
			if (!level.getBlockState(edge1Pos).is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get()))
				level.setBlock(edge1Pos, chainState, 3);
			if (!level.getBlockState(edge2Pos).is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get()))
				level.setBlock(edge2Pos, chainState, 3);
		}

		for (int i = -perSide / 2; i <= perSide / 2; i++) {
			BlockPos edge1Pos = pressPos.relative(towardEdge1, distFromPressToEdge)
				.relative(alongEdge, i);
			BlockPos edge2Pos = pressPos.relative(towardEdge2, distFromPressToEdge)
				.relative(alongEdge, i);
			updateChainDriveState(level, edge1Pos, axis, alongEdgeAxis);
			updateChainDriveState(level, edge2Pos, axis, alongEdgeAxis);
		}

		refreshStructureKinetics(level, origin, size);
	}

	private void onStructureBroken(Level level, int size, BlockPos origin, @Nullable Axis pressAxis) {
		restoreChainDrivePositionsToCasing(level, origin, size, pressAxis);
		refreshStructureKinetics(level, origin, size);
		releaseManagedCreepers(origin, size);
		cancelPendingUnpacks();
	}

	private void restoreChainDrivePositionsToCasing(Level level, BlockPos origin, int size, @Nullable Axis pressAxis) {
		if (pressAxis == null)
			return;

		int topY = 3;
		if (pressAxis == Axis.X) {
			for (int z = 1; z < size - 1; z++) {
				revertToCasing(level, origin.offset(0, topY, z));
				revertToCasing(level, origin.offset(size - 1, topY, z));
			}
			return;
		}

		for (int x = 1; x < size - 1; x++) {
			revertToCasing(level, origin.offset(x, topY, 0));
			revertToCasing(level, origin.offset(x, topY, size - 1));
		}
	}

	private void refreshStructureKinetics(Level level, BlockPos origin, int size) {
		if (level.isClientSide)
			return;

		List<KineticBlockEntity> kinetics = new ArrayList<>();
		int topY = 3;
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				BlockEntity blockEntity = level.getBlockEntity(origin.offset(x, topY, z));
				if (blockEntity instanceof KineticBlockEntity kinetic)
					kinetics.add(kinetic);
			}
		}

		for (KineticBlockEntity kinetic : kinetics) {
			kinetic.detachKinetics();
			kinetic.removeSource();
		}

		for (KineticBlockEntity kinetic : kinetics) {
			kinetic.attachKinetics();
			kinetic.setChanged();
			kinetic.sendData();
		}
	}

	private void revertToCasing(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get()))
			level.setBlock(pos, CBBlocks.EXPLOSION_PROOF_CASING.get().defaultBlockState(), 3);
	}

	private void updateChainDriveState(Level level, BlockPos pos, Axis axis, Axis alongEdgeAxis) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get()))
			return;
		ChainDriveBlock chainDrive = (ChainDriveBlock) CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get();
		boolean alongFirst = axis == Axis.Z && alongEdgeAxis == Axis.X;
		BlockState updated = state.setValue(BlockStateProperties.AXIS, axis)
			.setValue(ChainDriveBlock.CONNECTED_ALONG_FIRST_COORDINATE, alongFirst);
		for (Direction facing : Iterate.directions) {
			if (facing.getAxis() == axis)
				continue;
			BlockPos offset = pos.relative(facing);
			updated = chainDrive.updateShape(updated, facing, level.getBlockState(offset), level, pos, offset);
		}
		if (updated != state)
			level.setBlock(pos, updated, 3);
	}

	private void setPackagerOutputsDown(Level level, BlockPos origin, int size) {
		for (int x = 1; x < size - 1; x++) {
			for (int z = 1; z < size - 1; z++) {
				BlockPos packagerPos = origin.offset(x, 0, z);
				BlockState packagerState = level.getBlockState(packagerPos);
				if (!packagerState.is(CBBlocks.BIO_PACKAGER.get())
					|| !packagerState.hasProperty(BlockStateProperties.FACING)
					|| packagerState.getValue(BlockStateProperties.FACING) == Direction.UP)
					continue;
				level.setBlock(packagerPos, packagerState.setValue(BlockStateProperties.FACING, Direction.UP), 3);
			}
		}
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
			rebuildStructurePositionCache();
	}

	public void forceStructureCheck() {
		tryDetectStructure();
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

	private void tickPressProcessing() {
		if (!structureValid || structureOrigin == null || inputVaultController == null || outputVaultController == null) {
			pressCycleProcessed = false;
			return;
		}

		List<MechanicalPressBlockEntity> presses = getMechanicalPresses();
		if (presses.isEmpty()) {
			pressCycleProcessed = false;
			return;
		}

		if (hasUnworkablePresses(presses)) {
			resetPressProgress(presses);
			return;
		}

		for (MechanicalPressBlockEntity press : presses) {
			if (press.getSpeed() != 0 && !press.getPressingBehaviour().running)
				press.getPressingBehaviour().start(PressingBehaviour.Mode.WORLD);
		}

		MechanicalPressBlockEntity press = getMasterPress(presses);
		if (press == null) {
			pressCycleProcessed = false;
			return;
		}

		PressingBehaviour pressingBehaviour = press.getPressingBehaviour();
		if (press.getSpeed() == 0) {
			if (!pressingBehaviour.running || pressingBehaviour.runningTicks < PRESSING_TRIGGER_TICKS)
				pressCycleProcessed = false;
			return;
		}

		if (pressingBehaviour.runningTicks < PRESSING_TRIGGER_TICKS) {
			pressCycleProcessed = false;
			return;
		}

		if (pressCycleProcessed)
			return;

		pressCycleProcessed = true;
		CreeperCountSummary workableCreepers = summarizeWorkableMarkedCreepers();
		if (workableCreepers.totalCount() <= 0)
			return;

		if (applyOverloadFromPress(press, workableCreepers))
			return;

		processMarkedCreepersCycle(workableCreepers);
	}

	private boolean hasUnworkablePresses(List<MechanicalPressBlockEntity> presses) {
		boolean hasWorkingPress = false;
		boolean hasStoppedPress = false;
		for (MechanicalPressBlockEntity press : presses) {
			if (press.getSpeed() != 0)
				hasWorkingPress = true;
			else
				hasStoppedPress = true;
			if (hasWorkingPress && hasStoppedPress)
				return true;
		}
		return false;
	}

	private boolean applyOverloadFromPress(MechanicalPressBlockEntity press, CreeperCountSummary creeperCounts) {
		if (!isOverloadExplosionsEnabled()) {
			if (overloadPoints > 0)
				setOverloadPoints(0);
			return false;
		}

		if (overloadPoints >= getOverloadPointsCap()) {
			triggerOverload(creeperCounts);
			return true;
		}

		int rpm = Mth.floor(Math.abs(press.getSpeed()));
		int overloadGain = rpm - getOverloadThresholdRpm();
		if (overloadGain <= 0)
			return false;

		int nextOverloadPoints = overloadPoints + overloadGain;
		if (nextOverloadPoints > getOverloadPointsCap()) {
			setOverloadPoints(getOverloadPointsCap());
			return false;
		}

		setOverloadPoints(nextOverloadPoints);
		return false;
	}

	private void triggerOverload(CreeperCountSummary creeperCounts) {
		Level level = getLevel();
		if (level == null || level.isClientSide || !structureValid || structureOrigin == null)
			return;
		if (!isOverloadExplosionsEnabled())
			return;

		setOverloadPoints(0);
		int effectiveExplosionCount = creeperCounts.getExplosionEquivalentCount();
		if (effectiveExplosionCount <= 0)
			return;

		destroyChamberStructureForOverload(level);
		if (creeperCounts.chargedCount() > 0) {
			triggerHighPressureOverload(level, effectiveExplosionCount);
			return;
		}

		float explosionPower = effectiveExplosionCount * getTntExplosionPower();
		explodeOverloadAt(level, getOverloadExplosionCenter(), explosionPower);
	}

	private void triggerHighPressureOverload(Level level, int effectiveExplosionCount) {
		if (structureOrigin == null)
			return;

		float explosionPower = effectiveExplosionCount * getOverloadTntEquivalentPerCreeper() * getTntExplosionPower();
		double[] xCoords = {structureOrigin.getX() + 0.5d, structureOrigin.getX() + structureSize - 0.5d};
		double[] yCoords = {structureOrigin.getY() + 0.5d, structureOrigin.getY() + 3.5d};
		double[] zCoords = {structureOrigin.getZ() + 0.5d, structureOrigin.getZ() + structureSize - 0.5d};

		for (double x : xCoords)
			for (double y : yCoords)
				for (double z : zCoords)
					level.explode(null, x, y, z, explosionPower, Level.ExplosionInteraction.TNT);

		explodeOverloadAt(level, getOverloadExplosionCenter(), explosionPower);
	}

	private Vec3 getOverloadExplosionCenter() {
		return Vec3.atCenterOf(structureOrigin.offset(structureSize / 2, 1, structureSize / 2));
	}

	private void awardSafeBlastAdvancement() {
		if (level == null || structureOrigin == null)
			return;
		CBAdvancements.awardNearby(level, getOverloadExplosionCenter(), 32, CBAdvancements.CREEPER_BLAST_CHAMBER);
	}

	private void explodeOverloadAt(Level level, Vec3 position, float explosionPower) {
		CBAdvancements.awardNearby(level, position, 32, CBAdvancements.CREEPER_BLAST_CHAMBER_OVERLOAD);
		level.explode(null, position.x, position.y, position.z, explosionPower, Level.ExplosionInteraction.TNT);
	}

	private void destroyChamberStructureForOverload(Level level) {
		if (!structureValid || structureOrigin == null)
			return;

		List<BlockPos> positionsToClear = new ArrayList<>(structureSize * structureSize * 4);
		for (int x = 0; x < structureSize; x++) {
			for (int y = 0; y <= 3; y++) {
				for (int z = 0; z < structureSize; z++)
					positionsToClear.add(structureOrigin.offset(x, y, z));
			}
		}

		for (BlockPos pos : positionsToClear) {
			if (!level.getBlockState(pos).isAir())
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		}
	}

	private void setOverloadPoints(int overloadPoints) {
		int clampedOverloadPoints = Mth.clamp(overloadPoints, 0, getOverloadPointsCap());
		if (this.overloadPoints == clampedOverloadPoints)
			return;
		this.overloadPoints = clampedOverloadPoints;
		setChanged();
		if (level != null && !level.isClientSide)
			notifyUpdate();
	}

	private void processMarkedCreepersCycle(CreeperCountSummary creeperCounts) {
		ChamberCreeperKind chamberKind = getHeldCreeperKind();
		boolean highPressure = chamberKind == ChamberCreeperKind.CHARGED || chamberKind == ChamberCreeperKind.MIXED;
		int operations = highPressure ? creeperCounts.chargedCount() : creeperCounts.totalCount();
		if (operations <= 0)
			return;

		IItemHandler inputHandler = getVaultItemHandler(inputVaultController);
		IItemHandler outputHandler = getVaultItemHandler(outputVaultController);
		if (inputHandler == null || outputHandler == null)
			return;

		int processedOperations = 0;
		for (int i = 0; i < operations; i++) {
			ProcessingAttemptResult result = highPressure
				? processNextHighPressureVaultItem(inputHandler, outputHandler)
				: processNextVaultStack(inputHandler, outputHandler);
			if (result == ProcessingAttemptResult.PROCESSED) {
				processedOperations++;
				continue;
			}
			break;
		}
		if (processedOperations > 0)
			awardSafeBlastAdvancement();
	}

	private void tickOverloadDecay(Level level) {
		if (overloadPoints <= 0 || isCurrentlyOverloading() || level.getGameTime() % 20 != 0)
			return;
		setOverloadPoints(overloadPoints - getOverloadDecayPointsPerSecond());
	}

	private boolean isCurrentlyOverloading() {
		if (!structureValid || structureOrigin == null)
			return false;

		MechanicalPressBlockEntity masterPress = getMasterPress(getMechanicalPresses());
		return masterPress != null && Math.abs(masterPress.getSpeed()) > getOverloadThresholdRpm();
	}

	private ProcessingAttemptResult processNextVaultStack(IItemHandler inputHandler, IItemHandler outputHandler) {
		for (int slot = 0; slot < inputHandler.getSlots(); slot++) {
			ItemStack stackInSlot = inputHandler.getStackInSlot(slot);
			if (stackInSlot.isEmpty())
				continue;
			return processVaultStack(inputHandler, outputHandler, slot, stackInSlot.copy());
		}
		return ProcessingAttemptResult.NO_INPUT;
	}

	private ProcessingAttemptResult processNextHighPressureVaultItem(IItemHandler inputHandler, IItemHandler outputHandler) {
		for (int slot = 0; slot < inputHandler.getSlots(); slot++) {
			ItemStack stackInSlot = inputHandler.getStackInSlot(slot);
			if (stackInSlot.isEmpty())
				continue;
			return processHighPressureVaultItem(inputHandler, outputHandler, slot, stackInSlot.copy());
		}
		return ProcessingAttemptResult.NO_INPUT;
	}

	private ProcessingAttemptResult processVaultStack(IItemHandler inputHandler, IItemHandler outputHandler, int slot,
		ItemStack stackToProcess) {
		Optional<ProcessingRecipe<RecipeWrapper, ?>> recipe = findCrushingRecipe(stackToProcess);
		if (recipe.isEmpty()) {
			inputHandler.extractItem(slot, stackToProcess.getCount(), false);
			return ProcessingAttemptResult.PROCESSED;
		}

		List<ItemStack> outputs = rollProcessingResults(stackToProcess, recipe.get());
		if (!canFullyInsertAll(outputHandler, outputs))
			return ProcessingAttemptResult.BLOCKED_OUTPUT;

		ItemStack extracted = inputHandler.extractItem(slot, stackToProcess.getCount(), false);
		if (extracted.getCount() != stackToProcess.getCount())
			return ProcessingAttemptResult.NO_INPUT;

		insertAllOutputs(outputHandler, outputs);
		return ProcessingAttemptResult.PROCESSED;
	}

	private ProcessingAttemptResult processHighPressureVaultItem(IItemHandler inputHandler, IItemHandler outputHandler,
		int slot, ItemStack stackToProcess) {
		if (stackToProcess.isEmpty())
			return ProcessingAttemptResult.NO_INPUT;

		ItemStack singleInput = stackToProcess.copy();
		singleInput.setCount(1);

		Optional<CreeperBlastChamberHighPressureRecipe> recipe = findHighPressureRecipe(singleInput);
		List<ItemStack> outputs = recipe.map(CreeperBlastChamberHighPressureRecipe::rollResults)
			.orElse(List.of());
		if (!canFullyInsertAll(outputHandler, outputs))
			return ProcessingAttemptResult.BLOCKED_OUTPUT;

		ItemStack extracted = inputHandler.extractItem(slot, 1, false);
		if (extracted.getCount() != 1)
			return ProcessingAttemptResult.NO_INPUT;

		insertAllOutputs(outputHandler, outputs);
		return ProcessingAttemptResult.PROCESSED;
	}

	private Optional<ProcessingRecipe<RecipeWrapper, ?>> findCrushingRecipe(ItemStack stack) {
		Level level = getLevel();
		if (level == null || stack.isEmpty())
			return Optional.empty();

		CRUSHING_RECIPE_INVENTORY.setStackInSlot(0, stack);
		Optional<RecipeHolder<StandardProcessingRecipe<RecipeWrapper>>> recipe =
			AllRecipeTypes.CRUSHING.find(CRUSHING_RECIPE_WRAPPER, level);
		if (recipe.isEmpty())
			recipe = AllRecipeTypes.MILLING.find(CRUSHING_RECIPE_WRAPPER, level);
		return recipe.<ProcessingRecipe<RecipeWrapper, ?>>map(RecipeHolder::value);
	}

	private List<ItemStack> rollProcessingResults(ItemStack input, ProcessingRecipe<RecipeWrapper, ?> recipe) {
		List<ItemStack> outputs = new ArrayList<>();
		for (int roll = 0; roll < input.getCount(); roll++) {
			for (ItemStack rolledResult : recipe.rollResults(level.random))
				ItemHelper.addToList(rolledResult, outputs);
		}
		if (input.hasCraftingRemainingItem())
			ItemHelper.addToList(input.getCraftingRemainingItem(), outputs);
		return outputs;
	}

	private Optional<CreeperBlastChamberHighPressureRecipe> findHighPressureRecipe(ItemStack stack) {
		Level level = getLevel();
		if (level == null || stack.isEmpty())
			return Optional.empty();

		HIGH_PRESSURE_RECIPE_INVENTORY.setStackInSlot(0, stack);
		return level.getRecipeManager()
			.getRecipeFor(CBRecipeTypes.CREEPER_BLAST_CHAMBER_HIGH_PRESSURE_TYPE.get(), HIGH_PRESSURE_RECIPE_WRAPPER,
				level)
			.map(RecipeHolder::value);
	}

	private boolean canFullyInsertAll(IItemHandler outputHandler, List<ItemStack> outputs) {
		List<ItemStack> simulatedSlots = new ArrayList<>(outputHandler.getSlots());
		for (int slot = 0; slot < outputHandler.getSlots(); slot++)
			simulatedSlots.add(outputHandler.getStackInSlot(slot).copy());

		for (ItemStack output : outputs) {
			ItemStack remainder = output.copy();
			for (int slot = 0; slot < simulatedSlots.size() && !remainder.isEmpty(); slot++) {
				ItemStack simulatedStack = simulatedSlots.get(slot);
				int slotLimit = Math.min(outputHandler.getSlotLimit(slot), remainder.getMaxStackSize());

				if (simulatedStack.isEmpty()) {
					int inserted = Math.min(slotLimit, remainder.getCount());
					if (inserted <= 0)
						continue;
					ItemStack insertedStack = remainder.copy();
					insertedStack.setCount(inserted);
					simulatedSlots.set(slot, insertedStack);
					remainder.shrink(inserted);
					continue;
				}

				if (!ItemStack.isSameItemSameComponents(simulatedStack, remainder))
					continue;

				int space = Math.min(slotLimit, simulatedStack.getMaxStackSize()) - simulatedStack.getCount();
				if (space <= 0)
					continue;

				int inserted = Math.min(space, remainder.getCount());
				simulatedStack.grow(inserted);
				remainder.shrink(inserted);
			}

			if (!remainder.isEmpty())
				return false;
		}

		return true;
	}

	private void insertAllOutputs(IItemHandler outputHandler, List<ItemStack> outputs) {
		for (ItemStack output : outputs) {
			ItemStack remainder = output.copy();
			for (int slot = 0; slot < outputHandler.getSlots() && !remainder.isEmpty(); slot++)
				remainder = outputHandler.insertItem(slot, remainder, false);
		}
	}

	@Nullable
	private IItemHandler getVaultItemHandler(@Nullable BlockPos controllerPos) {
		if (controllerPos == null || level == null)
			return null;
		BlockEntity blockEntity = level.getBlockEntity(controllerPos);
		if (!(blockEntity instanceof ExplosionProofItemVaultBlockEntity vault))
			return null;
		return vault.getItemCapability(null);
	}

	@Nullable
	private MechanicalPressBlockEntity getMasterPress(List<MechanicalPressBlockEntity> presses) {
		if (presses.isEmpty())
			return null;
		MechanicalPressBlockEntity centerPress = getMechanicalPress(structureOrigin.offset(structureSize / 2, 3,
			structureSize / 2));
		if (centerPress != null && centerPress.getSpeed() != 0)
			return centerPress;
		for (MechanicalPressBlockEntity press : presses)
			if (press.getSpeed() != 0)
				return press;
		if (centerPress != null)
			return centerPress;
		for (MechanicalPressBlockEntity press : presses)
			if (press.getPressingBehaviour().running)
				return press;
		return presses.get(0);
	}

	private MechanicalPressBlockEntity getMechanicalPress(BlockPos pressPos) {
		if (level == null)
			return null;
		BlockEntity blockEntity = level.getBlockEntity(pressPos);
		return blockEntity instanceof MechanicalPressBlockEntity press ? press : null;
	}

	private List<MechanicalPressBlockEntity> getMechanicalPresses() {
		resolvedMechanicalPresses.clear();
		if (!structureValid)
			return resolvedMechanicalPresses;

		for (BlockPos pressPos : cachedPressPositions) {
			MechanicalPressBlockEntity press = getMechanicalPress(pressPos);
			if (press != null)
				resolvedMechanicalPresses.add(press);
		}
		return resolvedMechanicalPresses;
	}

	float getRenderedPressHeadOffset(BlockPos packagerPos, float partialTicks) {
		BlockPos pressPos = cachedPressByPackager.get(packagerPos.asLong());
		MechanicalPressBlockEntity press = pressPos == null ? null : getMechanicalPress(pressPos);
		return getSynchronizedPressHeadOffset(press, partialTicks);
	}

	/**
	 * Resolves the press whose cycle every animation in this chamber follows, memoized for the current
	 * tick. Which press is the master depends only on block state and speed, so it is stable within a
	 * tick while the head progress derived from it still interpolates per frame.
	 */
	private void resolveRenderPressState() {
		int tick = AnimationTickHolder.getTicks();
		if (renderPressStateTick == tick && (renderMasterPress == null || !renderMasterPress.isRemoved()))
			return;

		renderPressStateTick = tick;
		renderMasterPress = null;
		renderPressesUnworkable = false;
		if (!structureValid || structureOrigin == null)
			return;
		List<MechanicalPressBlockEntity> presses = getMechanicalPresses();
		if (presses.isEmpty())
			return;
		renderPressesUnworkable = hasUnworkablePresses(presses);
		renderMasterPress = getMasterPress(presses);
	}

	float getRenderedCreeperEffectPressOffset(BlockPos packagerPos, float partialTicks) {
		resolveRenderPressState();
		if (renderMasterPress == null || renderMasterPress.getSpeed() == 0)
			return 0f;
		if (!renderPressesUnworkable)
			return getLocalPressHeadProgress(renderMasterPress.getPressingBehaviour(), partialTicks)
				* PressingBehaviour.Mode.WORLD.headOffset;

		// Only the uncommon mixed-speed fallback needs to resolve the press above this particular slot.
		BlockPos pressPos = cachedPressByPackager.get(packagerPos.asLong());
		MechanicalPressBlockEntity press = pressPos == null ? null : getMechanicalPress(pressPos);
		if (press == null)
			return 0f;
		PressingBehaviour ownBehaviour = press.getPressingBehaviour();
		if (ownBehaviour.mode == null)
			return 0f;
		return getLocalPressHeadProgress(ownBehaviour, partialTicks) * ownBehaviour.mode.headOffset;
	}

	float getWorkingCreeperCompression(BlockPos packagerPos, float partialTicks) {
		return getCompressionFromPressOffset(getRenderedCreeperEffectPressOffset(packagerPos, partialTicks));
	}

	private void resetPressProgress(List<MechanicalPressBlockEntity> presses) {
		for (MechanicalPressBlockEntity press : presses) {
			PressingBehaviour pressingBehaviour = press.getPressingBehaviour();
			boolean changed = pressingBehaviour.running || pressingBehaviour.finished || pressingBehaviour.prevRunningTicks != 0
				|| pressingBehaviour.runningTicks != 0 || !pressingBehaviour.particleItems.isEmpty();
			if (!changed)
				continue;
			pressingBehaviour.running = false;
			pressingBehaviour.finished = false;
			pressingBehaviour.prevRunningTicks = 0;
			pressingBehaviour.runningTicks = 0;
			pressingBehaviour.particleItems.clear();
			press.setChanged();
			press.sendData();
		}
		pressCycleProcessed = false;
	}

	private void tickPendingUnpacks() {
		Level level = getLevel();
		if (level == null || pendingUnpacks.isEmpty())
			return;

		if (!structureValid || structureOrigin == null)
			return;

		boolean changed = false;
		boolean transitionStructureValidated = false;
		Iterator<PendingUnpack> iterator = pendingUnpacks.iterator();
		while (iterator.hasNext()) {
			PendingUnpack pending = iterator.next();
			BioPackagerBlockEntity packager = getPackager(pending.packagerPos);
			if (!isPackagerPartOfStructure(pending.packagerPos) || packager == null) {
				dropBox(pending);
				iterator.remove();
				changed = true;
				continue;
			}

			if (pending.ticksRemaining > 0)
				pending.ticksRemaining--;

			if (!pending.transitioned && pending.ticksRemaining <= BioPackagerBlockEntity.getCycleTicks()) {
				if (!transitionStructureValidated) {
					transitionStructureValidated = true;
					if (!validateStructureForUnpackTransition(level))
						return;
				}
				if (!completePendingUnpack(pending)) {
					dropBox(pending);
					clearPackagerAnimationState(pending.packagerPos);
					iterator.remove();
					changed = true;
					continue;
				}
				pending.transitioned = true;
				ItemStack emptyBox = pending.boxStack.copy();
				emptyBox.setCount(1);
				CapturedEntityBoxHelper.clearCapturedEntity(emptyBox);
				packager.heldBox = emptyBox;
				packager.previouslyUnwrapped = ItemStack.EMPTY;
				packager.animationInward = true;
				packager.animationTicks = BioPackagerBlockEntity.getCycleTicks();
				packager.notifyUpdate();
				packager.setChanged();
			}

			if (pending.ticksRemaining > 0)
				continue;

			clearPackagerAnimationState(pending.packagerPos);
			iterator.remove();
			changed = true;
		}

		if (changed)
			setChanged();
	}

	private boolean validateStructureForUnpackTransition(Level level) {
		if (!structureValid || structureOrigin == null)
			return false;
		if (scanStructure(level, structureOrigin, structureSize) != null) {
			recheckTimer = STRUCTURE_RECHECK_INTERVAL_TICKS;
			return true;
		}

		int oldSize = structureSize;
		BlockPos oldOrigin = structureOrigin;
		Axis oldPressAxis = structurePressAxis;
		setStructure(level, false, 0, null, null, null);
		onStructureBroken(level, oldSize, oldOrigin, oldPressAxis);
		return false;
	}

	private void tickPendingAppearances() {
		if (pendingAppearances.isEmpty())
			return;

		boolean changed = false;
		Iterator<PendingAppearance> iterator = pendingAppearances.iterator();
		while (iterator.hasNext()) {
			PendingAppearance pending = iterator.next();
			if (pending.ticksRemaining > 0)
				pending.ticksRemaining--;
			if (pending.ticksRemaining > 0)
				continue;

			iterator.remove();
			changed = true;
		}

		if (changed) {
			setChanged();
			notifyUpdate();
		}
	}

	private void tickPendingPackagings() {
		Level level = getLevel();
		if (level == null || pendingPackagings.isEmpty())
			return;

		boolean changed = false;
		Iterator<PendingPackaging> iterator = pendingPackagings.iterator();
		while (iterator.hasNext()) {
			PendingPackaging pending = iterator.next();
			BioPackagerBlockEntity packager = getPackager(pending.packagerPos);
			if (packager == null) {
				restorePendingPackaging(pending, true);
				iterator.remove();
				changed = true;
				continue;
			}

			if (pending.ticksRemaining > 0)
				pending.ticksRemaining--;

			if (!pending.transitioned && pending.ticksRemaining <= BioPackagerBlockEntity.getCycleTicks()) {
				pending.transitioned = true;
				packager.heldBox = pending.boxStack.copy();
				packager.previouslyUnwrapped = ItemStack.EMPTY;
				packager.animationInward = true;
				packager.animationTicks = BioPackagerBlockEntity.getCycleTicks();
				packager.notifyUpdate();
				packager.setChanged();
			}

			if (pending.ticksRemaining > 0)
				continue;

			packager.heldBox = ItemStack.EMPTY;
			packager.animationTicks = 0;
			packager.notifyUpdate();
			packager.setChanged();
			storedCreepers.remove(pending.packagerPos);
			readyOutputs.add(new ReadyOutput(pending.packagerPos, pending.boxStack.copy(), getReadyOutputTimeout()));
			iterator.remove();
			changed = true;
		}

		if (changed) {
			setChanged();
			notifyUpdate();
		}
	}

	private void tickReadyOutputs() {
		Level level = getLevel();
		if (level == null || readyOutputs.isEmpty() || !structureValid)
			return;

		boolean changed = false;
		Iterator<ReadyOutput> iterator = readyOutputs.iterator();
		while (iterator.hasNext()) {
			ReadyOutput readyOutput = iterator.next();
			if (isControllerOutputRequestActive()) {
				if (readyOutput.ticksRemaining != getReadyOutputTimeout()) {
					readyOutput.ticksRemaining = getReadyOutputTimeout();
					changed = true;
				}
				continue;
			}
			if (readyOutput.ticksRemaining > 0)
				readyOutput.ticksRemaining--;
			if (readyOutput.ticksRemaining > 0)
				continue;

			BlockPos targetPackager = findFirstEmptyPackagerSlot();
			if (targetPackager != null && queueUnpack(targetPackager, readyOutput.boxStack, true)) {
				iterator.remove();
				changed = true;
			}
		}

		if (changed)
			setChanged();
	}

	private void tickControllerOutputRequest() {
		if (!controllerOutputRequested)
			return;
		if (isPausedForPartialChunkUnload()) {
			controllerOutputRequested = false;
			controllerOutputRequestTicks = 0;
			setChanged();
			return;
		}

		boolean changed = packageMarkedCreepersForOutput();
		if (controllerOutputRequestTicks > 0)
			controllerOutputRequestTicks--;
		if (controllerOutputRequestTicks <= 0) {
			controllerOutputRequested = false;
			changed = true;
		}

		if (changed)
			setChanged();
	}

	private void tickClientAnimations() {
		syncClientPressControllers();
		tickClientAppearanceEffects();
		tickClientAnimationList(pendingAppearances);
		tickClientAnimationList(pendingPackagings);
		tickClientWorkingCreeperEffects();
	}

	/** Puffs a ring of smoke the first client tick a creeper appears in a packager. */
	private void tickClientAppearanceEffects() {
		Level level = getLevel();
		if (level == null || !level.isClientSide)
			return;
		if (pendingAppearances.isEmpty()) {
			clientAppearanceEffectsSpawned.clear();
			return;
		}

		for (PendingAppearance pending : pendingAppearances) {
			if (!clientAppearanceEffectsSpawned.add(pending.packagerPos.asLong()))
				continue;
			spawnAppearancePuff(level, pending.packagerPos);
		}
		clientAppearanceEffectsSpawned.removeIf(key -> !isPackagerAppearing(BlockPos.of(key)));
	}

	private void spawnAppearancePuff(Level level, BlockPos packagerPos) {
		int stride = ClientParticleBudget.decorativeStride();
		double centerX = packagerPos.getX() + .5d;
		double centerY = packagerPos.getY() + 1.15d;
		double centerZ = packagerPos.getZ() + .5d;
		for (int index = 0; index < APPEARANCE_PUFF_COUNT; index += stride) {
			double angle = Math.PI * 2d * index / APPEARANCE_PUFF_COUNT;
			double offsetX = Math.cos(angle);
			double offsetZ = Math.sin(angle);
			level.addParticle(ParticleTypes.POOF, centerX + offsetX * APPEARANCE_PUFF_RADIUS, centerY,
				centerZ + offsetZ * APPEARANCE_PUFF_RADIUS, offsetX * .02d, .02d, offsetZ * .02d);
		}
	}

	private void syncClientPressControllers() {
		Level level = getLevel();
		if (level == null || !level.isClientSide || !structureValid || structureOrigin == null) {
			clearClientTrackedPresses();
			clientPressLayoutDirty = false;
			return;
		}
		if (!clientPressLayoutDirty)
			return;

		clientActivePressPositions.clear();
		for (BlockPos pressPos : cachedPressPositions) {
			long key = clientPressKey(level, pressPos);
			clientActivePressPositions.add(key);
			CLIENT_PRESS_CONTROLLERS.put(key, getBlockPos());
			clientTrackedPressPositions.add(key);
		}

		clientTrackedPressPositions.removeIf(key -> {
			if (clientActivePressPositions.contains(key))
				return false;
			if (Objects.equals(CLIENT_PRESS_CONTROLLERS.get(key), getBlockPos()))
				CLIENT_PRESS_CONTROLLERS.remove(key);
			return true;
		});
		clientPressLayoutDirty = false;
	}

	/**
	 * The controller registry is keyed by packed position alone, so it is scoped to a single dimension
	 * at a time: two chambers sharing coordinates across dimensions would otherwise drive each other.
	 * The client only ever renders one dimension, so switching simply drops the stale mapping.
	 */
	private static long clientPressKey(Level level, BlockPos pressPos) {
		ResourceKey<Level> dimension = level.dimension();
		if (!dimension.equals(clientPressControllerDimension)) {
			clientPressControllerDimension = dimension;
			CLIENT_PRESS_CONTROLLERS.clear();
		}
		return pressPos.asLong();
	}

	private void clearClientTrackedPresses() {
		if (!clientTrackedPressPositions.isEmpty()) {
			for (long key : clientTrackedPressPositions) {
				if (Objects.equals(CLIENT_PRESS_CONTROLLERS.get(key), getBlockPos()))
					CLIENT_PRESS_CONTROLLERS.remove(key);
			}
			clientTrackedPressPositions.clear();
		}
	}

	private void tickClientWorkingCreeperEffects() {
		Level level = getLevel();
		if (level == null || !structureValid) {
			clientPressOffsets.clear();
			clientReturnEffectsArmed.clear();
			return;
		}

		clientNextPressOffsets.clear();
		clientActivePackagers.clear();
		boolean spawnedReturnEffectThisTick = false;
		for (RenderManagedCreeper creeper : getWorkingRenderCreepers()) {
			long key = creeper.packagerPos().asLong();
			float pressOffset = getRenderedCreeperEffectPressOffset(creeper.packagerPos(), 0);
			float previousOffset = clientPressOffsets.getOrDefault(key, 0f);
			boolean returning = isPressReturning(previousOffset, pressOffset);

			clientNextPressOffsets.put(key, pressOffset);
			clientActivePackagers.add(key);
			if (previousOffset < CLIENT_PRESS_EFFECT_START_OFFSET && pressOffset >= CLIENT_PRESS_EFFECT_START_OFFSET) {
				BlockPos pos = creeper.packagerPos();
				float pitch = 0.9f + ((Math.floorMod(pos.getX() * 31 + pos.getZ() * 17, 8)) * 0.025f);
				level.playLocalSound(pos.getX() + 0.5d, pos.getY() + 1d, pos.getZ() + 0.5d, SoundEvents.CREEPER_PRIMED,
					SoundSource.BLOCKS, 0.2f, pitch, false);
			}

			if (pressOffset >= CLIENT_RETURN_EFFECT_ARM_THRESHOLD)
				clientReturnEffectsArmed.add(key);
			if (!spawnedReturnEffectThisTick && clientReturnEffectsArmed.contains(key) && returning) {
				spawnClientReturnExplosionEffect();
				spawnedReturnEffectThisTick = true;
			}
			if (pressOffset <= CLIENT_PRESS_EFFECT_START_OFFSET * 0.5f)
				clientReturnEffectsArmed.remove(key);
		}

		clientPressOffsets.clear();
		clientPressOffsets.putAll(clientNextPressOffsets);
		if (spawnedReturnEffectThisTick)
			clientReturnEffectsArmed.clear();
		clientReturnEffectsArmed.removeIf(key -> !clientActivePackagers.contains(key));
	}

	private boolean isPressReturning(float previousOffset, float pressOffset) {
		return previousOffset > pressOffset + CLIENT_PRESS_RETURN_EPSILON
			&& previousOffset >= CLIENT_PRESS_EFFECT_START_OFFSET;
	}

	private void spawnClientReturnExplosionEffect() {
		Level level = getLevel();
		if (level == null || !level.isClientSide || !structureValid || structureOrigin == null)
			return;
		if (!areExplosionParticlesEnabled())
			return;

		int innerSize = Mth.clamp(structureSize - 2, 1, 3);
		double centerX = structureOrigin.getX() + structureSize / 2d;
		double centerY = structureOrigin.getY() + CLIENT_RETURN_EFFECT_Y_OFFSET;
		double centerZ = structureOrigin.getZ() + structureSize / 2d;
		double firstOffset = -((innerSize - 1) / 2d);
		CreeperBlastChamberClientSoundHandler.stopManagedPrimedSound();
		level.playLocalSound(centerX, centerY, centerZ, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.45f, 1.15f,
			false);

		for (int xIndex = 0; xIndex < innerSize; xIndex++) {
			for (int zIndex = 0; zIndex < innerSize; zIndex++) {
				double x = centerX + firstOffset + xIndex
					+ (level.random.nextDouble() * 2d - 1d) * CLIENT_RETURN_BASE_EXPLOSION_JITTER;
				double z = centerZ + firstOffset + zIndex
					+ (level.random.nextDouble() * 2d - 1d) * CLIENT_RETURN_BASE_EXPLOSION_JITTER;
				spawnRandomizedReturnExplosionParticle(level, x, centerY, z);
			}
		}

		int extraExplosionCount = Math.max(CLIENT_RETURN_EXTRA_EXPLOSION_MIN,
			(level.random.nextInt(CLIENT_RETURN_EXTRA_EXPLOSION_MAX - CLIENT_RETURN_EXTRA_EXPLOSION_MIN + 1)
				+ CLIENT_RETURN_EXTRA_EXPLOSION_MIN) / ClientParticleBudget.decorativeStride());
		for (int i = 0; i < extraExplosionCount; i++) {
			double x = centerX + (level.random.nextDouble() * 2d - 1d) * CLIENT_RETURN_EXTRA_EXPLOSION_RADIUS;
			double z = centerZ + (level.random.nextDouble() * 2d - 1d) * CLIENT_RETURN_EXTRA_EXPLOSION_RADIUS;
			spawnRandomizedReturnExplosionParticle(level, x, centerY, z);
		}

		spawnClientShellSurfaceExplosionEffect(level, centerY);
	}

	private void spawnRandomizedReturnExplosionParticle(Level level, double x, double y, double z) {
		if (!areExplosionParticlesEnabled())
			return;
		double sizeParam = Mth.lerp(level.random.nextDouble(),
			CLIENT_RETURN_EXPLOSION_SIZE_PARAM_MIN, CLIENT_RETURN_EXPLOSION_SIZE_PARAM_MAX);
		level.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION, true, x, y, z, sizeParam, 0, 0);
	}

	private void spawnClientShellSurfaceExplosionEffect(Level level, double centerY) {
		if (structureOrigin == null || structureSize <= 0)
			return;

		int stride = ClientParticleBudget.decorativeStride();
		int candidate = 0;
		for (int yOffset = 1; yOffset <= 2; yOffset++) {
			for (int xOffset = 0; xOffset < structureSize; xOffset++) {
				for (int zOffset = 0; zOffset < structureSize; zOffset++) {
					boolean onXWall = xOffset == 0 || xOffset == structureSize - 1;
					boolean onZWall = zOffset == 0 || zOffset == structureSize - 1;
					if (onXWall == onZWall)
						continue;
					if (candidate++ % stride != 0)
						continue;

					BlockPos pos = structureOrigin.offset(xOffset, yOffset, zOffset);
					BlockState state = level.getBlockState(pos);
					if (!isClientOuterShellBlock(state))
						continue;

					double normalX = xOffset == 0 ? -1d : xOffset == structureSize - 1 ? 1d : 0d;
					double normalZ = zOffset == 0 ? -1d : zOffset == structureSize - 1 ? 1d : 0d;
					double x = pos.getX() + 0.5d + normalX * 0.55d;
					double y = Mth.lerp(level.random.nextDouble(), pos.getY() + 0.2d, pos.getY() + 0.8d);
					double z = pos.getZ() + 0.5d + normalZ * 0.55d;
					spawnRandomizedReturnExplosionParticle(level, x, y, z);
				}
			}
		}

		double roofX = structureOrigin.getX() + structureSize / 2d;
		double roofZ = structureOrigin.getZ() + structureSize / 2d;
		spawnRandomizedReturnExplosionParticle(level, roofX, centerY + 0.35d, roofZ);
	}

	private boolean isClientOuterShellBlock(BlockState state) {
		if (state.isAir())
			return false;
		if (state.getBlock() instanceof CreeperBlastChamberBlock)
			return true;
		return state.is(CBBlocks.EXPLOSION_PROOF_CASING.get())
			|| state.is(CBBlocks.BLAST_PROOF_GLASS.get())
			|| state.is(CBBlocks.BLAST_PROOF_FRAMED_GLASS.get())
			|| state.is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get());
	}

	private static <T extends TimedAnimation> void tickClientAnimationList(List<T> animations) {
		Iterator<T> iterator = animations.iterator();
		while (iterator.hasNext()) {
			T animation = iterator.next();
			if (animation.ticksRemaining > 0)
				animation.ticksRemaining--;
			if (animation.ticksRemaining <= 0)
				iterator.remove();
		}
	}

	@Nullable
	private BlockPos findAvailablePackagerForNewInput() {
		if (getHeldCreeperCount() >= getPackagerPositions().size())
			return null;
		return findFirstEmptyPackagerSlot();
	}

	@Nullable
	private BlockPos findFirstEmptyPackagerSlot() {
		for (BlockPos packagerPos : getPackagerPositions()) {
			if (isPackagerSlotEmpty(packagerPos) && canUsePackagerForInternalTransfer(getPackager(packagerPos)))
				return packagerPos;
		}
		return null;
	}

	private int getHeldCreeperCount() {
		int uncommittedInputs = 0;
		for (PendingUnpack pending : pendingUnpacks)
			if (!pending.transitioned)
				uncommittedInputs++;
		return uncommittedInputs + readyOutputs.size() + storedCreepers.size();
	}

	private CreeperCountSummary summarizeWorkableMarkedCreepers() {
		int normalCount = 0;
		int chargedCount = 0;
		for (StoredCreeper stored : storedCreepers.values()) {
			BlockPos packagerPos = stored.packagerPos();
			if (isPackagerAppearing(packagerPos) || isPackagerPackaging(packagerPos))
				continue;
			if (stored.charged())
				chargedCount++;
			else
				normalCount++;
		}
		return new CreeperCountSummary(normalCount, chargedCount);
	}

	private ChamberCreeperKind getHeldCreeperKind() {
		ChamberCreeperKind kind = ChamberCreeperKind.NONE;
		for (PendingUnpack pending : pendingUnpacks)
			kind = kind.merge(getBoxedCreeperKind(pending.boxStack));
		for (PendingPackaging pending : pendingPackagings)
			kind = kind.merge(getBoxedCreeperKind(pending.boxStack));
		for (ReadyOutput readyOutput : readyOutputs)
			kind = kind.merge(getBoxedCreeperKind(readyOutput.boxStack));
		for (StoredCreeper stored : storedCreepers.values())
			kind = kind.merge(stored.charged() ? ChamberCreeperKind.CHARGED : ChamberCreeperKind.NORMAL);
		return kind;
	}

	private boolean canAcceptIncomingCreeperBox(ItemStack stack) {
		ChamberCreeperKind incomingKind = getBoxedCreeperKind(stack);
		if (incomingKind == ChamberCreeperKind.NONE || incomingKind == ChamberCreeperKind.MIXED)
			return false;

		ChamberCreeperKind heldKind = getHeldCreeperKind();
		if (heldKind == ChamberCreeperKind.NONE)
			return true;
		if (heldKind == ChamberCreeperKind.NORMAL)
			return incomingKind == ChamberCreeperKind.NORMAL;
		return incomingKind == ChamberCreeperKind.CHARGED;
	}

	private boolean isPackagerSlotEmpty(BlockPos packagerPos) {
		return !isPackagerReserved(packagerPos)
			&& !isPackagerPackaging(packagerPos)
			&& !storedCreepers.containsKey(packagerPos);
	}

	private boolean canUsePackagerForInternalTransfer(@Nullable BioPackagerBlockEntity packager) {
		return packager != null
			&& packager.animationTicks <= 0
			&& packager.heldBox.isEmpty();
	}

	private boolean queueUnpack(BlockPos packagerPos, ItemStack boxStack, boolean returnBox) {
		BioPackagerBlockEntity packager = getPackager(packagerPos);
		if (!isPackagerSlotEmpty(packagerPos) || !canUsePackagerForInternalTransfer(packager))
			return false;

		packager.heldBox = boxStack.copy();
		packager.previouslyUnwrapped = ItemStack.EMPTY;
		packager.animationInward = false;
		packager.animationTicks = BioPackagerBlockEntity.getCycleTicks();
		packager.chainReturnAnimation = false;
		packager.notifyUpdate();
		packager.setChanged();

		pendingUnpacks.add(new PendingUnpack(packagerPos, boxStack.copy(),
			BioPackagerBlockEntity.getCycleTicks() * 2, false, returnBox));
		setChanged();
		return true;
	}

	private InsertResult tryInsertLargeCreeperBox(ItemStack stack, boolean simulate, boolean returnBox) {
		if (!isValidLargeCreeperBox(stack))
			return new InsertResult(false, stack);

		Level level = getLevel();
		if (level == null || level.isClientSide)
			return new InsertResult(false, stack);

		if (!structureValid || recheckTimer <= 0) {
			tryDetectStructure();
			recheckTimer = STRUCTURE_RECHECK_INTERVAL_TICKS;
		}
		if (!structureValid)
			return new InsertResult(false, stack);
		if (isPausedForPartialChunkUnload())
			return new InsertResult(false, stack);
		if (!canAcceptIncomingCreeperBox(stack))
			return new InsertResult(false, stack);

		BlockPos targetPackager = findAvailablePackagerForNewInput();
		if (targetPackager == null)
			return new InsertResult(false, stack);

		if (!simulate) {
			ItemStack boxToInsert = stack.copy();
			boxToInsert.setCount(1);
			if (!queueUnpack(targetPackager, boxToInsert, returnBox))
				return new InsertResult(false, stack);
		}

		if (stack.getCount() <= 1)
			return new InsertResult(true, ItemStack.EMPTY);

		ItemStack remainder = stack.copy();
		remainder.shrink(1);
		return new InsertResult(true, remainder);
	}

	private boolean isValidLargeCreeperBox(ItemStack stack) {
		return stack.is(CBItems.LARGE_CARDBOARD_BOX.get())
			&& CapturedEntityBoxHelper.containsEntityType(stack, EntityType.CREEPER);
	}

	private ChamberCreeperKind getCreeperKind(Creeper creeper) {
		return creeper.isPowered() ? ChamberCreeperKind.CHARGED : ChamberCreeperKind.NORMAL;
	}

	private ChamberCreeperKind getBoxedCreeperKind(ItemStack stack) {
		Level level = getLevel();
		if (level != null) {
			Entity entity = CapturedEntityBoxHelper.createCapturedEntity(stack, level);
			if (entity instanceof Creeper creeper)
				return getCreeperKind(creeper);
		}

		CompoundTag boxTag = CBItemData.get(stack);
		if (boxTag == null || !boxTag.contains("CapturedEntity", Tag.TAG_COMPOUND))
			return ChamberCreeperKind.NONE;

		CompoundTag entityData = boxTag.getCompound("CapturedEntity");
		ResourceLocation entityId = ResourceLocation.tryParse(entityData.getString("id"));
		if (entityId == null || !entityId.equals(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.CREEPER)))
			return ChamberCreeperKind.NONE;
		return entityData.getBoolean("powered") ? ChamberCreeperKind.CHARGED : ChamberCreeperKind.NORMAL;
	}

	@Nullable
	private ItemStack getActualControllerOutput() {
		if (readyOutputs.isEmpty())
			return null;
		return readyOutputs.get(0).boxStack.copy();
	}

	private ItemStack getDeferredControllerOutputPreview() {
		StoredCreeperTarget target = findStoredCreeperForOutput();
		if (target == null)
			return ItemStack.EMPTY;
		return CapturedEntityBoxHelper.copyCapturedEntityIntoFreshBox(
			target.stored.normalizedPayloadBox(), CBItems.LARGE_CARDBOARD_BOX.get());
	}

	private void requestControllerOutput() {
		if (isPausedForPartialChunkUnload())
			return;
		controllerOutputRequested = true;
		controllerOutputRequestTicks = OUTPUT_REQUEST_KEEPALIVE_TICKS;
		setChanged();
	}

	private boolean isControllerOutputRequestActive() {
		return controllerOutputRequested && controllerOutputRequestTicks > 0;
	}

	@Nullable
	private ItemStack extractReadyOutput(boolean simulate) {
		if (readyOutputs.isEmpty())
			return null;

		ItemStack output = readyOutputs.get(0).boxStack.copy();
		if (!simulate) {
			readyOutputs.remove(0);
			setChanged();
		}
		return output;
	}

	private boolean packageMarkedCreepersForOutput() {
		boolean packagedAny = false;
		while (true) {
			StoredCreeperTarget target = findStoredCreeperForOutput();
			if (target == null)
				return packagedAny;
			if (!packageMarkedCreeperForOutput(target))
				return packagedAny;
			packagedAny = true;
		}
	}

	private boolean packageMarkedCreeperForOutput(StoredCreeperTarget target) {
		ItemStack output = CapturedEntityBoxHelper.copyCapturedEntityIntoFreshBox(
			target.stored.normalizedPayloadBox(), CBItems.LARGE_CARDBOARD_BOX.get());
		if (output.isEmpty())
			return false;

		BioPackagerBlockEntity packager = getPackager(target.packagerPos());
		if (!canUsePackagerForInternalTransfer(packager))
			return false;

		packager.heldBox = createEmptyPackageVisual();
		packager.previouslyUnwrapped = ItemStack.EMPTY;
		packager.animationInward = false;
		packager.animationTicks = BioPackagerBlockEntity.getCycleTicks();
		packager.chainReturnAnimation = false;
		packager.notifyUpdate();
		packager.setChanged();
		pendingPackagings.add(new PendingPackaging(target.packagerPos(), output.copy(),
			BioPackagerBlockEntity.getCycleTicks() * 2, false));
		setChanged();
		notifyUpdate();
		return true;
	}

	private ItemStack createEmptyPackageVisual() {
		return new ItemStack(CBItems.LARGE_CARDBOARD_BOX.get());
	}

	@Nullable
	private StoredCreeperTarget findStoredCreeperForOutput() {
		if (!structureValid)
			return null;

		for (BlockPos packagerPos : getPackagerPositions()) {
			if (isPackagerReserved(packagerPos) || isPackagerAppearing(packagerPos) || isPackagerPackaging(packagerPos))
				continue;
			BioPackagerBlockEntity packager = getPackager(packagerPos);
			if (!canUsePackagerForInternalTransfer(packager))
				continue;

			StoredCreeper stored = storedCreepers.get(packagerPos);
			if (stored != null)
				return new StoredCreeperTarget(packagerPos, stored);
		}

		return null;
	}

	private boolean isPackagerPackaging(BlockPos packagerPos) {
		for (PendingPackaging pending : pendingPackagings) {
			if (pending.packagerPos.equals(packagerPos))
				return true;
		}
		return false;
	}

	@Nullable
	private ItemStack createBoxedCreeper(Creeper creeper) {
		ItemStack box = new ItemStack(CBItems.LARGE_CARDBOARD_BOX.get());
		if (!CapturedEntityBoxHelper.captureEntity(box, creeper))
			return null;
		CapturedEntityBoxHelper.clearLegacyCreeperBlastChamberMarkers(box);
		return box;
	}

	@Nullable
	private StoredCreeper createStoredCreeper(BlockPos packagerPos, ItemStack sourceBox) {
		ItemStack normalized = CapturedEntityBoxHelper.copyCapturedEntityIntoFreshBox(
			sourceBox, CBItems.LARGE_CARDBOARD_BOX.get());
		if (normalized.isEmpty() || level == null)
			return null;
		Entity entity = CapturedEntityBoxHelper.createCapturedEntity(normalized, level);
		if (!(entity instanceof Creeper creeper))
			return null;
		long renderSeed = creeper.getUUID().getMostSignificantBits() ^ creeper.getUUID().getLeastSignificantBits()
			^ packagerPos.asLong();
		return new StoredCreeper(packagerPos.immutable(), normalized, creeper.isPowered(), renderSeed,
			getPreferredCreeperYaw());
	}

	private float getPreferredCreeperYaw() {
		Level level = getLevel();
		if (level == null || !structureValid || structureOrigin == null || structureSize <= 2)
			return Direction.NORTH.toYRot();

		Direction preferred = CREEPER_WINDOW_FACING_PRIORITY[0];
		int greatestWindowCount = -1;
		for (Direction side : CREEPER_WINDOW_FACING_PRIORITY) {
			int windowCount = countBlastProofWindows(level, side);
			if (windowCount <= greatestWindowCount)
				continue;
			preferred = side;
			greatestWindowCount = windowCount;
		}
		return preferred.toYRot();
	}

	private int countBlastProofWindows(Level level, Direction side) {
		if (structureOrigin == null)
			return 0;
		int count = 0;
		for (int y = 1; y <= 2; y++) {
			for (int lateral = 1; lateral < structureSize - 1; lateral++) {
				BlockPos pos = switch (side) {
					case NORTH -> structureOrigin.offset(lateral, y, 0);
					case SOUTH -> structureOrigin.offset(lateral, y, structureSize - 1);
					case WEST -> structureOrigin.offset(0, y, lateral);
					case EAST -> structureOrigin.offset(structureSize - 1, y, lateral);
					default -> throw new IllegalArgumentException("Expected a horizontal side, got " + side);
				};
				BlockState state = level.getBlockState(pos);
				if (state.is(CBBlocks.BLAST_PROOF_GLASS.get())
					|| state.is(CBBlocks.BLAST_PROOF_FRAMED_GLASS.get()))
					count++;
			}
		}
		return count;
	}

	private boolean completePendingUnpack(PendingUnpack pending) {
		Level level = getLevel();
		if (level == null || storedCreepers.containsKey(pending.packagerPos))
			return false;

		StoredCreeper stored = createStoredCreeper(pending.packagerPos, pending.boxStack);
		if (stored == null)
			return false;
		storedCreepers.put(pending.packagerPos, stored);
		pendingAppearances.add(new PendingAppearance(pending.packagerPos, CREEPER_ENTRY_ANIMATION_TICKS));
		setChanged();
		notifyUpdate();

		return true;
	}

	private void cancelPendingUnpacks() {
		if (pendingUnpacks.isEmpty())
			return;

		for (PendingUnpack pending : pendingUnpacks)
			dropBox(pending);
		pendingUnpacks.clear();
		setChanged();
	}

	private void releaseManagedCreepers(BlockPos origin, int size) {
		Level level = getLevel();
		if (level == null || level.isClientSide)
			return;
		migrateLegacyContainedCreepers();

		for (PendingPackaging pending : pendingPackagings)
			clearPackagerAnimationState(pending.packagerPos);

		for (StoredCreeper stored : new ArrayList<>(storedCreepers.values())) {
			if (!materializeStoredCreeper(stored))
				dropPackagedBox(stored.normalizedPayloadBox(), stored.packagerPos());
			storedCreepers.remove(stored.packagerPos());
		}

		pendingAppearances.clear();
		pendingPackagings.clear();
		legacyMarkedCreepers.clear();
		controllerOutputRequested = false;
		controllerOutputRequestTicks = 0;
		setChanged();
		notifyUpdate();
	}

	private boolean materializeStoredCreeper(StoredCreeper stored) {
		if (!(level instanceof ServerLevel serverLevel))
			return false;
		Entity entity = CapturedEntityBoxHelper.createCapturedEntity(stored.normalizedPayloadBox(), level);
		if (!(entity instanceof Creeper creeper))
			return false;
		CapturedEntityBoxHelper.clearLegacyCreeperBlastChamberMarkers(creeper);
		Vec3 position = Vec3.atBottomCenterOf(stored.packagerPos().above());
		creeper.stopRiding();
		creeper.moveTo(position.x, position.y, position.z, creeper.getYRot(), creeper.getXRot());
		creeper.setDeltaMovement(Vec3.ZERO);
		creeper.fallDistance = 0;
		creeper.setInvisible(false);
		ContainedEntityHandoffPacket.announce(serverLevel, creeper, getBlockPos(), stored.packagerPos(),
			stored.renderSeed(), 0);
		if (level.addFreshEntity(creeper))
			return true;
		ContainedEntityHandoffPacket.cancel(serverLevel, creeper, getBlockPos());
		return false;
	}

	private void migrateLegacyContainedCreepers() {
		if (containedDataVersion >= CONTAINED_DATA_VERSION || !(level instanceof ServerLevel serverLevel))
			return;
		if (structureValid && isPausedForPartialChunkUnload())
			return;

		Set<UUID> migrated = new HashSet<>();
		for (Map.Entry<UUID, BlockPos> entry : new ArrayList<>(legacyMarkedCreepers.entrySet())) {
			UUID uuid = entry.getKey();
			BlockPos packagerPos = entry.getValue();
			Entity found = serverLevel.getEntity(uuid);
			PendingPackaging packaging = findPendingPackaging(packagerPos);
			if (packaging != null) {
				StoredCreeper stored = createStoredCreeper(packagerPos, packaging.boxStack);
				if (stored != null && !storedCreepers.containsKey(packagerPos)) {
					storedCreepers.put(packagerPos, stored);
					if (found instanceof Creeper creeper)
						creeper.discard();
					migrated.add(uuid);
					continue;
				}
			}
			if (found instanceof Creeper creeper && isMarkedCreeperForThisChamber(creeper, packagerPos)) {
				ItemStack captured = createBoxedCreeper(creeper);
				if (captured != null)
					clearLegacyMachineMutations(captured);
				StoredCreeper stored = captured == null ? null : createStoredCreeper(packagerPos, captured);
				if (stored != null && !storedCreepers.containsKey(packagerPos)) {
					storedCreepers.put(packagerPos, new StoredCreeper(packagerPos, stored.normalizedPayloadBox(),
						creeper.isPowered(), uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits()
							^ packagerPos.asLong(), stored.defaultYaw()));
					creeper.discard();
					migrated.add(uuid);
					continue;
				}
				releaseManagedCreeper(creeper);
				continue;
			}

			LOGGER.warn("Could not migrate contained creeper {} for blast chamber at {}; releasing the empty slot",
				uuid, getBlockPos());
		}

		if (structureOrigin != null) {
			AABB bounds = getMarkedCreeperSearchBounds();
			for (Creeper creeper : level.getEntitiesOfClass(Creeper.class, bounds,
				entity -> entity.isAlive() && isMarkedCreeperForThisChamber(entity, null))) {
				if (!migrated.contains(creeper.getUUID()))
					releaseManagedCreeper(creeper);
			}
		}

		legacyMarkedCreepers.clear();
		containedDataVersion = CONTAINED_DATA_VERSION;
		pendingAppearances.removeIf(pending -> !storedCreepers.containsKey(pending.packagerPos));
		setChanged();
		notifyUpdate();
	}

	@Nullable
	private PendingPackaging findPendingPackaging(BlockPos packagerPos) {
		for (PendingPackaging pending : pendingPackagings)
			if (pending.packagerPos.equals(packagerPos))
				return pending;
		return null;
	}

	private static void clearLegacyMachineMutations(ItemStack box) {
		CBItemData.edit(box, root -> {
			if (!root.contains("CapturedEntity", Tag.TAG_COMPOUND))
				return;
			CompoundTag entityData = root.getCompound("CapturedEntity");
			entityData.remove("NoAI");
			entityData.remove("PersistenceRequired");
			entityData.remove("Invisible");
		});
		CapturedEntityBoxHelper.clearLegacyCreeperBlastChamberMarkers(box);
	}

	private void clearPackagerAnimationState(BlockPos packagerPos) {
		BioPackagerBlockEntity packager = getPackager(packagerPos);
		if (packager == null)
			return;
		packager.heldBox = ItemStack.EMPTY;
		packager.animationTicks = 0;
		packager.notifyUpdate();
		packager.setChanged();
	}

	private void releaseManagedCreeper(Creeper creeper) {
		clearMarkedCreeperData(creeper);
		creeper.setNoAi(false);
		CapturedEntityBoxHelper.unmarkAiDisabledByMod(creeper);
		creeper.setInvisible(false);
		creeper.setDeltaMovement(Vec3.ZERO);
		creeper.fallDistance = 0;
		((MobAccessor) creeper).createBiotech$setPersistenceRequired(false);
	}

	private void clearMarkedCreeperData(Entity entity) {
		CompoundTag persistentData = entity.getPersistentData();
		if (!persistentData.contains(DATA_ROOT, Tag.TAG_COMPOUND))
			return;

		CompoundTag data = persistentData.getCompound(DATA_ROOT);
		data.remove(MARKED_CREEPER_TAG);
		data.remove(CONTROLLER_POS_TAG);
		data.remove(PACKAGER_POS_TAG);
		if (data.isEmpty()) {
			persistentData.remove(DATA_ROOT);
			return;
		}
		persistentData.put(DATA_ROOT, data);
	}

	private void dropBox(PendingUnpack pending) {
		Level level = getLevel();
		if (level == null || pending.boxStack.isEmpty() || !pending.returnBox)
			return;
		if (pending.transitioned)
			return;
		ItemStack toDrop = pending.boxStack.copy();
		Block.popResource(level, pending.packagerPos.above(), toDrop);
	}

	private void dropPackagedBox(ItemStack stack, BlockPos pos) {
		Level level = getLevel();
		if (level == null || stack.isEmpty())
			return;
		Block.popResource(level, pos.above(), stack.copy());
	}

	private void restorePendingPackaging(PendingPackaging pending, boolean dropIfMissing) {
		if (storedCreepers.containsKey(pending.packagerPos))
			return;
		if (dropIfMissing)
			dropPackagedBox(pending.boxStack, pending.packagerPos);
	}

	private List<BlockPos> getPackagerPositions() {
		return cachedPackagerPositions;
	}

	private boolean isPackagerPartOfStructure(BlockPos packagerPos) {
		Level level = getLevel();
		if (level == null || !structureValid || structureOrigin == null || packagerPos.getY() != structureOrigin.getY())
			return false;

		int x = packagerPos.getX() - structureOrigin.getX();
		int z = packagerPos.getZ() - structureOrigin.getZ();
		return x >= 1 && x < structureSize - 1
			&& z >= 1 && z < structureSize - 1
			&& level.getBlockState(packagerPos).is(CBBlocks.BIO_PACKAGER.get());
	}

	private boolean isPressPartOfStructure(BlockPos pressPos) {
		Level level = getLevel();
		if (level == null || !structureValid || structureOrigin == null || pressPos.getY() != structureOrigin.getY() + 3)
			return false;

		int x = pressPos.getX() - structureOrigin.getX();
		int z = pressPos.getZ() - structureOrigin.getZ();
		return x >= 1 && x < structureSize - 1
			&& z >= 1 && z < structureSize - 1
			&& AllBlocks.MECHANICAL_PRESS.has(level.getBlockState(pressPos));
	}

	public static boolean shouldMutePressActivationSound(MechanicalPressBlockEntity press) {
		Level level = press.getLevel();
		if (level == null)
			return false;

		BlockPos pressPos = press.getBlockPos();
		int maxSize = getMaxSize();
		BlockPos min = pressPos.offset(-maxSize, -3, -maxSize);
		BlockPos max = pressPos.offset(maxSize, 0, maxSize);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int y = min.getY(); y <= max.getY(); y++) {
			for (int x = min.getX(); x <= max.getX(); x++) {
				for (int z = min.getZ(); z <= max.getZ(); z++) {
					cursor.set(x, y, z);
					if (!(level.getBlockEntity(cursor) instanceof CreeperBlastChamberBlockEntity chamber))
						continue;
					if (chamber.structureValid && chamber.isPressPartOfStructure(pressPos))
						return true;
				}
			}
		}
		return false;
	}

	@Nullable
	public static BlockPos findGoggleInformationSource(Level level, BlockPos structurePos) {
		CreeperBlastChamberBlockEntity controller = findLoadedClientStructureController(level, structurePos);
		return controller != null ? controller.getBlockPos() : null;
	}

	@Nullable
	private static CreeperBlastChamberBlockEntity findLoadedClientStructureController(Level level,
		BlockPos structurePos) {
		if (!level.isClientSide)
			return null;

		Set<BlockPos> controllerPositions = CLIENT_LOADED_CHAMBERS.get(level);
		if (controllerPositions == null || controllerPositions.isEmpty())
			return null;

		Iterator<BlockPos> iterator = controllerPositions.iterator();
		while (iterator.hasNext()) {
			BlockPos controllerPos = iterator.next();
			BlockEntity blockEntity = level.getBlockEntity(controllerPos);
			if (!(blockEntity instanceof CreeperBlastChamberBlockEntity chamber)) {
				iterator.remove();
				continue;
			}
			if (chamber.isStructurePart(structurePos))
				return chamber;
		}

		if (controllerPositions.isEmpty())
			CLIENT_LOADED_CHAMBERS.remove(level);
		return null;
	}

	@Nullable
	public static CreeperBlastChamberBlockEntity findStructureController(Level level, BlockPos structurePos) {
		BlockEntity directBlockEntity = level.getBlockEntity(structurePos);
		if (directBlockEntity instanceof CreeperBlastChamberBlockEntity chamber
			&& chamber.isStructurePart(structurePos))
			return chamber;

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int y = structurePos.getY() - 3; y <= structurePos.getY() + 3; y++) {
			int maxSize = getMaxSize();
			for (int x = structurePos.getX() - maxSize; x <= structurePos.getX() + maxSize; x++) {
				for (int z = structurePos.getZ() - maxSize; z <= structurePos.getZ() + maxSize; z++) {
					cursor.set(x, y, z);
					if (!(level.getBlockEntity(cursor) instanceof CreeperBlastChamberBlockEntity chamber))
						continue;
					if (chamber.isStructurePart(structurePos))
						return chamber;
				}
			}
		}

		return null;
	}
	public static InteractionResult onStructureCasingWrenched(Level level, BlockPos clickedPos, @Nullable Player player) {
		CreeperBlastChamberBlockEntity chamber = findStructureController(level, clickedPos);
		if (chamber == null)
			return InteractionResult.FAIL;
		if (chamber.canToggleReservedChainDriveAt(clickedPos)) {
			if (level.isClientSide)
				return InteractionResult.SUCCESS;
			chamber.toggleReservedChainDrive(clickedPos);
			return InteractionResult.SUCCESS;
		}
		if (!chamber.canToggleCreeperFaceAt(clickedPos))
			return InteractionResult.FAIL;
		if (level.isClientSide)
			return InteractionResult.SUCCESS;
		chamber.toggleCreeperFaceVisible(player);
		return InteractionResult.SUCCESS;
	}

	private boolean isStructurePart(BlockPos pos) {
		return structureValid && structureOrigin != null && isWithinStructureVolume(pos, structureOrigin, structureSize);
	}

	private boolean canToggleCreeperFaceAt(BlockPos pos) {
		if (!isStructurePart(pos) || level == null)
			return false;
		BlockState state = level.getBlockState(pos);
		boolean isController = pos.equals(getBlockPos()) && state.getBlock() instanceof CreeperBlastChamberBlock;
		boolean isCasing = state.is(CBBlocks.EXPLOSION_PROOF_CASING.get());
		if (!isController && !isCasing)
			return false;
		return !isReservedChainDrivePosition(pos);
	}

	private boolean canToggleReservedChainDriveAt(BlockPos pos) {
		if (!isStructurePart(pos) || level == null || !isReservedChainDrivePosition(pos))
			return false;
		BlockState state = level.getBlockState(pos);
		return state.is(CBBlocks.EXPLOSION_PROOF_CASING.get()) || state.is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get());
	}

	private boolean isReservedChainDrivePosition(BlockPos pos) {
		if (!structureValid || structureOrigin == null || structurePressAxis == null)
			return false;
		int x = pos.getX() - structureOrigin.getX();
		int y = pos.getY() - structureOrigin.getY();
		int z = pos.getZ() - structureOrigin.getZ();
		if (x < 0 || x >= structureSize || y < 0 || y >= 4 || z < 0 || z >= structureSize)
			return false;
		return isReservedChainDrivePosition(x, y, z, structureSize, structurePressAxis);
	}

	private void toggleReservedChainDrive(BlockPos pos) {
		if (level == null || !structureValid || structureOrigin == null || structurePressAxis == null)
			return;

		BlockState state = level.getBlockState(pos);
		if (state.is(CBBlocks.EXPLOSION_PROOF_CASING.get())) {
			Axis alongEdgeAxis = structurePressAxis == Axis.X ? Axis.Z : Axis.X;
			BlockState chainState = CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get().defaultBlockState()
				.setValue(BlockStateProperties.AXIS, structurePressAxis)
				.setValue(ChainDriveBlock.CONNECTED_ALONG_FIRST_COORDINATE, structurePressAxis == Axis.Z);
			level.setBlock(pos, chainState, 3);
			updateChainDriveState(level, pos, structurePressAxis, alongEdgeAxis);
		} else if (state.is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get())) {
			level.setBlock(pos, CBBlocks.EXPLOSION_PROOF_CASING.get().defaultBlockState(), 3);
		} else {
			return;
		}

		refreshStructureKinetics(level, structureOrigin, structureSize);
	}

	private void toggleCreeperFaceVisible(@Nullable Player player) {
		creeperFaceVisible = !creeperFaceVisible;
		setChanged();
		notifyUpdate();
		if (player != null) {
			player.displayClientMessage(Component.translatable(creeperFaceVisible
				? "create_biotech.creeper_blast_chamber.creeper_face.shown"
				: "create_biotech.creeper_blast_chamber.creeper_face.hidden"), true);
		}
	}

	public float getOverloadFraction() {
		return overloadPoints / (float) getOverloadPointsCap();
	}

	private static int getMinSize() {
		return CBConfigs.SERVER.creeperBlastChamber.minSize.get();
	}

	private static int getMaxSize() {
		return Math.max(getMinSize(), CBConfigs.SERVER.creeperBlastChamber.maxSize.get());
	}

	private static int getOverloadThresholdRpm() {
		return CBConfigs.SERVER.creeperBlastChamber.overloadThresholdRpm.get();
	}

	private static int getOverloadPointsCap() {
		return Math.max(1, CBConfigs.SERVER.creeperBlastChamber.overloadPointsCap.get());
	}

	private static int getOverloadDecayPointsPerSecond() {
		return CBConfigs.SERVER.creeperBlastChamber.overloadDecayPointsPerSecond.get();
	}

	private static int getOverloadTntEquivalentPerCreeper() {
		return CBConfigs.SERVER.creeperBlastChamber.overloadTntEquivalentPerCreeper.get();
	}

	private static int getChargedCreeperEquivalentMultiplier() {
		return CBConfigs.SERVER.creeperBlastChamber.chargedCreeperEquivalentMultiplier.get();
	}

	private static float getTntExplosionPower() {
		return CBConfigs.SERVER.creeperBlastChamber.tntExplosionPower.get()
			.floatValue();
	}

	private static int getReadyOutputTimeout() {
		return CBConfigs.SERVER.creeperBlastChamber.readyOutputTimeout.get();
	}

	private static boolean isOverloadExplosionsEnabled() {
		return CBConfigs.SERVER.creeperBlastChamber.enableOverloadExplosions.get();
	}

	private static boolean areExplosionParticlesEnabled() {
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
		if (isPausedForPartialChunkUnload())
			return "create_biotech.creeper_blast_chamber.tooltip.status.chunk_unloaded";

		List<MechanicalPressBlockEntity> presses = getMechanicalPresses();
		if (hasUnworkablePresses(presses))
			return "create_biotech.creeper_blast_chamber.tooltip.status.blocked_press";

		if (isCurrentlyOverloading())
			return "create_biotech.creeper_blast_chamber.tooltip.status.overloading";

		MechanicalPressBlockEntity masterPress = getMasterPress(presses);
		if (masterPress == null || masterPress.getSpeed() == 0)
			return "create_biotech.creeper_blast_chamber.tooltip.status.insufficient_stress";

		return "create_biotech.creeper_blast_chamber.tooltip.status.working";
	}

	private ChatFormatting getStatusColor() {
		if (isPausedForPartialChunkUnload())
			return ChatFormatting.YELLOW;

		if (hasUnworkablePresses(getMechanicalPresses()))
			return ChatFormatting.RED;

		if (isCurrentlyOverloading())
			return ChatFormatting.DARK_RED;

		MechanicalPressBlockEntity masterPress = getMasterPress(getMechanicalPresses());
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
		return AllBlocks.MECHANICAL_PRESS.has(pressState) ? getPressShaftAxis(pressState) : null;
	}

	private boolean isPackagerReserved(BlockPos packagerPos) {
		for (PendingUnpack pending : pendingUnpacks) {
			if (pending.packagerPos.equals(packagerPos))
				return true;
		}
		return false;
	}

	private AABB getMarkedCreeperSearchBounds() {
		if (structureOrigin == null)
			return new AABB(getBlockPos()).inflate(32);
		return AABB.encapsulatingFullBlocks(structureOrigin,
			structureOrigin.offset(structureSize - 1, 3, structureSize - 1)).inflate(32);
	}

	private boolean isMarkedCreeperForThisChamber(Creeper creeper, @Nullable BlockPos packagerPos) {
		CompoundTag data = getExistingCreateBiotechData(creeper);
		if (data == null || !data.getBoolean(MARKED_CREEPER_TAG))
			return false;
		if (data.getLong(CONTROLLER_POS_TAG) != getBlockPos().asLong())
			return false;
		return packagerPos == null || data.getLong(PACKAGER_POS_TAG) == packagerPos.asLong();
	}

	private boolean isPackagerAppearing(BlockPos packagerPos) {
		for (PendingAppearance pending : pendingAppearances) {
			if (pending.packagerPos.equals(packagerPos))
				return true;
		}
		return false;
	}

	@Nullable
	private BioPackagerBlockEntity getPackager(BlockPos packagerPos) {
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
		workingRenderCreepers.clear();
		if (!structureValid || getLevel() == null || storedCreepers.isEmpty())
			return workingRenderCreepers;
		for (StoredCreeper stored : storedCreepers.values()) {
			BlockPos packagerPos = stored.packagerPos();
			if (isPackagerAppearing(packagerPos) || isPackagerPackaging(packagerPos))
				continue;
			workingRenderCreepers
				.add(new RenderManagedCreeper(packagerPos, stored.normalizedPayloadBox(), stored.renderSeed(),
					stored.defaultYaw()));
		}
		return workingRenderCreepers;
	}

	/** @see #getWorkingRenderCreepers() for the reuse contract. */
	List<RenderCreeperAnimation> getRenderAnimations() {
		renderAnimations.clear();
		if (pendingAppearances.isEmpty() && pendingPackagings.isEmpty())
			return renderAnimations;
		for (PendingAppearance pending : pendingAppearances) {
			StoredCreeper stored = storedCreepers.get(pending.packagerPos);
			if (stored != null)
				renderAnimations.add(new RenderCreeperAnimation(pending.packagerPos, stored.normalizedPayloadBox(),
					stored.renderSeed(), stored.defaultYaw(), pending.ticksRemaining, pending.totalTicks, false));
		}
		for (PendingPackaging pending : pendingPackagings) {
			if (pending.ticksRemaining <= BioPackagerBlockEntity.getCycleTicks())
				continue;
			int outwardTicksRemaining = pending.ticksRemaining - BioPackagerBlockEntity.getCycleTicks();
			StoredCreeper stored = storedCreepers.get(pending.packagerPos);
			ItemStack payload = stored == null ? pending.boxStack : stored.normalizedPayloadBox();
			long renderSeed = stored == null ? pending.packagerPos.asLong() : stored.renderSeed();
			float defaultYaw = stored == null ? Direction.NORTH.toYRot() : stored.defaultYaw();
			renderAnimations.add(new RenderCreeperAnimation(pending.packagerPos, payload, renderSeed, defaultYaw,
				outwardTicksRemaining, BioPackagerBlockEntity.getCycleTicks(), true));
		}
		return renderAnimations;
	}

	public static float getClientWorkingCreeperCompression(Creeper creeper, float partialTicks) {
		CompoundTag biotechData = getExistingCreateBiotechData(creeper);
		if (biotechData != null) {
			if (biotechData.contains(PONDER_COMPRESSION_ANIM_TAG, Tag.TAG_COMPOUND)) {
				return computePonderCompressionAnim(creeper, partialTicks,
					biotechData.getCompound(PONDER_COMPRESSION_ANIM_TAG));
			}
			if (biotechData.contains(PONDER_COMPRESSION_TAG, Tag.TAG_FLOAT)) {
				return Mth.clamp(biotechData.getFloat(PONDER_COMPRESSION_TAG), 0f, 1f);
			}
		}
		return 0f;
	}

	private static float computePonderCompressionAnim(Creeper creeper, float partialTicks, CompoundTag anim) {
		int startTick = anim.getInt("StartTick");
		int rtStart = anim.getInt("RtStart");
		int tickSpeed = anim.getInt("TickSpeed");
		float peak = anim.contains("Peak", Tag.TAG_FLOAT) ? anim.getFloat("Peak") : 1f;
		float elapsed = (creeper.tickCount + partialTicks) - startTick;
		if (elapsed < 0)
			return 0f;
		float rt = rtStart + elapsed * tickSpeed;
		if (rt < 0f || rt >= 240f)
			return 0f;
		float progress;
		if (rt < 160f)
			progress = (float) Math.pow(rt / 240f * 2f, 3);
		else
			progress = (240f - rt) / 240f * 3f;
		progress = Mth.clamp(progress, 0f, 1f);
		return Mth.clamp((progress - CLIENT_PRESS_EFFECT_START_OFFSET)
			/ (1f - CLIENT_PRESS_EFFECT_START_OFFSET), 0f, 1f) * peak;
	}

	public static float getSynchronizedPressHeadProgress(@Nullable MechanicalPressBlockEntity press, float partialTicks) {
		if (press == null)
			return 0f;

		PressingBehaviour pressingBehaviour = press.getPressingBehaviour();
		if (pressingBehaviour.mode == null)
			return 0f;

		Level level = press.getLevel();
		if (level == null || !level.isClientSide)
			return getLocalPressHeadProgress(pressingBehaviour, partialTicks);

		BlockPos controllerPos = CLIENT_PRESS_CONTROLLERS.get(clientPressKey(level, press.getBlockPos()));
		if (controllerPos == null)
			return getLocalPressHeadProgress(pressingBehaviour, partialTicks);

		BlockEntity blockEntity = level.getBlockEntity(controllerPos);
		if (!(blockEntity instanceof CreeperBlastChamberBlockEntity chamber)
			|| !chamber.structureValid
			|| !chamber.isPressPartOfStructure(press.getBlockPos())) {
			return getLocalPressHeadProgress(pressingBehaviour, partialTicks);
		}

		chamber.resolveRenderPressState();
		if (chamber.renderPressesUnworkable || chamber.renderMasterPress == null)
			return getLocalPressHeadProgress(pressingBehaviour, partialTicks);

		MechanicalPressBlockEntity masterPress = chamber.renderMasterPress;

		return getLocalPressHeadProgress(masterPress.getPressingBehaviour(), partialTicks);
	}

	public static float getSynchronizedPressHeadOffset(@Nullable MechanicalPressBlockEntity press, float partialTicks) {
		if (press == null)
			return 0f;
		PressingBehaviour pressingBehaviour = press.getPressingBehaviour();
		if (pressingBehaviour.mode == null)
			return 0f;
		return getSynchronizedPressHeadProgress(press, partialTicks) * pressingBehaviour.mode.headOffset;
	}

	private static float getCompressionFromPressOffset(float pressOffset) {
		return Mth.clamp((pressOffset - CLIENT_PRESS_EFFECT_START_OFFSET) / (1f - CLIENT_PRESS_EFFECT_START_OFFSET), 0f,
			1f);
	}

	private static float getLocalPressHeadProgress(PressingBehaviour pressingBehaviour, float partialTicks) {
		if (pressingBehaviour.mode == null || !pressingBehaviour.running)
			return 0f;

		int runningTicks = Math.abs(pressingBehaviour.runningTicks);
		float renderedTick = Mth.lerp(partialTicks, pressingBehaviour.prevRunningTicks, runningTicks);
		if (runningTicks < 160)
			return (float) Mth.clamp(Math.pow(renderedTick / 240f * 2f, 3), 0d, 1d);
		return Mth.clamp((240f - renderedTick) / 240f * 3f, 0f, 1f);
	}

	private static CompoundTag getExistingCreateBiotechData(Entity entity) {
		CompoundTag persistentData = entity.getPersistentData();
		return persistentData.contains(DATA_ROOT) ? persistentData.getCompound(DATA_ROOT) : null;
	}

	private static class PendingUnpack {
		private final BlockPos packagerPos;
		private final ItemStack boxStack;
		private int ticksRemaining;
		private boolean transitioned;
		private final boolean returnBox;

		private PendingUnpack(BlockPos packagerPos, ItemStack boxStack, int ticksRemaining, boolean transitioned,
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

	private abstract static class TimedAnimation {
		protected int ticksRemaining;
		protected final int totalTicks;

		private TimedAnimation(int ticksRemaining, int totalTicks) {
			this.ticksRemaining = ticksRemaining;
			this.totalTicks = totalTicks;
		}
	}

	private static class PendingAppearance extends TimedAnimation {
		private final BlockPos packagerPos;

		private PendingAppearance(BlockPos packagerPos, int ticksRemaining) {
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

	private static class PendingPackaging extends TimedAnimation {
		private final BlockPos packagerPos;
		private final ItemStack boxStack;
		private boolean transitioned;

		private PendingPackaging(BlockPos packagerPos, ItemStack boxStack, int ticksRemaining,
			boolean transitioned) {
			super(ticksRemaining, BioPackagerBlockEntity.getCycleTicks() * 2);
			this.packagerPos = packagerPos;
			this.boxStack = boxStack;
			this.transitioned = transitioned;
		}

		private CompoundTag write(HolderLookup.Provider registries) {
			CompoundTag tag = new CompoundTag();
			tag.putLong("PackagerPos", packagerPos.asLong());
			tag.put("Box", boxStack.save(registries));
			tag.putInt("TicksRemaining", ticksRemaining);
			tag.putBoolean("Transitioned", transitioned);
			return tag;
		}

		private static PendingPackaging read(CompoundTag tag, HolderLookup.Provider registries) {
			return new PendingPackaging(
				BlockPos.of(tag.getLong("PackagerPos")),
				ItemStack.parseOptional(registries, tag.getCompound("Box")),
				tag.getInt("TicksRemaining"),
				tag.getBoolean("Transitioned"));
		}
	}

	private static class ReadyOutput {
		private final BlockPos packagerPos;
		private final ItemStack boxStack;
		private int ticksRemaining;

		private ReadyOutput(BlockPos packagerPos, ItemStack boxStack, int ticksRemaining) {
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

	private enum ChamberCreeperKind {
		NONE,
		NORMAL,
		CHARGED,
		MIXED;

		private ChamberCreeperKind merge(ChamberCreeperKind other) {
			if (other == NONE || other == this)
				return this;
			if (this == NONE)
				return other;
			return MIXED;
		}
	}

	private record CreeperCountSummary(int normalCount, int chargedCount) {
		private int totalCount() {
			return normalCount + chargedCount;
		}

		private int getExplosionEquivalentCount() {
			return normalCount + chargedCount * getChargedCreeperEquivalentMultiplier();
		}
	}

	private static class InsertResult {
		private final boolean accepted;
		private final ItemStack remainder;

		private InsertResult(boolean accepted, ItemStack remainder) {
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

	private enum ProcessingAttemptResult {
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
			ItemStack output = getActualControllerOutput();
			return output == null ? ItemStack.EMPTY : output;
		}

		@Override
		public ItemStack getDeferredExtractionPreview(int slot) {
			validateSlot(slot);
			return getDeferredControllerOutputPreview();
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			validateSlot(slot);
			if (stack.isEmpty())
				return ItemStack.EMPTY;

			InsertResult result = tryInsertLargeCreeperBox(stack, simulate, true);
			return result.accepted() ? result.remainder() : stack;
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			validateSlot(slot);
			if (amount <= 0)
				return ItemStack.EMPTY;

			requestControllerOutput();
			ItemStack extracted = extractReadyOutput(simulate);
			if (extracted != null)
				return extracted;

			if (!simulate)
				tickControllerOutputRequest();
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
			return isValidLargeCreeperBox(stack);
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


	private record StoredCreeper(BlockPos packagerPos, ItemStack normalizedPayloadBox, boolean charged,
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

	private record StoredCreeperTarget(BlockPos packagerPos, StoredCreeper stored) {}

	private record VaultRoleAssignment(BlockPos inputVaultController, BlockPos outputVaultController) {}

	private record StructureScanResult(int size, BlockPos origin, BlockPos inputVaultController,
		BlockPos outputVaultController) {}
}
