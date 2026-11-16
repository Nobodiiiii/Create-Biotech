package com.nobodiiiii.createbiotech.content.creeperblastchamber;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import com.nobodiiiii.createbiotech.content.explosionproofitemvault.ExplosionProofItemVaultBlock;
import com.nobodiiiii.createbiotech.content.explosionproofitemvault.ExplosionProofItemVaultBlockEntity;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.chainDrive.ChainDriveBlock;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** ChamberStructure algorithms over the canonical CreeperBlastChamberBlockEntity state. */
final class ChamberStructure {
	private ChamberStructure() {}


	static void tryDetectStructure(CreeperBlastChamberBlockEntity operationOwner) {
		Level level = operationOwner.getLevel();
		if (level == null)
			return;
		BlockPos pos = operationOwner.getBlockPos();
		if (operationOwner.structureValid && operationOwner.structureOrigin != null) {
			CreeperBlastChamberBlockEntity.StructureScanResult current = ChamberStructure.scanStructure(operationOwner, level, operationOwner.structureOrigin, operationOwner.structureSize);
			if (current != null) {
				ChamberStructure.applyDetectedStructure(operationOwner, level, current);
				return;
			}
		}

		for (int s = CreeperBlastChamberBlockEntity.getMinSize(); s <= CreeperBlastChamberBlockEntity.getMaxSize(); s++) {
			CreeperBlastChamberBlockEntity.StructureScanResult result = ChamberStructure.findStructure(operationOwner, level, pos, s);
			if (result != null) {
				ChamberStructure.applyDetectedStructure(operationOwner, level, result);
				return;
			}
		}
		boolean wasValid = operationOwner.structureValid;
		int oldSize = operationOwner.structureSize;
		BlockPos oldOrigin = operationOwner.structureOrigin;
		Axis oldPressAxis = operationOwner.structurePressAxis;
		ChamberStructure.setStructure(operationOwner, level, false, 0, null, null, null);
		if (wasValid)
			ChamberStructure.onStructureBroken(operationOwner, level, oldSize, oldOrigin, oldPressAxis);
	}

	static void applyDetectedStructure(CreeperBlastChamberBlockEntity operationOwner, Level level, CreeperBlastChamberBlockEntity.StructureScanResult result) {
		boolean wasValid = operationOwner.structureValid;
		int oldSize = operationOwner.structureSize;
		BlockPos oldOrigin = operationOwner.structureOrigin;
		BlockPos oldInputVault = operationOwner.inputVaultController;
		BlockPos oldOutputVault = operationOwner.outputVaultController;
		Axis oldPressAxis = operationOwner.structurePressAxis;
		ChamberStructure.setStructure(operationOwner, level, true, result.size(), result.origin(), result.inputVaultController(),
			result.outputVaultController());
		if (!wasValid || oldSize != result.size() || !Objects.equals(result.origin(), oldOrigin)
			|| !Objects.equals(operationOwner.inputVaultController, oldInputVault)
			|| !Objects.equals(operationOwner.outputVaultController, oldOutputVault) || operationOwner.structurePressAxis != oldPressAxis)
			ChamberStructure.onStructureFormed(operationOwner, level, result.size(), result.origin());
	}

	static @Nullable
	CreeperBlastChamberBlockEntity.StructureScanResult findStructure(CreeperBlastChamberBlockEntity operationOwner, Level level, BlockPos controllerPos, int size) {
		for (int dx = 0; dx < size; dx++) {
			for (int dz = 0; dz < size; dz++) {
				if (dx > 0 && dx < size - 1 && dz > 0 && dz < size - 1)
					continue;

				BlockPos origin = controllerPos.offset(-dx, 0, -dz);
				CreeperBlastChamberBlockEntity.StructureScanResult result = ChamberStructure.scanStructure(operationOwner, level, origin, size);
				if (result != null)
					return result;
			}
		}
		return null;
	}

