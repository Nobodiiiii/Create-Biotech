package com.nobodiiiii.createbiotech.content.petridish;

import net.minecraft.core.registries.BuiltInRegistries;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import com.mojang.authlib.GameProfile;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.entity.PlayerMimicEntity;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/** PetriDishRecording algorithms over the canonical PetriDishBlockEntity state. */
final class PetriDishRecording {
	private PetriDishRecording() {}


	static void refreshTargetMode(PetriDishBlockEntity operationOwner) {
		if (operationOwner.recordedEntityId == null)
			return;
		if (PetriDishRecording.isPlayerTargetMode(operationOwner) != (operationOwner.recordedPlayerProfile != null))
			PetriDishRecording.clearRecordedEntity(operationOwner);
	}

	static boolean isPlayerTargetMode(PetriDishBlockEntity operationOwner) {
		return operationOwner.getLevel() != null && operationOwner.getLevel().hasNeighborSignal(operationOwner.getBlockPos());
	}

	static void updateRecordedEntityFromNearby(PetriDishBlockEntity operationOwner) {
		if (operationOwner.getLevel() == null || !operationOwner.hasBionicMechanism())
			return;
		if (operationOwner.recordedEntityId != null) {
			if (PetriDishRecording.requiresNearbyMatchingEntity() && !PetriDishRecording.hasMatchingEntityNearby(operationOwner)) {
				// Keep the record, but reject further filling until the type comes back nearby.
				operationOwner.sendData();
			}
			return;
		}

		if (PetriDishRecording.isPlayerTargetMode(operationOwner)) {
			for (Player player : PetriDishRecording.getNearbyPlayers(operationOwner)) {
				if (!operationOwner.isRecordablePlayer(player))
					continue;
				PetriDishRecording.recordPlayer(operationOwner, player);
				return;
			}
			return;
		}

		List<Mob> entities = PetriDishRecording.getNearbyMobs(operationOwner);
		for (Mob entity : entities) {
			if (!operationOwner.isRecordableEntity(entity))
				continue;
			PetriDishRecording.recordEntity(operationOwner, entity);
			return;
		}
	}

	static boolean hasMatchingEntityNearby(PetriDishBlockEntity operationOwner) {
		if (operationOwner.recordedEntityId == null)
			return false;
		if (operationOwner.recordedPlayerProfile != null) {
			for (Player player : PetriDishRecording.getNearbyPlayers(operationOwner)) {
				if (operationOwner.recordedPlayerProfile.getId().equals(player.getGameProfile().getId()))
					return true;
			}
			return false;
		}
		for (Mob entity : PetriDishRecording.getNearbyMobs(operationOwner)) {
			ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
			if (Objects.equals(operationOwner.recordedEntityId, entityId))
				return true;
		}
		return false;
	}

	static List<Player> getNearbyPlayers(PetriDishBlockEntity operationOwner) {
		if (operationOwner.getLevel() == null)
			return List.of();
		AABB bounds = new AABB(operationOwner.getBlockPos()).inflate(PetriDishRecording.getScanRadius());
		return operationOwner.getLevel().getEntitiesOfClass(Player.class, bounds, operationOwner::isRecordablePlayer);
	}

	static List<Mob> getNearbyMobs(PetriDishBlockEntity operationOwner) {
		if (operationOwner.getLevel() == null)
			return List.of();
		AABB bounds = new AABB(operationOwner.getBlockPos()).inflate(PetriDishRecording.getScanRadius());
		// LivingEntity is deliberately too broad: Create packages are LivingEntity instances as an
		// implementation detail. Mob is the gameplay boundary for creatures the dish may imitate.
		return operationOwner.getLevel().getEntitiesOfClass(Mob.class, bounds, operationOwner::isRecordableEntity);
	}

	static int getScanInterval() {
		return CBConfigs.SERVER.petriDish.scanInterval.get();
	}

	static int getFluidPerHealth() {
		return CBConfigs.SERVER.petriDish.fluidPerHealth.get();
	}

	static int getScanRadius() {
		return CBConfigs.SERVER.petriDish.scanRadius.get();
	}

