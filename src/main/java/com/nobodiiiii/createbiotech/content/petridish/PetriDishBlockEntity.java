package com.nobodiiiii.createbiotech.content.petridish;

import net.minecraft.core.HolderLookup;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.entity.PlayerMimicEntity;
import com.nobodiiiii.createbiotech.foundation.advancement.PlacedByPlayerAdvancementTracker;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

public class PetriDishBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, Clearable {

	static final Logger LOGGER = LogUtils.getLogger();
	static final int[] FALLBACK_SLIME_SIZES = { 1, 2, 4 };

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
	private static final String RECORDED_PLAYER_PROFILE_TAG = "RecordedPlayerProfile";
	private static final String SCAN_COOLDOWN_TAG = "ScanCooldown";
	private static final String EMERGENCE_IN_PROGRESS_TAG = "EmergenceInProgress";
	private static final String EMERGENCE_TICKS_REMAINING_TAG = "EmergenceTicksRemaining";

	final ItemStackHandler inventory = new ItemStackHandler(1) {
		@Override
		protected void onContentsChanged(int slot) {
			if (slot == 0 && getStackInSlot(slot).isEmpty()) {
				PetriDishRecording.clearRecordedEntity(PetriDishBlockEntity.this);
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

	final FluidTank fluidTank = new FluidTank(PetriDishRecording.getTankCapacity()) {
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
	ResourceLocation recordedEntityId;
	@Nullable
	MimicProfile recordedMimicProfile;
	@Nullable
	GameProfile recordedPlayerProfile;
	float recordedMaxHealth;
	float recordedWidth;
	float recordedHeight;
	int scanCooldown;
	@Nullable
	private UUID advancementOwner;
	boolean clientAnimationInitialized;
	boolean clientGrowthAnimating;
	int clientSettledStage;
	int clientGrowthFromStage;
	int clientGrowthToStage;
	int clientQueuedGrowthSteps;
	int clientGrowthAnimationTick;
	int clientPreviousGrowthAnimationTick;
	boolean emergenceInProgress;
	int emergenceTicksRemaining;
	boolean clientEmergenceAnimating;
	int clientEmergenceAnimationTick;
	int clientPreviousEmergenceAnimationTick;
	int clientLastSyncedEmergenceTicksRemaining;
	@Nullable
	ResourceLocation clientPreviewEntityId;
	@Nullable
	LivingEntity clientPreviewEntity;

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
			PetriDishClientAnimation.tickClientAnimation(this);
			return;
		}

		PetriDishRecording.refreshTargetMode(this);

		if (emergenceInProgress) {
			PetriDishCultivation.tickEmergence(this);
			return;
		}

		if (scanCooldown > 0)
			scanCooldown--;

		if (hasBionicMechanism() && scanCooldown <= 0) {
			scanCooldown = PetriDishRecording.getScanInterval();
			PetriDishRecording.updateRecordedEntityFromNearby(this);
		}

		PetriDishCultivation.tryCompleteSpawn(this);
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
			PetriDishRecording.updateRecordedEntityFromNearby(this);
			return InteractionResult.SUCCESS;
		}

		if (heldItem.isEmpty() && hasBionicMechanism()) {
			if (level.isClientSide)
				return InteractionResult.SUCCESS;
			ItemStack extracted = inventory.extractItem(0, 1, false);
			player.getInventory().placeItemBackInInventory(extracted);
			level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.5f, 1.0f);
			fluidTank.setFluid(FluidStack.EMPTY);
			PetriDishRecording.clearRecordedEntity(this);
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	@Override
	public void clearContent() {
		inventory.setStackInSlot(0, ItemStack.EMPTY);
		fluidTank.setFluid(FluidStack.EMPTY);
		PetriDishRecording.clearRecordedEntity(this);
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
		return Math.max(1, (int) Math.ceil(recordedMaxHealth)) * PetriDishRecording.getFluidPerHealth();
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
		return PetriDishClientAnimation.getRenderedSlimeStage(this);
	}

	public boolean isGrowthAnimating() {
		return PetriDishClientAnimation.isGrowthAnimating(this);
	}

	public boolean isEmergenceAnimating() {
		return PetriDishClientAnimation.isEmergenceAnimating(this);
	}

	public float getEmergenceAnimationProgress(float partialTicks) {
		return PetriDishClientAnimation.getEmergenceAnimationProgress(this, partialTicks);
	}

	public float getEmergenceAnimationTick(float partialTicks) {
		return PetriDishClientAnimation.getEmergenceAnimationTick(this, partialTicks);
	}

	@Nullable
	public LivingEntity getClientPreviewEntity() {
		return PetriDishClientAnimation.getClientPreviewEntity(this);
	}

	public int getGrowthAnimationFromStage() {
		return PetriDishClientAnimation.getGrowthAnimationFromStage(this);
	}

	public int getGrowthAnimationToStage() {
		return PetriDishClientAnimation.getGrowthAnimationToStage(this);
	}

	public float getGrowthAnimationProgress(float partialTicks) {
		return PetriDishClientAnimation.getGrowthAnimationProgress(this, partialTicks);
	}

	public boolean canAcceptFluidNow() {
		if (!hasBionicMechanism())
			return false;
		if (level != null && !level.isClientSide)
			PetriDishRecording.refreshTargetMode(this);
		if (recordedEntityId == null) {
			PetriDishRecording.updateRecordedEntityFromNearby(this);
			return recordedEntityId != null;
		}
		return !PetriDishRecording.requiresNearbyMatchingEntity() || PetriDishRecording.hasMatchingEntityNearby(this);
	}

	boolean hasBionicMechanism() {
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
		if (!stored.isEmpty() && !FluidStack.isSameFluidSameComponents(stored, stack))
			return false;
		return stored.getAmount() < required;
	}

	boolean isRecordableEntity(Mob entity) {
		return PetriDishRecording.isRecordableEntity(this, entity);
	}

	boolean isRecordablePlayer(Player player) {
		return PetriDishRecording.isRecordablePlayer(this, player);
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
		if (recordedPlayerProfile != null)
			tag.put(RECORDED_PLAYER_PROFILE_TAG, PlayerMimicEntity.encodePlayerProfile(recordedPlayerProfile));
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
		recordedPlayerProfile = tag.contains(RECORDED_PLAYER_PROFILE_TAG, Tag.TAG_COMPOUND)
			? PlayerMimicEntity.decodePlayerProfile(tag.getCompound(RECORDED_PLAYER_PROFILE_TAG)) : null;
		boolean recordedPlayerType = Objects.equals(recordedEntityId, CBEntityTypes.PLAYER_MIMIC.getId());
		boolean invalidRecordedTarget = recordedPlayerType != (recordedPlayerProfile != null);
		recordedMaxHealth = tag.getFloat(RECORDED_MAX_HEALTH_TAG);
		recordedWidth = tag.getFloat(RECORDED_WIDTH_TAG);
		recordedHeight = tag.getFloat(RECORDED_HEIGHT_TAG);
		scanCooldown = tag.getInt(SCAN_COOLDOWN_TAG);
		emergenceInProgress = tag.getBoolean(EMERGENCE_IN_PROGRESS_TAG);
		emergenceTicksRemaining = tag.getInt(EMERGENCE_TICKS_REMAINING_TAG);
		if (invalidRecordedTarget) {
			recordedEntityId = null;
			recordedMimicProfile = null;
			recordedPlayerProfile = null;
			recordedMaxHealth = 0;
			recordedWidth = 0;
			recordedHeight = 0;
			emergenceInProgress = false;
			emergenceTicksRemaining = 0;
		}
		if (!emergenceInProgress)
			PetriDishClientAnimation.stopClientEmergenceAnimation(this);
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
			Component recordedName = recordedPlayerProfile != null
				? Component.literal(recordedPlayerProfile.getName())
				: Component.translatable(entityTranslationKey(recordedEntityId));
			tooltip.add(Component.translatable("create_biotech.petri_dish.goggles.recorded",
				recordedName)
				.withStyle(ChatFormatting.GRAY));
			tooltip.add(Component.translatable("create_biotech.petri_dish.goggles.required_fluid", getRequiredFluidAmount())
				.withStyle(ChatFormatting.AQUA));
		} else {
			String unrecordedKey = PetriDishRecording.isPlayerTargetMode(this)
				? "create_biotech.petri_dish.goggles.unrecorded_player"
				: "create_biotech.petri_dish.goggles.unrecorded";
			tooltip.add(Component.translatable(unrecordedKey)
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