	static @Nullable
	CreeperBlastChamberBlockEntity.StructureScanResult scanStructure(CreeperBlastChamberBlockEntity operationOwner, Level level, BlockPos origin, int size) {
		int innerMin = 1;
		int innerMax = size - 2;
		BlockState centerPressState = level.getBlockState(origin.offset(size / 2, 3, size / 2));

		if (!AllBlocks.MECHANICAL_PRESS.has(centerPressState))
			return null;
		Axis pressShaftAxis = ChamberStructure.getPressShaftAxis(centerPressState);
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
						if (!ChamberStructure.isMechanicalPressOnAxis(state, pressShaftAxis))
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
						boolean isChainDrive = ChamberStructure.isBlastProofChainDriveOnAxis(state, pressShaftAxis);
						boolean isVault = ExplosionProofItemVaultBlock.isVault(state);
						boolean isVerticalEdge = !isInnerX && !isInnerZ;
						boolean isTopOrBottom = (y == 0 || y == 3);
						boolean chainDriveReserved = ChamberStructure.isReservedChainDrivePosition(operationOwner, x, y, z, size, pressShaftAxis);

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
							ExplosionProofItemVaultBlockEntity controller = ChamberStructure.resolveVaultController(operationOwner, level, pos);
							if (controller == null || !ChamberStructure.isWholeVaultInsideStructure(operationOwner, level, controller, origin, size))
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
		return new CreeperBlastChamberBlockEntity.StructureScanResult(size, origin, orderedControllers.get(0), orderedControllers.get(1));
	}

	static Axis getPressShaftAxis(BlockState state) {
		return state.getValue(BlockStateProperties.HORIZONTAL_FACING)
			.getAxis();
	}

	static boolean isMechanicalPressOnAxis(BlockState state, Axis axis) {
		return AllBlocks.MECHANICAL_PRESS.has(state) && ChamberStructure.getPressShaftAxis(state) == axis;
	}

