package com.nobodiiiii.createbiotech.content.petridish;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.foundation.advancement.PlacedByPlayerAdvancementTracker;
import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.nobodiiiii.createbiotech.registry.CBFluids;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.FluidHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

public class PetriDishBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, Clearable {

	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int[] FALLBACK_SLIME_SIZES = { 1, 2, 4 };

	public static final int GROWTH_ANIMATION_DURATION = 8;
	public static final int EMERGENCE_ANIMATION_DURATION = 20;
	public static final double EMERGENCE_SPAWN_Y_OFFSET = 2.0d / 16.0d;

	private static final String INVENTORY_TAG = "Inventory";
	private static final String TANK_TAG = "Tank";
	private static final String RECORDED_ENTITY_ID_TAG = "RecordedEntityId";
	private static final String RECORDED_MAX_HEALTH_TAG = "RecordedMaxHealth";
	private static final String RECORDED_WIDTH_TAG = "RecordedWidth";
	private static final String RECORDED_HEIGHT_TAG = "RecordedHeight";
	private static final String RECORDED_MIMIC_PROFILE_TAG = "RecordedMimicProfile";
	private static final String SCAN_COOLDOWN_TAG = "ScanCooldown";
	private static final String EMERGENCE_IN_PROGRESS_TAG = "EmergenceInProgress";
	private static final String EMERGENCE_TICKS_REMAINING_TAG = "EmergenceTicksRemaining";

	private final ItemStackHandler inventory = new ItemStackHandler(1) {
		@Override
		protected void onContentsChanged(int slot) {
			if (slot == 0 && getStackInSlot(slot).isEmpty()) {
				clearRecordedEntity();
			}
			setChanged();
			sendData();
		}

		@Override
		public int getSlotLimit(int slot) {
			return 1;
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return stack.is(CBItems.BIONIC_MECHANISM.get());
		}
	};

	private final FluidTank fluidTank = new FluidTank(getTankCapacity()) {
		@Override
		public boolean isFluidValid(FluidStack stack) {
			return isAcceptedFluid(stack);
		}

		@Override
		protected void onContentsChanged() {
			setChanged();
			sendData();
		}
	};

	private final IFluidHandler fluidCapability = new PetriDishFluidHandler();

	@Nullable
	private ResourceLocation recordedEntityId;
	@Nullable
	private MimicProfile recordedMimicProfile;
	private float recordedMaxHealth;
	private float recordedWidth;
	private float recordedHeight;
	private int scanCooldown;
	@Nullable
	private UUID advancementOwner;
	private boolean clientAnimationInitialized;
	private boolean clientGrowthAnimating;
	private int clientSettledStage;
	private int clientGrowthFromStage;
	private int clientGrowthToStage;
	private int clientQueuedGrowthSteps;
	private int clientGrowthAnimationTick;
	private int clientPreviousGrowthAnimationTick;
	private boolean emergenceInProgress;
	private int emergenceTicksRemaining;
	private boolean clientEmergenceAnimating;
	private int clientEmergenceAnimationTick;
	private int clientPreviousEmergenceAnimationTick;
	private int clientLastSyncedEmergenceTicksRemaining;
	@Nullable
	private ResourceLocation clientPreviewEntityId;
	@Nullable
	private LivingEntity clientPreviewEntity;

	public PetriDishBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.PETRI_DISH.get(), pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

	@Override
	public void tick() {
		super.tick();

		if (level == null)
			return;

		if (level.isClientSide) {
			tickClientAnimation();
			return;
		}

		if (emergenceInProgress) {
			tickEmergence();
			return;
		}

		if (scanCooldown > 0)
			scanCooldown--;

		if (hasBionicMechanism() && scanCooldown <= 0) {
			scanCooldown = getScanInterval();
			updateRecordedEntityFromNearby();
		}

		tryCompleteSpawn();
	}

