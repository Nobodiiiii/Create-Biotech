package com.nobodiiiii.createbiotech.content.allay.block.allayport;

import net.minecraft.core.HolderLookup;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.nobodiiiii.createbiotech.content.allay.logistics.courier.AllayCourierReturnMode;
import com.nobodiiiii.createbiotech.content.allay.logistics.courier.AllayCourierTask;
import com.nobodiiiii.createbiotech.content.allay.logistics.address.AllayAddressRules;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class AllayPortBlockEntity extends PackagePortBlockEntity {
	private final AllayPortInventory portInventory;
	private final AllayPortDispatchAccess dispatchAccess;
	private final AllayPortAutomation automation;
	private final AllayPortReturnQueue returnQueue;
	private final LerpedFloat flap;
	private final Set<UUID> wavingCouriers = new HashSet<>();
	private boolean courierWaving;
	private AllayCourierReturnMode returnMode = AllayCourierReturnMode.DEFAULT_FOR_PORT;

	public AllayPortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		itemHandler = new AllayPortAutomationInventoryWrapper(inventory, this);
		portInventory = new AllayPortInventory(this);
		dispatchAccess = new AllayPortDispatchAccess(this, portInventory);
		automation = new AllayPortAutomation(this, portInventory);
		returnQueue = new AllayPortReturnQueue(this, portInventory);
		flap = createChasingFlap();
	}

	public AllayPortBlockEntity(BlockPos pos, BlockState state) {
		this(CBBlockEntityTypes.ALLAY_PORT.get(), pos, state);
	}

	@Override
	public void tick() {
		super.tick();
		flap.tickChaser();
		if (level != null && level.isClientSide()) {
			return;
		}
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		returnQueue.tick();
		dispatchAccess.tryDispatch();
		if (serverLevel.getGameTime() % 20 == 0) {
			AllayPortTargetRegistry.update(serverLevel, worldPosition, addressFilter);
		}
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		if (level == null || level.isClientSide()) {
			return;
		}
		automation.tick();
		dispatchAccess.tryDispatch();
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
	}

	public @Nullable IItemHandler getItemHandler(@Nullable Direction side) {
		return dispatchAccess.getItemHandler(side);
	}

	@Nullable IItemHandler getAutomationItemHandler() {
		return itemHandler;
	}

	public Direction getLaunchSide() {
		return getBlockState().getValue(AllayPortBlock.FACING);
	}

	Vec3 getCourierSpawnPosition() {
		Vec3 localPosition = Vec3.atCenterOf(worldPosition).add(0, -0.25, 0);
		return level == null ? localPosition : SubLevelCompat.toWorld(level, worldPosition, localPosition);
	}

	Vec3 getCourierLaunchDirection() {
		Vec3 localDirection = Vec3.atLowerCornerOf(getLaunchSide().getNormal());
		if (level == null) {
			return localDirection;
		}
		Vec3 worldDirection = SubLevelCompat.localNormalToWorld(level, worldPosition, localDirection);
		return worldDirection.lengthSqr() < 1.0E-8 ? localDirection : worldDirection.normalize();
	}

	AllayCourierTask prepareCourierDeparture(AllayCourierTask task) {
		return task.departFromAllayPort(this);
	}

	public boolean tryPullFromBelow() {
		if (level == null || level.isClientSide()) {
			return false;
		}
		boolean pulled = automation.tryPullingFromBelow();
		if (pulled) {
			dispatchAccess.tryDispatch();
		}
		return pulled;
	}

	public boolean tryDispatch() {
		if (level == null || level.isClientSide()) {
			return false;
		}
		return dispatchAccess.tryDispatch();
	}

	public ItemStackHandler getCarrierInventory() {
		return portInventory.carrierInventory();
	}

	void markPortContentsChanged() {
		setChanged();
		if (level != null) {
			level.blockEntityChanged(worldPosition);
		}
	}

	public boolean canReceiveCourier(ItemStack box) {
		return portInventory.canReceiveCourier(box);
	}

	public boolean receiveCourier(ItemStack box) {
		return portInventory.receiveCourier(box);
	}

	public boolean canReceivePackage(ItemStack box) {
		return portInventory.canReceivePackage(box);
	}

	public boolean receivePackage(ItemStack box) {
		return portInventory.receivePackage(box);
	}

	public boolean canReceiveCarrier() {
		return portInventory.canReceiveCarrier();
	}

	public boolean receiveCarrier() {
		return portInventory.receiveCarrier();
	}

	public boolean receivePackageAndScheduleCarrierReturnToPlayer(ItemStack box, UUID playerId, int delayTicks) {
		return returnQueue.receivePackageAndScheduleCarrierReturnToPlayer(box, playerId, delayTicks);
	}

	public boolean receivePackageAndScheduleCarrierReturnToPlayer(ItemStack box, UUID playerId) {
		return returnQueue.receivePackageAndScheduleCarrierReturnToPlayer(
			box, playerId, AllayPortReturnQueue.returnLaunchDelayTicks());
	}

	public boolean tryQueueReturnCarrier(@Nullable ResourceKey<net.minecraft.world.level.Level> returnDimension,
										 @Nullable BlockPos returnPos) {
		return returnQueue.tryQueueReturnCarrier(returnDimension, returnPos);
	}

	public AllayCourierReturnMode getReturnMode() {
		return returnMode;
	}

	public void setReturnMode(@Nullable AllayCourierReturnMode returnMode) {
		this.returnMode = returnMode == null ? AllayCourierReturnMode.DEFAULT_FOR_PORT : returnMode;
	}

	public enum CourierReceiveResult {
		REJECTED,
		CARRIER_STORED,
		RETURN_QUEUED,
		CARRIER_DROPPED
	}

	public CourierReceiveResult receivePackageAndHandleCarrier(ItemStack box,
		@Nullable ResourceKey<net.minecraft.world.level.Level> returnDimension, @Nullable BlockPos returnPos) {
		return returnQueue.receivePackageAndHandleCarrier(box, returnDimension, returnPos);
	}

	@Override
	protected void onOpenChange(boolean open) {
		if (level == null) {
			return;
		}
		level.playSound(null, worldPosition, open ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE,
			SoundSource.BLOCKS, 0.5f, level.random.nextFloat() * 0.1f + 0.9f);
	}

	public float getFlap(float partialTicks) {
		return flap.getValue(partialTicks);
	}

	public void flap(boolean inward) {
		if (level == null) {
			return;
		}
		if (!level.isClientSide()) {
			if (level instanceof net.minecraft.server.level.ServerLevel serverLevel)
				CBPackets.sendToTrackingChunk(new AllayPortFlapPacket(this, inward), serverLevel, worldPosition);
		} else {
			flap.setValue(inward ? -1 : 1);
			AllSoundEvents.FUNNEL_FLAP.playAt(level, worldPosition, 1, 1, true);
		}
	}

	private static LerpedFloat createChasingFlap() {
		return LerpedFloat.linear()
			.startWithValue(.25f)
			.chase(0, .05f, Chaser.EXP);
	}

	public void setCourierWaving(UUID courierId, boolean waving) {
		boolean wasWaving = courierWaving;
		boolean changed = waving ? wavingCouriers.add(courierId) : wavingCouriers.remove(courierId);
		if (!changed) {
			return;
		}
		courierWaving = !wavingCouriers.isEmpty();
		if (courierWaving != wasWaving) {
			sendData();
		}
	}

	public boolean isCourierWaving() {
		return courierWaving;
	}

	@Override
	public ItemInteractionResult use(Player player) {
		if (!canPlayerUse(player)) {
			return ItemInteractionResult.FAIL;
		}
		return super.use(player);
	}

	@Override
	public boolean canPlayerUse(Player player) {
		if (level == null || !SubLevelCompat.isValidSpacePosition(level, worldPosition)
			|| level.getBlockEntity(worldPosition) != this
			|| !SubLevelCompat.canEntityInteractWith(level, worldPosition, player)) {
			return false;
		}
		Vec3 worldCenter = SubLevelCompat.toWorld(level, worldPosition,
			Vec3.atCenterOf(worldPosition));
		return player.position().distanceToSqr(worldCenter) <= 64.0;
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return AllayPortMenu.create(containerId, playerInventory, this);
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		if (clientPacket) {
			tag.putBoolean("CourierWaving", courierWaving);
		}
		tag.putString("ReturnMode", returnMode.serializedName());
		portInventory.write(tag, registries);
		if (!clientPacket) {
			returnQueue.write(tag);
		}
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		addressFilter = AllayAddressRules.normalizePortAddress(addressFilter);
		if (clientPacket) {
			courierWaving = tag.getBoolean("CourierWaving");
		}
		returnMode = tag.contains("ReturnMode")
			? AllayCourierReturnMode.byName(tag.getString("ReturnMode"))
			: AllayCourierReturnMode.DEFAULT_FOR_PORT;
		portInventory.read(tag, registries);
		if (!clientPacket) {
			returnQueue.read(tag);
		}
	}

	@Override
	public void clearContent() {
		getCarrierInventory().setStackInSlot(0, ItemStack.EMPTY);
		super.clearContent();
	}

	@Override
	public void destroy() {
		portInventory.dropAllCarriers();
		super.destroy();
	}

	@Override
	public AABB getRenderBoundingBox() {
		return super.getRenderBoundingBox().expandTowards(0, 1, 0);
	}
}