	static int getTankCapacity() {
		return CBConfigs.SERVER.petriDish.tankCapacity.get();
	}

	static boolean requiresNearbyMatchingEntity() {
		return CBConfigs.SERVER.petriDish.requireNearbyMatchingEntity.get();
	}

	static boolean isRecordableEntity(PetriDishBlockEntity operationOwner, Mob entity) {
		if (!entity.isAlive())
			return false;
		if (entity.isSpectator())
			return false;
		if (entity.blockPosition().equals(operationOwner.getBlockPos()))
			return false;
		if (entity.getType() == CBEntityTypes.PLAYER_MIMIC.get())
			return false;
		ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		return entityId != null;
	}

	static boolean isRecordablePlayer(PetriDishBlockEntity operationOwner, Player player) {
		return player.isAlive() && !player.isSpectator();
	}

	static void recordPlayer(PetriDishBlockEntity operationOwner, Player player) {
		operationOwner.recordedEntityId = CBEntityTypes.PLAYER_MIMIC.getId();
		operationOwner.recordedMimicProfile = null;
		operationOwner.recordedPlayerProfile = PetriDishRecording.copyPlayerProfile(player);
		if (operationOwner.recordedPlayerProfile == null) {
			PetriDishRecording.clearRecordedEntity(operationOwner);
			return;
		}
		operationOwner.recordedMaxHealth = player.getMaxHealth();
		operationOwner.recordedWidth = PlayerMimicEntity.WIDTH;
		operationOwner.recordedHeight = PlayerMimicEntity.HEIGHT;
		PetriDishCultivation.clampFluidToRequiredAmount(operationOwner);
		operationOwner.clientPreviewEntityId = null;
		operationOwner.clientPreviewEntity = null;
		operationOwner.setChanged();
		operationOwner.sendData();
	}

	static void snapshotPlayerProfileAtSpawn(PetriDishBlockEntity operationOwner) {
		if (operationOwner.recordedPlayerProfile == null)
			return;
		UUID playerId = operationOwner.recordedPlayerProfile.getId();
		for (Player player : PetriDishRecording.getNearbyPlayers(operationOwner)) {
			if (!playerId.equals(player.getGameProfile().getId()))
				continue;
			GameProfile currentProfile = PetriDishRecording.copyPlayerProfile(player);
			if (currentProfile != null)
				operationOwner.recordedPlayerProfile = currentProfile;
			return;
		}
	}

	static @Nullable
	GameProfile copyPlayerProfile(Player player) {
		return PlayerMimicEntity.decodePlayerProfile(
			PlayerMimicEntity.encodePlayerProfile(player.getGameProfile()));
	}

	static void recordEntity(PetriDishBlockEntity operationOwner, Mob entity) {
		ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		if (entityId == null)
			return;
		operationOwner.recordedEntityId = entityId;
		operationOwner.recordedMimicProfile = MimicProfile.capture(entity);
		operationOwner.recordedPlayerProfile = null;
		operationOwner.recordedMaxHealth = entity.getMaxHealth();
		operationOwner.recordedWidth = entity.getBbWidth();
		operationOwner.recordedHeight = entity.getBbHeight();
		PetriDishCultivation.clampFluidToRequiredAmount(operationOwner);
		operationOwner.clientPreviewEntityId = null;
		operationOwner.clientPreviewEntity = null;
		operationOwner.setChanged();
		operationOwner.sendData();
	}

	static void clearRecordedEntity(PetriDishBlockEntity operationOwner) {
		operationOwner.recordedEntityId = null;
		operationOwner.recordedMimicProfile = null;
		operationOwner.recordedPlayerProfile = null;
		operationOwner.recordedMaxHealth = 0;
		operationOwner.recordedWidth = 0;
		operationOwner.recordedHeight = 0;
		operationOwner.scanCooldown = 0;
		operationOwner.emergenceInProgress = false;
		operationOwner.emergenceTicksRemaining = 0;
		operationOwner.clientPreviewEntityId = null;
		operationOwner.clientPreviewEntity = null;
		PetriDishClientAnimation.stopClientEmergenceAnimation(operationOwner);
		operationOwner.setChanged();
		operationOwner.sendData();
	}
}