	private void tickClientAnimation() {
		syncClientEmergenceAnimation();

		int actualStage = getSlimeGrowthStage();

		if (!clientAnimationInitialized) {
			snapClientAnimation(actualStage);
			return;
		}

		if (actualStage <= 0) {
			snapClientAnimation(0);
			return;
		}

		int representedStage = clientGrowthAnimating ? clientGrowthToStage + clientQueuedGrowthSteps : clientSettledStage;
		if (actualStage < clientSettledStage || (clientGrowthAnimating && actualStage < clientGrowthToStage)) {
			snapClientAnimation(actualStage);
			return;
		}

		if (actualStage > representedStage)
			clientQueuedGrowthSteps += actualStage - representedStage;

		clientPreviousGrowthAnimationTick = clientGrowthAnimationTick;

		if (clientGrowthAnimating) {
			clientGrowthAnimationTick++;
			if (clientGrowthAnimationTick >= GROWTH_ANIMATION_DURATION) {
				clientSettledStage = clientGrowthToStage;
				clientGrowthAnimating = false;
				clientGrowthAnimationTick = 0;
				clientPreviousGrowthAnimationTick = 0;
			}
		}

		if (!clientGrowthAnimating && clientQueuedGrowthSteps > 0 && clientSettledStage < actualStage)
			startNextClientGrowthAnimation();

		if (clientEmergenceAnimating) {
			clientPreviousEmergenceAnimationTick = clientEmergenceAnimationTick;
			if (clientEmergenceAnimationTick < EMERGENCE_ANIMATION_DURATION)
				clientEmergenceAnimationTick++;
			if (!emergenceInProgress && clientEmergenceAnimationTick >= EMERGENCE_ANIMATION_DURATION)
				stopClientEmergenceAnimation();
		}
	}

	private void syncClientEmergenceAnimation() {
		if (!emergenceInProgress)
			return;

		int syncedTick =
			Mth.clamp(EMERGENCE_ANIMATION_DURATION - emergenceTicksRemaining, 0, EMERGENCE_ANIMATION_DURATION);
		if (!clientEmergenceAnimating || emergenceTicksRemaining > clientLastSyncedEmergenceTicksRemaining) {
			clientEmergenceAnimating = true;
			clientEmergenceAnimationTick = syncedTick;
			clientPreviousEmergenceAnimationTick = syncedTick;
		} else if (syncedTick > clientEmergenceAnimationTick) {
			clientEmergenceAnimationTick = syncedTick;
			clientPreviousEmergenceAnimationTick = syncedTick;
		}
		clientLastSyncedEmergenceTicksRemaining = emergenceTicksRemaining;
	}

	private void stopClientEmergenceAnimation() {
		clientEmergenceAnimating = false;
		clientEmergenceAnimationTick = 0;
		clientPreviousEmergenceAnimationTick = 0;
		clientLastSyncedEmergenceTicksRemaining = 0;
	}

	private void snapClientAnimation(int stage) {
		clientAnimationInitialized = true;
		clientGrowthAnimating = false;
		clientSettledStage = stage;
		clientGrowthFromStage = stage;
		clientGrowthToStage = stage;
		clientQueuedGrowthSteps = 0;
		clientGrowthAnimationTick = 0;
		clientPreviousGrowthAnimationTick = 0;
	}

	private void startNextClientGrowthAnimation() {
		clientGrowthAnimating = true;
		clientGrowthFromStage = clientSettledStage;
		clientGrowthToStage = Math.min(4, clientSettledStage + 1);
		clientQueuedGrowthSteps = Math.max(0, clientQueuedGrowthSteps - 1);
		clientGrowthAnimationTick = 0;
		clientPreviousGrowthAnimationTick = 0;
	}

