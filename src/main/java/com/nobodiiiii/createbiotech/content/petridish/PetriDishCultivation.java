package com.nobodiiiii.createbiotech.content.petridish;

import net.minecraft.core.registries.BuiltInRegistries;
import javax.annotation.Nullable;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.nobodiiiii.createbiotech.entity.PlayerMimicEntity;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** PetriDishCultivation algorithms over the canonical PetriDishBlockEntity state. */
final class PetriDishCultivation {
	private PetriDishCultivation() {}


	static void tryCompleteSpawn(PetriDishBlockEntity operationOwner) {
		if (!(operationOwner.getLevel() instanceof ServerLevel))
			return;
		if (!operationOwner.hasBionicMechanism())
			return;
		if (operationOwner.recordedEntityId == null || operationOwner.recordedMaxHealth <= 0)
			return;
		if (PetriDishRecording.requiresNearbyMatchingEntity() && !PetriDishRecording.hasMatchingEntityNearby(operationOwner))
			return;

		int required = operationOwner.getRequiredFluidAmount();
		if (required <= 0 || operationOwner.fluidTank.getFluidAmount() < required)
			return;

		EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(operationOwner.recordedEntityId)
			.orElse(null);
		if (entityType == null)
			return;

		if (!PetriDishCultivation.hasClearEmergenceSpace(operationOwner))
			return;

		PetriDishCultivation.beginEmergence(operationOwner);
	}

	static void beginEmergence(PetriDishBlockEntity operationOwner) {
		operationOwner.emergenceInProgress = true;
		operationOwner.emergenceTicksRemaining = PetriDishBlockEntity.EMERGENCE_ANIMATION_DURATION;
		operationOwner.setChanged();
		operationOwner.sendData();
		operationOwner.getLevel().playSound(null, operationOwner.getBlockPos(), SoundEvents.SLIME_JUMP, SoundSource.BLOCKS, 0.8f, 0.9f);
	}

	static void tickEmergence(PetriDishBlockEntity operationOwner) {
		if (!(operationOwner.getLevel() instanceof ServerLevel serverLevel))
			return;
		if (!operationOwner.emergenceInProgress)
			return;
		if (operationOwner.emergenceTicksRemaining > 0)
			operationOwner.emergenceTicksRemaining--;
		if (operationOwner.emergenceTicksRemaining > 0)
			return;

		PetriDishCultivation.finishEmergence(operationOwner, serverLevel);
	}

