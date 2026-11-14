package com.nobodiiiii.createbiotech.content.petridish;

import net.minecraft.core.registries.BuiltInRegistries;
import java.util.Objects;
import javax.annotation.Nullable;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.nobodiiiii.createbiotech.entity.PlayerMimicEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** PetriDishClientAnimation algorithms over the canonical PetriDishBlockEntity state. */
final class PetriDishClientAnimation {
	private PetriDishClientAnimation() {}


	static void tickClientAnimation(PetriDishBlockEntity operationOwner) {
		PetriDishClientAnimation.syncClientEmergenceAnimation(operationOwner);

		int actualStage = operationOwner.getSlimeGrowthStage();

		if (!operationOwner.clientAnimationInitialized) {
			PetriDishClientAnimation.snapClientAnimation(operationOwner, actualStage);
			return;
		}

		if (actualStage <= 0) {
			PetriDishClientAnimation.snapClientAnimation(operationOwner, 0);
			return;
		}

		int representedStage = operationOwner.clientGrowthAnimating ? operationOwner.clientGrowthToStage + operationOwner.clientQueuedGrowthSteps : operationOwner.clientSettledStage;
		if (actualStage < operationOwner.clientSettledStage || (operationOwner.clientGrowthAnimating && actualStage < operationOwner.clientGrowthToStage)) {
			PetriDishClientAnimation.snapClientAnimation(operationOwner, actualStage);
			return;
		}

		if (actualStage > representedStage)
			operationOwner.clientQueuedGrowthSteps += actualStage - representedStage;

		operationOwner.clientPreviousGrowthAnimationTick = operationOwner.clientGrowthAnimationTick;

		if (operationOwner.clientGrowthAnimating) {
			operationOwner.clientGrowthAnimationTick++;
			if (operationOwner.clientGrowthAnimationTick >= PetriDishBlockEntity.GROWTH_ANIMATION_DURATION) {
				operationOwner.clientSettledStage = operationOwner.clientGrowthToStage;
				operationOwner.clientGrowthAnimating = false;
				operationOwner.clientGrowthAnimationTick = 0;
				operationOwner.clientPreviousGrowthAnimationTick = 0;
			}
		}

		if (!operationOwner.clientGrowthAnimating && operationOwner.clientQueuedGrowthSteps > 0 && operationOwner.clientSettledStage < actualStage)
			PetriDishClientAnimation.startNextClientGrowthAnimation(operationOwner);

		if (operationOwner.clientEmergenceAnimating) {
			operationOwner.clientPreviousEmergenceAnimationTick = operationOwner.clientEmergenceAnimationTick;
			if (operationOwner.clientEmergenceAnimationTick < PetriDishBlockEntity.EMERGENCE_ANIMATION_DURATION)
				operationOwner.clientEmergenceAnimationTick++;
			if (!operationOwner.emergenceInProgress && operationOwner.clientEmergenceAnimationTick >= PetriDishBlockEntity.EMERGENCE_ANIMATION_DURATION)
				PetriDishClientAnimation.stopClientEmergenceAnimation(operationOwner);
		}
	}

	static void syncClientEmergenceAnimation(PetriDishBlockEntity operationOwner) {
		if (!operationOwner.emergenceInProgress)
			return;

		int syncedTick =
			Mth.clamp(PetriDishBlockEntity.EMERGENCE_ANIMATION_DURATION - operationOwner.emergenceTicksRemaining, 0, PetriDishBlockEntity.EMERGENCE_ANIMATION_DURATION);
		if (!operationOwner.clientEmergenceAnimating || operationOwner.emergenceTicksRemaining > operationOwner.clientLastSyncedEmergenceTicksRemaining) {
			operationOwner.clientEmergenceAnimating = true;
			operationOwner.clientEmergenceAnimationTick = syncedTick;
			operationOwner.clientPreviousEmergenceAnimationTick = syncedTick;
		} else if (syncedTick > operationOwner.clientEmergenceAnimationTick) {
			operationOwner.clientEmergenceAnimationTick = syncedTick;
			operationOwner.clientPreviousEmergenceAnimationTick = syncedTick;
		}
		operationOwner.clientLastSyncedEmergenceTicksRemaining = operationOwner.emergenceTicksRemaining;
	}

	static void stopClientEmergenceAnimation(PetriDishBlockEntity operationOwner) {
		operationOwner.clientEmergenceAnimating = false;
		operationOwner.clientEmergenceAnimationTick = 0;
		operationOwner.clientPreviousEmergenceAnimationTick = 0;
		operationOwner.clientLastSyncedEmergenceTicksRemaining = 0;
	}

	static void snapClientAnimation(PetriDishBlockEntity operationOwner, int stage) {
		operationOwner.clientAnimationInitialized = true;
		operationOwner.clientGrowthAnimating = false;
		operationOwner.clientSettledStage = stage;
		operationOwner.clientGrowthFromStage = stage;
		operationOwner.clientGrowthToStage = stage;
		operationOwner.clientQueuedGrowthSteps = 0;
		operationOwner.clientGrowthAnimationTick = 0;
		operationOwner.clientPreviousGrowthAnimationTick = 0;
	}