	public InteractionResult use(Player player, InteractionHand hand) {
		if (level == null)
			return InteractionResult.PASS;
		if (emergenceInProgress)
			return InteractionResult.SUCCESS;

		ItemStack heldItem = player.getItemInHand(hand);
		if (FluidHelper.tryEmptyItemIntoBE(level, player, hand, heldItem, this))
			return InteractionResult.SUCCESS;
		if (FluidHelper.tryFillItemFromBE(level, player, hand, heldItem, this))
			return InteractionResult.SUCCESS;

		if (heldItem.is(CBItems.BIONIC_MECHANISM.get())) {
			if (!inventory.getStackInSlot(0).isEmpty())
				return InteractionResult.SUCCESS;
			if (level.isClientSide)
				return InteractionResult.SUCCESS;

			ItemStack inserted = heldItem.copyWithCount(1);
			inventory.setStackInSlot(0, inserted);
			if (!player.isCreative())
				heldItem.shrink(1);
			level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.5f, 1.0f);
			updateRecordedEntityFromNearby();
			return InteractionResult.SUCCESS;
		}

		if (heldItem.isEmpty() && hasBionicMechanism()) {
			if (level.isClientSide)
				return InteractionResult.SUCCESS;
			ItemStack extracted = inventory.extractItem(0, 1, false);
			player.getInventory().placeItemBackInInventory(extracted);
			level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.5f, 1.0f);
			fluidTank.setFluid(FluidStack.EMPTY);
			clearRecordedEntity();
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	@Override
	public void clearContent() {
		inventory.setStackInSlot(0, ItemStack.EMPTY);
		fluidTank.setFluid(FluidStack.EMPTY);
		clearRecordedEntity();
	}

	public void setAdvancementOwner(@Nullable LivingEntity placer) {
		advancementOwner = PlacedByPlayerAdvancementTracker.ownerFrom(placer);
		setChanged();
	}

	public ItemStack getBionicMechanism() {
		return inventory.getStackInSlot(0);
	}

	public FluidStack getFluid() {
		return fluidTank.getFluid();
	}

	@Nullable
	public ResourceLocation getRecordedEntityId() {
		return recordedEntityId;
	}

	public float getRecordedMaxHealth() {
		return recordedMaxHealth;
	}

	public int getRequiredFluidAmount() {
		if (recordedEntityId == null || recordedMaxHealth <= 0)
			return 0;
		return Math.max(1, (int) Math.ceil(recordedMaxHealth)) * getFluidPerHealth();
	}

	public float getFillProgress() {
		int required = getRequiredFluidAmount();
		if (required <= 0 || fluidTank.getFluidAmount() <= 0)
			return 0.0f;
		return Mth.clamp((float) fluidTank.getFluidAmount() / required, 0.0f, 1.0f);
	}

	public int getSlimeGrowthStage() {
		float progress = getFillProgress();
		if (progress <= 0.0f)
			return 0;
		if (progress < 0.25f)
			return 1;
		if (progress < 0.5f)
			return 2;
		if (progress < 0.75f)
			return 3;
		return 4;
	}

	public int getRenderedSlimeStage() {
		if (level == null || !level.isClientSide || !clientAnimationInitialized)
			return getSlimeGrowthStage();
		return clientGrowthAnimating ? clientGrowthToStage : clientSettledStage;
	}

	public boolean isGrowthAnimating() {
		return level != null && level.isClientSide && clientAnimationInitialized && clientGrowthAnimating;
	}

	public boolean isEmergenceAnimating() {
		return level != null && level.isClientSide && clientEmergenceAnimating;
	}

	public float getEmergenceAnimationProgress(float partialTicks) {
		if (!isEmergenceAnimating())
			return 0.0f;
		float tick = Mth.lerp(partialTicks, clientPreviousEmergenceAnimationTick, clientEmergenceAnimationTick);
		return Mth.clamp(tick / EMERGENCE_ANIMATION_DURATION, 0.0f, 1.0f);
	}

	public float getEmergenceAnimationTick(float partialTicks) {
		if (!isEmergenceAnimating())
			return 0.0f;
		return Mth.clamp(Mth.lerp(partialTicks, clientPreviousEmergenceAnimationTick, clientEmergenceAnimationTick),
			0.0f, EMERGENCE_ANIMATION_DURATION);
	}