	static void finishEmergence(PetriDishBlockEntity operationOwner, ServerLevel serverLevel) {
		if (operationOwner.recordedEntityId == null) {
			PetriDishCultivation.cancelEmergence(operationOwner);
			return;
		}
		EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(operationOwner.recordedEntityId)
			.orElse(null);
		if (entityType == null) {
			PetriDishCultivation.cancelEmergence(operationOwner);
			return;
		}

		double spawnX = operationOwner.getBlockPos().getX() + 0.5d;
		double spawnY = operationOwner.getBlockPos().getY() + PetriDishBlockEntity.EMERGENCE_SPAWN_Y_OFFSET;
		double spawnZ = operationOwner.getBlockPos().getZ() + 0.5d;
		BlockPos spawnPos = BlockPos.containing(spawnX, spawnY, spawnZ);
		if (!PetriDishCultivation.hasClearEmergenceSpace(operationOwner)) {
			PetriDishCultivation.cancelEmergence(operationOwner);
			return;
		}

		int required = operationOwner.getRequiredFluidAmount();
		if (required <= 0 || operationOwner.fluidTank.getFluidAmount() < required) {
			PetriDishCultivation.cancelEmergence(operationOwner);
			return;
		}

		PetriDishRecording.snapshotPlayerProfileAtSpawn(operationOwner);
		float spawnYaw = operationOwner.getSpawnYaw();
		Mob livingEntity = PetriDishCultivation.trySpawnRecordedMob(operationOwner, serverLevel, entityType, spawnPos, spawnX, spawnY, spawnZ, spawnYaw);
		if (livingEntity == null)
			livingEntity = PetriDishCultivation.trySpawnFallbackSlime(operationOwner, serverLevel, entityType, spawnPos, spawnX, spawnY, spawnZ, spawnYaw);
		if (livingEntity == null) {
			PetriDishCultivation.cancelEmergence(operationOwner);
			return;
		}

		operationOwner.emergenceInProgress = false;
		operationOwner.emergenceTicksRemaining = 0;
		operationOwner.fluidTank.drain(required, FluidAction.EXECUTE);
		operationOwner.inventory.extractItem(0, 1, false);
		PetriDishCultivation.awardCultivationAdvancements(operationOwner, serverLevel, livingEntity);
		PetriDishRecording.clearRecordedEntity(operationOwner);
		operationOwner.getLevel().playSound(null, operationOwner.getBlockPos(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.7f, 0.9f);
		operationOwner.sendData();
	}

	static @Nullable
	Mob trySpawnRecordedMob(PetriDishBlockEntity operationOwner, ServerLevel serverLevel, EntityType<?> entityType, BlockPos spawnPos,
		double spawnX, double spawnY, double spawnZ, float spawnYaw) {
		Entity created = null;
		try {
			// EntityType.spawn() adds the entity before returning it. Keep creation and insertion separate so
			// unsupported types and failed spawn packets can be discarded before falling back safely.
			created = entityType.create(serverLevel, null, spawnPos, MobSpawnType.DISPENSER, true, false);
			if (!(created instanceof Mob mob)) {
				if (created != null)
					created.discard();
				PetriDishBlockEntity.LOGGER.warn("Petri dish at {} recorded non-mob entity type {}; using slime fallback",
					operationOwner.getBlockPos(), operationOwner.recordedEntityId);
				return null;
			}

			PetriDishCultivation.prepareSpawnedMimic(operationOwner, mob);
			PetriDishCultivation.positionSpawnedMob(mob, spawnX, spawnY, spawnZ, spawnYaw);
			if (!serverLevel.addFreshEntity(mob)) {
				mob.discard();
				PetriDishBlockEntity.LOGGER.warn("Petri dish at {} could not add cultivated {}; using slime fallback",
					operationOwner.getBlockPos(), operationOwner.recordedEntityId);
				return null;
			}
			return mob;
		} catch (RuntimeException exception) {
			if (created != null)
				created.discard();
			PetriDishBlockEntity.LOGGER.error("Petri dish at {} failed to cultivate {}; using slime fallback",
				operationOwner.getBlockPos(), operationOwner.recordedEntityId, exception);
			return null;
		}
	}

	static @Nullable
	Slime trySpawnFallbackSlime(PetriDishBlockEntity operationOwner, ServerLevel serverLevel, EntityType<?> recordedType, BlockPos spawnPos,
		double spawnX, double spawnY, double spawnZ, float spawnYaw) {
		Slime slime = null;
		try {
			slime = EntityType.SLIME.create(serverLevel, null, spawnPos, MobSpawnType.DISPENSER, true, false);
			if (slime == null) {
				PetriDishBlockEntity.LOGGER.error("Petri dish at {} could not create its slime fallback for {}",
					operationOwner.getBlockPos(), operationOwner.recordedEntityId);
				return null;
			}

			int slimeSize = PetriDishCultivation.selectFallbackSlimeSize(operationOwner, slime, recordedType);
			slime.setSize(slimeSize, true);
			SlimeMimicHandler.markSpawnedEntity(slime);
			PetriDishCultivation.positionSpawnedMob(slime, spawnX, spawnY, spawnZ, spawnYaw);
			if (!serverLevel.addFreshEntity(slime)) {
				slime.discard();
				PetriDishBlockEntity.LOGGER.error("Petri dish at {} could not add its size-{} slime fallback for {}",
					operationOwner.getBlockPos(), slimeSize, operationOwner.recordedEntityId);
				return null;
			}
			PetriDishBlockEntity.LOGGER.warn("Petri dish at {} replaced failed cultivation of {} with a size-{} slime",
				operationOwner.getBlockPos(), operationOwner.recordedEntityId, slimeSize);
			return slime;
		} catch (RuntimeException exception) {
			if (slime != null)
				slime.discard();
			PetriDishBlockEntity.LOGGER.error("Petri dish at {} failed to spawn its slime fallback for {}",
				operationOwner.getBlockPos(), operationOwner.recordedEntityId, exception);
			return null;
		}
	}

	static int selectFallbackSlimeSize(PetriDishBlockEntity operationOwner, Slime slime, EntityType<?> recordedType) {
		float targetWidth = PetriDishCultivation.sanitizeRecordedDimension(operationOwner.recordedWidth, recordedType.getWidth());
		float targetHeight = PetriDishCultivation.sanitizeRecordedDimension(operationOwner.recordedHeight, recordedType.getHeight());
		double targetVolume = targetWidth * targetWidth * targetHeight;
		int closestSize = PetriDishBlockEntity.FALLBACK_SLIME_SIZES[0];
		double closestDistance = Double.POSITIVE_INFINITY;
		for (int candidate : PetriDishBlockEntity.FALLBACK_SLIME_SIZES) {
			slime.setSize(candidate, false);
			double candidateVolume = slime.getBbWidth() * slime.getBbWidth() * slime.getBbHeight();
			double distance = Math.abs(Math.log(candidateVolume / targetVolume));
			if (distance < closestDistance) {
				closestDistance = distance;
				closestSize = candidate;
			}
		}
		return closestSize;
	}

	static float sanitizeRecordedDimension(float recorded, float fallback) {
		return Float.isFinite(recorded) && recorded > 0.0f ? recorded : Math.max(fallback, 0.01f);
	}

	static void positionSpawnedMob(Mob mob, double spawnX, double spawnY, double spawnZ, float spawnYaw) {
		mob.moveTo(spawnX, spawnY, spawnZ, spawnYaw, mob.getXRot());
		mob.setYRot(spawnYaw);
		mob.yRotO = spawnYaw;
		mob.setYBodyRot(spawnYaw);
		mob.yBodyRotO = spawnYaw;
		mob.setYHeadRot(spawnYaw);
		mob.yHeadRotO = spawnYaw;
	}

	static void prepareSpawnedMimic(PetriDishBlockEntity operationOwner, @Nullable Entity entity) {
		if (entity instanceof PlayerMimicEntity playerMimic && operationOwner.recordedPlayerProfile != null)
			playerMimic.setImitatedPlayer(operationOwner.recordedPlayerProfile);
		if (entity instanceof LivingEntity livingEntity && operationOwner.recordedMimicProfile != null)
			operationOwner.recordedMimicProfile.apply(livingEntity);
		SlimeMimicHandler.markSpawnedEntity(entity);
	}

	static void awardCultivationAdvancements(PetriDishBlockEntity operationOwner, ServerLevel serverLevel, LivingEntity cultivatedEntity) {
		Vec3 observationPosition =
			SubLevelCompat.toWorld(serverLevel, operationOwner.getBlockPos(), Vec3.atCenterOf(operationOwner.getBlockPos()));
		CBAdvancements.awardNearby(serverLevel, observationPosition, 16, CBAdvancements.PETRI_DISH);
		if (cultivatedEntity.getType() == EntityType.SLIME)
			CBAdvancements.awardNearby(serverLevel, observationPosition, 16, CBAdvancements.PERFECT_DISGUISE);
		if (cultivatedEntity.isBaby())
			CBAdvancements.awardNearby(serverLevel, observationPosition, 16, CBAdvancements.MIMIC_BABY);
	}

	static boolean hasClearEmergenceSpace(PetriDishBlockEntity operationOwner) {
		if (operationOwner.getLevel() == null)
			return false;

		return operationOwner.getLevel().isEmptyBlock(operationOwner.getBlockPos().above());
	}

	static void cancelEmergence(PetriDishBlockEntity operationOwner) {
		operationOwner.emergenceInProgress = false;
		operationOwner.emergenceTicksRemaining = 0;
		operationOwner.setChanged();
		operationOwner.sendData();
	}

	static void clampFluidToRequiredAmount(PetriDishBlockEntity operationOwner) {
		int required = operationOwner.getRequiredFluidAmount();
		if (operationOwner.fluidTank.getFluidAmount() > required) {
			operationOwner.fluidTank.drain(operationOwner.fluidTank.getFluidAmount() - required, FluidAction.EXECUTE);
		}
	}
}
