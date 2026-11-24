package com.nobodiiiii.createbiotech.content.biopackager;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.capturenet.ArmCaptureBehaviour;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxItem;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.compat.computercraft.events.PackageEvent;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerItemHandler;
import com.simibubi.create.content.logistics.packager.PackagingRequest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
	private boolean chainReturnAnimation;
	private boolean biologicalTray;
	private boolean chamberAnimation;
	private boolean checkLegacyArmCapture;

	public BioPackagerBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.BIO_PACKAGER.get(), pos, state);
	}

	@Override
	public void tick() {
		if (level instanceof ServerLevel && checkLegacyArmCapture) {
			checkLegacyArmCapture = false;
			if (isCreaturePackage(heldBox) && level.getBlockEntity(worldPosition.above()) instanceof ArmBlockEntity arm
				&& ArmCaptureBehaviour.hasNet(arm)) {
				captureOutput = heldBox;
				setChanged();
			}
		}
		boolean wasAnimating = animationTicks > 0;
		super.tick();
		if (!(level instanceof ServerLevel server) || animationTicks != 0)
			return;
		if (!wasAnimating) {
			tryReleaseFromAttachedInventory();
			return;
		}

		if (chamberAnimation) {
			// The blast chamber owns this tray until it advances or clears its operation.
			animationTicks = 1;
			return;
		}
		biologicalTray = false;
		if (chainReturnAnimation) {
			if (!animationInward) {
				releaseCreature(server);
				startBiologicalAnimation(heldBox, true);
			} else {
				ItemStack leftover = insertIntoAttachedInventory(heldBox);
				heldBox = ItemStack.EMPTY;
				chainReturnAnimation = false;
				triggerStockCheck();
				dropAtPackager(leftover);
			}
			notifyUpdate();
		} else if (hasCaptureOutput()) {
			depositCapture();
			if (!heldBox.isEmpty())
				super.wakeTheFrogs();
		}
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
		if (!chainReturnAnimation && !chamberAnimation && !hasCaptureOutput())
			super.wakeTheFrogs();
	}

	@Nullable
	public IItemHandler getAttachedInventory() {
		IItemHandler handler = targetInventory.getInventory();
		return handler instanceof PackagerItemHandler ? null : handler;
	}

	public boolean canStartPacking() {
		return level instanceof ServerLevel && !isRemoved() && !chainReturnAnimation && !chamberAnimation
			&& animationTicks == 0 && heldBox.isEmpty() && queuedExitingPackages.isEmpty();
	}

	public boolean startPacking(ItemStack box) {
		if (!canStartPacking() || !isCreaturePackage(box))
			return false;
		captureOutput = box.copyWithCount(1);
		startBiologicalAnimation(captureOutput, true);
		notifyUpdate();
		return true;
	}

	@Override
	public boolean unwrapBox(ItemStack box, boolean simulate) {
		if (!isCreaturePackage(box) || !canReleaseCreature())
			return false;
		if (!simulate) {
			chainReturnAnimation = true;
			startBiologicalAnimation(box.copyWithCount(1), false);
			computerBehaviour.prepareComputerEvent(new PackageEvent(box.copyWithCount(1), "package_received"));
			notifyUpdate();
		}
		return true;
	}

	@Override
	public void attemptToSend(@Nullable List<PackagingRequest> requests) {
		if (requests != null) {
			super.attemptToSend(requests);
		} else {
			tryReleaseFromAttachedInventory();
		}
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
			if (isCreaturePackage(box) && unwrapBox(box, false)) {
				triggerStockCheck();
				return;
			}
			ItemStack remainder = handler.insertItem(slot, box, false);
			dropAtPackager(ItemHandlerHelper.insertItemStacked(handler, remainder, false));
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

	private boolean hasCaptureOutput() {
		// The inherited inventory can take the package when the tray is idle.
		return !heldBox.isEmpty() && heldBox == captureOutput;
	}

	private void depositCapture() {
		ItemStack remainder = insertIntoAttachedInventory(heldBox);
		if (remainder.getCount() == heldBox.getCount())
			return;
		heldBox = remainder;
		captureOutput = remainder;
		triggerStockCheck();
		notifyUpdate();
	}

	private ItemStack insertIntoAttachedInventory(ItemStack stack) {
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

	private void startBiologicalAnimation(ItemStack box, boolean inward) {
		setTrayAnimation(box, ItemStack.EMPTY, inward, getCycleTicks());
	}

	public void startChamberAnimation(ItemStack trayBox, boolean inward) {
		chamberAnimation = true;
		startBiologicalAnimation(trayBox.copy(), inward);
		notifyUpdate();
	}

	public void clearChamberAnimation() {
		chamberAnimation = false;
		setTrayAnimation(ItemStack.EMPTY, ItemStack.EMPTY, true, 0);
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
		chainReturnAnimation = false;
	}

	@Override
	public void destroy() {
		if (chamberAnimation)
			heldBox = ItemStack.EMPTY;
		super.destroy();
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		chainReturnAnimation = tag.getBoolean("ChainReturnAnimation");
		chamberAnimation = tag.getBoolean("ChamberAnimation");
		biologicalTray = tag.contains("BiologicalTray") ? tag.getBoolean("BiologicalTray") : animationTicks > 0;
		animationTicks = Mth.clamp(animationTicks, 0, getAnimationCycle());
		captureOutput = tag.getBoolean("PackedBoxPending") ? heldBox : ItemStack.EMPTY;
		checkLegacyArmCapture = !clientPacket && !tag.contains("PackedBoxPending") && animationInward
			&& !chainReturnAnimation && isCreaturePackage(heldBox);
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.putBoolean("ChainReturnAnimation", chainReturnAnimation);
		tag.putBoolean("ChamberAnimation", chamberAnimation);
		tag.putBoolean("BiologicalTray", biologicalTray);
		tag.putBoolean("PackedBoxPending", hasCaptureOutput());
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