	static boolean isBlastProofChainDriveOnAxis(BlockState state, Axis axis) {
		return state.is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get())
			&& state.hasProperty(BlockStateProperties.AXIS)
			&& state.getValue(BlockStateProperties.AXIS) == axis;
	}

	static boolean isReservedChainDrivePosition(CreeperBlastChamberBlockEntity operationOwner, int x, int y, int z, int size, Axis pressShaftAxis) {
		if (y != 3)
			return false;
		if (pressShaftAxis == Axis.X)
			return (x == 0 || x == size - 1) && z >= 1 && z < size - 1;
		return (z == 0 || z == size - 1) && x >= 1 && x < size - 1;
	}

	static @Nullable
	ExplosionProofItemVaultBlockEntity resolveVaultController(CreeperBlastChamberBlockEntity operationOwner, Level level, BlockPos pos) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (!(blockEntity instanceof ExplosionProofItemVaultBlockEntity vault))
			return null;
		if (vault.isController())
			return vault;
		return vault.getControllerBE() instanceof ExplosionProofItemVaultBlockEntity controller ? controller : null;
	}

	static boolean isWholeVaultInsideStructure(CreeperBlastChamberBlockEntity operationOwner, Level level, ExplosionProofItemVaultBlockEntity controller, BlockPos origin,
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
					if (!ChamberStructure.isWithinStructureVolume(vaultPos, origin, size))
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

	static boolean isWithinStructureVolume(BlockPos pos, BlockPos origin, int size) {
		return pos.getX() >= origin.getX() && pos.getX() < origin.getX() + size
			&& pos.getY() >= origin.getY() && pos.getY() < origin.getY() + 4
			&& pos.getZ() >= origin.getZ() && pos.getZ() < origin.getZ() + size;
	}

	static void setStructure(CreeperBlastChamberBlockEntity operationOwner, Level level, boolean valid, int size, @Nullable BlockPos origin,
		@Nullable BlockPos firstVaultController, @Nullable BlockPos secondVaultController) {
		boolean previousValid = operationOwner.structureValid;
		int previousSize = operationOwner.structureSize;
		BlockPos previousOrigin = operationOwner.structureOrigin;
		BlockPos previousInput = operationOwner.inputVaultController;
		BlockPos previousOutput = operationOwner.outputVaultController;
		BlockPos previousConfiguredInput = operationOwner.configuredInputVaultController;
		Axis previousPressAxis = operationOwner.structurePressAxis;
		int previousOverloadPoints = operationOwner.overloadPoints;
		operationOwner.structureValid = valid;
		operationOwner.structureSize = size;
		operationOwner.structureOrigin = origin;
		if (valid && firstVaultController != null && secondVaultController != null) {
			CreeperBlastChamberBlockEntity.VaultRoleAssignment assignment = ChamberStructure.resolveVaultRoleAssignment(operationOwner, firstVaultController, secondVaultController);
			operationOwner.inputVaultController = assignment.inputVaultController();
			operationOwner.outputVaultController = assignment.outputVaultController();
			operationOwner.configuredInputVaultController = operationOwner.inputVaultController;
			BlockState pressState = level.getBlockState(origin.offset(size / 2, 3, size / 2));
			operationOwner.structurePressAxis = AllBlocks.MECHANICAL_PRESS.has(pressState) ? ChamberStructure.getPressShaftAxis(pressState) : null;
		} else {
			operationOwner.inputVaultController = null;
			operationOwner.outputVaultController = null;
			operationOwner.structurePressAxis = null;
			operationOwner.overloadPoints = 0;
		}
		operationOwner.bottomCenter = valid && origin != null
			? origin.offset(size / 2, 0, size / 2)
			: null;
		operationOwner.pausedByUnloadedChunks = valid && ChamberStructure.isStructureAreaPartiallyUnloaded(operationOwner, level, origin, size);
		if (!valid)
			operationOwner.pressCycleProcessed = false;
		boolean geometryChanged = previousValid != operationOwner.structureValid || previousSize != operationOwner.structureSize
			|| !Objects.equals(previousOrigin, operationOwner.structureOrigin);
		if (geometryChanged)
			ChamberStructure.rebuildStructurePositionCache(operationOwner);
		ChamberStructure.syncFormedBlockState(operationOwner);
		boolean vaultBindingsChanged = previousValid != operationOwner.structureValid
			|| !Objects.equals(previousInput, operationOwner.inputVaultController)
			|| !Objects.equals(previousOutput, operationOwner.outputVaultController);
		if (vaultBindingsChanged)
			ChamberStructure.syncVaultRoleBindings(operationOwner, level, previousInput, previousOutput);
		boolean stateChanged = geometryChanged || vaultBindingsChanged
			|| !Objects.equals(previousConfiguredInput, operationOwner.configuredInputVaultController)
			|| previousPressAxis != operationOwner.structurePressAxis || previousOverloadPoints != operationOwner.overloadPoints;
		if (stateChanged)
			operationOwner.notifyUpdate();
	}

	static void rebuildStructurePositionCache(CreeperBlastChamberBlockEntity operationOwner) {
		operationOwner.cachedPackagerPositions.clear();
		operationOwner.cachedPressPositions.clear();
		operationOwner.cachedPressByPackager.clear();
		ChamberClientEffects.invalidatePressRenderStates(operationOwner);
		if (!operationOwner.structureValid || operationOwner.structureOrigin == null || operationOwner.structureSize <= 2)
			return;

		for (int x = 1; x < operationOwner.structureSize - 1; x++) {
			for (int z = 1; z < operationOwner.structureSize - 1; z++) {
				BlockPos packagerPos = operationOwner.structureOrigin.offset(x, 0, z);
				BlockPos pressPos = operationOwner.structureOrigin.offset(x, 3, z);
				operationOwner.cachedPackagerPositions.add(packagerPos);
				operationOwner.cachedPressPositions.add(pressPos);
				operationOwner.cachedPressByPackager.put(packagerPos.asLong(), pressPos);
			}
		}
	}

	static boolean updatePausedByUnloadedChunks(CreeperBlastChamberBlockEntity operationOwner) {
		Level level = operationOwner.getLevel();
		boolean paused = level != null
			&& !level.isClientSide
			&& operationOwner.structureValid
			&& operationOwner.structureOrigin != null
			&& ChamberStructure.isStructureAreaPartiallyUnloaded(operationOwner, level, operationOwner.structureOrigin, operationOwner.structureSize);
		if (operationOwner.pausedByUnloadedChunks == paused)
			return paused;
		operationOwner.pausedByUnloadedChunks = paused;
		operationOwner.setChanged();
		operationOwner.notifyUpdate();
		return paused;
	}

	static boolean isStructureAreaPartiallyUnloaded(CreeperBlastChamberBlockEntity operationOwner, Level level, @Nullable BlockPos origin, int size) {
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

	static boolean isPausedForPartialChunkUnload(CreeperBlastChamberBlockEntity operationOwner) {
		return operationOwner.structureValid && operationOwner.pausedByUnloadedChunks;
	}

	static void syncFormedBlockState(CreeperBlastChamberBlockEntity operationOwner) {
		if (operationOwner.getLevel() == null || operationOwner.getLevel().isClientSide)
			return;
		BlockState state = operationOwner.getBlockState();
		if (!state.hasProperty(CreeperBlastChamberBlock.FORMED))
			return;
		boolean formed = operationOwner.structureValid;
		if (state.getValue(CreeperBlastChamberBlock.FORMED) == formed)
			return;
		operationOwner.getLevel().setBlock(operationOwner.getBlockPos(), state.setValue(CreeperBlastChamberBlock.FORMED, formed), 3);
	}

	static CreeperBlastChamberBlockEntity.VaultRoleAssignment resolveVaultRoleAssignment(CreeperBlastChamberBlockEntity operationOwner, BlockPos firstVaultController, BlockPos secondVaultController) {
		if (operationOwner.configuredInputVaultController != null) {
			if (operationOwner.configuredInputVaultController.equals(firstVaultController))
				return new CreeperBlastChamberBlockEntity.VaultRoleAssignment(firstVaultController, secondVaultController);
			if (operationOwner.configuredInputVaultController.equals(secondVaultController))
				return new CreeperBlastChamberBlockEntity.VaultRoleAssignment(secondVaultController, firstVaultController);
		}

		if (operationOwner.inputVaultController != null) {
			if (operationOwner.inputVaultController.equals(firstVaultController))
				return new CreeperBlastChamberBlockEntity.VaultRoleAssignment(firstVaultController, secondVaultController);
			if (operationOwner.inputVaultController.equals(secondVaultController))
				return new CreeperBlastChamberBlockEntity.VaultRoleAssignment(secondVaultController, firstVaultController);
		}

		return new CreeperBlastChamberBlockEntity.VaultRoleAssignment(firstVaultController, secondVaultController);
	}

	static void syncVaultRoleBindings(CreeperBlastChamberBlockEntity operationOwner, Level level, @Nullable BlockPos previousInput, @Nullable BlockPos previousOutput) {
		if (level.isClientSide)
			return;

		if (previousInput != null && (!previousInput.equals(operationOwner.inputVaultController) || !operationOwner.structureValid))
			ChamberStructure.setVaultRoleBinding(operationOwner, previousInput, null);
		if (previousOutput != null && (!previousOutput.equals(operationOwner.outputVaultController) || !operationOwner.structureValid))
			ChamberStructure.setVaultRoleBinding(operationOwner, previousOutput, null);

		if (operationOwner.inputVaultController != null)
			ChamberStructure.setVaultRoleBinding(operationOwner, operationOwner.inputVaultController, CreeperBlastChamberVaultRole.INPUT);
		if (operationOwner.outputVaultController != null)
			ChamberStructure.setVaultRoleBinding(operationOwner, operationOwner.outputVaultController, CreeperBlastChamberVaultRole.OUTPUT);
	}

	static void clearCurrentVaultRoleBindings(CreeperBlastChamberBlockEntity operationOwner) {
		Level level = operationOwner.getLevel();
		if (level == null || level.isClientSide)
			return;
		ChamberStructure.setVaultRoleBinding(operationOwner, operationOwner.inputVaultController, null);
		ChamberStructure.setVaultRoleBinding(operationOwner, operationOwner.outputVaultController, null);
	}

	static void onControllerRemoved(CreeperBlastChamberBlockEntity operationOwner) {
		Level level = operationOwner.getLevel();
		if (level == null || level.isClientSide || !operationOwner.structureValid || operationOwner.structureOrigin == null)
			return;
		ChamberStructure.onStructureBroken(operationOwner, level, operationOwner.structureSize, operationOwner.structureOrigin, operationOwner.structurePressAxis);
	}

	static void setVaultRoleBinding(CreeperBlastChamberBlockEntity operationOwner, @Nullable BlockPos controllerPos, @Nullable CreeperBlastChamberVaultRole role) {
		if (controllerPos == null || operationOwner.getLevel() == null)
			return;
		BlockEntity blockEntity = operationOwner.getLevel().getBlockEntity(controllerPos);
		if (!(blockEntity instanceof ExplosionProofItemVaultBlockEntity vault))
			return;
		if (role == null) {
			vault.clearBlastChamberBinding(operationOwner.getBlockPos());
			return;
		}
		vault.bindToBlastChamber(operationOwner.getBlockPos(), role);
	}

	static void configureVaultRole(CreeperBlastChamberBlockEntity operationOwner, BlockPos vaultControllerPos, CreeperBlastChamberVaultRole role) {
		if (operationOwner.getLevel() == null || operationOwner.getLevel().isClientSide || !operationOwner.structureValid || operationOwner.inputVaultController == null
			|| operationOwner.outputVaultController == null)
			return;
		if (!vaultControllerPos.equals(operationOwner.inputVaultController) && !vaultControllerPos.equals(operationOwner.outputVaultController))
			return;

		BlockPos previousInput = operationOwner.inputVaultController;
		BlockPos previousOutput = operationOwner.outputVaultController;
		operationOwner.configuredInputVaultController = role == CreeperBlastChamberVaultRole.INPUT
			? vaultControllerPos
			: vaultControllerPos.equals(operationOwner.inputVaultController) ? operationOwner.outputVaultController : operationOwner.inputVaultController;

		CreeperBlastChamberBlockEntity.VaultRoleAssignment assignment = ChamberStructure.resolveVaultRoleAssignment(operationOwner, previousInput, previousOutput);
		operationOwner.inputVaultController = assignment.inputVaultController();
		operationOwner.outputVaultController = assignment.outputVaultController();
		if (Objects.equals(previousInput, operationOwner.inputVaultController) && Objects.equals(previousOutput, operationOwner.outputVaultController))
			return;

		ChamberStructure.syncVaultRoleBindings(operationOwner, operationOwner.getLevel(), previousInput, previousOutput);
		operationOwner.notifyUpdate();
	}

	static void onStructureFormed(CreeperBlastChamberBlockEntity operationOwner, Level level, int size, BlockPos origin) {
		BlockPos pressPos = origin.offset(size / 2, 3, size / 2);
		BlockState pressState = level.getBlockState(pressPos);
		Direction shaftFacing = pressState.getValue(BlockStateProperties.HORIZONTAL_FACING);
		ChamberProcessing.resetPressProgress(operationOwner, ChamberProcessing.getMechanicalPresses(operationOwner));
		ChamberStructure.setPackagerOutputsDown(operationOwner, level, origin, size);
		// Chain drives sit on the two side edges that line up with the press shaft.
		Axis axis = ChamberStructure.getPressShaftAxis(pressState);
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
					ChamberStructure.updateChainDriveState(operationOwner, level, origin.offset(0, topY, z), axis, alongEdgeAxis);
					ChamberStructure.updateChainDriveState(operationOwner, level, origin.offset(size - 1, topY, z), axis, alongEdgeAxis);
				}
				ChamberStructure.refreshStructureKinetics(operationOwner, level, origin, size);
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
				ChamberStructure.updateChainDriveState(operationOwner, level, origin.offset(x, topY, 0), axis, alongEdgeAxis);
				ChamberStructure.updateChainDriveState(operationOwner, level, origin.offset(x, topY, size - 1), axis, alongEdgeAxis);
			}
			ChamberStructure.refreshStructureKinetics(operationOwner, level, origin, size);
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
			ChamberStructure.updateChainDriveState(operationOwner, level, edge1Pos, axis, alongEdgeAxis);
			ChamberStructure.updateChainDriveState(operationOwner, level, edge2Pos, axis, alongEdgeAxis);
		}

		ChamberStructure.refreshStructureKinetics(operationOwner, level, origin, size);
	}

	static void onStructureBroken(CreeperBlastChamberBlockEntity operationOwner, Level level, int size, BlockPos origin, @Nullable Axis pressAxis) {
		ChamberStructure.restoreChainDrivePositionsToCasing(operationOwner, level, origin, size, pressAxis);
		ChamberStructure.refreshStructureKinetics(operationOwner, level, origin, size);
		ChamberContainment.releaseManagedCreepers(operationOwner, origin, size);
		ChamberContainment.cancelPendingUnpacks(operationOwner);
	}

	static void restoreChainDrivePositionsToCasing(CreeperBlastChamberBlockEntity operationOwner, Level level, BlockPos origin, int size, @Nullable Axis pressAxis) {
		if (pressAxis == null)
			return;

		int topY = 3;
		if (pressAxis == Axis.X) {
			for (int z = 1; z < size - 1; z++) {
				ChamberStructure.revertToCasing(operationOwner, level, origin.offset(0, topY, z));
				ChamberStructure.revertToCasing(operationOwner, level, origin.offset(size - 1, topY, z));
			}
			return;
		}

		for (int x = 1; x < size - 1; x++) {
			ChamberStructure.revertToCasing(operationOwner, level, origin.offset(x, topY, 0));
			ChamberStructure.revertToCasing(operationOwner, level, origin.offset(x, topY, size - 1));
		}
	}

	static void refreshStructureKinetics(CreeperBlastChamberBlockEntity operationOwner, Level level, BlockPos origin, int size) {
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

	static void revertToCasing(CreeperBlastChamberBlockEntity operationOwner, Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get()))
			level.setBlock(pos, CBBlocks.EXPLOSION_PROOF_CASING.get().defaultBlockState(), 3);
	}

	static void updateChainDriveState(CreeperBlastChamberBlockEntity operationOwner, Level level, BlockPos pos, Axis axis, Axis alongEdgeAxis) {
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

	static void setPackagerOutputsDown(CreeperBlastChamberBlockEntity operationOwner, Level level, BlockPos origin, int size) {
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

	static List<BlockPos> getPackagerPositions(CreeperBlastChamberBlockEntity operationOwner) {
		return operationOwner.cachedPackagerPositions;
	}

	static boolean isPackagerPartOfStructure(CreeperBlastChamberBlockEntity operationOwner, BlockPos packagerPos) {
		Level level = operationOwner.getLevel();
		if (level == null || !operationOwner.structureValid || operationOwner.structureOrigin == null || packagerPos.getY() != operationOwner.structureOrigin.getY())
			return false;

		int x = packagerPos.getX() - operationOwner.structureOrigin.getX();
		int z = packagerPos.getZ() - operationOwner.structureOrigin.getZ();
		return x >= 1 && x < operationOwner.structureSize - 1
			&& z >= 1 && z < operationOwner.structureSize - 1
			&& level.getBlockState(packagerPos).is(CBBlocks.BIO_PACKAGER.get());
	}

	static boolean isPressPartOfStructure(CreeperBlastChamberBlockEntity operationOwner, BlockPos pressPos) {
		Level level = operationOwner.getLevel();
		if (level == null || !operationOwner.structureValid || operationOwner.structureOrigin == null || pressPos.getY() != operationOwner.structureOrigin.getY() + 3)
			return false;

		int x = pressPos.getX() - operationOwner.structureOrigin.getX();
		int z = pressPos.getZ() - operationOwner.structureOrigin.getZ();
		return x >= 1 && x < operationOwner.structureSize - 1
			&& z >= 1 && z < operationOwner.structureSize - 1
			&& AllBlocks.MECHANICAL_PRESS.has(level.getBlockState(pressPos));
	}

	static boolean shouldMutePressActivationSound(MechanicalPressBlockEntity press) {
		Level level = press.getLevel();
		if (level == null)
			return false;

		BlockPos pressPos = press.getBlockPos();
		int maxSize = CreeperBlastChamberBlockEntity.getMaxSize();
		BlockPos min = pressPos.offset(-maxSize, -3, -maxSize);
		BlockPos max = pressPos.offset(maxSize, 0, maxSize);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int y = min.getY(); y <= max.getY(); y++) {
			for (int x = min.getX(); x <= max.getX(); x++) {
				for (int z = min.getZ(); z <= max.getZ(); z++) {
					cursor.set(x, y, z);
					if (!(level.getBlockEntity(cursor) instanceof CreeperBlastChamberBlockEntity chamber))
						continue;
					if (chamber.structureValid && ChamberStructure.isPressPartOfStructure(chamber, pressPos))
						return true;
				}
			}
		}
		return false;
	}

	static @Nullable
	BlockPos findGoggleInformationSource(Level level, BlockPos structurePos) {
		CreeperBlastChamberBlockEntity controller = ChamberStructure.findLoadedClientStructureController(level, structurePos);
		return controller != null ? controller.getBlockPos() : null;
	}

	static @Nullable
	CreeperBlastChamberBlockEntity findLoadedClientStructureController(Level level,
		BlockPos structurePos) {
		if (!level.isClientSide)
			return null;

		Set<BlockPos> controllerPositions = CreeperBlastChamberBlockEntity.CLIENT_LOADED_CHAMBERS.get(level);
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
			if (ChamberStructure.isStructurePart(chamber, structurePos))
				return chamber;
		}

		if (controllerPositions.isEmpty())
			CreeperBlastChamberBlockEntity.CLIENT_LOADED_CHAMBERS.remove(level);
		return null;
	}

	static @Nullable
	CreeperBlastChamberBlockEntity findStructureController(Level level, BlockPos structurePos) {
		BlockEntity directBlockEntity = level.getBlockEntity(structurePos);
		if (directBlockEntity instanceof CreeperBlastChamberBlockEntity chamber
			&& ChamberStructure.isStructurePart(chamber, structurePos))
			return chamber;

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int y = structurePos.getY() - 3; y <= structurePos.getY() + 3; y++) {
			int maxSize = CreeperBlastChamberBlockEntity.getMaxSize();
			for (int x = structurePos.getX() - maxSize; x <= structurePos.getX() + maxSize; x++) {
				for (int z = structurePos.getZ() - maxSize; z <= structurePos.getZ() + maxSize; z++) {
					cursor.set(x, y, z);
					if (!(level.getBlockEntity(cursor) instanceof CreeperBlastChamberBlockEntity chamber))
						continue;
					if (ChamberStructure.isStructurePart(chamber, structurePos))
						return chamber;
				}
			}
		}

		return null;
	}
	static InteractionResult onStructureCasingWrenched(Level level, BlockPos clickedPos, @Nullable Player player) {
		CreeperBlastChamberBlockEntity chamber = ChamberStructure.findStructureController(level, clickedPos);
		if (chamber == null)
			return InteractionResult.FAIL;
		if (ChamberStructure.canToggleReservedChainDriveAt(chamber, clickedPos)) {
			if (level.isClientSide)
				return InteractionResult.SUCCESS;
			ChamberStructure.toggleReservedChainDrive(chamber, clickedPos);
			return InteractionResult.SUCCESS;
		}
		if (!ChamberStructure.canToggleCreeperFaceAt(chamber, clickedPos))
			return InteractionResult.FAIL;
		if (level.isClientSide)
			return InteractionResult.SUCCESS;
		ChamberStructure.toggleCreeperFaceVisible(chamber, player);
		return InteractionResult.SUCCESS;
	}

	static boolean isStructurePart(CreeperBlastChamberBlockEntity operationOwner, BlockPos pos) {
		return operationOwner.structureValid && operationOwner.structureOrigin != null && ChamberStructure.isWithinStructureVolume(pos, operationOwner.structureOrigin, operationOwner.structureSize);
	}

	static boolean canToggleCreeperFaceAt(CreeperBlastChamberBlockEntity operationOwner, BlockPos pos) {
		if (!ChamberStructure.isStructurePart(operationOwner, pos) || operationOwner.getLevel() == null)
			return false;
		BlockState state = operationOwner.getLevel().getBlockState(pos);
		boolean isController = pos.equals(operationOwner.getBlockPos()) && state.getBlock() instanceof CreeperBlastChamberBlock;
		boolean isCasing = state.is(CBBlocks.EXPLOSION_PROOF_CASING.get());
		if (!isController && !isCasing)
			return false;
		return !ChamberStructure.isReservedChainDrivePosition(operationOwner, pos);
	}

	static boolean canToggleReservedChainDriveAt(CreeperBlastChamberBlockEntity operationOwner, BlockPos pos) {
		if (!ChamberStructure.isStructurePart(operationOwner, pos) || operationOwner.getLevel() == null || !ChamberStructure.isReservedChainDrivePosition(operationOwner, pos))
			return false;
		BlockState state = operationOwner.getLevel().getBlockState(pos);
		return state.is(CBBlocks.EXPLOSION_PROOF_CASING.get()) || state.is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get());
	}

	static boolean isReservedChainDrivePosition(CreeperBlastChamberBlockEntity operationOwner, BlockPos pos) {
		if (!operationOwner.structureValid || operationOwner.structureOrigin == null || operationOwner.structurePressAxis == null)
			return false;
		int x = pos.getX() - operationOwner.structureOrigin.getX();
		int y = pos.getY() - operationOwner.structureOrigin.getY();
		int z = pos.getZ() - operationOwner.structureOrigin.getZ();
		if (x < 0 || x >= operationOwner.structureSize || y < 0 || y >= 4 || z < 0 || z >= operationOwner.structureSize)
			return false;
		return ChamberStructure.isReservedChainDrivePosition(operationOwner, x, y, z, operationOwner.structureSize, operationOwner.structurePressAxis);
	}

	static void toggleReservedChainDrive(CreeperBlastChamberBlockEntity operationOwner, BlockPos pos) {
		if (operationOwner.getLevel() == null || !operationOwner.structureValid || operationOwner.structureOrigin == null || operationOwner.structurePressAxis == null)
			return;

		BlockState state = operationOwner.getLevel().getBlockState(pos);
		if (state.is(CBBlocks.EXPLOSION_PROOF_CASING.get())) {
			Axis alongEdgeAxis = operationOwner.structurePressAxis == Axis.X ? Axis.Z : Axis.X;
			BlockState chainState = CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get().defaultBlockState()
				.setValue(BlockStateProperties.AXIS, operationOwner.structurePressAxis)
				.setValue(ChainDriveBlock.CONNECTED_ALONG_FIRST_COORDINATE, operationOwner.structurePressAxis == Axis.Z);
			operationOwner.getLevel().setBlock(pos, chainState, 3);
			ChamberStructure.updateChainDriveState(operationOwner, operationOwner.getLevel(), pos, operationOwner.structurePressAxis, alongEdgeAxis);
		} else if (state.is(CBBlocks.BLAST_PROOF_CHAIN_DRIVE.get())) {
			operationOwner.getLevel().setBlock(pos, CBBlocks.EXPLOSION_PROOF_CASING.get().defaultBlockState(), 3);
		} else {
			return;
		}

		ChamberStructure.refreshStructureKinetics(operationOwner, operationOwner.getLevel(), operationOwner.structureOrigin, operationOwner.structureSize);
	}

	static void toggleCreeperFaceVisible(CreeperBlastChamberBlockEntity operationOwner, @Nullable Player player) {
		operationOwner.creeperFaceVisible = !operationOwner.creeperFaceVisible;
		operationOwner.setChanged();
		operationOwner.notifyUpdate();
		if (player != null) {
			player.displayClientMessage(Component.translatable(operationOwner.creeperFaceVisible
				? "create_biotech.creeper_blast_chamber.creeper_face.shown"
				: "create_biotech.creeper_blast_chamber.creeper_face.hidden"), true);
		}
	}
}
