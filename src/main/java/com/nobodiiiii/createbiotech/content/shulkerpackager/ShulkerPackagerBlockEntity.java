package com.nobodiiiii.createbiotech.content.shulkerpackager;

import java.util.ArrayList;
import java.util.List;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.compat.computercraft.events.PackageEvent;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerItemHandler;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

public class ShulkerPackagerBlockEntity extends PackagerBlockEntity {

	List<ArmInteractionPoint> outputs;
	ListTag interactionPointTag;
	boolean updateInteractionPoints;
	int heldBoxIdleTicks;
	ItemStack pendingTransferredPackage;
	boolean transferOutAnimation;

	protected ScrollOptionBehaviour<ArmBlockEntity.SelectionMode> selectionMode;
	protected int lastOutputIndex;

	public ShulkerPackagerBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.SHULKER_PACKAGER.get(), pos, state);
		inventory = new ShulkerPackagerItemHandler(this);
		outputs = new ArrayList<>();
		interactionPointTag = new ListTag();
		updateInteractionPoints = true;
		heldBoxIdleTicks = 0;
		pendingTransferredPackage = ItemStack.EMPTY;
		transferOutAnimation = false;
		lastOutputIndex = -1;
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		selectionMode = new ScrollOptionBehaviour<>(ArmBlockEntity.SelectionMode.class,
			CreateLang.translateDirect("logistics.when_multiple_outputs_available"), this,
			new CenteredSideValueBoxTransform((state, side) -> !side.getAxis()
				.isVertical()));
		behaviours.add(selectionMode);
	}

	@Override
	public void tick() {
		initInteractionPoints();
		super.tick();

		if (level == null || level.isClientSide)
			return;

		if (transferOutAnimation) {
			heldBoxIdleTicks = 0;
			if (animationTicks <= CYCLE / 2) {
				transferOutAnimation = false;
				previouslyUnwrapped = ItemStack.EMPTY;
				animationTicks = 0;
				notifyUpdate();
				setChanged();
			}
			return;
		}

		if (!pendingTransferredPackage.isEmpty()) {
			heldBoxIdleTicks = 0;
			if (animationTicks <= CYCLE / 2)
				finishIncomingTransfer();
			return;
		}

		if (animationTicks != 0) {
			heldBoxIdleTicks = 0;
			return;
		}

		if (!heldBox.isEmpty()) {
			heldBoxIdleTicks++;
			if (heldBoxIdleTicks >= getTransferDelay())
				attemptTransferToOutput();
			return;
		}

		heldBoxIdleTicks = 0;
	}

	public boolean canAcceptTransferredPackage(ItemStack stack) {
		return canReceiveTransferredPackage(this, stack);
	}

	public boolean canExportHeldBoxTo(ShulkerPackagerBlockEntity target) {
		return canExportHeldBoxTo((PackagerBlockEntity) target);
	}

	public boolean canExportHeldBoxTo(PackagerBlockEntity target) {
		return canTransferHeldBoxTo(target);
	}

	@Override
	public void attemptToSend(List<PackagingRequest> queuedRequests) {
		if (queuedRequests == null && (!heldBox.isEmpty() || animationTicks != 0 || buttonCooldown > 0))
			return;

		IItemHandler targetInv = targetInventory.getInventory();
		if (targetInv == null || targetInv instanceof PackagerItemHandler)
			return;

		boolean anyItemPresent = false;
		ItemStackHandler extractedItems = new ItemStackHandler(PackageItem.SLOTS);
		ItemStack extractedPackageItem = ItemStack.EMPTY;
		PackagingRequest nextRequest = null;
		String fixedAddress = null;
		int fixedOrderId = 0;

		int linkIndexInOrder = 0;
		boolean finalLinkInOrder = false;
		int packageIndexAtLink = 0;
		boolean finalPackageAtLink = false;
		PackageOrderWithCrafts orderContext = null;
		boolean requestQueue = queuedRequests != null;

		if (requestQueue && !queuedRequests.isEmpty()) {
			nextRequest = queuedRequests.get(0);
			fixedAddress = nextRequest.address();
			fixedOrderId = nextRequest.orderId();
			linkIndexInOrder = nextRequest.linkIndex();
			finalLinkInOrder = nextRequest.finalLink()
				.booleanValue();
			packageIndexAtLink = nextRequest.packageCounter()
				.getAndIncrement();
			orderContext = nextRequest.context();
		}

		Outer:
		for (int i = 0; i < PackageItem.SLOTS; i++) {
			boolean continuePacking = true;

			while (continuePacking) {
				continuePacking = false;

				for (int slot = 0; slot < targetInv.getSlots(); slot++) {
					int initialCount = requestQueue ? Math.min(64, nextRequest.getCount()) : 64;
					ItemStack extracted = targetInv.extractItem(slot, initialCount, true);
					if (extracted.isEmpty())
						continue;
					if (requestQueue && !ItemHandlerHelper.canItemStacksStack(extracted, nextRequest.item()))
						continue;

					boolean bulky = !extracted.getItem()
						.canFitInsideContainerItems();
					if (bulky && anyItemPresent)
						continue;

					anyItemPresent = true;
					int leftovers = ItemHandlerHelper.insertItemStacked(extractedItems, extracted.copy(), false)
						.getCount();
					int transferred = extracted.getCount() - leftovers;
					targetInv.extractItem(slot, transferred, false);

					if (extracted.getItem() instanceof PackageItem)
						extractedPackageItem = extracted;

					if (!requestQueue) {
						if (bulky)
							break Outer;
						continue;
					}

					nextRequest.subtract(transferred);

					if (!nextRequest.isEmpty()) {
						if (bulky)
							break Outer;
						continue;
					}

					finalPackageAtLink = true;
					queuedRequests.remove(0);
					if (queuedRequests.isEmpty())
						break Outer;
					int previousCount = nextRequest.packageCounter()
						.intValue();
					nextRequest = queuedRequests.get(0);
					if (!fixedAddress.equals(nextRequest.address()))
						break Outer;
					if (fixedOrderId != nextRequest.orderId())
						break Outer;

					nextRequest.packageCounter()
						.setValue(previousCount);
					finalPackageAtLink = false;
					continuePacking = true;
					if (nextRequest.context() != null)
						orderContext = nextRequest.context();

					if (bulky)
						break Outer;
					break;
				}
			}
		}

		if (!anyItemPresent) {
			if (nextRequest != null)
				queuedRequests.remove(0);
			return;
		}

		ItemStack createdBox = extractedPackageItem.isEmpty() ? ShulkerPackageItem.containing(extractedItems)
			: extractedPackageItem.copy();
		computerBehaviour.prepareComputerEvent(new PackageEvent(createdBox, "package_created"));
		PackageItem.clearAddress(createdBox);

		if (fixedAddress != null)
			PackageItem.addAddress(createdBox, fixedAddress);
		if (requestQueue)
			PackageItem.setOrder(createdBox, fixedOrderId, linkIndexInOrder, finalLinkInOrder, packageIndexAtLink,
				finalPackageAtLink, orderContext);
		if (!requestQueue && !signBasedAddress.isBlank())
			PackageItem.addAddress(createdBox, signBasedAddress);

		BlockPos linkPos = getLinkPos();
		if (extractedPackageItem.isEmpty() && linkPos != null
			&& level.getBlockEntity(linkPos) instanceof PackagerLinkBlockEntity plbe)
			plbe.behaviour.deductFromAccurateSummary(extractedItems);

		if (!heldBox.isEmpty() || animationTicks != 0) {
			queuedExitingPackages.add(new BigItemStack(createdBox, 1));
			return;
		}

		heldBox = createdBox;
		animationInward = false;
		animationTicks = CYCLE;

		award(AllAdvancements.PACKAGER);
		triggerStockCheck();
		notifyUpdate();
	}

	public List<ArmInteractionPoint> getOutputs() {
		initInteractionPoints();
		return outputs;
	}

	public void setInteractionPointTag(ListTag interactionPointTag) {
		this.interactionPointTag = interactionPointTag;
		updateInteractionPoints = true;
		notifyUpdate();
		setChanged();
	}

	private void attemptTransferToOutput() {
		if (outputs.isEmpty() || heldBox.isEmpty())
			return;

		boolean foundOutput = false;
		int startIndex = selectionMode.get() == ArmBlockEntity.SelectionMode.PREFER_FIRST ? 0 : lastOutputIndex + 1;
		int scanRange = selectionMode.get() == ArmBlockEntity.SelectionMode.FORCED_ROUND_ROBIN ? lastOutputIndex + 2
			: outputs.size();
		if (scanRange > outputs.size())
			scanRange = outputs.size();

		for (int i = startIndex; i < scanRange; i++) {
			PackagerBlockEntity target = getConnectedPackagerTarget(outputs.get(i));
			if (target == null || target == this || !canTransferHeldBoxTo(target))
				continue;
			if (!transferHeldBoxTo(target))
				continue;
			lastOutputIndex = i;
			foundOutput = true;
			break;
		}

		if (!foundOutput && selectionMode.get() == ArmBlockEntity.SelectionMode.ROUND_ROBIN)
			lastOutputIndex = -1;
		if (lastOutputIndex == outputs.size() - 1)
			lastOutputIndex = -1;
	}

	private boolean canTransferHeldBoxTo(PackagerBlockEntity target) {
		return target != null && target != this && !heldBox.isEmpty() && PackageItem.isPackage(heldBox)
			&& heldBoxIdleTicks >= getTransferDelay() && animationTicks == 0 && queuedExitingPackages.isEmpty()
			&& canReceiveTransferredPackage(target, heldBox);
	}

	private boolean transferHeldBoxTo(PackagerBlockEntity target) {
		if (!canTransferHeldBoxTo(target))
			return false;
		ItemStack box = heldBox.copy();
		if (!receiveTransferredPackage(target, box))
			return false;
		playTransferEffects(target);
		heldBox = ItemStack.EMPTY;
		previouslyUnwrapped = box.copy();
		animationInward = false;
		animationTicks = CYCLE;
		transferOutAnimation = true;
		heldBoxIdleTicks = 0;
		notifyUpdate();
		setChanged();
		return true;
	}

	private void playTransferEffects(PackagerBlockEntity target) {
		spawnTransferParticles(this);
		spawnTransferParticles(target);
		playTransferSound(this);
	}

	private static void spawnTransferParticles(PackagerBlockEntity packager) {
		Level level = packager.getLevel();
		if (!(level instanceof ServerLevel serverLevel))
			return;

		Vec3 effectPos = getTransferEffectPos(packager);
		serverLevel.sendParticles(ParticleTypes.PORTAL, effectPos.x, effectPos.y, effectPos.z, 32, .35d, .35d,
			.35d, .08d);
	}

	private static void playTransferSound(PackagerBlockEntity packager) {
		Level level = packager.getLevel();
		if (level == null)
			return;

		Vec3 effectPos = getTransferEffectPos(packager);
		level.playSound(null, effectPos.x, effectPos.y, effectPos.z, SoundEvents.ENDERMAN_TELEPORT,
			SoundSource.BLOCKS, .8f, 1.0f);
	}

	private static Vec3 getTransferEffectPos(PackagerBlockEntity packager) {
		BlockPos pos = packager.getBlockPos();
		Direction facing = packager.getBlockState()
			.getOptionalValue(ShulkerPackagerBlock.FACING)
			.orElse(Direction.UP)
			.getOpposite();
		return Vec3.atCenterOf(pos)
			.add(Vec3.atLowerCornerOf(facing.getNormal())
				.scale(.65d));
	}

	private PackagerBlockEntity getConnectedPackagerTarget(ArmInteractionPoint point) {
		if (level == null || point == null || !point.isValid())
			return null;
		BlockEntity blockEntity = level.getBlockEntity(point.getPos());
		return blockEntity instanceof PackagerBlockEntity packager ? packager : null;
	}

	private void initInteractionPoints() {
		if (!updateInteractionPoints || interactionPointTag == null || level == null)
			return;
		if (!isAreaActuallyLoaded(worldPosition, getConnectionRange() + 1))
			return;

		outputs.clear();

		for (Tag tag : interactionPointTag) {
			ArmInteractionPoint point = ArmInteractionPoint.deserialize((CompoundTag) tag, level, worldPosition);
			if (point == null)
				continue;
			if (point.getMode() == ArmInteractionPoint.Mode.TAKE)
				point.cycleMode();
			outputs.add(point);
		}

		updateInteractionPoints = false;
	}

	private static boolean canReceiveTransferredPackage(PackagerBlockEntity target, ItemStack box) {
		if (target == null || box.isEmpty() || !PackageItem.isPackage(box) || target.animationTicks != 0
			|| !target.heldBox.isEmpty() || !target.queuedExitingPackages.isEmpty())
			return false;
		if (target instanceof ShulkerPackagerBlockEntity shulkerTarget)
			return shulkerTarget.pendingTransferredPackage.isEmpty() && shulkerTarget.unpackTransferredPackage(box, true);
		return target.unwrapBox(box, true);
	}

	private static boolean receiveTransferredPackage(PackagerBlockEntity target, ItemStack box) {
		if (!canReceiveTransferredPackage(target, box))
			return false;
		if (target instanceof ShulkerPackagerBlockEntity shulkerTarget)
			return shulkerTarget.startIncomingTransfer(box);
		if (!target.unwrapBox(box.copy(), false))
			return false;
		target.triggerStockCheck();
		return true;
	}

	private boolean startIncomingTransfer(ItemStack box) {
		if (!canReceiveTransferredPackage(this, box))
			return false;
		pendingTransferredPackage = box.copy();
		previouslyUnwrapped = box.copy();
		animationInward = true;
		animationTicks = CYCLE;
		heldBoxIdleTicks = 0;
		notifyUpdate();
		setChanged();
		return true;
	}

	private void finishIncomingTransfer() {
		ItemStack box = pendingTransferredPackage.copy();
		pendingTransferredPackage = ItemStack.EMPTY;
		previouslyUnwrapped = ItemStack.EMPTY;
		animationInward = true;

		if (unpackTransferredPackage(box, false)) {
			triggerStockCheck();
		} else if (heldBox.isEmpty()) {
			heldBox = box.copy();
			animationInward = false;
			animationTicks = 0;
		}

		notifyUpdate();
		setChanged();
	}

	private boolean unpackTransferredPackage(ItemStack box, boolean simulate) {
		if (level == null)
			return false;

		ItemStackHandler contents = PackageItem.getContents(box);
		List<ItemStack> items = ItemHelper.getNonEmptyStacks(contents);
		if (items.isEmpty())
			return true;

		PackageOrderWithCrafts orderContext = PackageItem.getOrderContext(box);
		Direction facing = getBlockState().getOptionalValue(ShulkerPackagerBlock.FACING)
			.orElse(Direction.UP);
		BlockPos target = worldPosition.relative(facing.getOpposite());
		BlockState targetState = level.getBlockState(target);

		UnpackingHandler handler = UnpackingHandler.REGISTRY.get(targetState);
		UnpackingHandler toUse = handler != null ? handler : UnpackingHandler.DEFAULT;
		boolean unpacked = toUse.unpack(level, target, targetState, facing, items, orderContext, simulate);

		if (unpacked && !simulate)
			computerBehaviour.prepareComputerEvent(new PackageEvent(box, "package_received"));

		return unpacked;
	}

	private static int getTransferDelay() {
		return CBConfigs.SERVER.shulkerPackager.transferDelay.get();
	}

	private static int getConnectionRange() {
		return CBConfigs.SERVER.shulkerPackager.connectionRange.get();
	}

	private BlockPos getLinkPos() {
		for (Direction d : Iterate.directions) {
			BlockState adjacentState = level.getBlockState(worldPosition.relative(d));
			if (!AllBlocks.STOCK_LINK.has(adjacentState))
				continue;
			if (PackagerLinkBlock.getConnectedDirection(adjacentState) != d)
				continue;
			return worldPosition.relative(d);
		}
		return null;
	}

	private boolean isAreaActuallyLoaded(BlockPos center, int range) {
		if (!level.isAreaLoaded(center, range))
			return false;
		if (!level.isClientSide)
			return true;

		int minY = center.getY() - range;
		int maxY = center.getY() + range;
		if (maxY < level.getMinBuildHeight() || minY >= level.getMaxBuildHeight())
			return false;

		int minX = center.getX() - range;
		int minZ = center.getZ() - range;
		int maxX = center.getX() + range;
		int maxZ = center.getZ() + range;
		int minChunkX = SectionPos.blockToSectionCoord(minX);
		int maxChunkX = SectionPos.blockToSectionCoord(maxX);
		int minChunkZ = SectionPos.blockToSectionCoord(minZ);
		int maxChunkZ = SectionPos.blockToSectionCoord(maxZ);

		ChunkSource chunkSource = level.getChunkSource();
		for (int chunkX = minChunkX; chunkX <= maxChunkX; ++chunkX)
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; ++chunkZ)
				if (!chunkSource.hasChunk(chunkX, chunkZ))
					return false;

		return true;
	}

	private void writeInteractionPoints(CompoundTag compound) {
		if (updateInteractionPoints && interactionPointTag != null) {
			compound.put("InteractionPoints", interactionPointTag);
			return;
		}

		ListTag pointsNBT = new ListTag();
		outputs.stream()
			.map(aip -> aip.serialize(worldPosition))
			.forEach(pointsNBT::add);
		compound.put("InteractionPoints", pointsNBT);
	}

	@Override
	public void write(CompoundTag compound, boolean clientPacket) {
		super.write(compound, clientPacket);
		writeInteractionPoints(compound);
		compound.put("PendingTransferredPackage", pendingTransferredPackage.serializeNBT());
		compound.putBoolean("TransferOutAnimation", transferOutAnimation);
	}

	@Override
	public void writeSafe(CompoundTag compound) {
		super.writeSafe(compound);
		writeInteractionPoints(compound);
		compound.put("PendingTransferredPackage", pendingTransferredPackage.serializeNBT());
		compound.putBoolean("TransferOutAnimation", transferOutAnimation);
	}

	@Override
	protected void read(CompoundTag compound, boolean clientPacket) {
		super.read(compound, clientPacket);
		interactionPointTag = compound.getList("InteractionPoints", Tag.TAG_COMPOUND);
		pendingTransferredPackage = ItemStack.of(compound.getCompound("PendingTransferredPackage"));
		transferOutAnimation = compound.getBoolean("TransferOutAnimation");
		updateInteractionPoints = true;
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		for (ArmInteractionPoint output : outputs)
			output.setLevel(level);
	}

	public static boolean isSelectableFace(LevelAccessor level, BlockPos pos, BlockState state) {
		return true;
	}

	@Override
	public ItemStack getRenderedBox() {
		if (transferOutAnimation && animationTicks > 0)
			return animationTicks > CYCLE / 2 ? previouslyUnwrapped : ItemStack.EMPTY;
		return super.getRenderedBox();
	}
}
