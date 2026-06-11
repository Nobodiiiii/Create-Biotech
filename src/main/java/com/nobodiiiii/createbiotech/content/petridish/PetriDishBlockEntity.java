package com.nobodiiiii.createbiotech.content.petridish;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.nobodiiiii.createbiotech.foundation.advancement.PlacedByPlayerAdvancementTracker;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.ItemStackHandler;

public class PetriDishBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

	public static final int GROWTH_ANIMATION_DURATION = 8;
	public static final int EMERGENCE_ANIMATION_DURATION = 20;
	public static final double EMERGENCE_SPAWN_Y_OFFSET = 2.0d / 16.0d;

	private static final String INVENTORY_TAG = "Inventory";
	private static final String TANK_TAG = "Tank";
	private static final String RECORDED_ENTITY_ID_TAG = "RecordedEntityId";
	private static final String RECORDED_MAX_HEALTH_TAG = "RecordedMaxHealth";
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

	private final LazyOptional<ItemStackHandler> itemCapability = LazyOptional.of(() -> inventory);
	private final LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> new PetriDishFluidHandler());

	@Nullable
	private ResourceLocation recordedEntityId;
	private float recordedMaxHealth;
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

		EntityType<?> entityType = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(recordedEntityId);
		if (entityType == null)
			return null;

		Entity preview = entityType.create(level);
		if (!(preview instanceof LivingEntity livingPreview))
			return null;

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

		EntityType<?> entityType = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(recordedEntityId);
		if (entityType == null)
			return;

		if (!hasClearEmergenceSpace(entityType))
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
		EntityType<?> entityType = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(recordedEntityId);
		if (entityType == null) {
			cancelEmergence();
			return;
		}

		double spawnX = worldPosition.getX() + 0.5d;
		double spawnY = worldPosition.getY() + EMERGENCE_SPAWN_Y_OFFSET;
		double spawnZ = worldPosition.getZ() + 0.5d;
		BlockPos spawnPos = BlockPos.containing(spawnX, spawnY, spawnZ);
		if (!hasClearEmergenceSpace(entityType)) {
			cancelEmergence();
			return;
		}

		int required = getRequiredFluidAmount();
		if (required <= 0 || fluidTank.getFluidAmount() < required) {
			cancelEmergence();
			return;
		}

		float spawnYaw = getSpawnYaw();
		CompoundTag spawnTag = SlimeMimicHandler.createPreparedEntityTag(null);
		Entity spawned = entityType.spawn(serverLevel, spawnTag, entity -> {
			entity.moveTo(spawnX, spawnY, spawnZ, spawnYaw, entity.getXRot());
			SlimeMimicHandler.markSpawnedEntity(entity);
		}, spawnPos, MobSpawnType.DISPENSER, true, false);
		if (spawned == null) {
			spawned = entityType.create(serverLevel);
			if (spawned != null) {
				SlimeMimicHandler.markSpawnedEntity(spawned);
				spawned.moveTo(spawnX, spawnY, spawnZ, serverLevel.random.nextFloat() * 360.0f, 0.0f);
				serverLevel.addFreshEntity(spawned);
			}
		}
		if (!(spawned instanceof LivingEntity livingEntity)) {
			cancelEmergence();
			return;
		}

		livingEntity.moveTo(spawnX, spawnY, spawnZ, spawnYaw, livingEntity.getXRot());
		livingEntity.setYRot(spawnYaw);
		livingEntity.yRotO = spawnYaw;
		livingEntity.setYBodyRot(spawnYaw);
		livingEntity.yBodyRotO = spawnYaw;
		livingEntity.setYHeadRot(spawnYaw);
		livingEntity.yHeadRotO = spawnYaw;
		SlimeMimicHandler.markSpawnedEntity(livingEntity);
		emergenceInProgress = false;
		emergenceTicksRemaining = 0;
		fluidTank.drain(required, FluidAction.EXECUTE);
		inventory.extractItem(0, 1, false);
		clearRecordedEntity();
		level.playSound(null, worldPosition, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.7f, 0.9f);
		sendData();
	}

	private boolean hasClearEmergenceSpace(EntityType<?> entityType) {
		if (level == null)
			return false;

		Entity probe = entityType.create(level);
		if (probe == null)
			return false;

		double spawnX = worldPosition.getX() + 0.5d;
		double spawnY = worldPosition.getY() + EMERGENCE_SPAWN_Y_OFFSET;
		double spawnZ = worldPosition.getZ() + 0.5d;
		float spawnYaw = getSpawnYaw();
		probe.moveTo(spawnX, spawnY, spawnZ, spawnYaw, probe.getXRot());

		AABB bounds = probe.getBoundingBox().deflate(1.0E-6d);
		if (!level.getEntityCollisions(probe, bounds)
			.isEmpty())
			return false;

		CollisionContext collisionContext = CollisionContext.of(probe);
		VoxelShape boundsShape = Shapes.create(bounds);
		BlockPos minPos = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
		BlockPos maxPos = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (int x = minPos.getX(); x <= maxPos.getX(); x++) {
			for (int y = minPos.getY(); y <= maxPos.getY(); y++) {
				for (int z = minPos.getZ(); z <= maxPos.getZ(); z++) {
					cursor.set(x, y, z);
					if (cursor.equals(worldPosition))
						continue;

					BlockState state = level.getBlockState(cursor);
					VoxelShape collisionShape = state.getCollisionShape(level, cursor, collisionContext);
					if (collisionShape.isEmpty())
						continue;

					if (Shapes.joinIsNotEmpty(collisionShape.move(x, y, z), boundsShape, BooleanOp.AND))
						return false;
				}
			}
		}

		return true;
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

		List<LivingEntity> entities = getNearbyLivingEntities();
		for (LivingEntity entity : entities) {
			if (!isRecordableEntity(entity))
				continue;
			recordEntity(entity);
			return;
		}
	}

	private boolean hasMatchingEntityNearby() {
		if (recordedEntityId == null)
			return false;
		for (LivingEntity entity : getNearbyLivingEntities()) {
			ResourceLocation entityId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
			if (Objects.equals(recordedEntityId, entityId))
				return true;
		}
		return false;
	}

	private List<LivingEntity> getNearbyLivingEntities() {
		if (level == null)
			return List.of();
		AABB bounds = new AABB(worldPosition).inflate(getScanRadius());
		return level.getEntitiesOfClass(LivingEntity.class, bounds, this::isRecordableEntity);
	}

	private static int getScanInterval() {
		return CBConfigs.COMMON.petriDish.scanInterval.get();
	}

	private static int getFluidPerHealth() {
		return CBConfigs.COMMON.petriDish.fluidPerHealth.get();
	}

	private static int getScanRadius() {
		return CBConfigs.COMMON.petriDish.scanRadius.get();
	}

	private static int getTankCapacity() {
		return CBConfigs.COMMON.petriDish.tankCapacity.get();
	}

	private static boolean requiresNearbyMatchingEntity() {
		return CBConfigs.COMMON.petriDish.requireNearbyMatchingEntity.get();
	}

	private boolean isRecordableEntity(LivingEntity entity) {
		if (!entity.isAlive())
			return false;
		if (entity.isSpectator())
			return false;
		if (entity.blockPosition().equals(worldPosition))
			return false;
		if (entity instanceof Player)
			return false;
		EntityType<?> type = entity.getType();
		if (type == EntityType.ARMOR_STAND)
			return false;
		ResourceLocation entityId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(type);
		return entityId != null;
	}

	private void recordEntity(LivingEntity entity) {
		ResourceLocation entityId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
		if (entityId == null)
			return;
		recordedEntityId = entityId;
		recordedMaxHealth = entity.getMaxHealth();
		int required = getRequiredFluidAmount();
		if (fluidTank.getFluidAmount() > required) {
			fluidTank.drain(fluidTank.getFluidAmount() - required, FluidAction.EXECUTE);
		}
		setChanged();
		sendData();
	}

	private void clearRecordedEntity() {
		recordedEntityId = null;
		recordedMaxHealth = 0;
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
	protected void write(CompoundTag tag, boolean clientPacket) {
		super.write(tag, clientPacket);
		tag.put(INVENTORY_TAG, inventory.serializeNBT());
		tag.put(TANK_TAG, fluidTank.writeToNBT(new CompoundTag()));
		if (recordedEntityId != null)
			tag.putString(RECORDED_ENTITY_ID_TAG, recordedEntityId.toString());
		tag.putFloat(RECORDED_MAX_HEALTH_TAG, recordedMaxHealth);
		tag.putInt(SCAN_COOLDOWN_TAG, scanCooldown);
		tag.putBoolean(EMERGENCE_IN_PROGRESS_TAG, emergenceInProgress);
		tag.putInt(EMERGENCE_TICKS_REMAINING_TAG, emergenceTicksRemaining);
		PlacedByPlayerAdvancementTracker.writeOwner(tag, advancementOwner);
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		inventory.deserializeNBT(tag.getCompound(INVENTORY_TAG));
		fluidTank.readFromNBT(tag.getCompound(TANK_TAG));
		recordedEntityId =
			tag.contains(RECORDED_ENTITY_ID_TAG) ? new ResourceLocation(tag.getString(RECORDED_ENTITY_ID_TAG)) : null;
		recordedMaxHealth = tag.getFloat(RECORDED_MAX_HEALTH_TAG);
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
		itemCapability.invalidate();
		fluidCapability.invalidate();
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
		if (cap == ForgeCapabilities.ITEM_HANDLER)
			return itemCapability.cast();
		if (cap == ForgeCapabilities.FLUID_HANDLER)
			return fluidCapability.cast();
		return super.getCapability(cap, side);
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

		return containedFluidTooltip(tooltip, isPlayerSneaking, getCapability(ForgeCapabilities.FLUID_HANDLER));
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
