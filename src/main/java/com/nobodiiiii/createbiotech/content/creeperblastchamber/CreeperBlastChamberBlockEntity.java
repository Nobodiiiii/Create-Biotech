package com.nobodiiiii.createbiotech.content.creeperblastchamber;

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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.client.CreeperBlastChamberClientSoundHandler;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerBlockEntity;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.explosionproofitemvault.ExplosionProofItemVaultBlock;
import com.nobodiiiii.createbiotech.content.explosionproofitemvault.ExplosionProofItemVaultBlockEntity;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.mixin.MobAccessor;
import com.nobodiiiii.createbiotech.mixin.client.CreeperAccessor;
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
import com.simibubi.create.foundation.blockEntity.SyncedBlockEntity;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.animation.AnimationTickHolder;
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
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.registries.ForgeRegistries;

public class CreeperBlastChamberBlockEntity extends SyncedBlockEntity implements IHaveGoggleInformation {

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
	private static final String INPUT_VAULT_CONTROLLER_TAG = "InputVaultController";
	private static final String OUTPUT_VAULT_CONTROLLER_TAG = "OutputVaultController";
	private static final String CONFIGURED_INPUT_VAULT_CONTROLLER_TAG = "ConfiguredInputVaultController";
	private static final String CREEPER_FACE_VISIBLE_TAG = "CreeperFaceVisible";
	private static final String OVERLOAD_POINTS_TAG = "OverloadPoints";
	private static final int CREEPER_ENTRY_ANIMATION_TICKS = 5;
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
	private static final RecipeWrapper CRUSHING_RECIPE_WRAPPER = new RecipeWrapper(new ItemStackHandler(1));
	private static final RecipeWrapper HIGH_PRESSURE_RECIPE_WRAPPER = new RecipeWrapper(new ItemStackHandler(1));
	private static final Map<UUID, ClientTrackedCreeper> CLIENT_TRACKED_CREEPERS = new HashMap<>();
	private static final Map<Long, BlockPos> CLIENT_PRESS_CONTROLLERS = new HashMap<>();

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
	private final Map<UUID, BlockPos> syncedMarkedCreepers = new HashMap<>();
	private boolean controllerOutputRequested;
	private boolean creeperFaceVisible = true;
	private final Map<Long, Float> clientPressOffsets = new HashMap<>();
	private final Set<Long> clientReturnEffectsArmed = new HashSet<>();
	private final Set<UUID> clientTrackedCreeperUuids = new HashSet<>();
	private final Set<Long> clientTrackedPressPositions = new HashSet<>();
	private final ChamberInputHandler inputHandler = new ChamberInputHandler();
	private final LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> inputHandler);

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

		be.tickPendingUnpacks();
		be.tickPendingAppearances();
		be.tickPendingPackagings();
		be.cleanupMissingMarkedCreepers();
		be.tickControllerOutputRequest();
		be.tickReadyOutputs();
		be.tickPressProcessing();
		be.tickOverloadDecay(level);
		be.syncFormedBlockState();

		if (be.recheckTimer > 0) {
			be.recheckTimer--;
			return;
		}
		be.recheckTimer = 20;

		be.tryDetectStructure();
	}

	@Override
	public AABB getRenderBoundingBox() {
		if (structureValid && structureOrigin != null)
			return new AABB(structureOrigin, structureOrigin.offset(structureSize, 4, structureSize)).inflate(1);
		return new AABB(worldPosition).inflate(1);
	}

	@Nonnull
	@Override
	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
		if (cap == ForgeCapabilities.ITEM_HANDLER)
			return itemCapability.cast();
		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		clearClientTrackedCreepers();
		clearClientTrackedPresses();
		super.invalidateCaps();
		itemCapability.invalidate();
	}

	@Override
	public void setRemoved() {
		clearClientTrackedCreepers();
		clearClientTrackedPresses();
		super.setRemoved();
	}

	private void tryDetectStructure() {
		Level level = getLevel();
		if (level == null)
			return;
		BlockPos pos = getBlockPos();

		for (int s = getMinSize(); s <= getMaxSize(); s++) {
			StructureScanResult result = findStructure(level, pos, s);
			if (result != null) {
				boolean wasValid = structureValid;
				int oldSize = structureSize;
				BlockPos oldOrigin = structureOrigin;
				BlockPos oldInputVault = inputVaultController;
				BlockPos oldOutputVault = outputVaultController;
				setStructure(level, true, result.size, result.origin, result.inputVaultController,
					result.outputVaultController);
				if (!wasValid || oldSize != result.size || !Objects.equals(result.origin, oldOrigin)
					|| !Objects.equals(inputVaultController, oldInputVault)
					|| !Objects.equals(outputVaultController, oldOutputVault))
					onStructureFormed(level, result.size, result.origin);
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
		BlockPos previousInput = inputVaultController;
		BlockPos previousOutput = outputVaultController;
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
		syncFormedBlockState();
		syncVaultRoleBindings(level, previousInput, previousOutput);
		notifyUpdate();
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
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putBoolean("StructureValid", structureValid);
		tag.putInt("StructureSize", structureSize);
		ListTag pendingList = new ListTag();
		for (PendingUnpack pending : pendingUnpacks)
			pendingList.add(pending.write());
		tag.put(PENDING_UNPACKS_TAG, pendingList);
		ListTag pendingPackagingList = new ListTag();
		for (PendingPackaging pending : pendingPackagings)
			pendingPackagingList.add(pending.write());
		tag.put(PENDING_PACKAGINGS_TAG, pendingPackagingList);
		ListTag pendingAppearanceList = new ListTag();
		for (PendingAppearance pending : pendingAppearances)
			pendingAppearanceList.add(pending.write());
		tag.put(PENDING_APPEARANCES_TAG, pendingAppearanceList);
		ListTag readyOutputList = new ListTag();
		for (ReadyOutput readyOutput : readyOutputs)
			readyOutputList.add(readyOutput.write());
		tag.put(READY_OUTPUTS_TAG, readyOutputList);
		ListTag markedCreeperList = new ListTag();
		for (Map.Entry<UUID, BlockPos> entry : syncedMarkedCreepers.entrySet())
			markedCreeperList.add(new TrackedMarkedCreeper(entry.getKey(), entry.getValue()).write());
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
	public void load(CompoundTag tag) {
		super.load(tag);
		structureValid = tag.getBoolean("StructureValid");
		structureSize = tag.getInt("StructureSize");
		pausedByUnloadedChunks = false;
		pendingUnpacks.clear();
		for (Tag pendingTag : tag.getList(PENDING_UNPACKS_TAG, Tag.TAG_COMPOUND))
			pendingUnpacks.add(PendingUnpack.read((CompoundTag) pendingTag));
		pendingPackagings.clear();
		for (Tag pendingTag : tag.getList(PENDING_PACKAGINGS_TAG, Tag.TAG_COMPOUND))
			pendingPackagings.add(PendingPackaging.read((CompoundTag) pendingTag));
		pendingAppearances.clear();
		for (Tag pendingTag : tag.getList(PENDING_APPEARANCES_TAG, Tag.TAG_COMPOUND))
			pendingAppearances.add(PendingAppearance.read((CompoundTag) pendingTag));
		readyOutputs.clear();
		for (Tag readyOutputTag : tag.getList(READY_OUTPUTS_TAG, Tag.TAG_COMPOUND))
			readyOutputs.add(ReadyOutput.read((CompoundTag) readyOutputTag));
		syncedMarkedCreepers.clear();
		for (Tag markedCreeperTag : tag.getList(MARKED_CREEPERS_TAG, Tag.TAG_COMPOUND)) {
			TrackedMarkedCreeper tracked = TrackedMarkedCreeper.read((CompoundTag) markedCreeperTag);
			syncedMarkedCreepers.put(tracked.creeperUuid, tracked.packagerPos);
		}
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
	}

	public void forceStructureCheck() {
		recheckTimer = 0;
		tryDetectStructure();
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
		Optional<ProcessingRecipe<RecipeWrapper>> recipe = findCrushingRecipe(stackToProcess);
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

	private Optional<ProcessingRecipe<RecipeWrapper>> findCrushingRecipe(ItemStack stack) {
		Level level = getLevel();
		if (level == null || stack.isEmpty())
			return Optional.empty();

		CRUSHING_RECIPE_WRAPPER.setItem(0, stack);
		Optional<ProcessingRecipe<RecipeWrapper>> recipe = AllRecipeTypes.CRUSHING.find(CRUSHING_RECIPE_WRAPPER, level);
		if (recipe.isEmpty())
			recipe = AllRecipeTypes.MILLING.find(CRUSHING_RECIPE_WRAPPER, level);
		return recipe;
	}

	private List<ItemStack> rollProcessingResults(ItemStack input, ProcessingRecipe<RecipeWrapper> recipe) {
		List<ItemStack> outputs = new ArrayList<>();
		for (int roll = 0; roll < input.getCount(); roll++) {
			for (ItemStack rolledResult : recipe.rollResults())
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

		HIGH_PRESSURE_RECIPE_WRAPPER.setItem(0, stack);
		return level.getRecipeManager()
			.getRecipeFor(CBRecipeTypes.CREEPER_BLAST_CHAMBER_HIGH_PRESSURE_TYPE.get(), HIGH_PRESSURE_RECIPE_WRAPPER,
				level);
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

				if (!ItemHandlerHelper.canItemStacksStack(simulatedStack, remainder))
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
		return vault.getCapability(ForgeCapabilities.ITEM_HANDLER, null)
			.orElse(null);
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
		List<MechanicalPressBlockEntity> presses = new ArrayList<>();
		if (!structureValid || structureOrigin == null)
			return presses;

		for (int x = 1; x < structureSize - 1; x++) {
			for (int z = 1; z < structureSize - 1; z++) {
				MechanicalPressBlockEntity press = getMechanicalPress(structureOrigin.offset(x, 3, z));
				if (press != null)
					presses.add(press);
			}
		}
		return presses;
	}

	float getRenderedPressHeadOffset(BlockPos packagerPos, float partialTicks) {
		MechanicalPressBlockEntity press = getMechanicalPress(packagerPos.above(3));
		return getSynchronizedPressHeadOffset(press, partialTicks);
	}

	float getRenderedCreeperEffectPressOffset(BlockPos packagerPos, float partialTicks) {
		MechanicalPressBlockEntity press = getMechanicalPress(packagerPos.above(3));
		MechanicalPressBlockEntity masterPress = getMasterPress(getMechanicalPresses());
		if (press == null || masterPress == null || masterPress.getSpeed() == 0)
			return 0f;
		return getSynchronizedPressHeadOffset(press, partialTicks);
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

		if (!structureValid || structureOrigin == null || scanStructure(level, structureOrigin, structureSize) == null) {
			if (structureValid && structureOrigin != null) {
				int oldSize = structureSize;
				BlockPos oldOrigin = structureOrigin;
				Axis oldPressAxis = structurePressAxis;
				setStructure(level, false, 0, null, null, null);
				onStructureBroken(level, oldSize, oldOrigin, oldPressAxis);
			}
			return;
		}

		boolean changed = false;
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

			if (!pending.transitioned && pending.ticksRemaining <= BioPackagerBlockEntity.CYCLE) {
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
				packager.animationTicks = BioPackagerBlockEntity.CYCLE;
				packager.notifyUpdate();
				packager.setChanged();
			}

			if (pending.ticksRemaining > 0)
				continue;

			if (pending.returnBox) {
				ItemStack emptyBox = pending.boxStack.copy();
				emptyBox.setCount(1);
				CapturedEntityBoxHelper.clearCapturedEntity(emptyBox);
				Block.popResource(level, pending.packagerPos.above(), emptyBox);
			}
			clearPackagerAnimationState(pending.packagerPos);
			iterator.remove();
			changed = true;
		}

		if (changed)
			setChanged();
	}

	private void tickPendingAppearances() {
		Level level = getLevel();
		if (level == null || pendingAppearances.isEmpty())
			return;

		boolean changed = false;
		Iterator<PendingAppearance> iterator = pendingAppearances.iterator();
		while (iterator.hasNext()) {
			PendingAppearance pending = iterator.next();
			if (pending.ticksRemaining > 0)
				pending.ticksRemaining--;
			if (pending.ticksRemaining > 0)
				continue;

			Creeper creeper = findMarkedCreeperByUuid(pending.creeperUuid, pending.packagerPos);
			if (creeper != null)
				creeper.setInvisible(false);
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

			if (!pending.transitioned && pending.ticksRemaining <= BioPackagerBlockEntity.CYCLE) {
				pending.transitioned = true;
				Creeper creeper = findMarkedCreeperByUuid(pending.creeperUuid, pending.packagerPos);
				if (creeper != null) {
					creeper.setInvisible(true);
					creeper.setDeltaMovement(Vec3.ZERO);
					creeper.fallDistance = 0;
				}
				packager.heldBox = pending.boxStack.copy();
				packager.previouslyUnwrapped = ItemStack.EMPTY;
				packager.animationInward = true;
				packager.animationTicks = BioPackagerBlockEntity.CYCLE;
				packager.notifyUpdate();
				packager.setChanged();
			}

			if (pending.ticksRemaining > 0)
				continue;

			packager.heldBox = ItemStack.EMPTY;
			packager.animationTicks = 0;
			packager.notifyUpdate();
			packager.setChanged();
			Creeper creeper = findMarkedCreeperByUuid(pending.creeperUuid, pending.packagerPos);
			if (creeper != null) {
				removeTrackedMarkedCreeper(creeper.getUUID());
				creeper.discard();
			}
			readyOutputs.add(new ReadyOutput(pending.packagerPos, pending.boxStack.copy(), getReadyOutputTimeout()));
			iterator.remove();
			changed = true;
		}

		if (changed) {
			setChanged();
			notifyUpdate();
		}
	}

	private void cleanupMissingMarkedCreepers() {
		Level level = getLevel();
		if (level == null || level.isClientSide)
			return;

		boolean changed = false;
		Iterator<PendingAppearance> appearanceIterator = pendingAppearances.iterator();
		while (appearanceIterator.hasNext()) {
			PendingAppearance pending = appearanceIterator.next();
			if (findMarkedCreeperByUuid(pending.creeperUuid, pending.packagerPos) != null)
				continue;
			removeTrackedMarkedCreeper(pending.creeperUuid);
			appearanceIterator.remove();
			changed = true;
		}

		Iterator<PendingPackaging> packagingIterator = pendingPackagings.iterator();
		while (packagingIterator.hasNext()) {
			PendingPackaging pending = packagingIterator.next();
			if (findMarkedCreeperByUuid(pending.creeperUuid, pending.packagerPos) != null)
				continue;
			clearPackagerAnimationState(pending.packagerPos);
			restorePendingPackaging(pending, true);
			packagingIterator.remove();
			changed = true;
		}

		Iterator<Map.Entry<UUID, BlockPos>> trackedIterator = syncedMarkedCreepers.entrySet().iterator();
		while (trackedIterator.hasNext()) {
			Map.Entry<UUID, BlockPos> entry = trackedIterator.next();
			if (findMarkedCreeperByUuid(entry.getKey(), entry.getValue()) != null)
				continue;
			trackedIterator.remove();
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
			if (readyOutput.ticksRemaining > 0)
				readyOutput.ticksRemaining--;
			if (readyOutput.ticksRemaining > 0)
				continue;

			BlockPos targetPackager = findFirstEmptyPackagerSlot();
			if (targetPackager != null && queueUnpack(targetPackager, readyOutput.boxStack, false)) {
				iterator.remove();
				changed = true;
			}
		}

		if (changed)
			setChanged();
	}

	private void tickControllerOutputRequest() {
		if (!controllerOutputRequested || !readyOutputs.isEmpty() || !pendingPackagings.isEmpty())
			return;
		if (packageMarkedCreeperForOutput()) {
			controllerOutputRequested = false;
			setChanged();
		}
	}

	private void tickClientAnimations() {
		syncClientPressControllers();
		syncClientTrackedCreepers();
		tickClientAnimationList(pendingAppearances);
		tickClientAnimationList(pendingPackagings);
		tickClientWorkingCreeperEffects();
	}

	private void syncClientPressControllers() {
		Level level = getLevel();
		if (level == null || !level.isClientSide || !structureValid || structureOrigin == null) {
			clearClientTrackedPresses();
			return;
		}

		Set<Long> activePressPositions = new HashSet<>();
		for (MechanicalPressBlockEntity press : getMechanicalPresses()) {
			long key = press.getBlockPos().asLong();
			activePressPositions.add(key);
			CLIENT_PRESS_CONTROLLERS.put(key, getBlockPos());
			clientTrackedPressPositions.add(key);
		}

		clientTrackedPressPositions.removeIf(key -> {
			if (activePressPositions.contains(key))
				return false;
			if (Objects.equals(CLIENT_PRESS_CONTROLLERS.get(key), getBlockPos()))
				CLIENT_PRESS_CONTROLLERS.remove(key);
			return true;
		});
	}

	private void syncClientTrackedCreepers() {
		Level level = getLevel();
		if (level == null || !level.isClientSide) {
			clearClientTrackedCreepers();
			return;
		}

		clientTrackedCreeperUuids.removeIf(uuid -> {
			if (syncedMarkedCreepers.containsKey(uuid))
				return false;
			ClientTrackedCreeper tracked = CLIENT_TRACKED_CREEPERS.get(uuid);
			if (tracked != null && tracked.controllerPos.equals(getBlockPos()))
				CLIENT_TRACKED_CREEPERS.remove(uuid);
			return true;
		});

		for (Map.Entry<UUID, BlockPos> entry : syncedMarkedCreepers.entrySet()) {
			CLIENT_TRACKED_CREEPERS.put(entry.getKey(), new ClientTrackedCreeper(getBlockPos(), entry.getValue()));
			clientTrackedCreeperUuids.add(entry.getKey());
		}
	}

	private void clearClientTrackedCreepers() {
		if (!clientTrackedCreeperUuids.isEmpty()) {
			for (UUID uuid : clientTrackedCreeperUuids) {
				ClientTrackedCreeper tracked = CLIENT_TRACKED_CREEPERS.get(uuid);
				if (tracked != null && tracked.controllerPos.equals(getBlockPos()))
					CLIENT_TRACKED_CREEPERS.remove(uuid);
			}
			clientTrackedCreeperUuids.clear();
		}
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
			resetClientWorkingCreeperEffects();
			clientPressOffsets.clear();
			clientReturnEffectsArmed.clear();
			return;
		}

		Map<Long, Float> nextPressOffsets = new HashMap<>();
		Set<Long> activePackagers = new HashSet<>();
		Set<UUID> activeCreepers = new HashSet<>();
		boolean spawnedReturnEffectThisTick = false;
		for (RenderManagedCreeper creeper : getWorkingRenderCreepers()) {
			long key = creeper.packagerPos().asLong();
			float pressOffset = getRenderedCreeperEffectPressOffset(creeper.packagerPos(), 0);
			float previousOffset = clientPressOffsets.getOrDefault(key, 0f);
			boolean returning = isPressReturning(previousOffset, pressOffset);

			nextPressOffsets.put(key, pressOffset);
			activePackagers.add(key);
			activeCreepers.add(creeper.creeperUuid());

			Creeper creeperEntity = getAnimatedCreeper(creeper.creeperUuid(), creeper.packagerPos());
			if (creeperEntity != null)
				applyClientWorkingCreeperVisualState(creeperEntity, pressOffset, !returning);

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

		for (UUID uuid : syncedMarkedCreepers.keySet()) {
			if (activeCreepers.contains(uuid))
				continue;
			Creeper creeper = findClientTrackedCreeper(uuid);
			if (creeper != null)
				applyClientWorkingCreeperVisualState(creeper, 0f, false);
		}

		clientPressOffsets.clear();
		clientPressOffsets.putAll(nextPressOffsets);
		if (spawnedReturnEffectThisTick)
			clientReturnEffectsArmed.clear();
		clientReturnEffectsArmed.removeIf(key -> !activePackagers.contains(key));
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
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
			() -> () -> CreeperBlastChamberClientSoundHandler.stopManagedPrimedSound());
		level.playLocalSound(centerX, centerY, centerZ, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.45f, 1.15f,
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

		int extraExplosionCount = level.random.nextInt(
			CLIENT_RETURN_EXTRA_EXPLOSION_MAX - CLIENT_RETURN_EXTRA_EXPLOSION_MIN + 1)
			+ CLIENT_RETURN_EXTRA_EXPLOSION_MIN;
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

		for (int yOffset = 1; yOffset <= 2; yOffset++) {
			for (int xOffset = 0; xOffset < structureSize; xOffset++) {
				for (int zOffset = 0; zOffset < structureSize; zOffset++) {
					boolean onXWall = xOffset == 0 || xOffset == structureSize - 1;
					boolean onZWall = zOffset == 0 || zOffset == structureSize - 1;
					if (onXWall == onZWall)
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

	private void resetClientWorkingCreeperEffects() {
		Level level = getLevel();
		if (level == null)
			return;
		for (UUID uuid : syncedMarkedCreepers.keySet()) {
			Creeper creeper = findClientTrackedCreeper(uuid);
			if (creeper != null)
				applyClientWorkingCreeperVisualState(creeper, 0f, false);
		}
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
		return pendingUnpacks.size() + readyOutputs.size() + countMarkedCreepers();
	}

	private int countMarkedCreepers() {
		Level level = getLevel();
		if (level == null)
			return 0;
		return level.getEntitiesOfClass(Creeper.class, getMarkedCreeperSearchBounds(),
			creeper -> creeper.isAlive() && isMarkedCreeperForThisChamber(creeper, null))
			.size();
	}

	private CreeperCountSummary summarizeWorkableMarkedCreepers() {
		int normalCount = 0;
		int chargedCount = 0;
		for (BlockPos packagerPos : getPackagerPositions()) {
			if (isPackagerAppearing(packagerPos) || isPackagerPackaging(packagerPos))
				continue;
			Creeper creeper = getMarkedCreeperAtPackager(packagerPos);
			if (creeper == null)
				continue;
			if (creeper.isPowered())
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
		for (BlockPos packagerPos : getPackagerPositions()) {
			Creeper creeper = getMarkedCreeperAtPackager(packagerPos);
			if (creeper != null)
				kind = kind.merge(getCreeperKind(creeper));
		}
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
			&& !hasMarkedCreeperAtPackager(packagerPos);
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
		packager.animationTicks = BioPackagerBlockEntity.CYCLE;
		packager.chainReturnAnimation = false;
		packager.notifyUpdate();
		packager.setChanged();

		pendingUnpacks.add(new PendingUnpack(packagerPos, boxStack.copy(),
			BioPackagerBlockEntity.CYCLE * 2, false, returnBox));
		setChanged();
		return true;
	}

	private InsertResult tryInsertLargeCreeperBox(ItemStack stack, boolean simulate, boolean returnBox) {
		if (!isValidLargeCreeperBox(stack))
			return new InsertResult(false, stack);

		Level level = getLevel();
		if (level == null || level.isClientSide)
			return new InsertResult(false, stack);

		tryDetectStructure();
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

		CompoundTag boxTag = stack.getTag();
		if (boxTag == null || !boxTag.contains("CapturedEntity", Tag.TAG_COMPOUND))
			return ChamberCreeperKind.NONE;

		CompoundTag entityData = boxTag.getCompound("CapturedEntity");
		ResourceLocation entityId = ResourceLocation.tryParse(entityData.getString("id"));
		if (entityId == null || !entityId.equals(ForgeRegistries.ENTITY_TYPES.getKey(EntityType.CREEPER)))
			return ChamberCreeperKind.NONE;
		return entityData.getBoolean("powered") ? ChamberCreeperKind.CHARGED : ChamberCreeperKind.NORMAL;
	}

	@Nullable
	private ItemStack getActualControllerOutput() {
		if (readyOutputs.isEmpty())
			return null;
		return readyOutputs.get(0).boxStack.copy();
	}

	private void requestControllerOutput() {
		if (controllerOutputRequested || isPausedForPartialChunkUnload())
			return;
		controllerOutputRequested = true;
		setChanged();
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

	private boolean packageMarkedCreeperForOutput() {
		MarkedCreeperTarget target = findMarkedCreeperForOutput();
		if (target == null)
			return false;

		ItemStack output = createBoxedCreeper(target.creeper);
		if (output == null)
			return false;

		BioPackagerBlockEntity packager = getPackager(target.packagerPos);
		if (!canUsePackagerForInternalTransfer(packager))
			return false;

		ItemStack emptyBox = output.copy();
		emptyBox.setCount(1);
		CapturedEntityBoxHelper.clearCapturedEntity(emptyBox);
		packager.heldBox = emptyBox;
		packager.previouslyUnwrapped = ItemStack.EMPTY;
		packager.animationInward = false;
		packager.animationTicks = BioPackagerBlockEntity.CYCLE;
		packager.chainReturnAnimation = false;
		packager.notifyUpdate();
		packager.setChanged();
		pendingPackagings.add(new PendingPackaging(target.packagerPos, output.copy(), target.creeper.getUUID(),
			BioPackagerBlockEntity.CYCLE * 2, false));
		setChanged();
		notifyUpdate();
		return true;
	}

	@Nullable
	private MarkedCreeperTarget findMarkedCreeperForOutput() {
		Level level = getLevel();
		if (level == null || !structureValid)
			return null;

		for (BlockPos packagerPos : getPackagerPositions()) {
			if (isPackagerReserved(packagerPos) || isPackagerAppearing(packagerPos) || isPackagerPackaging(packagerPos))
				continue;
			BioPackagerBlockEntity packager = getPackager(packagerPos);
			if (!canUsePackagerForInternalTransfer(packager))
				continue;

			List<Creeper> creepers = level.getEntitiesOfClass(Creeper.class, getPackagerCreeperSearchBounds(packagerPos),
				creeper -> creeper.isAlive() && isMarkedCreeperForThisChamber(creeper, packagerPos));
			if (!creepers.isEmpty())
				return new MarkedCreeperTarget(packagerPos, creepers.get(0));
		}

		return null;
	}

	private AABB getPackagerCreeperSearchBounds(BlockPos packagerPos) {
		return new AABB(
			packagerPos.getX() + 0.1d,
			packagerPos.getY() + 0.75d,
			packagerPos.getZ() + 0.1d,
			packagerPos.getX() + 0.9d,
			packagerPos.getY() + 2.25d,
			packagerPos.getZ() + 0.9d);
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

		CompoundTag boxTag = box.getTag();
		if (boxTag == null || !boxTag.contains("CapturedEntity", Tag.TAG_COMPOUND))
			return box;

		CompoundTag entityData = boxTag.getCompound("CapturedEntity");
		entityData.remove("NoAI");
		entityData.remove("PersistenceRequired");
		if (entityData.contains("ForgeData", Tag.TAG_COMPOUND)) {
			CompoundTag forgeData = entityData.getCompound("ForgeData");
			forgeData.remove(DATA_ROOT);
			if (forgeData.isEmpty())
				entityData.remove("ForgeData");
		}
		return box;
	}

	private boolean completePendingUnpack(PendingUnpack pending) {
		Level level = getLevel();
		if (level == null || hasMarkedCreeperAtPackager(pending.packagerPos))
			return false;

		Entity entity = CapturedEntityBoxHelper.createCapturedEntity(pending.boxStack, level);
		if (!(entity instanceof Creeper creeper))
			return false;

		creeper.stopRiding();
		creeper.moveTo(
			pending.packagerPos.getX() + 0.5d,
			pending.packagerPos.getY() + 1d,
			pending.packagerPos.getZ() + 0.5d,
			creeper.getYRot(),
			creeper.getXRot());
		creeper.setNoAi(true);
		CapturedEntityBoxHelper.markAiDisabledByMod(creeper);
		creeper.setPersistenceRequired();
		creeper.setDeltaMovement(Vec3.ZERO);
		creeper.fallDistance = 0;
		creeper.setInvisible(true);
		markCreeper(creeper, pending.packagerPos);
		if (!level.addFreshEntity(creeper)) {
			removeTrackedMarkedCreeper(creeper.getUUID());
			return false;
		}
		pendingAppearances.add(new PendingAppearance(pending.packagerPos, creeper.getUUID(), CREEPER_ENTRY_ANIMATION_TICKS));
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

		for (PendingPackaging pending : pendingPackagings)
			clearPackagerAnimationState(pending.packagerPos);

		AABB searchBounds = new AABB(origin, origin.offset(size, 4, size)).inflate(32);
		for (Creeper creeper : level.getEntitiesOfClass(Creeper.class, searchBounds,
			entity -> entity.isAlive() && isMarkedCreeperForThisChamber(entity, null))) {
			releaseManagedCreeper(creeper);
		}

		pendingAppearances.clear();
		pendingPackagings.clear();
		syncedMarkedCreepers.clear();
		controllerOutputRequested = false;
		setChanged();
		notifyUpdate();
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
		ItemStack toDrop = pending.boxStack.copy();
		if (pending.transitioned) {
			toDrop.setCount(1);
			CapturedEntityBoxHelper.clearCapturedEntity(toDrop);
		}
		Block.popResource(level, pending.packagerPos.above(), toDrop);
	}

	private void dropPackagedBox(ItemStack stack, BlockPos pos) {
		Level level = getLevel();
		if (level == null || stack.isEmpty())
			return;
		Block.popResource(level, pos.above(), stack.copy());
	}

	private void restorePendingPackaging(PendingPackaging pending, boolean dropIfMissing) {
		Creeper creeper = findMarkedCreeperByUuid(pending.creeperUuid, pending.packagerPos);
		if (creeper != null) {
			creeper.setInvisible(false);
			creeper.setDeltaMovement(Vec3.ZERO);
			creeper.fallDistance = 0;
			return;
		}
		removeTrackedMarkedCreeper(pending.creeperUuid);
		if (dropIfMissing)
			dropPackagedBox(pending.boxStack, pending.packagerPos);
	}

	private List<BlockPos> getPackagerPositions() {
		List<BlockPos> packagers = new ArrayList<>();
		Level level = getLevel();
		if (level == null || !structureValid || structureOrigin == null)
			return packagers;

		for (int x = 1; x < structureSize - 1; x++) {
			for (int z = 1; z < structureSize - 1; z++) {
				BlockPos packagerPos = structureOrigin.offset(x, 0, z);
				if (level.getBlockState(packagerPos).is(CBBlocks.BIO_PACKAGER.get()))
					packagers.add(packagerPos);
			}
		}
		return packagers;
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
		CreeperBlastChamberBlockEntity controller = findStructureController(level, structurePos);
		return controller != null ? controller.getBlockPos() : null;
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

	private boolean hasMarkedCreeperAtPackager(BlockPos packagerPos) {
		return getMarkedCreeperAtPackager(packagerPos) != null;
	}

	@Nullable
	private Creeper getMarkedCreeperAtPackager(BlockPos packagerPos) {
		Level level = getLevel();
		if (level == null)
			return null;

		if (level.isClientSide) {
			for (Map.Entry<UUID, BlockPos> entry : syncedMarkedCreepers.entrySet()) {
				if (!entry.getValue().equals(packagerPos))
					continue;
				Creeper creeper = findClientTrackedCreeper(entry.getKey());
				if (creeper != null)
					return creeper;
			}
			return null;
		}

		List<Creeper> creepers = level.getEntitiesOfClass(Creeper.class, getMarkedCreeperSearchBounds(),
			creeper -> creeper.isAlive() && isMarkedCreeperForThisChamber(creeper, packagerPos));
		return creepers.isEmpty() ? null : creepers.get(0);
	}

	private AABB getMarkedCreeperSearchBounds() {
		if (structureOrigin == null)
			return new AABB(getBlockPos()).inflate(32);
		return new AABB(structureOrigin, structureOrigin.offset(structureSize, 4, structureSize)).inflate(32);
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

	private boolean shouldRenderManagedMarkedCreeper(Creeper creeper) {
		if (!structureValid)
			return false;
		BlockPos packagerPos = getLevel() != null && getLevel().isClientSide
			? syncedMarkedCreepers.get(creeper.getUUID())
			: getMarkedCreeperPackagerPos(creeper);
		if (packagerPos == null)
			return false;
		return isPackagerPartOfStructure(packagerPos)
			|| isPackagerAppearing(packagerPos)
			|| isPackagerPackaging(packagerPos);
	}

	private void applyClientWorkingCreeperVisualState(Creeper creeper, float pressOffset, boolean allowWhiteFlash) {
		CreeperAccessor accessor = (CreeperAccessor) creeper;
		int renderSwell = 0;
		if (allowWhiteFlash) {
			float compression = getCompressionFromPressOffset(pressOffset);
			float pulse = 0.5f + 0.5f * Mth.sin(AnimationTickHolder.getRenderTime(creeper.level()) * 0.9f);
			renderSwell = Mth.floor(Mth.clamp(compression * Mth.lerp(pulse, 0.55f, 1f), 0f, 1f) * 24f);
		}
		accessor.createBiotech$setOldSwell(renderSwell);
		accessor.createBiotech$setSwell(renderSwell);
	}

	private void markCreeper(Creeper creeper, BlockPos packagerPos) {
		CompoundTag data = getCreateBiotechData(creeper);
		data.putBoolean(MARKED_CREEPER_TAG, true);
		data.putLong(CONTROLLER_POS_TAG, getBlockPos().asLong());
		data.putLong(PACKAGER_POS_TAG, packagerPos.asLong());
		syncedMarkedCreepers.put(creeper.getUUID(), packagerPos);
	}

	private void removeTrackedMarkedCreeper(UUID creeperUuid) {
		syncedMarkedCreepers.remove(creeperUuid);
	}

	@Nullable
	private BioPackagerBlockEntity getPackager(BlockPos packagerPos) {
		Level level = getLevel();
		if (level == null || !level.getBlockState(packagerPos).is(CBBlocks.BIO_PACKAGER.get()))
			return null;
		BlockEntity blockEntity = level.getBlockEntity(packagerPos);
		return blockEntity instanceof BioPackagerBlockEntity packager ? packager : null;
	}

	@Nullable
	Creeper getAnimatedCreeper(UUID creeperUuid, BlockPos packagerPos) {
		return findMarkedCreeperByUuid(creeperUuid, packagerPos);
	}

	List<RenderManagedCreeper> getWorkingRenderCreepers() {
		List<RenderManagedCreeper> creepers = new ArrayList<>();
		Level level = getLevel();
		if (!structureValid || level == null)
			return creepers;

		if (level.isClientSide) {
			for (Map.Entry<UUID, BlockPos> entry : syncedMarkedCreepers.entrySet()) {
				BlockPos packagerPos = entry.getValue();
				if (isPackagerAppearing(packagerPos) || isPackagerPackaging(packagerPos))
					continue;
				Creeper creeper = findClientTrackedCreeper(entry.getKey());
				if (creeper != null)
					creepers.add(new RenderManagedCreeper(packagerPos, entry.getKey()));
			}
			return creepers;
		}

		for (BlockPos packagerPos : getPackagerPositions()) {
			if (isPackagerAppearing(packagerPos) || isPackagerPackaging(packagerPos))
				continue;
			Creeper creeper = getMarkedCreeperAtPackager(packagerPos);
			if (creeper != null)
				creepers.add(new RenderManagedCreeper(packagerPos, creeper.getUUID()));
		}
		return creepers;
	}

	private Creeper findMarkedCreeperByUuid(UUID creeperUuid, BlockPos packagerPos) {
		Level level = getLevel();
		if (level == null)
			return null;

		if (level.isClientSide) {
			BlockPos syncedPackagerPos = syncedMarkedCreepers.get(creeperUuid);
			if (syncedPackagerPos == null || !syncedPackagerPos.equals(packagerPos))
				return null;
			Creeper creeper = findClientTrackedCreeper(creeperUuid);
			return creeper != null && getPackagerCreeperSearchBounds(packagerPos).contains(creeper.position()) ? creeper : null;
		}

		List<Creeper> creepers = level.getEntitiesOfClass(Creeper.class, getPackagerCreeperSearchBounds(packagerPos),
			creeper -> creeper.isAlive() && creeperUuid.equals(creeper.getUUID())
				&& isMarkedCreeperForThisChamber(creeper, packagerPos));
		return creepers.isEmpty() ? null : creepers.get(0);
	}

	List<RenderCreeperAnimation> getRenderAnimations() {
		List<RenderCreeperAnimation> animations = new ArrayList<>(pendingAppearances.size() + pendingPackagings.size());
		for (PendingAppearance pending : pendingAppearances)
			animations.add(new RenderCreeperAnimation(pending.packagerPos, pending.creeperUuid, pending.ticksRemaining,
				pending.totalTicks, false));
		for (PendingPackaging pending : pendingPackagings) {
			if (pending.ticksRemaining <= BioPackagerBlockEntity.CYCLE)
				continue;
			int outwardTicksRemaining = pending.ticksRemaining - BioPackagerBlockEntity.CYCLE;
			animations.add(new RenderCreeperAnimation(pending.packagerPos, pending.creeperUuid, outwardTicksRemaining,
				BioPackagerBlockEntity.CYCLE, true));
		}
		return animations;
	}

	public static boolean shouldCancelDefaultMarkedCreeperRender(Creeper creeper) {
		Level level = creeper.level();
		BlockPos controllerPos = getMarkedCreeperControllerPos(creeper);
		if (level == null || controllerPos == null || !level.isLoaded(controllerPos))
			return false;
		BlockEntity blockEntity = level.getBlockEntity(controllerPos);
		return blockEntity instanceof CreeperBlastChamberBlockEntity chamber
			&& chamber.shouldRenderManagedMarkedCreeper(creeper);
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
		Level level = creeper.level();
		ClientTrackedCreeper tracked = CLIENT_TRACKED_CREEPERS.get(creeper.getUUID());
		if (level == null || tracked == null || !level.isLoaded(tracked.controllerPos))
			return 0f;
		BlockEntity blockEntity = level.getBlockEntity(tracked.controllerPos);
		if (!(blockEntity instanceof CreeperBlastChamberBlockEntity chamber))
			return 0f;
		if (!chamber.structureValid || chamber.isPackagerAppearing(tracked.packagerPos)
			|| chamber.isPackagerPackaging(tracked.packagerPos))
			return 0f;
		return getCompressionFromPressOffset(chamber.getRenderedCreeperEffectPressOffset(tracked.packagerPos, partialTicks));
	}

	public static boolean isPonderCompressionActive(Creeper creeper) {
		CompoundTag biotechData = getExistingCreateBiotechData(creeper);
		return biotechData != null
			&& (biotechData.contains(PONDER_COMPRESSION_TAG, Tag.TAG_FLOAT)
				|| biotechData.contains(PONDER_COMPRESSION_ANIM_TAG, Tag.TAG_COMPOUND));
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

		BlockPos controllerPos = CLIENT_PRESS_CONTROLLERS.get(press.getBlockPos().asLong());
		if (controllerPos == null)
			return getLocalPressHeadProgress(pressingBehaviour, partialTicks);

		BlockEntity blockEntity = level.getBlockEntity(controllerPos);
		if (!(blockEntity instanceof CreeperBlastChamberBlockEntity chamber)
			|| !chamber.structureValid
			|| !chamber.isPressPartOfStructure(press.getBlockPos())) {
			return getLocalPressHeadProgress(pressingBehaviour, partialTicks);
		}

		List<MechanicalPressBlockEntity> presses = chamber.getMechanicalPresses();
		if (chamber.hasUnworkablePresses(presses))
			return getLocalPressHeadProgress(pressingBehaviour, partialTicks);

		MechanicalPressBlockEntity masterPress = chamber.getMasterPress(presses);
		if (masterPress == null)
			return getLocalPressHeadProgress(pressingBehaviour, partialTicks);

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

	@Nullable
	private Creeper findClientTrackedCreeper(UUID creeperUuid) {
		Level level = getLevel();
		if (level == null)
			return null;
		List<Creeper> creepers = level.getEntitiesOfClass(Creeper.class, getMarkedCreeperSearchBounds(),
			creeper -> creeper.isAlive() && creeperUuid.equals(creeper.getUUID()));
		return creepers.isEmpty() ? null : creepers.get(0);
	}

	private static CompoundTag getCreateBiotechData(Entity entity) {
		CompoundTag persistentData = entity.getPersistentData();
		if (!persistentData.contains(DATA_ROOT))
			persistentData.put(DATA_ROOT, new CompoundTag());
		return persistentData.getCompound(DATA_ROOT);
	}

	private static CompoundTag getExistingCreateBiotechData(Entity entity) {
		CompoundTag persistentData = entity.getPersistentData();
		return persistentData.contains(DATA_ROOT) ? persistentData.getCompound(DATA_ROOT) : null;
	}

	@Nullable
	public static BlockPos getMarkedCreeperControllerPos(Entity entity) {
		CompoundTag data = getExistingCreateBiotechData(entity);
		if (data == null || !data.getBoolean(MARKED_CREEPER_TAG) || !data.contains(CONTROLLER_POS_TAG, Tag.TAG_LONG))
			return null;
		return BlockPos.of(data.getLong(CONTROLLER_POS_TAG));
	}

	@Nullable
	static BlockPos getMarkedCreeperPackagerPos(Entity entity) {
		CompoundTag data = getExistingCreateBiotechData(entity);
		if (data == null || !data.getBoolean(MARKED_CREEPER_TAG) || !data.contains(PACKAGER_POS_TAG, Tag.TAG_LONG))
			return null;
		return BlockPos.of(data.getLong(PACKAGER_POS_TAG));
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

		private CompoundTag write() {
			CompoundTag tag = new CompoundTag();
			tag.putLong("PackagerPos", packagerPos.asLong());
			tag.put("Box", boxStack.serializeNBT());
			tag.putInt("TicksRemaining", ticksRemaining);
			tag.putBoolean("Transitioned", transitioned);
			tag.putBoolean("ReturnBox", returnBox);
			return tag;
		}

		private static PendingUnpack read(CompoundTag tag) {
			return new PendingUnpack(
				BlockPos.of(tag.getLong("PackagerPos")),
				ItemStack.of(tag.getCompound("Box")),
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
		private final UUID creeperUuid;

		private PendingAppearance(BlockPos packagerPos, UUID creeperUuid, int ticksRemaining) {
			super(ticksRemaining, CREEPER_ENTRY_ANIMATION_TICKS);
			this.packagerPos = packagerPos;
			this.creeperUuid = creeperUuid;
		}

		private CompoundTag write() {
			CompoundTag tag = new CompoundTag();
			tag.putLong("PackagerPos", packagerPos.asLong());
			tag.putUUID("CreeperUuid", creeperUuid);
			tag.putInt("TicksRemaining", ticksRemaining);
			return tag;
		}

		private static PendingAppearance read(CompoundTag tag) {
			return new PendingAppearance(
				BlockPos.of(tag.getLong("PackagerPos")),
				tag.getUUID("CreeperUuid"),
				tag.getInt("TicksRemaining"));
		}
	}

	private static class PendingPackaging extends TimedAnimation {
		private final BlockPos packagerPos;
		private final ItemStack boxStack;
		private final UUID creeperUuid;
		private boolean transitioned;

		private PendingPackaging(BlockPos packagerPos, ItemStack boxStack, UUID creeperUuid, int ticksRemaining,
			boolean transitioned) {
			super(ticksRemaining, BioPackagerBlockEntity.CYCLE * 2);
			this.packagerPos = packagerPos;
			this.boxStack = boxStack;
			this.creeperUuid = creeperUuid;
			this.transitioned = transitioned;
		}

		private CompoundTag write() {
			CompoundTag tag = new CompoundTag();
			tag.putLong("PackagerPos", packagerPos.asLong());
			tag.put("Box", boxStack.serializeNBT());
			tag.putUUID("CreeperUuid", creeperUuid);
			tag.putInt("TicksRemaining", ticksRemaining);
			tag.putBoolean("Transitioned", transitioned);
			return tag;
		}

		private static PendingPackaging read(CompoundTag tag) {
			return new PendingPackaging(
				BlockPos.of(tag.getLong("PackagerPos")),
				ItemStack.of(tag.getCompound("Box")),
				tag.getUUID("CreeperUuid"),
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

		private CompoundTag write() {
			CompoundTag tag = new CompoundTag();
			tag.putLong("PackagerPos", packagerPos.asLong());
			tag.put("Box", boxStack.serializeNBT());
			tag.putInt("TicksRemaining", ticksRemaining);
			return tag;
		}

		private static ReadyOutput read(CompoundTag tag) {
			return new ReadyOutput(
				BlockPos.of(tag.getLong("PackagerPos")),
				ItemStack.of(tag.getCompound("Box")),
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

	private class ChamberInputHandler implements IItemHandler {
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

			ItemStack extracted = extractReadyOutput(simulate);
			if (extracted != null)
				return extracted;

			requestControllerOutput();
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

	record RenderCreeperAnimation(BlockPos packagerPos, UUID creeperUuid, int ticksRemaining, int totalTicks,
		boolean exiting) {}

	record RenderManagedCreeper(BlockPos packagerPos, UUID creeperUuid) {}

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

	private record ClientTrackedCreeper(BlockPos controllerPos, BlockPos packagerPos) {}

	private record MarkedCreeperTarget(BlockPos packagerPos, Creeper creeper) {}

	private record VaultRoleAssignment(BlockPos inputVaultController, BlockPos outputVaultController) {}

	private record StructureScanResult(int size, BlockPos origin, BlockPos inputVaultController,
		BlockPos outputVaultController) {}
}