	@Nullable
	public LivingEntity getClientPreviewEntity() {
		if (level == null || !level.isClientSide || recordedEntityId == null)
			return null;

		if (clientPreviewEntity != null && Objects.equals(clientPreviewEntityId, recordedEntityId)
			&& clientPreviewEntity.level() == level)
			return clientPreviewEntity;

		EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(recordedEntityId)
			.orElse(null);
		if (entityType == null)
			return null;

		Entity preview = entityType.create(level);
		if (!(preview instanceof Mob livingPreview))
			return null;

		if (recordedMimicProfile != null)
			recordedMimicProfile.apply(livingPreview);
		SlimeMimicHandler.setSlimeMimic(livingPreview, true);
		livingPreview.setNoGravity(true);
		clientPreviewEntityId = recordedEntityId;
		clientPreviewEntity = livingPreview;
		return clientPreviewEntity;
	}

	public int getGrowthAnimationFromStage() {
		return level != null && level.isClientSide && clientAnimationInitialized ? clientGrowthFromStage : getSlimeGrowthStage();
	}

	public int getGrowthAnimationToStage() {
		return level != null && level.isClientSide && clientAnimationInitialized ? clientGrowthToStage : getSlimeGrowthStage();
	}

	public float getGrowthAnimationProgress(float partialTicks) {
		if (!isGrowthAnimating())
			return 1.0f;
		float tick = Mth.lerp(partialTicks, clientPreviousGrowthAnimationTick, clientGrowthAnimationTick);
		return Mth.clamp(tick / GROWTH_ANIMATION_DURATION, 0.0f, 1.0f);
	}

	public boolean canAcceptFluidNow() {
		if (!hasBionicMechanism())
			return false;
		if (recordedEntityId == null) {
			updateRecordedEntityFromNearby();
			return recordedEntityId != null;
		}
		return !requiresNearbyMatchingEntity() || hasMatchingEntityNearby();
	}

	private boolean hasBionicMechanism() {
		return inventory.getStackInSlot(0).is(CBItems.BIONIC_MECHANISM.get());
	}

	private boolean isAcceptedFluid(FluidStack stack) {
		if (stack.isEmpty())
			return false;
		if (stack.getFluid() != CBFluids.LIQUID_LIVING_SLIME.get())
			return false;
		if (!canAcceptFluidNow())
			return false;
		int required = getRequiredFluidAmount();
		if (required <= 0)
			return false;
		FluidStack stored = fluidTank.getFluid();
		if (!stored.isEmpty() && !stored.isFluidEqual(stack))
			return false;
		return stored.getAmount() < required;
	}

	private void tryCompleteSpawn() {
		if (!(level instanceof ServerLevel))
			return;
		if (!hasBionicMechanism())
			return;
		if (recordedEntityId == null || recordedMaxHealth <= 0)
			return;
		if (requiresNearbyMatchingEntity() && !hasMatchingEntityNearby())
			return;

		int required = getRequiredFluidAmount();
		if (required <= 0 || fluidTank.getFluidAmount() < required)
			return;

		EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(recordedEntityId)
			.orElse(null);
		if (entityType == null)
			return;

		if (!hasClearEmergenceSpace())
			return;

		beginEmergence();
	}

	private void beginEmergence() {
		emergenceInProgress = true;
		emergenceTicksRemaining = EMERGENCE_ANIMATION_DURATION;
		setChanged();
		sendData();
		level.playSound(null, worldPosition, SoundEvents.SLIME_JUMP, SoundSource.BLOCKS, 0.8f, 0.9f);
	}

	private void tickEmergence() {
		if (!(level instanceof ServerLevel serverLevel))
			return;
		if (!emergenceInProgress)
			return;
		if (emergenceTicksRemaining > 0)
			emergenceTicksRemaining--;
		if (emergenceTicksRemaining > 0)
			return;

		finishEmergence(serverLevel);
	}