	static void startNextClientGrowthAnimation(PetriDishBlockEntity operationOwner) {
		operationOwner.clientGrowthAnimating = true;
		operationOwner.clientGrowthFromStage = operationOwner.clientSettledStage;
		operationOwner.clientGrowthToStage = Math.min(4, operationOwner.clientSettledStage + 1);
		operationOwner.clientQueuedGrowthSteps = Math.max(0, operationOwner.clientQueuedGrowthSteps - 1);
		operationOwner.clientGrowthAnimationTick = 0;
		operationOwner.clientPreviousGrowthAnimationTick = 0;
	}

	static int getRenderedSlimeStage(PetriDishBlockEntity operationOwner) {
		if (operationOwner.getLevel() == null || !operationOwner.getLevel().isClientSide || !operationOwner.clientAnimationInitialized)
			return operationOwner.getSlimeGrowthStage();
		return operationOwner.clientGrowthAnimating ? operationOwner.clientGrowthToStage : operationOwner.clientSettledStage;
	}

	static boolean isGrowthAnimating(PetriDishBlockEntity operationOwner) {
		return operationOwner.getLevel() != null && operationOwner.getLevel().isClientSide && operationOwner.clientAnimationInitialized && operationOwner.clientGrowthAnimating;
	}

	static boolean isEmergenceAnimating(PetriDishBlockEntity operationOwner) {
		return operationOwner.getLevel() != null && operationOwner.getLevel().isClientSide && operationOwner.clientEmergenceAnimating;
	}

	static float getEmergenceAnimationProgress(PetriDishBlockEntity operationOwner, float partialTicks) {
		if (!PetriDishClientAnimation.isEmergenceAnimating(operationOwner))
			return 0.0f;
		float tick = Mth.lerp(partialTicks, operationOwner.clientPreviousEmergenceAnimationTick, operationOwner.clientEmergenceAnimationTick);
		return Mth.clamp(tick / PetriDishBlockEntity.EMERGENCE_ANIMATION_DURATION, 0.0f, 1.0f);
	}

	static float getEmergenceAnimationTick(PetriDishBlockEntity operationOwner, float partialTicks) {
		if (!PetriDishClientAnimation.isEmergenceAnimating(operationOwner))
			return 0.0f;
		return Mth.clamp(Mth.lerp(partialTicks, operationOwner.clientPreviousEmergenceAnimationTick, operationOwner.clientEmergenceAnimationTick),
			0.0f, PetriDishBlockEntity.EMERGENCE_ANIMATION_DURATION);
	}

	static @Nullable
	LivingEntity getClientPreviewEntity(PetriDishBlockEntity operationOwner) {
		if (operationOwner.getLevel() == null || !operationOwner.getLevel().isClientSide || operationOwner.recordedEntityId == null)
			return null;

		if (operationOwner.clientPreviewEntity != null && Objects.equals(operationOwner.clientPreviewEntityId, operationOwner.recordedEntityId)
			&& operationOwner.clientPreviewEntity.level() == operationOwner.getLevel())
			return operationOwner.clientPreviewEntity;

		EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(operationOwner.recordedEntityId)
			.orElse(null);
		if (entityType == null)
			return null;

		Entity preview = entityType.create(operationOwner.getLevel());
		if (!(preview instanceof Mob livingPreview))
			return null;

		if (livingPreview instanceof PlayerMimicEntity playerMimic && operationOwner.recordedPlayerProfile != null)
			playerMimic.setImitatedPlayer(operationOwner.recordedPlayerProfile);
		if (operationOwner.recordedMimicProfile != null)
			operationOwner.recordedMimicProfile.apply(livingPreview);
		SlimeMimicHandler.setSlimeMimic(livingPreview, true);
		livingPreview.setNoGravity(true);
		operationOwner.clientPreviewEntityId = operationOwner.recordedEntityId;
		operationOwner.clientPreviewEntity = livingPreview;
		return operationOwner.clientPreviewEntity;
	}

	static int getGrowthAnimationFromStage(PetriDishBlockEntity operationOwner) {
		return operationOwner.getLevel() != null && operationOwner.getLevel().isClientSide && operationOwner.clientAnimationInitialized ? operationOwner.clientGrowthFromStage : operationOwner.getSlimeGrowthStage();
	}

	static int getGrowthAnimationToStage(PetriDishBlockEntity operationOwner) {
		return operationOwner.getLevel() != null && operationOwner.getLevel().isClientSide && operationOwner.clientAnimationInitialized ? operationOwner.clientGrowthToStage : operationOwner.getSlimeGrowthStage();
	}

	static float getGrowthAnimationProgress(PetriDishBlockEntity operationOwner, float partialTicks) {
		if (!PetriDishClientAnimation.isGrowthAnimating(operationOwner))
			return 1.0f;
		float tick = Mth.lerp(partialTicks, operationOwner.clientPreviousGrowthAnimationTick, operationOwner.clientGrowthAnimationTick);
		return Mth.clamp(tick / PetriDishBlockEntity.GROWTH_ANIMATION_DURATION, 0.0f, 1.0f);
	}
}
