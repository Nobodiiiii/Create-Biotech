package com.nobodiiiii.createbiotech.content.biopackager;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxItem;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.compat.computercraft.events.PackageEvent;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerItemHandler;
import com.simibubi.create.content.logistics.packager.PackagingRequest;

import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public class BioPackagerBlockEntity extends PackagerBlockEntity {

	private ItemStack captureOutput = ItemStack.EMPTY;
	private boolean releasePending;
	private boolean biologicalTray;
	private boolean chamberReserved;

	public BioPackagerBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.BIO_PACKAGER.get(), pos, state);
	}

	@Override
	public void tick() {
		boolean wasAnimating = animationTicks > 0;
		super.tick();
		if (!(level instanceof ServerLevel server) || animationTicks != 0)
			return;
		if (releasePending) {
			if (!animationInward)
				releaseCreature(server);
			returnReleasedBox();
			return;
		}
		if (wasAnimating)
			biologicalTray = false;
		else
			tryReleaseFromAttachedInventory();
	}

	@Override
	public void lazyTick() {
		if (level instanceof ServerLevel && animationTicks == 0) {
			if (hasCaptureOutput())
				depositCapture();
			else
				captureOutput = ItemStack.EMPTY;
		}
		super.lazyTick();
	}

	@Override
	protected void wakeTheFrogs() {
		if (!releasePending && !chamberReserved && !hasCaptureOutput())
			super.wakeTheFrogs();
	}

	@Nullable
	public IItemHandler getAttachedInventory() {
		IItemHandler handler = targetInventory.getInventory();
		return handler instanceof PackagerItemHandler ? null : handler;
	}

	public boolean canStartPacking() {
		return level instanceof ServerLevel && !isRemoved() && !releasePending && !chamberReserved
			&& animationTicks == 0 && heldBox.isEmpty() && queuedExitingPackages.isEmpty();
	}

	public boolean startPacking(ItemStack box) {
		if (!canStartPacking() || !isCreaturePackage(box))
			return false;
		captureOutput = box.copyWithCount(1);
		setTrayAnimation(captureOutput, ItemStack.EMPTY, false, 0);
		// The capture caller discards the source creature before the next inventory transfer.
		lazyTickCounter = 0;
		notifyUpdate();
		return true;
	}

	@Override
	public boolean unwrapBox(ItemStack box, boolean simulate) {
		if (!(level instanceof ServerLevel server) || !isCreaturePackage(box) || !canReleaseCreature())
			return false;
		if (!simulate) {
			prepareRelease(box);
			releaseCreature(server);
			returnReleasedBox();
		}
		return true;
	}

	private void prepareRelease(ItemStack box) {
		releasePending = true;
		heldBox = box.copyWithCount(1);
		computerBehaviour.prepareComputerEvent(new PackageEvent(heldBox.copy(), "package_received"));
	}

	@Override
	public InventorySummary getAvailableItems() {
		return InventorySummary.EMPTY;
	}

	@Override
	public void attemptToSend(@Nullable List<PackagingRequest> requests) {
		if (requests == null)
			tryReleaseFromAttachedInventory();
	}

	private boolean canReleaseCreature() {
		return canStartPacking() && !isReleaseBlocked();
	}

	private void tryReleaseFromAttachedInventory() {
		if (!getBlockState().getValue(BioPackagerBlock.POWERED) || !canReleaseCreature())
			return;
		IItemHandler handler = getAttachedInventory();
		if (handler == null)
			return;
		for (int slot = 0; slot < handler.getSlots(); slot++) {
			if (!isCreaturePackage(handler.getStackInSlot(slot)) || !isCreaturePackage(handler.extractItem(slot, 1, true)))
				continue;

			ItemStack box = handler.extractItem(slot, 1, false);
			if (box.isEmpty())
				continue;

			if (isCreaturePackage(box) && canReleaseCreature()) {
				prepareRelease(box);
				setTrayAnimation(heldBox, ItemStack.EMPTY, false, getCycleTicks());
				notifyUpdate();
			} else {
				ItemStack remainder = handler.insertItem(slot, box, false);
				dropAtPackager(ItemHandlerHelper.insertItemStacked(handler, remainder, false));
			}
			return;
		}
	}

	private boolean isReleaseBlocked() {
		BlockPos pos = worldPosition.above();
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	private void releaseCreature(ServerLevel server) {
		if (!isCreaturePackage(heldBox) || isReleaseBlocked())
			return;
		Entity entity = CapturedEntityBoxHelper.createCapturedEntity(heldBox, server);
		if (entity == null)
			return;
		BlockPos pos = worldPosition.above();
		entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		if (entity instanceof Mob mob)
			mob.setNoAi(false);
		CapturedEntityBoxHelper.unmarkAiDisabledByMod(entity);
		if (!server.addFreshEntity(entity))
			return;

		heldBox = CapturedEntityBoxHelper.createEmptyBox(heldBox).copyWithCount(1);
		CBPackets.sendToTrackingChunk(new BioPackagerReleaseAnimationPacket(entity.getId()), server, pos);
	}

	private void returnReleasedBox() {
		ItemStack box = heldBox;
		ItemStack leftover = insertIntoAttachedInventory(box);
		releasePending = false;
		if (!box.isEmpty() && leftover.isEmpty())
			animateInventoryDeposit(box);
		else
			setTrayAnimation(ItemStack.EMPTY, ItemStack.EMPTY, true, 0);
		dropAtPackager(leftover);
		notifyUpdate();
	}

	private boolean hasCaptureOutput() {
		// The inherited inventory can take the package when the tray is idle.
		return !heldBox.isEmpty() && heldBox == captureOutput;
	}

	private void depositCapture() {
		if (!insertIntoAttachedInventory(heldBox).isEmpty()) {
			super.wakeTheFrogs();
			return;
		}
		animateInventoryDeposit(heldBox);
		captureOutput = ItemStack.EMPTY;
		notifyUpdate();
	}

	private void animateInventoryDeposit(ItemStack box) {
		setTrayAnimation(ItemStack.EMPTY, box.copy(), true, getCycleTicks());
	}

	private ItemStack insertIntoAttachedInventory(ItemStack stack) {
		if (stack.isEmpty())
			return ItemStack.EMPTY;
		IItemHandler handler = getAttachedInventory();
		return handler == null ? stack : ItemHandlerHelper.insertItemStacked(handler, stack, false);
	}

	private void dropAtPackager(ItemStack stack) {
		if (!stack.isEmpty())
			Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
				worldPosition.getZ() + 0.5, stack);
	}

	public static boolean isCreaturePackage(ItemStack stack) {
		return stack.getItem() instanceof CapturedEntityBoxItem && CapturedEntityBoxHelper.hasCapturedEntity(stack);
	}

	public void reserveForChamber() {
		chamberReserved = true;
		notifyUpdate();
	}

	public void releaseChamberReservation() {
		if (!chamberReserved)
			return;
		chamberReserved = false;
		notifyUpdate();
	}

	public void setTrayAnimation(ItemStack box, ItemStack insertedBox, boolean inward, int remainingTicks) {
		heldBox = box;
		previouslyUnwrapped = insertedBox;
		animationInward = inward;
		animationTicks = remainingTicks;
		biologicalTray = remainingTicks > 0;
	}

	@Override
	public void clearContent() {
		super.clearContent();
		captureOutput = ItemStack.EMPTY;
		releasePending = false;
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		boolean legacyRelease = tag.getBoolean("ChainReturnAnimation");
		releasePending = tag.getBoolean("ReleasePending") || legacyRelease;
		chamberReserved = tag.getBoolean("ChamberAnimation");
		biologicalTray = tag.contains("BiologicalTray") ? tag.getBoolean("BiologicalTray") : animationTicks > 0;
		if (chamberReserved) {
			setTrayAnimation(ItemStack.EMPTY, ItemStack.EMPTY, true, 0);
		} else if (legacyRelease) {
			// Finish the saved release or return without replaying its unverified inventory movement.
			setTrayAnimation(heldBox, ItemStack.EMPTY, animationInward, 0);
		} else if (tag.getBoolean("PackedBoxPending")) {
			setTrayAnimation(heldBox, ItemStack.EMPTY, false, 0);
		}
		animationTicks = Mth.clamp(animationTicks, 0, getAnimationCycle());
		captureOutput = tag.getBoolean("PackedBoxPending") ? heldBox : ItemStack.EMPTY;
		if (!clientPacket && tag.contains("QueuedCreatureReleases", Tag.TAG_LIST)) {
			// Boxes already withdrawn by old orders can be collected at the tray.
			for (ItemStack box : NBTHelper.readItemList(tag.getList("QueuedCreatureReleases", Tag.TAG_COMPOUND), registries))
				if (!box.isEmpty())
					queuedExitingPackages.add(new BigItemStack(box.copyWithCount(1), box.getCount()));
		}
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.putBoolean("ReleasePending", releasePending);
		tag.putBoolean("ChamberAnimation", chamberReserved);
		tag.putBoolean("BiologicalTray", biologicalTray);
		tag.putBoolean("PackedBoxPending", hasCaptureOutput());
		tag.remove("LastSummary");
	}

	public int getAnimationCycle() {
		return biologicalTray ? getCycleTicks() : CYCLE;
	}

	@Override
	public float getTrayOffset(float partialTicks) {
		if (!biologicalTray)
			return super.getTrayOffset(partialTicks);
		float tickCycle = animationTicks - partialTicks - (animationInward ? 0 : 5);
		float progress = Mth.clamp(tickCycle / (getAnimationCycle() - 5) * 2 - 1, -1, 1);
		progress = 1 - progress * progress;
		return progress * progress;
	}

	@Override
	public ItemStack getRenderedBox() {
		if (!biologicalTray)
			return super.getRenderedBox();
		int halfway = getAnimationCycle() / 2;
		if (animationInward)
			return animationTicks <= halfway ? ItemStack.EMPTY
				: previouslyUnwrapped.isEmpty() ? heldBox : previouslyUnwrapped;
		return animationTicks >= halfway ? ItemStack.EMPTY : heldBox;
	}

	public static int getCycleTicks() {
		return CBConfigs.SERVER.bioPackager.cycleTicks.get();
	}
}