	private void finishEmergence(ServerLevel serverLevel) {
		if (recordedEntityId == null) {
			cancelEmergence();
			return;
		}
		EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(recordedEntityId)
			.orElse(null);
		if (entityType == null) {
			cancelEmergence();
			return;
		}

		double spawnX = worldPosition.getX() + 0.5d;
		double spawnY = worldPosition.getY() + EMERGENCE_SPAWN_Y_OFFSET;
		double spawnZ = worldPosition.getZ() + 0.5d;
		BlockPos spawnPos = BlockPos.containing(spawnX, spawnY, spawnZ);
		if (!hasClearEmergenceSpace()) {
			cancelEmergence();
			return;
		}

		int required = getRequiredFluidAmount();
		if (required <= 0 || fluidTank.getFluidAmount() < required) {
			cancelEmergence();
			return;
		}

		float spawnYaw = getSpawnYaw();
		Mob livingEntity = trySpawnRecordedMob(serverLevel, entityType, spawnPos, spawnX, spawnY, spawnZ, spawnYaw);
		if (livingEntity == null)
			livingEntity = trySpawnFallbackSlime(serverLevel, entityType, spawnPos, spawnX, spawnY, spawnZ, spawnYaw);
		if (livingEntity == null) {
			cancelEmergence();
			return;
		}

		emergenceInProgress = false;
		emergenceTicksRemaining = 0;
		fluidTank.drain(required, FluidAction.EXECUTE);
		inventory.extractItem(0, 1, false);
		awardCultivationAdvancements(serverLevel, livingEntity);
		clearRecordedEntity();
		level.playSound(null, worldPosition, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.7f, 0.9f);
		sendData();
	}

	@Nullable
	private Mob trySpawnRecordedMob(ServerLevel serverLevel, EntityType<?> entityType, BlockPos spawnPos,
		double spawnX, double spawnY, double spawnZ, float spawnYaw) {
		Entity created = null;
		try {
			// EntityType.spawn() adds the entity before returning it. Keep creation and insertion separate so
			// unsupported types and failed spawn packets can be discarded before falling back safely.
			created = entityType.create(serverLevel, null, spawnPos, MobSpawnType.DISPENSER, true, false);
			if (!(created instanceof Mob mob)) {
				if (created != null)
					created.discard();
				LOGGER.warn("Petri dish at {} recorded non-mob entity type {}; using slime fallback",
					worldPosition, recordedEntityId);
				return null;
			}

			prepareSpawnedMimic(mob);
			positionSpawnedMob(mob, spawnX, spawnY, spawnZ, spawnYaw);
			if (!serverLevel.addFreshEntity(mob)) {
				mob.discard();
				LOGGER.warn("Petri dish at {} could not add cultivated {}; using slime fallback",
					worldPosition, recordedEntityId);
				return null;
			}
			return mob;
		} catch (RuntimeException exception) {
			if (created != null)
				created.discard();
			LOGGER.error("Petri dish at {} failed to cultivate {}; using slime fallback",
				worldPosition, recordedEntityId, exception);
			return null;
		}
	}

	@Nullable
	private Slime trySpawnFallbackSlime(ServerLevel serverLevel, EntityType<?> recordedType, BlockPos spawnPos,
		double spawnX, double spawnY, double spawnZ, float spawnYaw) {
		Slime slime = null;
		try {
			slime = EntityType.SLIME.create(serverLevel, null, spawnPos, MobSpawnType.DISPENSER, true, false);
			if (slime == null) {
				LOGGER.error("Petri dish at {} could not create its slime fallback for {}",
					worldPosition, recordedEntityId);
				return null;
			}

			int slimeSize = selectFallbackSlimeSize(slime, recordedType);
			slime.setSize(slimeSize, true);
			SlimeMimicHandler.markSpawnedEntity(slime);
			positionSpawnedMob(slime, spawnX, spawnY, spawnZ, spawnYaw);
			if (!serverLevel.addFreshEntity(slime)) {
				slime.discard();
				LOGGER.error("Petri dish at {} could not add its size-{} slime fallback for {}",
					worldPosition, slimeSize, recordedEntityId);
				return null;
			}
			LOGGER.warn("Petri dish at {} replaced failed cultivation of {} with a size-{} slime",
				worldPosition, recordedEntityId, slimeSize);
			return slime;
		} catch (RuntimeException exception) {
			if (slime != null)
				slime.discard();
			LOGGER.error("Petri dish at {} failed to spawn its slime fallback for {}",
				worldPosition, recordedEntityId, exception);
			return null;
		}
	}

