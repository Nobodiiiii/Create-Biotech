package com.nobodiiiii.createbiotech.content.shulkerpackager;

import net.minecraft.core.HolderLookup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.compat.computercraft.events.PackageEvent;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
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
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

public class ShulkerPackagerBlockEntity extends PackagerBlockEntity {

	/** Resource bound for both persisted links and the placement protocol. */
	public static final int MAX_OUTPUTS = 256;
	private static final int PLACEMENT_REQUEST_LIFETIME = 20 * 10;

	List<ShulkerPackagerTarget> outputs;
	ListTag interactionPointTag;
	boolean updateInteractionPoints;
	int heldBoxIdleTicks;
	public final ShulkerPackagerItemHandler shulkerInventory;

	protected ScrollOptionBehaviour<ArmBlockEntity.SelectionMode> selectionMode;
	protected int lastOutputIndex;
	private long interactionPointRevision;
	@Nullable
	private UUID pendingConfigurationPlayer;
	@Nullable
	private UUID pendingConfigurationNonce;
	private long pendingConfigurationExpiry;

	public ShulkerPackagerBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.SHULKER_PACKAGER.get(), pos, state);
		shulkerInventory = new ShulkerPackagerItemHandler(this);
		outputs = new ArrayList<>();
		interactionPointTag = new ListTag();
		updateInteractionPoints = true;
		heldBoxIdleTicks = 0;
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
		if (pendingConfigurationNonce != null && level.getGameTime() > pendingConfigurationExpiry)
			clearPlacementConfiguration();

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
		return canInsertPackageFromAutomation(this, stack);
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
					if (requestQueue && !ItemStack.isSameItemSameComponents(extracted, nextRequest.item()))
						continue;

					boolean bulky = !extracted.canFitInsideContainerItems();
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

	public List<ShulkerPackagerTarget> getOutputs() {
		initInteractionPoints();
		return outputs;
	}

	public void setInteractionPointTag(ListTag interactionPointTag) {
		ListTag limitedPoints = limitInteractionPoints(interactionPointTag);
		if (!limitedPoints.equals(this.interactionPointTag))
			interactionPointRevision++;
		this.interactionPointTag = limitedPoints;
		updateInteractionPoints = true;
		lastOutputIndex = -1;
		notifyUpdate();
		setChanged();
	}

	public long getInteractionPointRevision() {
		return interactionPointRevision;
	}

	/**
	 * Starts the one-shot server authorization used by the placement configuration packet.
	 * The token is intentionally transient: saving or moving the block must not authorize a later
	 * packet against a different block entity at the same raw position.
	 */
	public UUID beginPlacementConfiguration(ServerPlayer player) {
		UUID nonce = UUID.randomUUID();
		pendingConfigurationPlayer = player.getUUID();
		pendingConfigurationNonce = nonce;
		pendingConfigurationExpiry = level == null ? 0 : level.getGameTime() + PLACEMENT_REQUEST_LIFETIME;
		return nonce;
	}

	public boolean consumePlacementConfiguration(ServerPlayer player, UUID nonce) {
		if (level != null && level.getGameTime() > pendingConfigurationExpiry)
			clearPlacementConfiguration();
		boolean valid = level != null
			&& player.level() == level
			&& level.getGameTime() <= pendingConfigurationExpiry
			&& player.getUUID().equals(pendingConfigurationPlayer)
			&& nonce.equals(pendingConfigurationNonce);
		if (valid)
			clearPlacementConfiguration();
		return valid;
	}

	private void clearPlacementConfiguration() {
		pendingConfigurationPlayer = null;
		pendingConfigurationNonce = null;
		pendingConfigurationExpiry = 0;
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
		ItemStack extracted = shulkerInventory.extractItem(0, 1, true);
		return target != null && target != this && PackageItem.isPackage(extracted)
			&& heldBoxIdleTicks >= getTransferDelay() && canInsertPackageFromAutomation(target, extracted);
	}

	private boolean transferHeldBoxTo(PackagerBlockEntity target) {
		if (!canTransferHeldBoxTo(target))
			return false;

		ItemStack extracted = shulkerInventory.extractItem(0, 1, false);
		if (extracted.isEmpty())
			return false;
		ItemStack toInsert = singlePackageStack(extracted);
		PackagerItemHandler targetInventory = getPackageTransferHandler(target);
		ItemStack remainder = targetInventory.insertItem(0, toInsert, false);
		if (!remainder.isEmpty()) {
			shulkerInventory.setStackInSlot(0, extracted);
			return false;
		}

		playTransferEffects(target);
		CBAdvancements.awardNearby(level, getTransferEffectPos(this), 16, CBAdvancements.SHULKER_PACKAGER);
		CBAdvancements.awardNearby(level, getTransferEffectPos(target), 16, CBAdvancements.SHULKER_PACKAGER);
		heldBoxIdleTicks = 0;
		setChanged();
		return true;
	}

	private static boolean canInsertPackageFromAutomation(PackagerBlockEntity target, ItemStack stack) {
		if (target == null || stack.isEmpty() || !PackageItem.isPackage(stack))
			return false;
		ItemStack toInsert = singlePackageStack(stack);
		ItemStack remainder = getPackageTransferHandler(target).insertItem(0, toInsert, true);
		return remainder.getCount() < toInsert.getCount();
	}

	private static PackagerItemHandler getPackageTransferHandler(PackagerBlockEntity packager) {
		return packager instanceof ShulkerPackagerBlockEntity shulkerPackager
			? shulkerPackager.shulkerInventory
			: packager.inventory;
	}

	private static ItemStack singlePackageStack(ItemStack stack) {
		ItemStack copy = stack.copy();
		copy.setCount(1);
		return copy;
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
		Level level = packager.getLevel();
		BlockPos pos = packager.getBlockPos();
		Direction facing = packager.getBlockState()
			.getOptionalValue(ShulkerPackagerBlock.FACING)
			.orElse(Direction.UP)
			.getOpposite();
		Vec3 localEffectPos = Vec3.atCenterOf(pos)
			.add(Vec3.atLowerCornerOf(facing.getNormal())
				.scale(.65d));
		return level == null ? localEffectPos : SubLevelCompat.toWorld(level, localEffectPos);
	}

	private PackagerBlockEntity getConnectedPackagerTarget(ShulkerPackagerTarget point) {
		if (level == null || point == null)
			return null;
		if (!ShulkerPackagerArmInteractions.isValidConnection(level, worldPosition,
			SubLevelCompat.getSpaceId(level, worldPosition),
			point.getPos(), point.targetSubLevelId()))
			return null;
		BlockEntity blockEntity = level.getBlockEntity(point.getPos());
		return blockEntity instanceof PackagerBlockEntity packager ? packager : null;
	}

	private void initInteractionPoints() {
		if (!updateInteractionPoints || interactionPointTag == null || level == null)
			return;
		// A client can receive the block entity before it starts tracking the containing
		// sublevel. Wait rather than mistaking a legacy relative target for the outer world.
		if (!SubLevelCompat.isValidSpacePosition(level, worldPosition))
			return;
		List<ShulkerPackagerTarget> resolvedOutputs = new ArrayList<>();
		Set<ShulkerPackagerTarget.Address> seenTargets = new HashSet<>();

		for (Tag tag : interactionPointTag) {
			if (resolvedOutputs.size() >= MAX_OUTPUTS)
				break;
			if (!(tag instanceof CompoundTag pointTag))
				continue;
			ShulkerPackagerTarget point = ShulkerPackagerTarget.fromTag(pointTag, level, worldPosition);
			if (point == null)
				continue;
			if (!seenTargets.add(point.address()))
				continue;
			resolvedOutputs.add(point);
		}

		if (!sameOutputAddresses(outputs, resolvedOutputs))
			interactionPointRevision++;
		outputs.clear();
		outputs.addAll(resolvedOutputs);
		updateInteractionPoints = false;
	}

	private static boolean sameOutputAddresses(List<ShulkerPackagerTarget> first,
		List<ShulkerPackagerTarget> second) {
		if (first.size() != second.size())
			return false;
		for (int i = 0; i < first.size(); i++)
			if (!first.get(i).address().equals(second.get(i).address()))
				return false;
		return true;
	}

	private static int getTransferDelay() {
		return CBConfigs.SERVER.shulkerPackager.transferDelay.get();
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

	private void writeInteractionPoints(CompoundTag compound) {
		if (updateInteractionPoints && interactionPointTag != null) {
			compound.put("InteractionPoints", interactionPointTag);
			return;
		}

		ListTag pointsNBT = new ListTag();
		outputs.stream()
			.map(target -> target.serialize(worldPosition))
			.forEach(pointsNBT::add);
		compound.put("InteractionPoints", pointsNBT);
	}

	private static ListTag limitInteractionPoints(ListTag points) {
		if (points.size() <= MAX_OUTPUTS)
			return points;
		ListTag limited = new ListTag();
		for (int i = 0; i < MAX_OUTPUTS; i++)
			limited.add(points.get(i));
		return limited;
	}

	@Override
	public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(compound, registries, clientPacket);
		writeInteractionPoints(compound);
	}

	@Override
	public void writeSafe(CompoundTag compound, HolderLookup.Provider registries) {
		super.writeSafe(compound, registries);
		writeInteractionPoints(compound);
	}

	@Override
	protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(compound, registries, clientPacket);
		ListTag limitedPoints = limitInteractionPoints(compound.getList("InteractionPoints", Tag.TAG_COMPOUND));
		if (!limitedPoints.equals(interactionPointTag))
			interactionPointRevision++;
		interactionPointTag = limitedPoints;
		updateInteractionPoints = true;
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		for (ShulkerPackagerTarget output : outputs)
			output.setLevel(level);
	}

	public static boolean isSelectableFace(LevelAccessor level, BlockPos pos, BlockState state) {
		return true;
	}
}