	private int selectFallbackSlimeSize(Slime slime, EntityType<?> recordedType) {
		float targetWidth = sanitizeRecordedDimension(recordedWidth, recordedType.getWidth());
		float targetHeight = sanitizeRecordedDimension(recordedHeight, recordedType.getHeight());
		double targetVolume = targetWidth * targetWidth * targetHeight;
		int closestSize = FALLBACK_SLIME_SIZES[0];
		double closestDistance = Double.POSITIVE_INFINITY;
		for (int candidate : FALLBACK_SLIME_SIZES) {
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

	private static float sanitizeRecordedDimension(float recorded, float fallback) {
		return Float.isFinite(recorded) && recorded > 0.0f ? recorded : Math.max(fallback, 0.01f);
	}

	private static void positionSpawnedMob(Mob mob, double spawnX, double spawnY, double spawnZ, float spawnYaw) {
		mob.moveTo(spawnX, spawnY, spawnZ, spawnYaw, mob.getXRot());
		mob.setYRot(spawnYaw);
		mob.yRotO = spawnYaw;
		mob.setYBodyRot(spawnYaw);
		mob.yBodyRotO = spawnYaw;
		mob.setYHeadRot(spawnYaw);
		mob.yHeadRotO = spawnYaw;
	}

	private void prepareSpawnedMimic(@Nullable Entity entity) {
		if (entity instanceof LivingEntity livingEntity && recordedMimicProfile != null)
			recordedMimicProfile.apply(livingEntity);
		SlimeMimicHandler.markSpawnedEntity(entity);
	}

	private void awardCultivationAdvancements(ServerLevel serverLevel, LivingEntity cultivatedEntity) {
		Vec3 observationPosition =
			SubLevelCompat.toWorld(serverLevel, worldPosition, Vec3.atCenterOf(worldPosition));
		CBAdvancements.awardNearby(serverLevel, observationPosition, 16, CBAdvancements.PETRI_DISH);
		if (cultivatedEntity.getType() == EntityType.SLIME)
			CBAdvancements.awardNearby(serverLevel, observationPosition, 16, CBAdvancements.PERFECT_DISGUISE);
		if (cultivatedEntity.isBaby())
			CBAdvancements.awardNearby(serverLevel, observationPosition, 16, CBAdvancements.MIMIC_BABY);
	}

	private boolean hasClearEmergenceSpace() {
		if (level == null)
			return false;

		return level.isEmptyBlock(worldPosition.above());
	}

	private void cancelEmergence() {
		emergenceInProgress = false;
		emergenceTicksRemaining = 0;
		setChanged();
		sendData();
	}

	private void updateRecordedEntityFromNearby() {
		if (level == null || !hasBionicMechanism())
			return;
		if (recordedEntityId != null) {
			if (requiresNearbyMatchingEntity() && !hasMatchingEntityNearby()) {
				// Keep the record, but reject further filling until the type comes back nearby.
				sendData();
			}
			return;
		}

		List<Mob> entities = getNearbyMobs();
		for (Mob entity : entities) {
			if (!isRecordableEntity(entity))
				continue;
			recordEntity(entity);
			return;
		}
	}

	private boolean hasMatchingEntityNearby() {
		if (recordedEntityId == null)
			return false;
		for (Mob entity : getNearbyMobs()) {
			ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
			if (Objects.equals(recordedEntityId, entityId))
				return true;
		}
		return false;
	}

	private List<Mob> getNearbyMobs() {
		if (level == null)
			return List.of();
		AABB bounds = new AABB(worldPosition).inflate(getScanRadius());
		// LivingEntity is deliberately too broad: Create packages are LivingEntity instances as an
		// implementation detail. Mob is the gameplay boundary for creatures the dish may imitate.
		return level.getEntitiesOfClass(Mob.class, bounds, this::isRecordableEntity);
	}

	private static int getScanInterval() {
		return CBConfigs.SERVER.petriDish.scanInterval.get();
	}

	private static int getFluidPerHealth() {
		return CBConfigs.SERVER.petriDish.fluidPerHealth.get();
	}

	private static int getScanRadius() {
		return CBConfigs.SERVER.petriDish.scanRadius.get();
	}

	private static int getTankCapacity() {
		return CBConfigs.SERVER.petriDish.tankCapacity.get();
	}

	private static boolean requiresNearbyMatchingEntity() {
		return CBConfigs.SERVER.petriDish.requireNearbyMatchingEntity.get();
	}

	private boolean isRecordableEntity(Mob entity) {
		if (!entity.isAlive())
			return false;
		if (entity.isSpectator())
			return false;
		if (entity.blockPosition().equals(worldPosition))
			return false;
		ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		return entityId != null;
	}

	private void recordEntity(Mob entity) {
		ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		if (entityId == null)
			return;
		recordedEntityId = entityId;
		recordedMimicProfile = MimicProfile.capture(entity);
		recordedMaxHealth = entity.getMaxHealth();
		recordedWidth = entity.getBbWidth();
		recordedHeight = entity.getBbHeight();
		int required = getRequiredFluidAmount();
		if (fluidTank.getFluidAmount() > required) {
			fluidTank.drain(fluidTank.getFluidAmount() - required, FluidAction.EXECUTE);
		}
		setChanged();
		sendData();
	}

	private void clearRecordedEntity() {
		recordedEntityId = null;
		recordedMimicProfile = null;
		recordedMaxHealth = 0;
		recordedWidth = 0;
		recordedHeight = 0;
		scanCooldown = 0;
		emergenceInProgress = false;
		emergenceTicksRemaining = 0;
		clientPreviewEntityId = null;
		clientPreviewEntity = null;
		stopClientEmergenceAnimation();
		setChanged();
		sendData();
	}

	public float getSpawnYaw() {
		if (getBlockState().hasProperty(PetriDishBlock.FACING))
			return getBlockState().getValue(PetriDishBlock.FACING).toYRot();
		return 0.0f;
	}

	@Override
	public void destroy() {
		if (level != null && !level.isClientSide) {
			ItemStack stack = inventory.getStackInSlot(0);
			if (!stack.isEmpty()) {
				Block.popResource(level, worldPosition, stack.copy());
				inventory.setStackInSlot(0, ItemStack.EMPTY);
			}
		}
		super.destroy();
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.put(INVENTORY_TAG, inventory.serializeNBT(registries));
		tag.put(TANK_TAG, fluidTank.writeToNBT(registries, new CompoundTag()));
		if (recordedEntityId != null)
			tag.putString(RECORDED_ENTITY_ID_TAG, recordedEntityId.toString());
		if (recordedMimicProfile != null)
			tag.put(RECORDED_MIMIC_PROFILE_TAG, recordedMimicProfile.save());
		tag.putFloat(RECORDED_MAX_HEALTH_TAG, recordedMaxHealth);
		tag.putFloat(RECORDED_WIDTH_TAG, recordedWidth);
		tag.putFloat(RECORDED_HEIGHT_TAG, recordedHeight);
		tag.putInt(SCAN_COOLDOWN_TAG, scanCooldown);
		tag.putBoolean(EMERGENCE_IN_PROGRESS_TAG, emergenceInProgress);
		tag.putInt(EMERGENCE_TICKS_REMAINING_TAG, emergenceTicksRemaining);
		PlacedByPlayerAdvancementTracker.writeOwner(tag, advancementOwner);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		inventory.deserializeNBT(registries, tag.getCompound(INVENTORY_TAG));
		fluidTank.readFromNBT(registries, tag.getCompound(TANK_TAG));
		recordedEntityId =
			tag.contains(RECORDED_ENTITY_ID_TAG) ? ResourceLocation.parse(tag.getString(RECORDED_ENTITY_ID_TAG)) : null;
		recordedMimicProfile = tag.contains(RECORDED_MIMIC_PROFILE_TAG, Tag.TAG_COMPOUND)
			? MimicProfile.load(tag.getCompound(RECORDED_MIMIC_PROFILE_TAG)) : null;
		if (recordedMimicProfile != null && !recordedMimicProfile.matches(recordedEntityId))
			recordedMimicProfile = null;
		recordedMaxHealth = tag.getFloat(RECORDED_MAX_HEALTH_TAG);
		recordedWidth = tag.getFloat(RECORDED_WIDTH_TAG);
		recordedHeight = tag.getFloat(RECORDED_HEIGHT_TAG);
		scanCooldown = tag.getInt(SCAN_COOLDOWN_TAG);
		emergenceInProgress = tag.getBoolean(EMERGENCE_IN_PROGRESS_TAG);
		emergenceTicksRemaining = tag.getInt(EMERGENCE_TICKS_REMAINING_TAG);
		if (!emergenceInProgress)
			stopClientEmergenceAnimation();
		clientPreviewEntityId = null;
		clientPreviewEntity = null;
		advancementOwner = PlacedByPlayerAdvancementTracker.readOwner(tag);
	}

	@Override
	public void invalidate() {
		super.invalidate();
	}

	public ItemStackHandler getItemCapability(@Nullable Direction side) {
		return inventory;
	}

	public IFluidHandler getFluidCapability(@Nullable Direction side) {
		return fluidCapability;
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		tooltip.add(Component.translatable("create_biotech.petri_dish.goggles.title")
			.withStyle(ChatFormatting.GRAY));
		if (hasBionicMechanism()) {
			tooltip.add(Component.translatable("create_biotech.petri_dish.goggles.bionic_loaded")
				.withStyle(ChatFormatting.GREEN));
		} else {
			tooltip.add(Component.translatable("create_biotech.petri_dish.goggles.bionic_missing")
				.withStyle(ChatFormatting.RED));
		}

		if (recordedEntityId != null) {
			tooltip.add(Component.translatable("create_biotech.petri_dish.goggles.recorded",
				Component.translatable(entityTranslationKey(recordedEntityId)))
				.withStyle(ChatFormatting.GRAY));
			tooltip.add(Component.translatable("create_biotech.petri_dish.goggles.required_fluid", getRequiredFluidAmount())
				.withStyle(ChatFormatting.AQUA));
		} else {
			tooltip.add(Component.translatable("create_biotech.petri_dish.goggles.unrecorded")
				.withStyle(ChatFormatting.YELLOW));
		}

		return containedFluidTooltip(tooltip, isPlayerSneaking, fluidCapability);
	}

	private static String entityTranslationKey(ResourceLocation entityId) {
		return "entity." + entityId.getNamespace() + "." + entityId.getPath();
	}

	private class PetriDishFluidHandler implements IFluidHandler {

		@Override
		public int getTanks() {
			return 1;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			return tank == 0 ? fluidTank.getFluid() : FluidStack.EMPTY;
		}

		@Override
		public int getTankCapacity(int tank) {
			return tank == 0 ? fluidTank.getCapacity() : 0;
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return tank == 0 && fluidTank.isFluidValid(stack);
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			if (!fluidTank.isFluidValid(resource))
				return 0;
			int required = getRequiredFluidAmount();
			if (required <= 0)
				return 0;
			int room = required - fluidTank.getFluidAmount();
			if (room <= 0)
				return 0;
			FluidStack limited = resource.copy();
			limited.setAmount(Math.min(resource.getAmount(), room));
			return fluidTank.fill(limited, action);
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			return fluidTank.drain(resource, action);
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			return fluidTank.drain(maxDrain, action);
		}
	}
}
